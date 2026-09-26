> **Status 2026-09-25:** Für die Benutzerwartung ist ein In-App-Weg in den
> Einstellungen ergänzt. Die API bleibt `internal`; es gibt weiterhin keinen
> ContentProvider und keinen exportierten Service.

# Wörterbuch-Prüfung und externe API — Plan

Stand: 2026-09-25. **Interner Vertrag mit In-App-Wartung; kein externes IPC.**

## 1. Ist-Zustand

- Android/Kotlin, minSdk 24, reine JVM-SuggestionEngine; JUnit 4/Robolectric vorhanden.
- Sieben FrequencyWords-Assets (de/en/es/fr/it/pt/nl), je 6.000 Einträge,
  insgesamt 42.000. Format: `wort häufigkeit`.
- Prüfung aller Dateien: keine fehlerhaften Zeilen, Duplikate, nichtpositiven
  Häufigkeiten, Großbuchstaben oder Abweichungen von Unicode NFC.
- Die Engine filtert kurze/nicht ausschließlich alphabetische Tokens.
  Abgewiesene Zeilen nach dieser grundlegenden Regel: de 0, en 24, es 21,
  fr 24, it 22, nl 21, pt 23. Nicht pauschal löschen: erst Sprachregeln klären.
- Deutsche Stichprobe: `wörterbuch`, `tastatur`, `schnittstelle`, `programme`,
  `cline`, `keytab`, `api` fehlen; `programm`, `projekt`, `agent`, `agenten` vorhanden.
  Dies ist ein Abdeckungsbefund, keine umfassende Qualitätsmessung.
- Präfixsuche scannt alle Basiswörter. Fuzzy-Suche besitzt bereits Zeichenindizes.
  Kein nachgewiesener Präfix-Funktionsfehler. Ein experimenteller Präfixindex
  wurde zurückgenommen; Engine, Tests und Sprachdaten bleiben im Ausgangszustand.
- Nutzerwörter (max. 500) und gelernte Bigramme (max. 2.000) liegen serialisiert
  unter einem gemeinsamen SharedPreferences-Schlüssel, nicht sprachgetrennt.
- Kein Wörterbuch-IPC vorhanden. FileProvider ist nicht öffentlich; der IME-Service
  ist durch BIND_INPUT_METHOD geschützt. Keine INTERNET-Berechtigung.

## 2. Optimierungen in Reihenfolge

### P0 — Datenintegrität und Privatsphäre vor externer Freigabe

1. Gemeinsame `DictionaryRepository`-Instanz pro App-Prozess: Laden, Lernen,
   Änderungen und Speichern über denselben seriellen Executor. IME und API dürfen
   keine unabhängigen, einander überschreibenden Engines verwalten.
2. Nutzerwörter/Bigramme sprachgetrennt speichern, Format versionieren. Vor Migration
   Altbestand sichern; seine Sprache ist nicht rekonstruierbar. Einmalige
   Zuordnung durch Nutzer, keine automatische Vervielfältigung in sieben Sprachen.
3. Import validieren: unterstützte Sprache, NFC, erlaubte Token, maximale Länge,
   endliche positive Gewichte und Mengenlimits. `restoreUserDict` prüft bisher
   weder NaN/Unendlich noch Eintragszahl oder Wortformat ausreichend.
4. Laden mit Generationskennung: veraltete Ladevorgänge dürfen nach Sprachwechsel
   nicht die neu gewählte Engine veröffentlichen. Veröffentlichung auf UI-Thread;
   Fehlerstatus statt stillschweigend leerem Basiswortschatz.
5. Lernen/Vorschläge/Autokorrektur in Passwortfeldern und bei
   IME_FLAG_NO_PERSONALIZED_LEARNING entlang des gesamten Aufrufpfads prüfen.
   Trail-Schutz allein belegt keinen Schutz der Lernpersistenz.
6. Änderungen atomar speichern, Schreibvorgänge bündeln und beim Lifecycle-Wechsel
   zuverlässig abschließen. Aktuell wird pro Wort der gesamte Bestand serialisiert.

### P1 — Suchleistung und Ranking

- Referenzmessung auf Zielgerät: kalter Start, Präfixe, Fuzzy, Next-Word und Lernen;
  alle sieben Korpora, mit/ohne vollen Nutzerbestand. p50/p95, Speicher und Ladezeit.
- Sortierter Basiswortindex mit binärer Untergrenze: Kandidatensuche von O(N)
  auf O(log N + Treffer); Scoring/Sortieren und User-Wörter bleiben separat.
  Trie erst bei Bedarf. Gleiche Scores deterministisch auflösen. Nicht behaupten,
  die gesamte Anfrage werde dadurch logarithmisch.
- Fuzzy-Recall separat testen (Einfügung, Löschung, Tausch, erster Buchstabe),
  bevor Zeichenindizes geändert werden; nicht mit Präfixoptimierung vermischen.
- Next-Word-Fallback nicht auf beliebige HashMap-Einträge begrenzen; stärkste
  Kandidaten reproduzierbar auswählen. Decay an Lernereignisse statt an
  `userFreq.size % 25` koppeln und Bigramm-Alterung definieren.
- Akzeptanz: identische Kandidaten/Scores zum Referenzscan außer ausdrücklich
  getesteten Tie-Breaks; messbarer p95-Gewinn ohne relevante Start-/Speicherregression.

### P2 — Wortqualität

- Kuratierte optionale Fachwortliste `de-tech`, getrennt vom Untertitelkorpus.
  Quellen/Lizenz und Gewichte dokumentieren, keine erfundenen Korpusfrequenzen.
- Größeren Allgemeinkorpus erst anhand festgelegter Alltag-/Technik-Testfälle prüfen.
- Einbuchstabenwörter, Apostrophe und Bindestriche sprachabhängig behandeln.
- FrequencyWords-Herkunft und CC-BY-SA-4.0-Hinweise bei Erweiterung/Export erhalten.

## 3. API-Architektur

Erster Umfang: **Wörterbuchzugriff**, nicht Fernsteuerung. Kein Lesen aktueller
Eingabefelder, Clipboard-Verlauf, Shell-Ausführen oder Text-Injizieren.

```text
Android-Client → DictionaryApiService (Binder/AIDL) → DictionaryRepository ← IME
                           ↑
              freigegebene Android-Bridge
                           ↑
              lokaler MCP-stdio-Adapter ← Cline
```

- Versionierter gebundener Service mit engen AIDL-Methoden; Android-SDK genügt.
  IME-Service bleibt geschützt. Freigabe standardmäßig aus.
- Onboarding in sichtbarer KeyTab-Activity: explizite Nutzerbestätigung und
  widerrufbare Rechte pro aufrufender UID samt Paket-/Signaturzuordnung.
  Jede Methode prüft echte Binder-Caller-UID, niemals Paketnamen aus dem Payload.
  Shared-UID-Apps sind nicht sicher voneinander trennbar; Zustimmung berücksichtigt das.
- Eine reine signature-Permission erlaubt nur gleich signierte eigene Clients,
  nicht beliebige Drittprogramme. Für diese braucht es die beschriebene Zulassung.
- Service funktioniert ohne aktive Tastatur. Begrenzte, paginierte Anfragen,
  keine Repository-I/O auf dem UI-Thread, serialisierte Schreiboperationen.
- CLI/MCP kann nicht allein aufgrund eines JSON-Formats Android-Binder ansprechen.
  Eine ausdrücklich freigegebene Android-Bridge vermittelt.
- Bridge ↔ Termux: Machbarkeitsprototyp für lokalen Transport, Sandbox, proot und
  Lifecycle. Socket-Erreichbarkeit nicht voraussetzen. Falls Loopback-TCP nötig:
  nur 127.0.0.1, gepaarter zufälliger Sitzungsschlüssel, Zeit-/Größenlimits,
  Widerruf. Loopback allein ist keine Authentifizierung.
- Netzwerkberechtigung nur in optionaler Bridge, nicht in der KeyTab-Kern-App.
  MCP per stdio; SDK/Laufzeit erst nach Prüfung der Cline-/Termux-Umgebung wählen.
- Remote-Zugriff später über explizit eingerichteten authentifizierten Tunnel,
  kein öffentlich lauschender Wörterbuchserver. Shizuku ist keine API-Authentifizierung.

## 4. Vertrag v1 (Vorschlag)

Alle Anfragen tragen `apiVersion: 1` und `requestId`. Wörterbuchoperationen
benötigen `language` aus de/en/es/fr/it/pt/nl; unbekannte Sprache ergibt Fehler
statt stiller Rückfall auf Deutsch. Antwort enthält `dictionaryRevision`.

| Operation / MCP-Tool | Eingabe | Recht / Semantik |
|---|---|---|
| capabilities / keytab_capabilities | keine | Versionen, Sprachen, gewährte Rechte |
| suggest / keytab_suggest | prefix, previousWord?, limit=3, personalized=false | dictionary.read; nur übergebener Kontext |
| lookup / keytab_lookup | word | dictionary.read; bekannt, Herkunft |
| stats / keytab_dictionary_stats | language | Zähler/Revision, keine Wortlisten |
| listUserWords / keytab_user_words | cursor?, limit≤100 | personal.read, explizit freigegeben |
| addUserWord / keytab_add_word | word, expectedRevision | personal.write; idempotentes Hinzufügen, kein Gewicht-Hochzählen |
| deleteUserWord / keytab_delete_word | word, expectedRevision | personal.write; zugehörige gelernte Bigramme entfernen |
| importPreview / keytab_import_preview | entries, mode=merge | import; validiert, verändert nichts |
| importCommit / keytab_import_commit | previewId, expectedRevision | import + Bestätigung in KeyTab, atomar |

- `personalized=false` verwendet ausschließlich Basis-/freigegebene Fachwortdaten.
  `true` benötigt personal.read, da Vorschläge private Wörter offenbaren können.
- Suggest-Limit 1..20, Wort/Präfix maximal 32 Zeichen im ersten Vertrag,
  nur definierte Tokens; je Request maximal 64 KiB. Import in begrenzten Paketen,
  Gesamtlimit und Ablauffrist für Vorschauen; keine beliebigen Dateipfade.
- Schreibzugriff standardmäßig verweigert; Import/Export/Löschen großer Mengen
  separate Rechte und Nutzerbestätigung. Gewichte nicht frei extern manipulieren.
- Fehlercodes: INVALID_ARGUMENT, UNSUPPORTED_VERSION, UNSUPPORTED_LANGUAGE,
  PERMISSION_DENIED, NOT_READY, REVISION_CONFLICT, TOO_LARGE, RATE_LIMITED, INTERNAL.
- Scores sind Rankingwerte, keine Wahrscheinlichkeiten. Keine Inhalte in Logs;
  Audit nur Operation, Client, Zeitpunkt und Ergebnis. Keine Request-Kontexte speichern.
- Startlimit 10 Leseanfragen/s pro Client, Burst 20, bounded Queue; Schreiblimits
  separat. Werte im Gerätetest prüfen. Widerruf beendet auch bestehende Sitzungen.

Beispiel des geplanten JSON-Vertrags, noch kein aufrufbarer Endpunkt:

```json
{"apiVersion":1,"requestId":"demo-1","operation":"suggest","language":"de","prefix":"pro","previousWord":null,"limit":3,"personalized":false}
```

## 5. Umsetzung und Abnahme

1. P0 samt Migrationstests und Datenschutzprüfung abschließen.
2. P1 messen, Präfixindex erst mit Gleichheits-/Performancevergleich übernehmen.
3. Lesende Binder-API: unbekannte UID, Rechteentzug, falsche Sprache, übergroße
   Payloads, Parallelzugriff, Prozessneustart und Service ohne aktive IME testen.
4. Bridge-Prototyp und MCP-stdio: capabilities/lookup/suggest; Cline-End-to-End
   gegen explizit übergebene Testdaten, ohne Zugriff auf echte Eingabefelder.
5. Schreib-API mit Revisionskonflikten, atomarem Import, Bestätigung und Rollback.
6. Optional Fachwortliste nach Qualitätsvergleich. Kein ungeprüfter Austausch
   der vorhandenen Sprachdateien, keine Installation auf dem Gerät in dieser Aufgabe.

Offene Produktentscheidung: Soll später Zugriff auf Snippets/Notizen hinzukommen?
Dieser Plan beschränkt v1 bewusst auf das Wörterbuch.

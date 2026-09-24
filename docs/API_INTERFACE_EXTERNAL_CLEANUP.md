# Schnittstellen-Dokumentation: Externe Aufräum-API für das gelernte Benutzer-Wörterbuch (KeyTab)

Stand: 2026-09-18. V1. Vollständige Dokumentation der Programmierschnittstelle.

> **Status-Banner (2026-09-22): NICHT VERDRAHTET — bewusst kein Transportweg.**
> Die API ist inzwischen `internal` und hat außerhalb der Tests keinen
> Konsumenten. Ein `ContentProvider`/exportierter Service wurde **abgelehnt**
> (Begründung in §0). Dieses Dokument beschreibt damit einen **internen Vertrag**,
> keine von außen erreichbare Schnittstelle.

## 0. Entscheidung (2026-09-22): keine externe Exposition

| Option | Bewertung |
|---|---|
| `ContentProvider`, `exported="true"`, normale Permission | **abgelehnt** — jedes App-Paket könnte das gelernte Wörterbuch lesen/schreiben; die App ist bewusst offline und ohne Netzwerk-Permission |
| `ContentProvider` mit `signature`-Permission | **abgelehnt** — funktioniert für den gedachten Konsumenten (adb/Cline/Shizuku-Werkzeuge) nicht, da die Shell keine Signatur-Permission der App hält; bleibt zusätzlich neue Angriffsfläche |
| `dangerous`-Permission + Provider | **abgelehnt** — der Nutzer müsste einer Tastatur Zugriff auf „gelernte Wörter“ erlauben; schlechter Deal für ein Nebenfeature |
| In-App-Weg (Einstellungen/Activity) | **offen** — richtiger Ort, wenn der Nutzer das Aufräumen selbst anstoßen soll |
| **V1-Status: `internal` API + Tests, nicht verdrahtet** | **gewählt** — kein neuer Abflusspfad, kein toter Transportweg; Entfernung möglich, falls kein Konsument entsteht |

Prüfbar (Kommandozeile):

```bash
grep -rn "LearnedDictionaryApi" app/src/main   # nur die Datei selbst, kein Aufrufer
grep -n "provider" app/src/main/AndroidManifest.xml   # kein Provider für die API
```

## 1. Zweck
Diese Schnittstelle ermöglicht es einem externen Programm, das **gelernte Benutzer-Wörterbuch** von KeyTab kontrolliert zu prüfen, vorzubereiten und nach expliziter Bestätigung zu ändern.

Gegenstand ist ausschließlich das **gelernte Feld**:
- gelernte Einzelwörter
- gelernte Bigramme

Die Schnittstelle berührt **nicht** den Basiswortschatz und beabsichtigt auch nicht, ihn zu ersetzen.

## 2. Zuständigkeitsgrenzen
### Was die Schnittstelle kann
- Zustand des gelernten Bereichs lesen
- Vorabprüfung von Lösch- und Ergänzungsvorschlägen
- revisionsgesicherte Ausführung nach Bestätigung
- Diagnose bekannter Artefaktmuster

### Was die Schnittstelle absichtlich nicht ist
- Kein automatischer Massenlöschservice
- Kein automatisches Hochzählen von Gewichten
- Kein Ersatz für Basiswörterbuch
- Keine vollautomatische externe Parallelzugriffsverwaltung über mehrere unabhängige Clients
- Keine Sprachverwaltung für gelernte Daten in diesem Vertrag; Sprache ist erforderlich, aber das gespeicherte Feld ist nicht sprachgetrennt

## 3. Architektur im Projekt
- API-Objekt: `LearnedDictionaryApi`
- Standort: `app/src/main/java/com/piotv/keytab/LearnedDictionaryApi.kt`
- Tests: `app/src/test/java/com/piotv/keytab/LearnedDictionaryApiTest.kt`
- Basis-Engine: `SuggestionEngine`
- Revisionskonzept: `SuggestionEngine.revision` plus `incrementRevision()`

Die Schnittstelle arbeitet direkt auf `SuggestionEngine` und liest oder ändert dessen gelernten Bereich.
```kotlin
data class LearnedEntry(
    val word: String,
    val weight: Double,
    val isBigramPair: Boolean = false
)
```
- `word` ist der gespeicherte Schlüssel
- `weight` ist der lerninterne Wert
- `isBigramPair` markiert, ob der Eintrag aus dem Bigrammbereich stammt

### Stats
```kotlin
data class Stats(
    val userWordCount: Int,
    val bigramCount: Int,
    val revision: Long
)
```
- `userWordCount`: Anzahl gelernter Einzelwörter
- `bigramCount`: Anzahl gelernter Bigramme
- `revision`: aktuelle Revisionsnummer des gelernten Bereichs

### CleanupOperation
```kotlin
sealed class CleanupOperation {
    data class Delete(val word: String, val expectedRevision: Long) : CleanupOperation()
    data class Add(
        val word: String,
        val expectedRevision: Long,
        val weight: Double = 0.05,
        val allowMerge: Boolean = true
    ) : CleanupOperation()
}
```
- `Delete`: Löschvorschlag für einen bekannten Schlüssel
- `Add`: Ergänzungsvorschlag für einen neuen oder bereits vorhandenen Eintrag
- `expectedRevision`: Revisionsnummer, die zum Zeitpunkt der Vorschau gültig sein muss
- `weight`: gewünschtes Gewicht bei einem Add; kein automatisches Hochzählen
- `allowMerge`: ob bereits vorhandene Einträge akzeptiert werden sollen

### BatchPreview
```kotlin
data class BatchPreview(
    val previewId: String,
    val expectedRevision: Long,
    val wouldDelete: List<String>,
    val wouldAdd: List<Pair<String, Double>>,
    val rejections: List<String>
)
```
- `previewId`: Identifikator für die erstellte Vorschau
- `expectedRevision`: Revisionsnummer, mit der die Vorschau erstellt wurde
- `wouldDelete`: Liste der Schlüssel, die beim Commit gelöscht würden
- `wouldAdd`: Liste der Einträge, die beim Commit hinzugefügt würden
- `rejections`: Liste der Reasons, warum Einträge nicht übernommen wurden

Eigenschaft:
```kotlin
val wouldChange: Boolean get() = wouldDelete.isNotEmpty() || wouldAdd.isNotEmpty()
```

### CleanupResult
```kotlin
sealed class CleanupResult {
    data class Ok(val revision: Long, val deletedCount: Int, val addedCount: Int) : CleanupResult()
    data class Rejected(val reason: String, val currentRevision: Long) : CleanupResult()
}
```
- `Ok`: Das Schreiben wurde durchgeführt; neue Revision und Mengen werden zurückgegeben
- `Rejected`: Das Schreiben wurde nicht durchgeführt; Grund und aktuelle Revision werden zurückgegeben

### RevisionConflictException
```kotlin
sealed class RevisionConflictException(val current: Long, val expected: Long) : Exception()
```
- Markiert eine Revisionsabweichung

## 5. Operationen im Detail

### 5.1 Lesen

#### stats
```kotlin
fun stats(store: SuggestionEngine): Stats
```
Gibt Anzahlen für gelernte Einzelwörter, gelernte Bigramme und die aktuelle Revision zurück.

Anwendung:
- Übersicht über Größe und Zustand des gelernten Bereichs
- Basis für Entscheidung, ob eine Bereinigung sinnvoll ist

#### listAll
```kotlin
fun listAll(store: SuggestionEngine): List<LearnedEntry>
```
Listet alle gelernten Einzelwörter und Bigramme auf.

Anwendung:
- Vollständige Inspektion des gelernten Bereichs
- Vorbereitung von Kandidatenlisten für Bereinigung

#### lookup
```kotlin
fun lookup(store: SuggestionEngine, word: String): LearnedEntry?
```
Sucht einen Eintrag nach Schlüssel.

Rückgabe:
- `null`, wenn der Schlüssel nicht im gelernten Bereich existiert
- sonst `LearnedEntry` mit Gewicht und Bigramm-Markierung

Anwendung:
- Eine Bewertung, ob ein Wort bereits gelernt ist
- Unterscheidung, ob es ein Einzelwort oder Bigramm ist

## 6. Schreibvorschau

### batchPreview
```kotlin
fun batchPreview(
    store: SuggestionEngine,
    deletes: List<CleanupOperation.Delete>,
    adds: List<CleanupOperation.Add>
): BatchPreview
```
Erstellt eine reine Vorabprüfung ohne Schreibzugriff.

Verarbeitung:
- Prüft Revisionssicherheit je Operation
- Prüft Existenz bei Löschvorschlägen
- Prüft Formatanforderungen bei Add-Vorschlägen
- Prüft Gewicht, Länge, Token, Limits
- Führt keine Änderung durch

Rückgabe enthält:
- was passieren würde
- was abgelehnt wurde und warum

Anwendung:
- externe Programme müssen vor jeder Änderung eine Preview erstellen
- Preview ist Voraussetzung für spätere Commit-Entscheidung

## 7. Schreiben

### applyPreview
```kotlin
fun applyPreview(store: SuggestionEngine, preview: BatchPreview, confirm: Boolean): CleanupResult
```
Führt die Vorschau nur aus, wenn `confirm = true` und die Revision noch stimmt.

Verhalten bei `confirm = false`:
- Schreibzugriff wird nicht ausgeführt
- Rückgabe signalisiert Ablehnung

Verhalten bei Revisionskonflikt:
- Schreibzugriff wird nicht ausgeführt
- Rückgabe signalisiert aktuellen Konflikt

### addWord
```kotlin
fun addWord(store: SuggestionEngine, op: CleanupOperation.Add): CleanupResult
```
Praktische Variante für eine einzelne Ergänzung.

Ablauf:
- interne Preview erstellen
- sofort mit `confirm = true` ausführen
- Ergebnis zurückgeben

### removeWord
```kotlin
fun removeWord(store: SuggestionEngine, op: CleanupOperation.Delete): CleanupResult
```
Praktische Variante für eine einzelne Löschung.

Ablauf:
- interne Preview erstellen
- sofort mit `confirm = true` ausführen
- Ergebnis zurückgeben

## 9. Wichtige Konstanten und Grenzen

```kotlin
private const val MAX_WORD_LEN = 32
private const val MAX_ADD_PER_COMMIT = 200
private const val MAX_DELETE_PER_COMMIT = 500
```

Bedeutung:
- `MAX_WORD_LEN`: maximale Eintragslänge im gelernten Feld
- `MAX_ADD_PER_COMMIT`: Obergrenze für neue Einträge pro Commit
- `MAX_DELETE_PER_COMMIT`: Obergrenze für den Gesamtbetrieb pro Commit

Diese Grenzen sind bewusst eingebaut, damit externe Programme nicht unkontrolliert schreiben können.

## 10. Revisionsmodell

### Warum Revisionsnummer
Das gelernte Feld kann von mehreren Stellen geändert werden:
- direkt durch Nutzerlernen
- durch externes Programm
- durch interne Wiederherstellung

Daher wird jede Änderung über eine Revisionsnummer geschützt.

### Wesentliche Eigenschaft
Jede Änderung erhöht `revision`.

### Konsequenz für externe Programme
- externe Programme müssen die Revision vor einer Preview erfassen
- beim Commit muss die Revision noch stimmen
- bei Abweichung wird abgelehnt

Das verhindert stillschweigendes Überschreiben durch veraltete Vorschläge.

## 11. Voraussetzungen für externes Aufräumen

Minimaler Ablauf:
1. `stats` oder `listAll` aufrufen
2. Kandidaten identifizieren
3. für jeden Kandidaten `lookup` prüfen
4. `looksLikeArtifact` nur als Diagnose nutzen
5. `batchPreview` mit Lösch- und ggf. Add-Liste erstellen
6. Preview-Ergebnis prüfen
7. nur bei sinnvoller Preview und expliziter Entscheidung `applyPreview(..., confirm = true)` aufrufen
8. bei gewünschten Ergänzungen `addWord` nach separater Preview nutzen

Explizit nicht erlaubt als automatischer Kurzschluss:
- Änderung ohne Preview
- Änderung ohne Bestätigung
- Blindes Löschen nach Heuristik ohne Kontrolle
- Automatisches Hochzählen ohne Bedarf

## 12. Beispielhafte Anfrage/Ausführung (konzeptionell)

### Beispiel: Löschvorschau
```kotlin
val e = SuggestionEngine(baseWords)
// ... laden, lernen, etc. ...

val preview = LearnedDictionaryApi.batchPreview(
    e,
    deletes = listOf(
        LearnedDictionaryApi.CleanupOperation.Delete("clebwerte", e.revision)
    ),
    adds = emptyList()
)

if (preview.wouldChange && preview.rejections.isEmpty()) {
    val result = LearnedDictionaryApi.applyPreview(e, preview, confirm = true)
    // Ergebnis prüfen
}
```

### Beispiel: Ergänzung
```kotlin
val addOp = LearnedDictionaryApi.CleanupOperation.Add(
    word = "termux",
    expectedRevision = e.revision,
    weight = 0.05,
    allowMerge = true
)
val result = LearnedDictionaryApi.addWord(e, addOp)
```

### Beispiel: Diagnose
```kotlin
val suspect = listOf("clebwerte", "termux", "shinstallire")
val artifacts = suspect.filter { LearnedDictionaryApi.looksLikeArtifact(it) }
```

## 13. Typische Fehler und Ablehnungsgründe

- `DELETE revision conflict: ...`
- `DELETE unknown: ...`
- `ADD revision conflict: ...`
- `ADD too long: ...`
- `ADD invalid token: ...`
- `ADD invalid weight: ...`
- `ADD already exists: ...`
- `ADD limit exceeded: ...`
- `BATCH too large: ...`
- `preview not confirmed`
- `revision conflict at apply time`

Bedeutung:
- Revisionskonflikte signalisieren, dass die Basis sich geändert hat
- Unbekannte Löschungen werden abgelehnt
- Formatfehler werden vor Schreibzugriff abgelehnt
- Bestätigungspflicht wird konsequent durchgesetzt

## 14. Was die Schnittstelle garantiert

- Nur das gelernte Feld wird gelesen/geändert
- Preview erfolgt ohne Schreibzugriff
- Schreiben nur nach Bestätigung
- Revisionssicherheit wird geprüft
- Grenzen für Länge, Anzahl und Gewicht werden eingehalten
- Bekannte Artefaktmuster können klassifiziert werden

## 15. Was die Schnittstelle nicht garantiert

- Automatische Korrektheit externer Klassifikation
- Vollständige Erkennung aller möglichen Artefakte
- Sprachgetrennte Semantik im gelernten Feld
- Konsistenz über mehrere unabhängige externe Clients hinweg ohne Koordinationsschicht
- Automatische Wartung des Basiswortschatzes

## 16. Heuristik-Regel für Artefakte

Die Diagnose `looksLikeArtifact` ist bewusst eng und erklärbar.

Aktuelle Muster:
- bekannte Fragmentfolgen wie `clebwerte`, `clearbewerte`, `shinstallire`, `actionok`, `klickba`, `shweiterpero`, `eingesuten`, `shon übertroffenund`, `clbewerte`, `habenkann`, `dichbrauche`, `machdich`, `unteruche`, `precupearsepero`, `drchscjeinender`
- bestimmte Phrasenmuster ohne sinnvollen Abstand

Es ist bewusst **keine** automatische Sweep-Logik.

## 17. Warum nicht alles auf einmal automatisiert wird

Das ist eine bewusste Entscheidung:
- das gelernte Feld ist flach und veralteter Lernrückstand kann ohne Kontrolle die Ranke verfälschen
- externes Aufräumen ohne Preview/Commit/Revision würde schnell parallelen Schreißkonflikten und korruptem Bestand ermöglichen
- eine echte automatische Massenlöschung ist hier kein vertrauenswürdiger Standard

Daher ist die Schnittstelle so gebaut, dass externes Aufräumen möglich ist, aber nicht unkontrollierbar.

## 18. Vollständigkeit

Diese Dokumentation führt alle öffentlichen Elemente von `LearnedDictionaryApi` auf:
- Lesen
- Schreibvorschau
- Schreiben
- Validierung
- Grenzen
- Revisionsmodell
- Beispielablauf
- Fehler

Sie ist explizit so angelegt, dass externe Programme sie direkt verstehen und anwenden können.

## 19. Verknüpfung zu Code und Test

Code:
- `LearnedDictionaryApi` ist das offizielle API-Objekt
- `SuggestionEngine` ist die Basisbereitstellung

Tests:
- `LearnedDictionaryApiTest` dokumentiert das Verhalten durch konkrete Fälle
- dabei werden Lesen, Preview, Ablehnung, Apply, Artefakt und Kontrolle abgedeckt

Dokumentation:
- `docs/TODO_EXTERNAL_CLEANUP_API.md`
- `docs/DICTIONARY_API_PLAN_FINAL.md`
- `docs/IMPLEMENTATION_STATUS_EXTERNAL_CLEANUP.md`

## 20. Fazit

Die Schnittstelle ist dokumentiert für:
- Lesen des gelernten Bereichs
- kontrollierte Vorschau
- kommentierte Ausführung
- validierte Ergänzung
- diagnostische Klassifikation

Sie ist bewusst so entworfen, dass externes Aufräumen möglich, aber nicht unkontrollierbar ist.



### isNotLearnable
```kotlin
private fun String.isNotLearnable(): Boolean
```
Prüft, ob ein Token den grundlegenden Lernregeln entspricht.

Regeln:
- nicht leer
- Länge zwischen 2 und `MAX_WORD_LEN`
- nur Buchstaben oder Leerzeichen

### looksLikeArtifact
```kotlin
fun looksLikeArtifact(word: String): Boolean
```
Diagnosefunktion für bekannte Artefaktmuster.

Zweck:
- erkennen, ob ein Eintrag wie ein unerwünschter Lernrückstand aussieht
- Diagnose, nicht automatische Löschgesetzgebung


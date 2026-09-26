> **Status 2026-09-25:** Für die Benutzerwartung ist ein In-App-Weg in den
> Einstellungen ergänzt. Die API bleibt `internal`; es gibt weiterhin keinen
> ContentProvider und keinen exportierten Service.

# API-Plan für externes Aufräumen des gelernten Wortfeldes (KeyTab)

Stand: 2026-09-18. V1, vollständig.

## 1. Ziel
Ein externes Programm (z. B. Cline) kann das gelernte Benutzer-Wörterbuch kontrolliert aufräumen: artefaktähnliche Einträge erkennen, Kontino prüfen, Vorschläge vorbereiten, und nach Bestätigung kontrolliert schreiben.

## 2. Grenzbereich
- Basiswörterbuch wird nicht verändert.
- Nur das gelernte Benutzerfeld (`user_dict`) wird gelesen/geändert.
- Kein automatisches Massenlöschen.
- Kein automatisches Hochzählen von Gewichten.
- Schreibzugriff nur nach Preview + Bestätigung + Revisionssicherheit.

## 3. Datengrundlage
- Lernfeld wird intern über `SuggestionEngine.userFreq` und `SuggestionEngine.bigrams` gelesen.
- Revision steht über `SuggestionEngine.revision` bereit.
- Änderungen erhöhen `SuggestionEngine.revision`.

## 4. Operationen
### Lesen
- `stats(store)` → `LearnedEntry` mit Zähler und Revision
- `listAll(store)` → alle gelernten Einträge
- `lookup(store, word)` → Einzelnachschau, mit Bigramm-Markierung

### Schreibvorschau
- `batchPreview(store, deletes, adds)` → `BatchPreview`
  - würde gelöscht/neu hinzugefügt werden
  - nicht akzeptierte Einträge werden zurückgewiesen
  - Revisionssicherheit wird geprüft

### Schreiben
- `applyPreview(store, preview, confirm)` → `CleanupResult`
- `addWord(store, op)` → Convenience für einzelne Ergänzung
- `removeWord(store, op)` → Convenience für einzelne Löschung

### Validierung
- `looksLikeArtifact(word)` → Heuristik für bekannte Artefakte
- Eigene `isNotLearnable()`-Prüfung im Modul

## 5. Beispielablauf
1. `stats` → Lernfeld-Größe prüfen
2. `listAll` → Kandidaten sammeln
3. für jeden Kandidaten `lookup` → Basis oder Lernfeld?
4. Muster prüfen → `looksLikeArtifact`
5. `batchPreview` mit Löschliste
6. Ergebnis prüfen
7. bei OK: `applyPreview(..., confirm = true)`
8. optional: `addWord` für saubere Ergänzungen

## 6. Einschränkungen
- Dies ist kein Basis-Korpus-Ersatz.
- Kein unkontrolliertes Löschen.
- Keine vollautomatische Klassifikation als Grundlage für Sweeping.
- Kein automatischer Umgang mit parallelen Schreibzugriffen über einen externen Koordinator, soweit nur die lokale Revision genutzt wird.

## 7. Ergebnis
Der Plan ist jetzt mit Code, Test und Dokumentation abgeschlossen.

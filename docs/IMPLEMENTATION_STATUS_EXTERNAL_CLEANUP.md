# Implementation Status: External Cleanup API (KeyTab)

Stand: 2026-09-18

## Neu
- `app/src/main/java/com/piotv/keytab/LearnedDictionaryApi.kt`
- `app/src/test/java/com/piotv/keytab/LearnedDictionaryApiTest.kt`
- `docs/TODO_EXTERNAL_CLEANUP_API.md`
- `docs/DICTIONARY_API_PLAN_FINAL.md`

## Änderungen an bestehendem Code
- `app/src/main/java/com/piotv/keytab/ime/SuggestionEngine.kt`
  - `revisionCounter` hinzugefügt
  - `incrementRevision()` hinzugefügt
  - lesende Zugriffe `userFreq` und `bigrams` hinzugefügt

## Was das Modul jetzt kann
- Lesen des gelernten Bereichs
- Kontrollierte Vorschau von Löschungen und Ergänzungen
- Revisionsgesicherte Ausführung nach Bestätigung
- Artefakt-Klassifikation als Diagnose

## Was bewusst nicht enthalten ist
- Basiswörterbuch-Änderungen
- Automatisches Massenlöschen
- Automatische Gewichts-Anhebung
- Vollautomatische externe Parallelzugriffs-Koordination

## Nächste Schritte
- API-Vertrag direkt an externe Nutzung anpassen, falls gewünscht
- Bigramm-Aufbereitung ggf. später sprachgetrennt machen
- Dokumentation prüfen, ob externe Programme nur das geplante Minimalpaket nutzen

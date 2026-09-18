# TODO-Checkliste: Externe Aufräum-API für KeyTab

Ziel: Ein externes Programm (z. B. Cline) kann das gelernte Benutzer-Wörterbuch kontrolliert aufräumen, ohne Basis-Korpus zu berühren und ohne unkontrolliertes Massenlöschen oder automatisches Gewicht-Hochzählen.

Stand: 2026-09-18

## 1. Grundlagen
- [x] Ziel definiert: externes Aufräumen des gelernten Bereichs (`LearnedDictionaryApi`)
- [x] Zuständigkeitsgrenze festgelegt: kein Basiswortschatz, kein vollautomatisches Sweeping
- [x] Revisionsmodell eingeführt: `SuggestionEngine.revision` + `incrementRevision()`
- [x] Lesedaten zugänglich gemacht: `userFreq` und `bigrams` als read-only Zugriff

## 2. Code
- [x] `LearnedDictionaryApi` implementiert:
  - [x] Lesen: `stats`, `listAll`, `lookup`
  - [x] Schreibvorschau: `batchPreview`
  - [x] Schreibende Ausführung: `applyPreview`, `addWord`, `removeWord`
  - [x] Validierungshilfen: `isNotLearnable`, `looksLikeArtifact`
- [x] `SuggestionEngine` angepasst:
  - [x] `revisionCounter` eingeführt
  - [x] `incrementRevision()` eingeführt
  - [x] Lesende Zugriffsmethoden für `userFreq`/`bigrams` hinzugefügt
- [x] Unit-Test `LearnedDictionaryApiTest` erstellt:
  - [x] Lesetests
  - [x] Preview-/Rejections-Tests
  - [x] Revisionskonflikt-Test
  - [x] Apply/Confirm-Tests
  - [x] Artefakt-Klassifikationstests
  - [x] Vollständiger Aufräum-Durchlauf

## 3. Validierung
- [x] Artefakte werden als Diagnose klassifiziert, nicht als automatische Löschliste
- [x] Preview muss bestätigt werden, sonst wird nichts geschrieben
- [x] Jeder Schreibzugriff prüft Revisionskonflikt
- [x] Unbekannte Löschungen werden abgelehnt, nicht stillschweigend ignoriert
- [x] Gewichte werden nicht frei hochgezählt

## 4. Offen
- [ ] API-Vertragsformate direkt an Cline-Core anpassen (z. B. JSON-RPC/stdio, falls extern genutzt)
- [ ] Bigramm-Erkennung/Behandlung ggf. verbessern, falls Lernfeld später sprachgetrennt werden soll
- [ ] Externe Nutzung ggf. mit expliziten Rechten/Freigaben versehen
- [ ] Dokumentation überprüfen, ob externe Programme tatsächlich nur das geplante Minimalpaket nutzen

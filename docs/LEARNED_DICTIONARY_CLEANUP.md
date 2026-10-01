# Gelerntes Wörterbuch — Aufräumen (Archiv)

> Stand: v0.15 (2026-09-30). Kurzfassung eines abgeschlossenen Themas.
> Ersetzt die früheren Plan-/Status-Dokumente
> (`API_INTERFACE_EXTERNAL_CLEANUP.md`, `DICTIONARY_API_PLAN*.md`,
> `TODO_EXTERNAL_CLEANUP_API.md`, `IMPLEMENTATION_STATUS_EXTERNAL_CLEANUP.md`).

Die Pflege des **gelernten Benutzer-Wörterbuchs** läuft **ausschließlich in der App**:

- `app/src/main/java/com/piotv/keytab/LearnedDictionaryApi.kt` — interne API
  (Lesen, Preview, revisionsgesichertes `applyPreview(confirm = true)`,
  Artefakt-Diagnose `looksLikeArtifact`).
- Erreichbar über die Einstellungen: **„🧹 Gelerntes Wörterbuch prüfen“**
  (Vorschau + Bestätigung; der Basiswortschatz bleibt unverändert).
- Test: `app/src/test/java/com/piotv/keytab/LearnedDictionaryApiTest.kt`.

## Bewusste Entscheidung (2026-09-22/25): kein externer Transport

Ein externer Zugriff (`ContentProvider`/exportierter Service) wurde **abgelehnt**:

- Die App ist offline und ohne `INTERNET`-Berechtigung; ein Provider wäre ein
  neuer Abflusspfad für genau die Daten, die lokal bleiben sollen.
- Eine `signature`-Permission würde für adb/Shizuku-Werkzeuge nicht funktionieren.

Die API bleibt daher `internal`. Sollte je ein externer Konsument entstehen,
wäre das eine eigene, neu zu bewertende Produktentscheidung.

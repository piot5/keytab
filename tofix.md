# KeyTab – Fehlerliste (Review 2026-10-14, Stand: uncommittete Änderungen auf `3689f8d`)

Geprüft: `app/src/main` (IME-Service, Panels, Prediction, Theme, FileManager;
ca. 40 Dateien vollständig + Pattern-Sweeps über `removeCallbacks`,
Prefs-Listener, `getInt`/`text[0]`).

Legende: **[G] = gravierend** (Datenverlust, Crash, Funktionsbruch),
**[N] = normal** (UI-Fehler, Jank, Wartung, Edge-Cases).

---

## Status-Update v0.20 (2026-10-19)

Im Zuge der v0.20-Aufräumarbeiten behoben bzw. abgeglichen:

- **[behoben] G2** – Tab-Zustand nach Rebuild: `TabController.setup()` setzt
  `currentKind` zurück (Leiste und Panel zeigen wieder denselben Tab).
- **[Feature entfernt] G7** – Die Swipe-„Schaltplan“-Preview (Likely-Knoten +
  Kanten-Overlay) ist komplett ausgebaut: `SwipeEdgeOverlay`/`SwipeEdgeDrawing`
  gelöscht, `applySwipeLikely`/`paintNodes`/`wasLikelyHit`/`drawEdges`/
  `previewNodes`/`isLikelyHit` entfernt (sie war toter Code und crashte nach dem
  Reaktivieren).
- **[behoben] N13** – Batch-Limit nutzt eigene Konstante `MAX_BATCH_PER_COMMIT`.
- **[behoben] N22** (aus `tofix-review-2026-10-16.md`) – toter Code
  `ThemeController.maybeRebuildForThemeChange()` + Feld `appliedThemeVersion` entfernt.
- **[Falschmeldung] G1** – der Bigramm-Key ist `"prev word"` (Leerzeichen, **nicht**
  `SEP_FIELD`); der Roundtrip ist durch `SuggestionEngineTest`
  (`Bigramme ueberleben den Roundtrip`) abgedeckt — kein Datenverlust.
- Bereits in v0.20 behoben: **G6** (Decay via `learnCount`), **G8** (Artefakt-Heuristik).
- **Weitere tote Feature-Reste entfernt (2026-10-20):** `LikelyHighlightSection`
  (nicht verdrahteter Einstellungs-Abschnitt), verwaiste Swipe-Farben
  (`swipe_color`/`swipe_edge_color`, `KIND_SWIPE`/`KIND_SWIPE_EDGE`), und
  `KEY_LIKELY` von `gaming_mode` auf `likely` migriert.

Behoben (2026-10-20, im Zuge der Code-Review-Umsetzung):
- **[behoben] G3** – Vollbild-Höhe bei Tab-Wechsel: `TabController.applySelected()`
  ruft beim Verlassen des Vollbilds `KeyboardCollapse.expand()` auf.
- **[behoben] G4** – Clipboard-Init-Load läuft jetzt unter `persistMutex`
  (serialisiert gegen den Write-Pfad); `CancellationException` wird nicht mehr verschluckt.
- **[behoben] G5** – Leere Commits: `TextCommitController.commit()` und
  `LetterButtonTouchHandler.commitTap()` behandeln leere Texte defensiv.

Weiter offen: **N1–N12, N14–N15** sowie aus `tofix-review-2026-10-16.md`
die Punkte **G9–G11, N16–N21, N23–N25**.

Verifiziert 2026-10-20 (Code-Review der Working Copy):
- **N11** (SuggestionEmojiBrowser): `showAsDropDown(anchor, 0, +4dp)` positioniert
  unterhalb des Ankers — in der IME (unten am Bildschirm) kein Fensterplatz.
  **Noch offen.**
- **N12** (FileManagerModel.isSameOrInside): vergleicht `absolutePath` statt
  `canonicalPath` — Symlinks/`..` können den Selbstkopie-Schutz umgehen.
  **Noch offen.**
- **N15** (KeyboardCollapse.expand): stellt nur `ime_tabs` wieder her, nicht
  `bottom_row`/Tastatur-Ebenen — inkonsistente Sichtbarkeiten. **Noch offen.**
- **N23** (PanelHeights.applyNormalHeights): bei `raw <= 0` (Messung noch nicht
  möglich) wird vor der Auswertung von `overridePx` zurückgekehrt — Config-Höhe
  wird beim ersten Aufruf verworfen. **Noch offen.**
- **N24** (PanelHeights.maximizedPanelHeight): endet mit
  `minOf(wanted, free).coerceAtLeast(1)` — liefert 1 statt 0 bei `free <= 0`.
  **Noch offen.**
- **N25** (KeyAnimations.expandRow/collapseRow): starten je Aufruf einen neuen
  `ValueAnimator` auf denselben `layoutParams`; kein Abbruch/Rebuild-Schutz.
  **Noch offen.**
- **N1** (hideKeyboard-Symbol), **N5** (TrailManager Prefs-Listener-Leak),
  **N10** (TrailLogic) — Dateien unverändert, Items stehen. **Noch offen.**

Build-Fix (2026-10-20): `MainActivity.setupLanguageSpinner()` referenzierte
`prefs` aus `onCreate()`-Scope, das in der ausgelagerten Methode nicht
verfügbar war → Compile-Fehler. Behoben durch `val prefs = Prefs.of(this)` in
der Methode. `:app:testDebugUnitTest` grün.

---

## Gravierende Fehler

### G1 – Gelernte Bigramme gehen bei jedem Neustart verloren
`app/src/main/java/com/piotv/keytab/ime/SuggestionEngine.kt:154-163 (learn), 286-307 (serialize/restore)`

- `learn(prev, word)` baut den Bigramm-Key als
  `"${prev}$SEP_FIELD${word}"` (SEP_FIELD = `\u0002`).
- `serializeUserDict()` schreibt `k + SEP_FIELD + f + SEP_ENTRY`.
- `restoreUserDict()` splittet jeden Eintrag an SEP_FIELD und verwirft
  alles mit `parts.size != 2`.
- Ein Bigramm-Eintrag enthält **zwei** SEP_FIELD (eines im Key) →
  `split` liefert 3 Teile → **jedes Bigramm wird beim Laden still
  verworfen**.

Folge: Next-Word-Prediction verlernt nach jedem App-/Engine-Neustart
alles; nur Einzelwörter überleben. Fix: beim Restore den **letzten**
SEP_FIELD als Trennzeichen nehmen (z. B. `lastIndexOf`) oder den Key
escapen.

### G2 – Falscher Tab-Zustand nach Tastatur-Rebuild (Theme-Wechsel)
`app/src/main/java/com/piotv/keytab/ime/TabController.kt:61-119`,
`KeyTabImeService.kt` (Feld `tabController` überlebt Rebuilds)

- `setup()` baut die Tab-Leiste neu (Tab 0 = abc ist danach gewählt),
  wendet aber `applyTabVisibility(root, currentKind)` mit dem **alten**
  `currentKind` an (z. B. FILES).
- Ergebnis: Leiste zeigt „abc“ markiert, sichtbar ist aber das
  Files-/Editor-/etc.-Panel.
- Tippen auf „abc“ tut dann **nichts** (Tab bereits gewählt → kein
  `onTabSelected`), der Nutzer muss erst woanders hin- und zurücktippen.

Repro: Files-Tab öffnen → Theme toggeln (☾) → inkonsistenter Zustand.
Fix: in `setup()` `currentKind` auf den tatsächlich gewählten Tab
setzen (bzw. `select(currentKind)` danach aufrufen).

### G3 – Vollbild-Tag wird bei Tab-Wechsel gelöscht, die Höhe aber nicht
`TabController.kt:130-140 (applySelected)`, `KeyboardCollapse.kt:145-161 (expand)`

- `applySelected()` setzt `R.id.maximized_state` immer auf `false`,
  ruft aber **kein** `KeyboardCollapse.expand()` auf.
- `applyTabVisibility()` setzt im Normal-Pfad **keine** Normalhöhe
  (`applyNormalHeights` läuft nur in `setup()` und in `expand()`).
- Ergebnis: Aus dem Vollbild per Tab-Wechsel zurück → Panel behält die
  Maximalhöhe, obwohl der Vollbild-Zustand als beendet gilt.
  Layout-Bruch bis zum nächsten Rebuild/hideKeyboard-Toggle.

Fix: beim Tab-Wechsel `expand()` aufrufen (oder in
`applyTabVisibility` die Normalhöhe wiederherstellen).

### G4 – Clipboard-Historie kann beim Start verloren gehen (Race)
`app/src/main/java/com/piotv/keytab/ime/ClipboardPanel.kt:54-72 (init), 174-193 (persistAsync)`

- Der Init-Load läuft async (`withContext(ioDispatcher)`), aber der
  Write-Pfad **nicht** unter `persistMutex` gegen den Load
  synchronisiert.
- `capture()` (z. B. schneller Wechsel auf den Clip-Tab mit
  `canAutoCapture`) vor Load-Ende: `history` ist nicht mehr leer →
  die geladene Datei-Historie wird **verworfen**
  (`if (history.isEmpty() && ...)`), und `persistAsync()` hat die Datei
  bereits mit nur dem neuen Eintrag **überschrieben**.
- Umgekehrt: `clear()` vor Load-Ende → Load stellt (`history` ist leer)
  die alte Liste wieder her, obwohl der Nutzer gelöscht hat.

Folge: im schlechtesten Fall ist die gesamte Historie weg.
Verstärkt dadurch, dass `KeyTabExecutors.io` ein Single-Thread ist und
der Engine-Load (6000 Wörter) das Zeitfenster vergrößert.
Fix: Init-Load ebenfalls unter `persistMutex` laufen lassen und statt
Verwerfen **mergen**.

### G5 – Crash-Pfad bei leerem Commit-Text
`TextCommitController.kt:24-40`, `LetterButtonTouchHandler.kt:235-240`,
`KeyboardBinder.kt:202-206 (tapLetter)`

- `tapLetter()` kann `""` liefern
  (`baseLetters[btn] ?: btn.text…firstOrNull() ?: return ""`).
- `TextCommitController.commit("")`: fällt durch bis
  `pm.currentTypedWord.isNotEmpty() && !text[0].isLetter()` →
  **StringIndexOutOfBoundsException**, sobald ein Wort angetippt ist.
- `commitTap()`: `trailManager?.snap(letter.first()…)` →
  **NoSuchElementException** bei `""` (wenn `commitText("")` vorher
  durchkam).


---

## Normale Fehler

### N1 – ⇱/⇲-Symbol wird beim Maximieren nicht aktualisiert
`KeyTabImeService.kt:344-367 (hideKeyboard)`, `TabController.kt:126-140`

- Das Symbol von `key_maximize`/`sug_hide` wird nur in `applySelected()`
  gesetzt. `hideKeyboard()` (Vollbild-Toggle) aktualisiert es nicht.
- Nach Maximieren steht weiter „⇲“, obwohl bereits Vollbild aktiv ist
  (bis zum nächsten Tab-Wechsel). Fix: Symbol auch in `hideKeyboard()`
  setzen (oder `select()` auch im Collapse-Zweig aufrufen).

### N2 – `KeyboardCollapse.collapse` bricht nach Sichtbarkeits-Umschaltung ab
`KeyboardCollapse.kt:79-122`

- Sichtbarkeiten (Panels GONE, `bottom_row` etc.) werden gesetzt, **dann**
  `if (maximized <= 0) return` — ohne die Höhe zu setzen.
- Ergebnis: Tastatur weg, Panel aber auf Normalhöhe → scheinbar „leeres“
  Keyboard, sobald `maximizeAvailableHeight ≤ chromeHeight`.
  Fix: Höhen-Check vor den Sichtbarkeits-Änderungen.

### N3 – User-Dict wird auf dem Main-Thread serialisiert (Jank beim Tippen)
`WordPredictionManager.kt:325-329 (persistUserDict)`

- `eng.serializeUserDict()` (bis 500 Wörter + 2000 Bigramme String-Aufbau)
  läuft auf dem **Aufruf-Thread (= Main)** bei **jedem** Wortabschluss;
  nur das Schreiben liegt auf dem Executor.
  Fix: Serialisierung in den `ioExecutor.execute { … }`-Block ziehen.

### N4 – Datei-I/O auf dem Main-Thread im IME-Hot-Path
`SettingsConfig.kt:76-98 (importIfChanged)`, `KeyTabConfig.kt:76-79 (load)`,
aufgerufen aus `onStartInput`/`onStartInputView` und `onCreateInputView`

- Pro Feldfokus: mehrere `stat`-Calls (`isFile`, `lastModified`,
  `length`) + bei Änderung `readText()` + SHA-256 — alles auf Main.
  `stat` ist auf Android (FUSE/sdcardfs) kein „gratis“ Call.
- Fix: Prüfung cachen/entprellen oder in den Hintergrund verlagern.

### N5 – `TrailManager` wird nie disposed (Ghost-Listener)
`TrailManager.kt:45-64, 230-238`, `KeyTabImeService.kt:142-172 (releasePanels)`,
Verifiziert: genau **ein** `register…`-Aufruf, **kein** `unregister…` im Code.

- Jeder Rebuild (Theme-Wechsel!) erzeugt einen neuen `TrailManager` mit
  neuem Prefs-Listener; der alte wird nie abgemeldet und nicht `clear()`t.
- Der alte Manager teilt sich die `baseLetters`-Map-Instanz mit dem
  neuen: sein `applyToKeyboard()` färbt bei Pref-Änderungen ggf. die
  **neuen** Buttons mit altem Zustand; ausstehende Fade-Runnables laufen
  nach `onDestroy` weiter.
- Fix: `dispose()` (unregister + `fadeHandler.removeCallbacks…`) und in
  `releasePanels()`/`onDestroy()` aufrufen.

### N6 – `WordPredictionManager.engine` ist nicht `@Volatile`
`WordPredictionManager.kt:47, 80-103`, `KeyTabImeService.kt:224`

- `engine = loaded` wird im IO-Thread geschrieben, `engine` auf Main
  gelesen (u. a. `predictionManager?.engine` direkt in `onStartInputView`).
  Sichtbarkeit ist nur über den `mainHandler.post`-Pfad garantiert.
- Daneben TOCTOU: `releaseEngine()` (Main) kann zwischen Request-ID-Check
  und `engine = loaded` (IO) laufen → freigegebene Engine wird doch gesetzt.
  Praktisch harmlos (nächster `loadEngine` lädt neu), aber unsauber.
- Fix: `engine` als `@Volatile` (oder alles unter einem Lock).

### N7 – `EditorPanel.refreshGutter`: mögliche Endlos-Post-Schleife
`EditorPanel.kt:264-279`

- `if (et.width == 0 && text.isNotEmpty()) et.post { refreshGutter() }`
  repostet sich, solange das Feld keine Breite hat. Wird das Panel nie
  gemessen (z. B. dauerhaft GONE), laufen die Posts endlos → CPU-Last.
  Fix: begrenzte Versuche oder nur posten, wenn attached/messbar.

### N8 – `FileManagerModel` wird vom Panel nicht benutzt (doppelte Logik)
`FileManagerModel.kt` vs. `FileManagerPanel.kt:43-66, 227-263`

- Das Panel implementiert Navigation/Back-Stack/Persistenz **selbst**;
  `FileManagerModel` existiert (inkl. Tests) parallel dazu und droht zu
  driften (z. B. prüft `FileManagerModel.navigate` `isDirectory/canRead`,
  `FileManagerPanel.navigate` nicht).
- Fix: Panel auf `FileManagerModel` umstellen oder das Model entfernen.

Schwer auslösbar im Normalbetrieb (Map wird in `hook()` gefüllt und in
`releasePanels()` geleert — alter Listener + leere Map reicht), aber ein
echter Absturzpfad. Fix: `if (text.isEmpty()) return` in `commit()` und
`firstOrNull()` in `commitTap()`.

### N9 – `looksLikeArtifact` trifft normale Wörter („und“-Heuristik)
`LearnedDictionaryApi.kt:132-143`

- `word.contains("und") && !word.contains(" ")` markiert
  Grund/Wunder/Hund/Mund/rund/… als Artefakt → `batchPreview` lehnt
  solche ADDs ab. Derzeit nur Aufräum-API (V1, „nicht verdrahtet“),
  trotzdem falsch. Fix: Wortgrenzen statt Substring prüfen.

### N10 – Trail/Trace überlebt Feldwechsel gleichen Typs
`TrailManager.kt:71-78 (setEditorInfo)`,
`SwipeManager.kt:43-50 (editorInfo-Setter)`

- Verglichen werden nur `inputType`/`imeOptions`; Wechsel zwischen zwei
  Feldern gleichen Typs (Notizen → anderes Textfeld) leert die Spur
  nicht — entgegen dem Doc („Wechselt das Feld, wird die Spur geleert“).
  Passwort-Felder sind durch anderes `inputType` abgedeckt, daher nur
  Kosmetik/Datensparsamkeit. Fix: zusätzlich Feld-Identität werten oder
  Doku anpassen.

### N11 – Emoji-Raster-Popup öffnet unterhalb des Ankers
`SuggestionEmojiBrowser.kt:205-211 (showEmojiGrid)`

- `showAsDropDown(anchor, 0, +4dp)` positioniert **unter** dem Anker —
  in der IME (unten am Bildschirm) ist dort kein Fensterplatz, das
  Popup wird beschnitten oder erscheint nicht. (Im Gegensatz dazu nutzt
  `LetterPopup` Fenster-Koordinaten + `isClippingEnabled = false`.)
- Fix: oberhalb positionieren (`showAtLocation` wie `LetterPopup`) oder
  negativen Offset + Clipping aus.

### N12 – `FileCopyRules.isSameOrInside` ohne kanonische Pfade
`FileManagerModel.kt:111-116`

- Vergleicht `absolutePath` statt `canonicalPath`: Symlinks
  (`/sdcard` → `/storage/emulated/0`) und `..`-Segmente können den
  Selbstkopie-Schutz umgehen → mögliche endlose Rekursion beim
  Ordner-Einfügen. Fix: `canonicalPath` (mit try/catch-Fallback).

### N13 – Batch-Limit nutzt falsche Konstante
`LearnedDictionaryApi.kt:196`

- `wouldDelete.size + wouldAdd.size > MAX_DELETE_PER_COMMIT` begrenzt
  den Gesamt-Batch mit dem Delete-Limit (500) statt einem eigenen
  Batch-Limit; irreführend benannt, Verhalten zufällig richtig.

### N14 – Mehrzeichen-Commits landen nicht in der Wort-Buchführung
`TextCommitController.kt:29-37`

- Nur `text.length == 1 && isLetter()` füttert `onCharacter`; längere
  Buchstaben-Commits (Autofill, Stapeltasten) lassen `currentTypedWord`
  stehen → Folge-Lernen (`prev`-Bigramm) basiert auf veraltetem Puffer.

### N15 – `KeyboardCollapse.expand()` stellt nur `ime_tabs` wieder her
`KeyboardCollapse.kt:145-161`

- `bottom_row`/Tastatur-Ebenen bleiben wie im Vollbild; allein
  aufgerufen hinterlässt `expand()` inkonsistente Sichtbarkeiten.
  Heute nur via `hideKeyboard()` + `select()` abgedeckt (Kommentar sagt
  es), aber fragil — siehe auch G3.

---

## Ausdrücklich geprüft, kein Befund

- `WordPredictionManager.loadEngine`: Stale-Load-Guard via `engineRequestId`
  i. O. (bis auf N6).
- `SuggestionEngine.suggest`/`completeWord`: `cur[0]` nur im
  Nicht-Leer-Zweig; Basis-Index nur aus Wörtern ≥ 2 Zeichen gebaut.
- `ClipboardPanel.persistAsync`: Mutex + Main-Scope korrekt (bis auf G4);
  `catch (Exception)` fängt auch `CancellationException` — bei
  `serviceScope.cancel()` in `onDestroy()` kann der letzte
  Clipboard-Write verloren gehen (Randfall, klein).
- `TabController.applyTabVisibility` Bottom-Row-Regel, `ImeDialog`-Token,
  `BackgroundImage`-Token-Guard, `restoreUserDict` für Einzelwörter,
  Manifest (kein INTERNET, `allowBackup=false`, BIND_INPUT_METHOD) — i. O.
- `KeyboardBinder` Del-Repeater: Planung **und** Stopp laufen über
  `longPressHandler` (kein Handler-Mismatch); `removeCallbacks` in
  UP/CANCEL vorhanden.
- `ThemeColorImport.readColors`: `getInt`-Fehler werden in
  `ThemePrefs.importColors` per `catch → return false` abgefangen, der
  Editor wird dann nicht angewendet (kein Halb-Import).
- `hideKeyboard()`-Toggle-Logik (`next = !collapsed`) selbst i. O. —
  nur das Symbol fehlt (N1).
- `TrailManager`-Prefs-Listener: genau eine `register…`-Stelle, keine
  `unregister…`-Stelle (siehe N5).

## Fix-Reihenfolge (Vorschlag)

1. G1 (Datenverlust Bigramme) 2. G4 (Clipboard-Race) 3. G2 (Tab-State)
   4. G3 (Vollbild-Höhe) 5. G5 (Empty-Crash-Guards) — danach N3, N4, N5,
   N7, N1/N2, Rest.


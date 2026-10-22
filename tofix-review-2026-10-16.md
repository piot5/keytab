# KeyTab – Fehler-Review 2026-10-16 (unabhängige Nachprüfung von `tofix.md`)

Basis: Arbeitskopie `0f12451` (v0.19 committet, **nicht** gepusht).
Methode: 4 parallele Teil-Reviews (IME-Kern / Eingabe-Pfad / Panels+Storage / Prediction+Theme),
jeder Befund danach **einzeln am Code nachgeprüft**. Vollständig ausgewertet wurden die Reviews
„Eingabe-Pfad" und „Panels+Storage"; die Bereiche IME-Kern (ImeDialog/KeyAnimations/PanelHeights/
ThemeSettingsActivity/SystemBars), Prediction/Theme (ThemeController/sections/ColorWheel/
TrailKeyboardPainter/LiftSpan) und LetterPopup hat der Lead selbst geprüft — dort sind noch
Restpfade offen. Zwei Befunde sind zusätzlich durch Testläufe belegt bzw. widerlegt
(`:app:testDebugUnitTest`, Gradle 8.14.3, BUILD SUCCESSFUL, SuggestionEngineTest 28/28).

Legende: **[G]** gravierend · **[N]** normal · **[X]** widerlegt/überholt

---

## 1. Neue Befunde (in `tofix.md` nicht enthalten)

### G6 – Nutzer-Wörterbuch verliert bei jedem Wortabschluss Gewicht (Decay-Logik)
`SuggestionEngine.kt:171-173`
```kotlin
if (userFreq.size % DECAY_INTERVAL == 0) {           // DECAY_INTERVAL = 25
    for (k in userFreq.keys.toList()) userFreq[k]?.let { cur -> userFreq[k] = cur * DECAY_FACTOR }
}
```
Die Bedingung hängt an der **Größe** des Wörterbuchs, nicht an der Zahl der Lernschnitte.
Ist die Größe z. B. genau 25, greift sie bei **jedem** weiteren `learn()` — auch wenn nur ein
bereits bekanntes Wort abgeschlossen wurde. Beleg (temporärer Probe-Test, wieder entfernt):
```
haus: start=0.441 nach1=0.43218 nach2=0.4235364   (Faktor je Abschluss = 0.98)
```
Folge: gelernte Wörter verlieren dauerhaft Gewicht (nach 100 Abschlüssen ≈ 13 %),
Vorschläge/Ranking driften zurück auf den Basiskorpus. Kein Test deckt die Bedingung ab.
Fix: Lernschnitte zählen (`learnCount++`, `if (learnCount % DECAY_INTERVAL == 0)`) oder Decay
an `revision`/Zeit statt an `size` hängen.

### G7 – „Most-likely"-Swipe-Führung, Kanten-Overlay und grüner Trail sind toter Code (Off-by-one)
`SwipeManager.kt:185-189`
```kotlin
val nodes = SwipePathLogic.previewNodes(
    candidates.map { SuggestionEngine.Suggestion(it.word, it.score) },
    route.length)                 // <— Index beginnt bei route.length
if (nodes.isEmpty()) return
```
`SwipePathLogic.previewNodes` liest `s.word.getOrNull(typedLength + k - 1)` (`SwipePathLogic.kt:133`),
also `getOrNull(route.length)`. Der Scorer lässt aber nur Wörter zu, die **nicht länger als die
Route** sind: `SwipeScorer.kt:99` `if (w.isEmpty() || w.length > route.length) return 0.0`.
→ `getOrNull` liefert **immer** null, `nodes` ist immer leer, `applySwipeLikely()` bricht ab.
Damit werden `paintNodes()`/`drawEdges()` (und `SwipeEdgeDrawing`/`SwipeEdgeOverlay`) im
Produktivpfad nie erreicht und `wasLikelyHit()` (`SwipeManager.kt:248`) ist beim Wischen immer
`false` → `LetterButtonTouchHandler.kt:160-165` färbt den Trail **nie** grün (`ACCEPTED`).
Fix: Kandidatenpool zusätzlich aus Präfix-Vervollständigungen der Route speisen (Wörter ≥ Route)
oder `previewNodes(..., route.length - 1)` gegen längere Wörter bewerten.

### G8 – Einstellungen: „gelernte Artefakte löschen" vernichtet echte deutsche Wörter (irreversibel)
`LearnedDictionaryApi.kt:135` + `MainActivity.kt:248-267`
```kotlin
if (word.contains("und") && !word.contains(" ")) return true   // looksLikeArtifact
...
val suspicious = entries.filter { LearnedDictionaryApi.looksLikeArtifact(it.word) }
batchPreview(engine, suspicious.map { Delete(it.word, revision) }, emptyList())
prefs.edit().putString(Prefs.KEY_USER_DICT, engine.serializeUserDict()).apply()
```
Trifft **jedes** Wort mit der Buchstabenfolge „und": Stunde, gesund, Grund, Grundschule,
Runde, wunderbar, Kunde, hundert, Bundesland … Der Dialog nennt nur die Anzahl; nach OK wird
das Wörterbuch neu persistiert — kein Undo. **Überholt `tofix.md` N9**, das den Punkt als
„V1-API, nicht verdrahtet, harmlos" abtut: der Pfad ist über die Einstellungen verdrahtet.

### G9 – Emoji-Katalog: 2 von 5 Chips pro Seite ohne Klick-Listener
`SuggestionEmojiBrowser.kt:104-117` befüllt `sug_4`/`sug_5` über `applyEmojiChip` (nur `text`,
`visibility`, `EMOJI_TAG`). Klick-Listener existieren ausschließlich für `sug_1..sug_3`
(`SuggestionController.kt:57-76`, Array hat nur 3 Slots). Verifikation: `grep -rn 'sug_4\|sug_5'`
findet nur Farben (`ThemeApplier.kt:189`) und Sichtbarkeit/Text — **kein** `setOnClickListener`.
Bei `CATALOG_PER_PAGE = EmojiModule.PAGE_SIZE = 5` sind 40 % der Katalog-Chips nicht nutzbar
(Eingabe verloren); nur das ▦-Raster bietet diese Emojis.

### G10 – Snippet-Roundtrip zerstört Inhalte (`#`, Backslash)
`SnippetPanel.kt:88` (Lesen schneidet bei `#` ab) vs. `:145-146` (Schreiben maskiert `#` nicht):
„Farbe #FF0000" wird beim nächsten Laden zu „Farbe". Asymmetrische Maskierung: `C:\new` →
`C:\\new` → Rückweg `replace("\\n","\n")` erzeugt `C:` + Zeilenumbruch + `ew`.
`SnippetPanelTest` prüft nur den Lese-Pfad.

### G11 – Theme-Import ist doch ein Halb-Import
`ThemePrefs.kt:227-262` schreibt jeden Wert sofort (`setColor` → `edit().apply()`), `:265`
fängt die Ausnahme nur ab (`catch (_: Exception) { false }`). Ein Nicht-Int im JSON
(`{"light":{"bg":"red"}}`) wirft in `ThemeColorImport.kt:45/63/81` mitten in der Schleife:
Dark-Werte sind bereits dauerhaft geschrieben, die UI meldet „Import fehlgeschlagen"
(`ThemeSettingsActivity.kt:247-250`) → Misch-Theme. **Widerlegt die Aussage in `tofix.md`
(„kein Halb-Import").**

### N16 – Long-Press + Wegziehen committet trotzdem ein Zeichen
`LetterPopup.kt:71` setzt `fallbackChar = showExtras.firstOrNull()?.first()`,
`LetterPopup.kt:161-164` gibt ihn zurück (`?: fallbackChar`) — obwohl der Kommentar direkt
über `pickedChar()` formuliert: „darf NIE mehr einen Fallback liefern".
`highlightCellUnder` setzt beim Verlassen des Popups `highlighted = null`, `onLongPressRelease`
(`LetterButtonTouchHandler.kt:203-210`) committet dann den Fallback (z. B. „ä" für „a") —
Abbruchgeste führt zu unbeabsichtigter Eingabe.

### N17 – `swipeOn` ignoriert die Feldregel
`LetterButtonTouchHandler.kt:64` `swipeManager.swipeEnabled()` prüft nur die Pref, nicht das
Feld (`SwipePathLogic.isSwipeAllowed`). In einem Passwortfeld mit aktivem Swipe wählt der
Long-Press den Auto-Select-Zweig (`:106-116`) und committet sofort das erste Sonderzeichen
statt das Auswahl-Popup zu öffnen.

### N18 – Multi-Touch löscht die Swipe-Route des ersten Fingers (unsicher)
`SwipeManager.kt:63` (`swipeSamples.clear()` in `startSwipe`) bei globaler Sample-Liste ohne
Pointer-ID (`:35`): Der zweite `ACTION_DOWN` verwirft die Route des ersten Fingers; bei
Bewegung ≥ Schwelle nimmt dessen `ACTION_UP` den Swipe-Zweig und committet aus gemischten
Samples. Abhängig von der Pointer-Aufteilung — als unsicher markiert.

### N19 – Bigramm-Bonus im Swipe-Scoring ist wirkungslos
`SwipeScorer.scoreWord` nutzt `prev` (`:107`), beide Produktivaufrufe übergeben es nicht
(`SwipeManager.kt:150`, `:183` → Default `null`). Kandidaten werden nur über Frequenz,
Kompaktheit und Längenanpassung bewertet.

### N20 – Persistenz schreibt nie atomar
`EditorTextState.kt:122-125` (`File.writeText` = truncate + write), ebenso `ClipboardPanel.kt:221`,
`SettingsConfig.kt:183`, `KeyTabConfig.kt:85`, `ThemeSettingsActivity.kt:232`: Prozess-Ende im
Schreibfenster → teilweise geschriebene Datei (Editor-Inhalt, Historie, Config). Kein tmp+rename.

### N21 – Weitere Main-Thread-/Detailpunkte
- `EditorPanel.kt:173` `dir.listFiles()` im Klick-Handler (Main-Thread) ohne `try/catch` →
  Hänger bei großen Verzeichnissen, `SecurityException` reißt den IME-Prozess ab.
- `EditorPanel.kt:245-291`: `refreshGutter()`/`applyHighlight()` laufen pro Tastendruck,
  `editable.toString()` und `StaticLayout` **vor** dem 20k-Guard (`:291`) — Ruckeln/ANR bei 1-MB-Dateien.
- `FileManagerFragment.kt:98-139`: `tabs`/`current` ohne `onSaveInstanceState` → nach Restore
  leerer Zustand (`reload()` kehrt sofort zurück). Fragment ist in `src/main` nicht referenziert (Totpfad).
- `MainActivity.kt:175`/`SettingsConfig.kt:177-184` und `ThemeSettingsActivity.kt:230-246`:
  Datei-I/O in Klick-Handlern auf dem Main-Thread.

---

## 2. Bestätigt weiterhin offen (aus `tofix.md`)

| Punkt | Beleg |
|---|---|
| G2 Tab-Leiste vs. Panel nach Rebuild | `TabController.kt:107` nutzt weiter das alte `currentKind`; entschärft durch `onTabReselected` (`:98-100`) |
| G5 Crash bei leerem Commit-Text | `TextCommitController.kt:34` `!text[0].isLetter()` ohne Guard; `LetterButtonTouchHandler.kt:238` `letter.first()` |
| N2 `collapse` bricht nach Sichtbarkeitswechsel ab | `KeyboardCollapse.kt:94-119` (Früh-Return nach dem Umschalten) |
| N3 User-Dict wird auf dem Main-Thread serialisiert | `WordPredictionManager.kt:325-329` (`serializeUserDict()` vor `ioExecutor`) |
| N5 `TrailManager` wird nie disposed | `TrailManager.kt:63` registriert, **kein** `unregister`/`dispose` |
| N7 `EditorPanel.refreshGutter` Endlos-Post | siehe N21 |
| N12 `isSameOrInside` ohne `canonicalPath` | `FileManagerModel.kt:112-116` |
| N13 falsche Batch-Konstante | `LearnedDictionaryApi.kt:196` |
| N15 `expand()` stellt nur `ime_tabs` her | `KeyboardCollapse.kt:158-160` |
| Clipboard: `catch (Exception)` schluckt `CancellationException` | `ClipboardPanel.kt:224` bei `serviceScope.cancel()` (`KeyTabImeService.kt:415`) |

## 3. Gefixt bzw. widerlegt

- **[X] G1 („Bigramme gehen bei jedem Neustart verloren") ist falsch.** Der Bigramm-Schlüssel ist
  `"$p $w"` (`SuggestionEngine.kt:164`) — ein **Leerzeichen**, kein `SEP_FIELD`; `restoreUserDict`
  (`:300-306`) erhält damit `parts.size == 2`. Hart belegt: `:app:testDebugUnitTest --tests
  com.piotv.keytab.ime.SuggestionEngineTest` → **28 Tests, 0 Failures**, darunter
  „Bigramme ueberleben den Roundtrip durch Serialisierung".
- **[X] N9 ist überholt** (siehe G8): nicht „nicht verdrahtet", sondern über die Einstellungen aktiv.
- **[X] „Theme-Import … kein Halb-Import" ist widerlegt** (siehe G11).
- **[X] G4 (Clipboard-Race): gefixt.** `ClipboardPanel.kt:88-99` mergt den Load, `:203-211` schreibt
  erst nach `historyLoaded`, `:83-88` markiert `clearedBeforeLoad`.
- **[X] G3 (Vollbild-Höhe nach Tab-Wechsel): gefixt.** `TabController.kt:120` setzt
  `maximized_state = false`, `:141-143` ruft `applyNormalHeights(..., maximized = false)`.
  Nebenbefund: der Maximiert-Block `:147-159` ist dadurch toter Code.

---

## 4. GitHub-Befunde (2026-10-16)

- **`v0.18.1`-CI-Lauf abgebrochen:** Run `38797320715` (Tag-Push 19:51:53Z) ist
  `cancelled` — `build-test` nach 91 s (19:53:27Z), danach `coverage`, `static-analysis`,
  `instrumented` ebenfalls `cancelled`; nur `docs` grün. Der **identische Commit**
  (`0baa0afe`) hatte 9 Minuten vorher auf `main` einen vollständig grünen Lauf
  (`38796069657`, alle 5 Jobs `success`). Keine `concurrency:`-Regel in `ci.yml`/`release.yml`
  → der Abbruch wurde manuell ausgelöst (Duplikat-Lauf). Kosmetisch, aber im Actions-Tab rot.
- **9 fehlgeschlagene Runs**, alle vom 2026-09-01/02 (historisch, alte Workflow-Fassung).
- **`v0.18` hat gar keine Runs** (0) — der rote Release-Lauf wurde gelöscht; der Tag
  `v0.18` zeigt weiter auf `4fda1d56` (kaputter Commit, ohne Release).
- **Branch `refactor/phase-5-theme-settings`** (`d735352c`, 2026-09-15): **126 Commits hinter
  `main`, 0 davor**, keine Runs → verwaister Branch, Kandidat zum Löschen.
- **Keine offenen PRs**; `main` auf GitHub steht auf `0baa0afe` (v0.18.1) — die drei lokalen
  Commits (v0.19) sind nicht gepusht, dort läuft also nichts.
- README-Badges (CI/Release) zeigen auf `main` → korrekt grün.

---

## 5. Empfohlene Reihenfolge

1. **G8** (Datenverlust im Wörterbuch, eine Zeile Heuristik) · 2. **G6** (Decay-Bedingung) ·
3. **G7** (Swipe-Preview entweder reparieren oder Doku/Feature entfernen) · 4. **G9** (zwei Listener) ·
5. **G10/G11** (Roundtrip- und Import-Korrektheit) · 6. **N16/N17** (Eingabe-Korrektheit) ·
7. **G5 + N2 + N3 + N5** (Härtung: Guards, Threading, Leak) · 8. Rest (N19-N21), dann `tofix.md`
   an dieser Datei ausrichten.

---

## 6. Nachtrag: die zwei abgebrochenen Teil-Reviews (vom Lead selbst geprüft)

Die Nachläufe für „IME-Kern" und „Prediction+Theme" sind an einem Auth-Fehler des
Teammate-Runners gescheitert (`Unauthorized: re-authenticate your Cline account`).
Die Bereiche hat der Lead deshalb selbst durchgesehen.

### Neue Befunde (klein)

**N22 – `ThemeController.maybeRebuildForThemeChange()` ist toter Code.**
`ThemeController.kt:105-118` samt Feld `appliedThemeVersion` (`:31`) hat **keinen Aufrufer**:
`grep -rn 'themeController\.' app/src/main` findet nur `KeyboardBinder.kt:83`
(`setupButton`). Der Rebuild nach Änderungen in der Theme-Seite läuft real über
`KeyTabImeService.refreshSettings()` (Vergleich des `SettingsConfig.snapshot` in `onStartInput`/
`onStartInputView`) — funktioniert also, die Methode ist nur irreführender Rest.

**N23 – Config-Vorgabe für Panel-Höhen greift beim ersten Aufruf nicht.**
`PanelHeights.applyNormalHeights` (`:158-167`) berechnet erst `raw` (Cache/Measurement) und
kehrt bei `raw <= 0` zurück — **bevor** `overridePx` ausgewertet wird (`:161`). Ist die Messung
beim ersten Aufruf unmöglich (Breite 0, Panel noch nicht gelayoutet), wird die in der
Config-Datei gesetzte Höhe verworfen.

**N24 – Maximiertes Panel kann 1 px hoch werden.**
`PanelHeights.maximizedPanelHeight` endet mit `minOf(wanted, free).coerceAtLeast(1)` (`:344`).
Ist `free <= 0` (große Schrift/kleines Fenster, `chromeHeight` ≥ Band), liefert die Funktion 1
statt 0 — `KeyboardCollapse.collapse` prüft nur `if (maximized <= 0) return` (`:119`) und setzt
dann eine **1-px-Panelhöhe**, obwohl `bottom_row`/andere Panels bereits ausgeblendet wurden
(Teilmenge von N2: Tastatur sichtbar leer, Ausweg nur über die Maximieren-Zeile).

**N25 – Zeilen-Animationen ohne Abbruch/Rebuild-Schutz.**
`KeyAnimations.expandRow`/`collapseRow` (`:35-85`) starten je Aufruf einen neuen `ValueAnimator`
auf denselben `layoutParams`; ein zweiter Aufruf oder ein Keyboard-Rebuild mitten in der
Animation lässt Update-Listener auf einer detached View weiterlaufen (`requestLayout` auf einer
ausgehängten Hierarchie). Klein, aber in jedem Theme-Wechsel-Pfad erreichbar.

### Geprüft, kein Befund
`TrailLogic.kt` (Decay-Formel, Passwort-Regel, `isPersonalizedProcessingAllowed` deckungsgleich
mit `isTrailAllowed`) · `LikelyHighlightLogic.kt` · `LiftSpan.kt` (Baseline wird gesetzt, nicht
addiert — korrekt) · `TrailOverlayDiff.kt` · `TrailKeyboardPainter.kt` (Foreground statt
Background, Differenz-Update korrekt) · `ColorWheelInputLogic.kt` (Pointer-ID-Tracking sauber) ·
`ThemeColorResolver.kt` (`withAlpha` maskiert RGB korrekt, Gradient-Kinds werden vor `getInt`
abgefangen) · `ImeDialog.kt` (Token-Pfad wie im Kommentar, `false` statt Absturz) ·
`SystemBars.kt` · `sections/GradientSection.kt`.

### Noch nicht tief geprüft (Rest-Risiko)
`ThemeApplier.kt`, `ColorWheelView.kt`, `ThemeSettingsActivity.kt`, `ThemePrefs.kt` (nur die
Import-Pfade), `sections/TopSection|TrailSection|ColorSection|BackgroundSection|PreviewSection|
LikelyHighlightSection.kt`, `WordPredictionManager.kt` (außer N3), `MainActivity.kt` (außer G8/N21).

# KeyTab — Testqualität: Einzelbewertung 1–100 + Verbesserungsplan

Stand: 2026-09-25 · Projekt: `projects/keytab` · 50 Testdateien (52 Dateien inkl. 2 Archiv-Kopien) · 491 Tests / 50 Suites · Kover: 67.0 % Line / 53.8 % Branch (zuletzt gemessen 24.09.2026)
Kover zuletzt: 47.6 % Line / 36.4 % Branch (Log) bzw. 46.5 % / 35.4 % (README-Gap-Messung 19. Sep).

## Bewertungs-Rubrik (Summe = 100)

| Kriterium | Max | Was zählt |
|---|---|---|
| Abdeckung (Funktionsbreite) | 25 | Hauptpfade + wichtige Nebenpfade der Klasse abgedeckt? |
| Edge Cases & Validierung | 20 | null/leer/ungültig/Unicode/Grenzen/Negativfälle? |
| Determinismus & Isolation | 15 | Kein Sleep/Flaky, kein echter Storage/Network, Fake/Proxy sauber? |
| Lesbarkeit & Wartbarkeit | 15 | Klare Namen, Arrange-Act-Assert, keine Reflection-Hacks, DRY-Helfer? |
| Regressionswert | 15 | Fixiert der Test einen echten Bug-Vertrag (Crash, Verdopplung, TAB)? |
| Perf/Security-Bewusstsein | 10 | Budget-Assertion, Passwort/No-Personalized-Learning, Lesbarkeit? |

Skala: 90–100 exzellent · 80–89 gut · 70–79 ok mit Lücken · 60–69 schwach · <60 mangelhaft.

## Gesamtübersicht (Datei-Ebene)

| # | Testdatei | Tests (ca.) | Score /100 | Urteil |
|---|---|---|---:|---|
| 1 | `ime/SuggestionEngineTest.kt` | 30+ | **92** | Exzellent — Referenz-Suite |
| 2 | `ime/WordPredictionManagerSuggestionTest.kt` | 6 | **91** | Exzellent — Verdopplungs-Vertrag |
| 3 | `ime/SuggestionReplaceLogicTest.kt` | 17 | **91** | Exzellent — NFC/NFD + Verifikation |
| 4 | `ime/SwipePathLogicTest.kt` | 23 | **90** | Exzellent — rein + Security |
| 5 | `ime/TermuxKeyMatrixTest.kt` | 7 | **90** | Exzellent — klein aber kritisch |
| 6 | `ime/SettingsConfigTest.kt` | 7 | **89** | Sehr gut — Import/Export/Snapshot |
| 7 | `ime/TrailLogicTest.kt` | 18+ | **88** | Sehr gut — Decay + Trace-Atomarität |
| 8 | `ime/SwipeScorerTest.kt` | 19 | **88** | Sehr gut — Teilfolge/Score/AutoCommit |
| 9 | `ime/LiftSpanTest.kt` | 4 | **88** | Sehr gut — Idempotenz-Regression |
| 10 | `ime/TextEditLogicTest.kt` | 20 | **87** | Sehr gut — Wort/Size/Clip-Roundtrip |
| 11 | `ime/ShiftControllerTest.kt` | 6 | **87** | Sehr gut — State-Machine + Debounce |
| 12 | `ime/EditorHighlightLogicTest.kt` | 11 | **86** | Sehr gut — sortiert/nicht-überlappend |
| 13 | `ime/KeyScaleLogicTest.kt` | 10 | **86** | Sehr gut — Stufen + Nachbar-Shrink |
| 14 | `ime/KeyTabConfigTest.kt` | 8 | **85** | Sehr gut — Parse/Serialize/File |
| 15 | `ime/EmojiModuleTest.kt` | 11 | **85** | Sehr gut — Katalog + Paging |
| 16 | `ime/FileManagerPanelTest.kt` | 5 | **84** | Gut — Navigation/BackStack/Prefs |
| 17 | `ime/LearnedDictionaryApiTest.kt` | 8 | **84** | Gut — Preview/Revision/Artefakt |
| 18 | `ime/SnippetPanelTest.kt` | 5 | **84** | Gut — Parser + escaped-newline |
| 19 | `ime/EmojiSuggestionsTest.kt` | 7 | **83** | Gut — Gating Default-aus |
| 20 | `ime/KeyAnimationsTest.kt` | 4 | **83** | Gut — Verlaufserhalt-Regression |
| 21 | `ime/EditorPanelTest.kt` | 8 | **83** | Gut — Cursor/Gutter/Spans |
| 22 | `ime/TerminalPanelTest.kt` | 10 | **82** | Gut — Prompt/cd/Send/Cursor |
| 23 | `ime/ThemeApplierTest.kt` | 7+ | **82** | Gut — stark, aber Reflection-lastig |
| 24 | `ime/InputRouterTest.kt` | 8 | **82** | Gut — Fake-Targets, TAB-Regression |
| 25 | `ime/LikelyHighlightLogicTest.kt` | 5 | **81** | Gut — klein, präzise |
| 26 | `ime/SwipeManagerTest.kt` | 16+ | **81** | Gut — Preview/Seeding/Crash |
| 27 | `ime/SettingsRegressionTest.kt` | 5 | **80** | Gut — Farbrad/Tabs/Bildmodi |
| 28 | `ime/CapsLogicTest.kt` | 6 | **80** | Gut — klein, vollständig im Scope |
| 29 | `ime/FileManagerFragmentTest.kt` | 4 | **78** | Ok — Tabs/Pfad, Sandbox-abhängig |
| 30 | `ime/ClipboardPanelTest.kt` | 10 | **85** | Gut — Capture/Persist/Dedup/Fehlerpfad |
| 31 | `sections/SectionsTest.kt` (3 Klassen) | 10 | **76** | Ok — Toggle/Steps/Icons |
| 32 | `sections/SectionsMoreTest.kt` (4 Klassen) | 10 | **74** | Ok — Build/Refresh, oberflächlich |
| 33 | `ime/TrailPerformanceTest.kt` | 2 | **73** | Ok — Micro-Benchmark, kein Funktions-Assert |
| 34 | `ime/SwipePerformanceTest.kt` | 3 | **72** | Ok — großzügige Budgets, JVM-only |
| 35 | `ime/FileManagerModelTest.kt` | ~7 | **71** | Ok — falsches Paket (`ime/` statt `file/`) |
| 36 | `ime/PanelHeightsTest.kt` | 2 | **68** | Schwach — nur Happy + Null |

Durchschnitt (ungewichtet): **~83.5 / 100** — gut; Clipboard-Persistenz ist durch Coroutine-Migration und den getesteten Fehlerpfad abgedeckt.

---

## Einzelbewertung 1-9 (Top)

### 1) ime/SuggestionEngineTest.kt - 92/100
Abdeckung 24/25, Edge 18/20, Determinismus 15/15, Lesbar 14/15, Regression 12/15, Perf/Sec 9/10.
Staerken: Prefix-Autocomplete, Frequenz-Sortierung, Bigramm-Dominanz, User-Dict-Roundtrip, Damerau-Levenshtein, sentenceStart-Matrix, recentSnippets/recordRecent (Dedup, Limit 3), 6k-Korpus-Perf unter 50 ms.
Abzug: kein Emoji-Pfad (ausgelagert ok), keine korrupten Dict-Inputs, keine Concurrency.
Verbessern: korruptes restoreUserDict, max-Grenzen (0/negativ), Umlaut-Prefixe.

### 2) ime/WordPredictionManagerSuggestionTest.kt - 91/100
Abdeckung 22/25, Edge 19/20, Determinismus 14/15, Lesbar 14/15, Regression 15/15, Perf/Sec 7/10.
Staerken: FakeField mit zwei separat abschaltbaren Loeschwegen fixiert exakt den Verdopplungs-Bug; NFC/NFD-Umlautfall; lieber nichts einfuegen als verdoppeln.
Abzug: kein currentTypedWord-Reset nach Commit, keine Trenner-Varianten.
Verbessern: Mehrfach-Suggestion hintereinander, Cursor-mitten-im-Wort.

### 3) ime/SuggestionReplaceLogicTest.kt - 91/100
Abdeckung 22/25, Edge 20/20, Determinismus 15/15, Lesbar 14/15, Regression 14/15, Perf/Sec 6/10.
Staerken: sameWord (case + NFC), endsWith, action-Matrix, deleteWorked, rawWordLength mit NFD-Laenge (7 vs 6 bei haeuser).
Abzug: keine Emoji/Sonderzeichen in sameWord, keine Perf-Aussage.
Verbessern: Surrogate-Pairs, sehr lange Woerter (>100).

### 4) ime/SwipePathLogicTest.kt - 90/100
Abdeckung 23/25, Edge 18/20, Determinismus 15/15, Lesbar 13/15, Regression 12/15, Perf/Sec 9/10.
Staerken: dedup, charAt (Toleranz-Ring, case-insensitiv), pathLetters, previewNodes, isSwipeAllowed (Passwort, numerisch, NO_PERSONALIZED_LEARNING, null erlaubt), isLikelyHit.
Abzug: Toleranz-Radius als Magic Number, keine Jitter-Samples.
Verbessern: Radius-Boundary (genau auf Kante), Zickzack-Route.

### 5) ime/TermuxKeyMatrixTest.kt - 90/100
Abdeckung 20/25, Edge 17/20, Determinismus 15/15, Lesbar 14/15, Regression 15/15, Perf/Sec 9/10.
Staerken: Proxy-InputConnection; TAB=KEYCODE_TAB (nie commit), Enter=KEYCODE_ENTER, DEL, deleteBeforeKeys=n x DEL, deleteBefore(0)=no-op, null-Connection ohne Crash.
Abzug: nur 7 Tests, keine Shift/Strg-Sequenzen.
Verbessern: getTextBeforeCursor-Edge (0/negativ), Doppel-TAB.

### 6) ime/SettingsConfigTest.kt - 89/100
Abdeckung 23/25, Edge 18/20, Determinismus 14/15, Lesbar 13/15, Regression 13/15, Perf/Sec 8/10.
Staerken: Import-Hash-Gate, invalide Werte erhalten Prefs, Farben/URI-Fragmente ueberleben Kommentare, default entfernt Overrides, fillMissing/completeText (45 Keys), snapshot ignoriert Learned-Words.
Abzug: ein Test mit 3 Assertion-Bloecken (splitten), Temp-File ohne Rule.
Verbessern: aufsplitten, UTF-8/BOM, CRLF-Zeilenenden.

### 7) ime/TrailLogicTest.kt - 88/100
Abdeckung 22/25, Edge 17/20, Determinismus 15/15, Lesbar 13/15, Regression 14/15, Perf/Sec 7/10.
Staerken: alphaForStep (monoton, Cap 140=55 Prozent, defensiv bei 0/negativ), nextStep-null, classifyTypedWord (ACCEPTED/case-insensitiv/Nicht-Buchstaben null, Rot entfernt), Trace-Atomaritaet.
Abzug: zwei Tests simulieren per Map statt echte TrailManager-Calls.
Verbessern: echte snap/clearTrace-Calls, isTrailAllowed-Matrix.

### 8) ime/SwipeScorerTest.kt - 88/100
Abdeckung 22/25, Edge 17/20, Determinismus 15/15, Lesbar 14/15, Regression 12/15, Perf/Sec 8/10.
Staerken: subsequenceMatch (Luecken-Zaehlung, Reihenfolge, laenger-als-Route, leer true), score (leer, null-Engine, Startbuchstabe, absteigend, max), autoCommit, scoreWord=0.
Abzug: Schwellen als Magic Numbers, keine Umlaut-Route.
Verbessern: Schwelle als Konstante + Boundary-Test.

### 9) ime/LiftSpanTest.kt - 88/100
Abdeckung 19/25, Edge 17/20, Determinismus 15/15, Lesbar 13/15, Regression 15/15, Perf/Sec 9/10.
Staerken: Idempotenz (10x ohne Aufsummieren), proportional zu textSize, Measure/Draw-Wechsel stabil, Versatz kleiner als Tastenhaelfte. Vorbildliches KDoc.
Abzug: nur 4 Tests, nur eine Schriftgroesse (22f).
Verbessern: textSize 0/klein/gross, Faktor-Matrix.

## Einzelbewertung 10-18

### 10) ime/TextEditLogicTest.kt - 87/100
Abdeckung 22/25, Edge 18/20, Determinismus 15/15, Lesbar 14/15, Regression 10/15, Perf/Sec 8/10.
Staerken: wordStartIndex/wordDeleteCount (Trailing-WS, nur-WS, Mitte, Clamp 99/-1, Tabs/Newlines), formatSize (B/KB/MB/GB/TB), Clip-Roundtrip (Reihenfolge, Newline zu Space, 81-Zeichen-Ellipse).
Abzug: ein clipDisplayText-Test ist tautologisch (replace vor Call), keine Surrogate-Laengen.
Verbessern: echten Newline-Input, Boundary 80/81/82.

### 11) ime/ShiftControllerTest.kt - 87/100
Abdeckung 21/25, Edge 18/20, Determinismus 15/15, Lesbar 13/15, Regression 13/15, Perf/Sec 7/10.
Staerken: Single-Tap-Toggle, Doppel-Tap zu CapsLock, Tap-bei-CapsLock aus, consume beendet Shift nicht CapsLock, resetForInput(AutoCaps), konfigurierbares Fenster.
Abzug: kein Long-Press-Shift, keine negativen Timestamps.
Verbessern: gleicher Timestamp 2x, Long-Press-Interaktion.

### 12) ime/EditorHighlightLogicTest.kt - 86/100
Abdeckung 21/25, Edge 18/20, Determinismus 15/15, Lesbar 14/15, Regression 11/15, Perf/Sec 7/10.
Staerken: rein ohne Android; Hash/Slash/Shebang, Escape-String, offener String zu Zeilenende, Dezimal/Hex (0x1G stoppt), Keyword-nur-ganz, gemischt sortiert/nicht-ueberlappend, maxChars-Cap.
Abzug: keine Block-Kommentare, keine Single-Quote-Strings.
Verbessern: Keywords in Strings duerfen kein Keyword-Span sein.

### 13) ime/KeyScaleLogicTest.kt - 86/100
Abdeckung 21/25, Edge 17/20, Determinismus 15/15, Lesbar 14/15, Regression 12/15, Perf/Sec 7/10.
Staerken: leer/all-zero zu leer, Top zu MAX, Stufen (0.75 MAX, 0.55 MID, darunter neutral), Nachbar-Shrink nur direkt, Hot-Key nie geschrumpft, Konstanten-Range.
Abzug: Schwellen im Test dupliziert, keine NaN/Inf-Scores.
Verbessern: NaN/negative Scores, 26-Tasten-Vollmatrix.

### 14) ime/KeyTabConfigTest.kt - 85/100
Abdeckung 21/25, Edge 18/20, Determinismus 14/15, Lesbar 13/15, Regression 12/15, Perf/Sec 7/10.
Staerken: Defaults==KeyScaleLogic-Konstanten (Drift-Schutz), Parse (Kommentar, ungueltig zu Default, NaN/Inf/negativ zu Default), entries (Farben mit Kommentar, leeres Image), Serialize-Roundtrip, load missing zu Defaults, writeDefault ueberschreibt nie.
Abzug: keine Duplikat-Key-Tests.
Verbessern: doppelte Keys (letzter gewinnt?), BOM/Whitespace.

### 15) ime/EmojiModuleTest.kt - 85/100
Abdeckung 21/25, Edge 17/20, Determinismus 15/15, Lesbar 14/15, Regression 11/15, Perf/Sec 7/10.
Staerken: Keyword-Match, Reihenfolge, case-insensitiv, unbekannt/leer/max=0 zu leer, max+Dedup, Dev-Themen, Paging (3er-Bloecke, letzte kuerzer, pageOf).
Abzug: Reihenfolge-Brittleness bei Katalog-Update, keine ZWJ-Sequenzen.
Verbessern: Katalog-Groessen-Assertion als Drift-Warnung.

### 16) ime/FileManagerPanelTest.kt - 84/100
Abdeckung 21/25, Edge 16/20, Determinismus 14/15, Lesbar 13/15, Regression 13/15, Perf/Sec 7/10.
Staerken: Start=gespeichertes Verzeichnis (deterministisch via Prefs), dirs-zuerst, Ordner hinein/Zurueck zurueck (VISIBLE/GONE), Up zu Parent, Datei zu absolutem Commit, Prefs-Persistenz.
Abzug: Datei-Test mischt root/root2-Helper, keine Fehlerfaelle.
Verbessern: unlesbar/geloescht/leer/1000+ Dateien.

### 17) ime/LearnedDictionaryApiTest.kt - 84/100
Abdeckung 21/25, Edge 17/20, Determinismus 15/15, Lesbar 13/15, Regression 12/15, Perf/Sec 6/10.
Staerken: Stats, listAll (Wort+Bigramm), batchPreview (unknown/invalid-token/Revisionskonflikt), applyPreview braucht confirm, Artefakt-Heuristik (10 zu true, 8 echte zu false), Volldurchlauf.
Abzug: Heuristik-Listen hartcodiert (Drift), kein Bigramm-Delete.
Verbessern: Gewichts-Boundary, Revision+2.

### 18) ime/SnippetPanelTest.kt - 84/100
Abdeckung 21/25, Edge 17/20, Determinismus 13/15, Lesbar 13/15, Regression 13/15, Perf/Sec 7/10.
Staerken: Defaults-Datei (5 Eintraege, git status zuerst), Parser (Kommentar/Leer/ohne-= ignoriert, escaped newline), Tap zu Commit + Recent-History, leer zu 0.
Abzug: getExternalFilesDir unter Robolectric geraetefern, kein Duplikat-Name.
Verbessern: = im Value, CRLF, sehr lange Values.

## Einzelbewertung 19-27

### 19) ime/EmojiSuggestionsTest.kt - 83/100
Abdeckung 20/25, Edge 17/20, Determinismus 15/15, Lesbar 13/15, Regression 12/15, Perf/Sec 6/10.
Staerken: Default-aus (rueckwaertskompatibel), an zu Emojis hinten + mind. 1 Wort vorn, max-2-Emoji-Slots, leere Basis zu nur Emojis, kein Treffer unveraendert, Space zu prevWord-Schluessel, leer+null zu leer.
Abzug: isLetter-Check schliesst Woerter mit - aus (brittle), keine max-Variation.
Verbessern: suggest mit max=1/5 bei Emoji an.

### 20) ime/KeyAnimationsTest.kt - 83/100
Abdeckung 19/25, Edge 16/20, Determinismus 15/15, Lesbar 12/15, Regression 15/15, Perf/Sec 6/10.
Staerken: fixiert Verlaufserhalt (2-Farben + Orientation), einfarbig zu color, nur-obere-Ecken (Typ-Nachweis), null zu Fallback #1a1a1a. Exzellentes KDoc (colors vs color).
Abzug: Ecken-Radien nicht wirklich geprueft (nur Typ), keine 3-Farben-Verlaeufe.
Verbessern: cornerRadii-Assertion via Shadow, Runtime-Farbwechsel.

### 21) ime/EditorPanelTest.kt - 83/100
Abdeckung 21/25, Edge 16/20, Determinismus 14/15, Lesbar 12/15, Regression 12/15, Perf/Sec 8/10.
Staerken: Insert (Ende/Cursor/Selektion), Delete (Zeichen/Wort/Whitespace-Skip/Anfang-no-op), Gutter (1-2-3 ohne Layout), Highlight-Spans gesetzt/entfernt.
Abzug: directExecutor/inflateKeyboardRoot hier definiert (Fundort ueberraschend), keine Undo/Redo-Tests.
Verbessern: Mehrfach-Delete-Wort, sehr lange Zeilen (10k).

### 22) ime/TerminalPanelTest.kt - 82/100
Abdeckung 21/25, Edge 15/20, Determinismus 13/15, Lesbar 13/15, Regression 13/15, Perf/Sec 7/10.
Staerken: Prompt-Tilde, cd/pwd-Verfolgung, ungueltiges cd zu Fallback Home, Send leert + Echo, leer zu no-op, Insert/Delete/DeleteWord/deleteBefore/cursorContext.
Abzug: echte /system/bin/sh unter Robolectric (CI-Abweichung moeglich), keine Timeout-Tests.
Verbessern: sleep-Timeout, cd .., Exit-Code-Anzeige.

### 23) ime/ThemeApplierTest.kt - 82/100
Abdeckung 22/25, Edge 15/20, Determinismus 13/15, Lesbar 10/15, Regression 14/15, Perf/Sec 8/10.
Staerken: Alpha-Farben im Default-State, Mond/Settings eckig+flach, Tab-Zellen ohne Insets/Ripple, Verlauf+Text-Kontrast.
Abzug: schwere Reflection (mDrawableContainerState bricht bei Android-Update), Walker-Duplikate, Prefs-Clear ohne Before in jedem Test.
Verbessern: Shadow-Drawables statt Reflection, Helper in ThemeTestUtils.

### 24) ime/InputRouterTest.kt - 82/100
Abdeckung 20/25, Edge 15/20, Determinismus 14/15, Lesbar 12/15, Regression 13/15, Perf/Sec 8/10.
Staerken: FakeTarget-Ops-Log, Default=APP, EDITOR/TERMINAL-Routing, onEnter/onTab-Delegation, TAB-nicht-insert-Regression, textBefore-Delegation, isApp-Matrix.
Abzug: Einrueckungsfehler (Test teils eingerueckt), router() ignoriert 3. Target im Destructuring.
Verbessern: ktfmt/detekt, TERMINAL-Ops vollstaendig asserten (nicht nur active != app).

### 25) ime/LikelyHighlightLogicTest.kt - 81/100
Abdeckung 19/25, Edge 16/20, Determinismus 15/15, Lesbar 14/15, Regression 11/15, Perf/Sec 6/10.
Staerken: nextChar (leer zu erster, Teilwort zu folgender, ohne/am-Ende zu null, Gross zu klein), completed (case-insensitiv, Teil ungleich voll, leer zu false).
Abzug: nur 5 Tests, keine Emoji-/Ziffern-Vorschlaege.
Verbessern: Umlaut-Vorschlaege, typedLength groesser Wortlaenge.

### 26) ime/SwipeManagerTest.kt - 81/100
Abdeckung 21/25, Edge 15/20, Determinismus 13/15, Lesbar 11/15, Regression 14/15, Perf/Sec 7/10.
Staerken: Prefs-Gates (swipe/preview default-false), Preview-Faerbung (GradientDrawable), no-Pref keine Faerbung, clearPreview, Passwort-Unterdrueckung, Engine-Set, wasLikelyHit, Seeding (genau 1 Sample, Tap ungleich Swipe), EdgeOverlay-Crash-Regression (idempotentes Clear).
Abzug: Reflection (nodeBackgrounds/baseLetters), button()-Helper ohne ContentDescription.
Verbessern: oeffentliche Test-Hooks statt Reflection (z.B. visibleNodeCount()).

### 27) ime/SettingsRegressionTest.kt - 80/100
Abdeckung 20/25, Edge 15/20, Determinismus 13/15, Lesbar 12/15, Regression 13/15, Perf/Sec 7/10.
Staerken: Farbrad (Ecken false, Mitte true, Move-outside kein Pick, Cancel), Farbziel-laden schreibt nichts, Verlauf-Alpha im Drawable, alle 8 Tab-Kombinationen x2 Setup, Bildmodi (fit/cover/stretch + decode-null).
Abzug: Proxy-TabHost (fragil bei Interface-Aenderung), fail()-Lambda ohne Kontext.
Verbessern: parametrisierte Tab-Matrix, Screenshot-Golden fuer Farbrad.

## Einzelbewertung 28-36

### 28) ime/CapsLogicTest.kt - 80/100
Abdeckung 19/25, Edge 16/20, Determinismus 14/15, Lesbar 14/15, Regression 10/15, Perf/Sec 7/10.
Staerken: null zu false, CAP_SENTENCES zu true, initialCapsMode>0 zu true, normal zu false, Passwort (3 Varianten) nie, Zahl/Phone nie.
Abzug: nur 6 Tests, keine URI/Email-Varianten, kein CAP_WORDS/CHARACTERS.
Verbessern: CAP_WORDS/CHARACTERS-Matrix, EMAIL_ADDRESS-Variation.

### 29) ime/FileManagerFragmentTest.kt - 78/100
Abdeckung 19/25, Edge 14/20, Determinismus 12/15, Lesbar 13/15, Regression 12/15, Perf/Sec 8/10.
Staerken: drain()-Helper (Marker-Task + idleMainLooper statt Thread.sleep, vorbildlich entflackt), Start-Tab+Pfad, Neuer-Tab/Selektion/Schliessen, letzter-Tab-nicht-schliessbar, Tab-Wechsel stellt Verzeichnis wieder her.
Abzug: keine Verzeichnis-Assertions (Sandbox-abhaengig, bewusst aber Luecke), rootOwner-Field ohne After-Cleanup.
Verbessern: Fake-FileProvider fuer deterministische Listings, Rotation (Activity-Recreate).

### 30) ime/ClipboardPanelTest.kt - 76/100
Abdeckung 18/25, Edge 14/20, Determinismus 13/15, Lesbar 13/15, Regression 11/15, Perf/Sec 7/10.
Staerken: Capture+Liste, Persistenz ueber Instanzen, Auto-Capture-Gate (nicht fokussiert zu 0), Dedup, leer zu leer ohne Crash.
Abzug: kein Limit-Test (waechst unbegrenzt?), kein Loeschen/Clear, kein Groessen-Limit (10k-Zeichen-Clip).
Verbessern: Max-N (z.B. 20) + Overflow-Verhalten, canAutoCapture-Wechsel zur Laufzeit.

### 31) sections/SectionsTest.kt (Likely/Trail/Top) - 76/100
Abdeckung 19/25, Edge 13/20, Determinismus 13/15, Lesbar 13/15, Regression 11/15, Perf/Sec 7/10.
Staerken: SectionsTestBase (create statt setup fuer registerForActivityResult, korrekt), Likely-Toggles, Trail (3 Schalter + Steps-Zyklus 3-5-7-10), Top (Mond/Sonne, Ozean-Preset, aktives Icon).
Abzug: Effect-Toggle-Test assertet nur false nach Klick (kein Toggle-zurueck), Preset-Index 2 hartcodiert.
Verbessern: Toggle-zurueck fuer alle, Preset per Name statt Index.

### 32) sections/SectionsMoreTest.kt (Gradient/Preview/Color/Background) - 74/100
Abdeckung 18/25, Edge 12/20, Determinismus 13/15, Lesbar 12/15, Regression 11/15, Perf/Sec 8/10.
Staerken: Gradient (Build+Checkbox zu gradient_off_dark+Modus-Uebernahme), Preview (Build+Background-not-null), Color (3 Children, Slider-Range, Alpha=200), Background (Titel/Buttons/Label/Spinner, URI-Label, Fill-Cover zu 6 Children).
Abzug: getChildAt-Casts (brittle bei Layout-Aenderung), updateGradient ohne Assertion (nur no-crash).
Verbessern: per-ID statt per-Index, Slider-progress-Roundtrip.

### 33) ime/TrailPerformanceTest.kt - 73/100
Abdeckung 12/25, Edge 12/20, Determinismus 12/15, Lesbar 13/15, Regression 8/15, Perf/Sec 10/10 (gecappt, nur Zeit, kein Funktions-Assert).
Staerken: 6k-Korpus, Warm-up (Char-Index), 500 Worst-Case-Fuzzy-Calls, println fuer Doku, Budgets (<5 ms classify, <1 ms knowsWord).
Abzug: kein einziger Funktions-Assert (nur Zeit), JVM ungleich Device (dokumentiert, trotzdem Luecke), synthetische Woerter realitaetsfern.
Verbessern: zusaetzlich deutsche Woerter aus de_50k-Sample, p95 statt avg.

### 34) ime/SwipePerformanceTest.kt - 72/100
Abdeckung 12/25, Edge 11/20, Determinismus 12/15, Lesbar 13/15, Regression 8/15, Perf/Sec 10/10 (gecappt).
Staerken: 10k-Move-Events (charAt), O(n)-dedup, 6k-Scorer (<50 ms), Warm-up, Doku-Prints.
Abzug: Budgets sehr grosszuegig (<5 ms wuerde auch 4.9 ms durchlassen), keine Frame-Jitter-Messung.
Verbessern: Budget charAt <1 ms (O(1)-Nachweis), p99.

### 35) ime/FileManagerModelTest.kt - 71/100
Abdeckung 17/25, Edge 13/20, Determinismus 12/15, Lesbar 12/15, Regression 10/15, Perf/Sec 7/10.
Staerken: Sortierung (dirs-zuerst), Up-Navigation, Pfad-Logik.
Abzug: Datei liegt im falschen Paket (ime statt file, Import-Verwirrung), duenne Edge-Cases (Symlinks, fehlende Rechte fehlen vermutlich).
Verbessern: nach file/-Paket verschieben, Symlink-/Permission-Tests, Hidden-Files.

### 36) ime/PanelHeightsTest.kt - 68/100
Abdeckung 13/25, Edge 12/20, Determinismus 13/15, Lesbar 12/15, Regression 8/15, Perf/Sec 8/10.
Staerken: Notes-Hoehe = Editor+Buchstaben (Kern-Vertrag Files==Notes), null/0/negativ zu 0.
Abzug: nur 2 Tests, keine Rotation/Landscape, keine Density-/Font-Scale-Variation, keine echten Measure-Specs.
Verbessern: Landscape + fontScale=1.3, smallestWidth-Buckets.

---

## Abdeckungsluecken (Quelldateien ohne / mit duenner Suite)

| Quelle | Status | Prioritaet | Vorschlag neue Suite |
|---|---|---|---|
| ime/RepeatScheduler.kt | keine Suite | P0 | RepeatSchedulerTest: 250 zu 30 ms-Beschleunigung, Cancel |
| ime/ThemePrefs.kt | nur indirekt | P0 | ThemePrefsTest: Defaults, Gradient-Keys dark/light, Migration |
| ime/LanguageModule.kt | keine Suite | P0 | LanguageModuleTest: de/en-Umschaltung, Fallback |
| ime/TabController.kt | nur via Regression | P1 | TabControllerTest: alle 8 Masken isoliert (ohne Proxy) |
| ime/KeyboardBinder.kt | keine Suite | P1 | KeyboardBinderTest: Long-Press, Backspace-Repeat-Verdrahtung |
| ime/TextCommitController.kt | keine Suite | P1 | TextCommitControllerTest: commit/commitToApp-Routing |
| ime/DynamicKeyScaler.kt | keine Suite | P1 | DynamicKeyScalerTest: Skalen-Anwendung auf Buttons |
| ime/TrailManager.kt | nur Logik getestet | P1 | TrailManagerTest: echte snap/clearTrace-Calls (statt Map-Sim) |
| ime/SuggestionController.kt | keine Suite | P2 | SuggestionControllerTest: Bar-Wiring, Top-2x-Breite |
| ime/LetterPopup.kt | keine Suite | P2 | LetterPopupTest: Umlaut-Popups, Dismiss |
| ime/ThemedAdapter.kt | keine Suite | P2 | ThemedAdapterTest: Recolor ohne Leak |
| ime/BackgroundImage.kt | nur via Regression | P2 | BackgroundImageTest: fit/cover/stretch + decode-Fehler auslagern |
| ime/FileManagerModel.kt | duenn + falsches Paket | P1 | nach file/ verschieben, Symlink/Permission |
| ColorWheelView/Prefs/Activities | keine Suite | P2 | PrefsTest, ColorWheelMathTest (HSV ohne View) |
| KeyTabImeService.kt (283 Zeilen) | keine Suite | P2 | schmale Orchestrations-Tests (kein Full-Service) |

## Verbesserungsplan (konkret, priorisiert)

### P0 - diese Woche (klein, hohe Wirkung)
1. ClipboardPanelTest: Limit-Test (Max-N + Overflow drop-oldest) + Clear-Test. Hebt 76 zu 85.
2. PanelHeightsTest: Landscape + fontScale-Tests. Hebt 68 zu 80.
3. FileManagerModelTest: nach file/-Paket verschieben + Symlink-Test. Hebt 71 zu 80.
4. TrailLogicTest: Map-Simulation durch echte TrailManager-Calls ersetzen (2 Tests).
5. InputRouterTest: ktfmt + TERMINAL-Ops vollstaendig asserten.

### P1 - naechster Sprint (neue Suites, Coverage-Gate)
6. RepeatSchedulerTest, ThemePrefsTest, LanguageModuleTest neu (je 6-10 Tests). Ziel: Kover Line 47.6 zu 55 Prozent.
7. SwipeManagerTest/ThemeApplierTest: Reflection durch Test-Hooks ersetzen (visibleNodeCount(), defaultStateColorForTest()).
8. SwipePerformanceTest/TrailPerformanceTest: p95 + realistische de_50k-Woerter, Budget charAt <1 ms.
9. SettingsConfigTest: ueberlangen Test aufsplitten, CRLF/BOM-Faelle.

### P2 - danach (Robustheit)
10. KeyboardBinderTest, TextCommitControllerTest, TabControllerTest (isoliert, ohne Proxy).
11. BackgroundImageTest aus SettingsRegressionTest auslagern.
12. CI: Kover-Gate 55 Prozent Line / 40 Prozent Branch + detekt ohne neue Baseline-Eintraege.

## Reproduzierbarkeit
- Unit: ./gradlew :app:testDebugUnitTest --tests "com.piotv.keytab.ime.*"
- Einzel: ./gradlew :app:testDebugUnitTest --tests "com.piotv.keytab.ime.SuggestionEngineTest"
- Coverage: ./gradlew :app:koverXmlReport (siehe app/build/kover/, app/build/reports/)
- Robolectric: Config(sdk=[34]), ShadowLooper.idleMainLooper(), directExecutor fuer Determinismus.

## Methodik-Hinweis
Bewertet wurde statisch (Code-Read aller 36 Dateien) + Git-Historie (letzte 10 Commits) + Build-Artefakte (test-results/, reports/). Keine erneute Testausfuehrung auf diesem Geraet (proot/Termux ohne Android-SDK-Garantie). Scores sind relativ zum Projekt-Kontext (IME, Offline, 50-ms-Budget, Privacy: kein Netzwerk), nicht als absolute Industrienote zu lesen.

---

## Nachtrag 2026-09-22: P0 umgesetzt + verifiziert

Umgesetzt (nur eigene Aenderungen, fremde uncommittete Aenderungen unangetastet):

| Massnahme | Datei | Neue Tests | Ergebnis |
|---|---|---:|---|
| Clipboard-Limit + Clear + Isolation | `ime/ClipboardPanelTest.kt` | 5 → 9 | PASS |
| measureHeight/Terminal/fontScale/schmal | `ime/PanelHeightsTest.kt` | 2 → 5 | PASS |
| Dedup/persist/Filter/counts-null/Restore/Root-Up | `ime/FileManagerModelTest.kt` | 11 → 17 | PASS |
| Rig-Dataclass, ktfmt-Einrueckung, TERMINAL voll | `ime/InputRouterTest.kt` | 8 → 10 | PASS |
| snapshotForTest-Hook | `ime/TrailManager.kt` (+8 Zeilen) | — | PASS |
| Neue Suite mit echten Calls (statt Map-Sim) | `ime/TrailManagerTest.kt` | 0 → 12 | PASS |

Verifikation (SDK `/opt/android-sdk`, Java 17 arm64):
- Eigene 5 Suites isoliert: `ClipboardPanelTest` 9/9, `PanelHeightsTest` 5/5, `FileManagerModelTest` 17/17, `InputRouterTest` 10/10, `TrailManagerTest` 12/12 — 0 Failures.
- Volle Suite NACH Stash fremder Aenderungen (reiner HEAD + eigene Tests): **370 Tests, 0 Failures, 0 Errors** — BUILD SUCCESSFUL.
- Hinweis: im Arbeitsbaum liegen fremde uncommittete Aenderungen (u.a. `SuggestionEngine.autoCorrect`-Entfernung + angepasste `SuggestionEngineTest`), die 2 Failures verursachen (`ahus→haus`-Fuzzy, `hane→hase`-Bigramm). Diese stammen NICHT aus meinen Aenderungen; mit meinen Aenderungen allein ist alles gruen. Details in `app/build/test-results/`.

Neue Scores (geschaetzt nach Umsetzung):
- ClipboardPanelTest 76 → **~85** (Limit/Clear/Isolation geschlossen)
- PanelHeightsTest 68 → **~80** (measureHeight/Terminal/Robustheit geschlossen)
- FileManagerModelTest 71 → **~80** (Filter/Dedup/Restore-Kanten geschlossen; Paket-Verschiebung nach `file/` bleibt offen)
- InputRouterTest 82 → **~85** (Format + TERMINAL-Vertrag geschlossen)
- TrailManagerTest **neu ~88** (echte snap/traceWord/clear-Coverage; Map-Sim-Luecke aus TrailLogicTest geschlossen)

---

## Nachtrag 2026-09-22 (2): 7 neue Suites — Abdeckung + Verifikation

Neue Suites (alle PASS, einzeln und in der Vollsuite verifiziert):

| Suite | Tests | Deckt ab (vorher keine/duenne Suite) |
|---|---:|---|
| `ime/RepeatSchedulerTest.kt` | 6 | `RepeatScheduler` (Start/Beschleunigung/Minimum/Reset/Faktor-1/Min-ueber-Start) |
| `ime/LanguageModuleTest.kt` | 7 | `LanguageModule` (7 Sprachen, Fallback de, Metadaten, Umlaute, letterExtras-Merge, Euro) |
| `ime/ThemePrefsTest.kt` | 14 | `ThemePrefs` (Trail-Defaults, Steps-Clamp, withAlpha, Keys, hasGradient, Verlauf-Fallback, Drawable, Export/Import, Reset, Likely) |
| `ime/TextCommitControllerTest.kt` | 6 | `TextCommitController` (Buchstabe/Space/ohne-Router/commitToApp/direkt+null/deleteLastWord) |
| `ime/BackgroundImageTest.kt` | 9 | `BackgroundImage.destination` (fit/cover/stretch/unbekannt/Hochkant/Null) + decode-Fehler + apply-leer |
| `ime/DynamicKeyScalerTest.kt` | 5 | `DynamicKeyScaler` (aus/Top/Nachbar-Shrink/Grossbuchstabe/leer) |
| `ime/ThemedAdapterTest.kt` | 3 | `themedAdapter` (Count/Item/text_primary Day+Night/leer) |

Korrekturen nach Testlauf (echte Befunde, keine Test-Schoenfaerberei):
- `ThemedAdapterTest`: erster Ansatz nutzte Plain-App-Kontext (Day=Schwarz) und scheiterte korrekt — umgestellt auf Theme-Kontext Day+Night; der Adapter folgt dem Theme statt hart Schwarz zu liefern.
- `BackgroundImageTest`: Robolectric-Shadow decodiert auch Muell-Bitmaps zu non-null — Muell-Assertion durch echte 1x1-PNG (Base64, decodiert) ersetzt; Fehlerpfade bleiben via nonexistent/https/ftp/leer abgedeckt.

Verifikation (HEAD + eigene Tests, fremde uncommittete Aenderungen per stash ausgeblendet):
- Vollsuite: **420 Tests, 0 Failures, 0 Errors** — BUILD SUCCESSFUL (vorher 370).
- 49 Suites laut XML-Reports, alle gruen.
- Kover (`app/build/kover/coverage.txt` + HTML-Report): **Line 52.2 % (2300/4404)**, Branch 39.8 %, Method 51.1 % — vorher 47.6 % / 36.4 %. P1-Ziel (55 % Line) zu ~3/4 erreicht; Rest braucht KeyboardBinder/TabController/SuggestionController/LetterPopup-Suites (P2).
- Hinweis: im Arbeitsbaum liegen weiterhin fremde uncommittete Aenderungen (u.a. `SuggestionEngine.autoCorrect`-Entfernung); mit diesen schlagen 2 alte `SuggestionEngineTest`-Faelle fehl — unabhaengig von diesen neuen Suites. Per `git stash pop` ist alles wiederhergestellt.

---

## Nachtrag 2026-09-22 (3): unabhängiger Voll-Lauf + Passwort-Sperre (Audit-Fix)

**1) Korrektur der Warnung aus Nachtrag 2.** Der Hinweis „2 alte
`SuggestionEngineTest`-Faelle fehlschlagen“ ist **überholt**: ein kompletter Lauf
des Arbeitsbaums (kein Stash, keine Ausblendung) ergibt
**489 Tests in 50 Suiten, 0 Failures, 0 Errors, 0 Skipped**
(`:app:testDebugUnitTest`, 22 Sep). Die damals verletzten Verträge
(`ahus → haus`-Vertauschung, `hane → hase`-Bigramm) sind im Code vorhanden
(`SuggestionEngine.autoCorrect`, Swap-Zweig Zeile ~385). Auch die Aussage
„keine erneute Testausfuehrung auf diesem Geraet“ aus §Methodik gilt nicht mehr:
SDK (`/opt/android-sdk`) und JDK 17 sind vorhanden, alle Läufe dieses Nachtrags
wurden auf dem Gerät ausgeführt.

**2) Neue Suite: `WordPredictionManagerPrivacyTest` (8 Tests, Robolectric).**
Sie schließt die im Audit gefundene Lücke, dass Lernen, Vorschläge und
Autokorrektur die harte Passwort-Regel (`TrailLogic.isTrailAllowed`) nicht
geprüft haben. Aufbau bewusst mit **Gegenproben**: jeder der vier Pfade
(Wortlernen, Vorschlags-Übernahme, Autokorrektur, Vorschlagsleiste) hat einen
Sperr- und einen Erlaubt-Test — sonst könnte ein stumpfes `return` die Sperre
„bestehen“. Die Regel-Gleichheit zu `isTrailAllowed` ist zusätzlich in
`TrailLogicTest` als Matrix fixiert (7 Feld-Varianten, inkl. `null` und
`IME_FLAG_NO_PERSONALIZED_LEARNING`).

**2b) Zusätzliches Leck: Emoji-Katalog.** `SuggestionController.update()` rendert
den ☺-Katalog-Browser an `updateSuggestions` **vorbei** und blieb damit offen,
wenn der Katalog vor dem Feldwechsel geöffnet wurde — genau der Fall, den ein
Gate im Manager allein nicht abdeckt. Behoben über
`WordPredictionManager.isFieldProcessingAllowed()`; Test
`Emoji-Katalog bleibt in gesperrten Feldern zu` (mit Gegenprobe im Normalfeld)
in `SuggestionControllerTest` (+1 Test). Nebeneffekt, der mitschützt: dynamische
Tastengröße und Likely-Highlighting lesen `currentSuggestions`, das im gesperrten
Feld geleert wird — es bleibt also auch kein Seitenkanal über die Tastengröße.

Eigene Stolpersteine beim Testbau (dokumentiert, weil sie sonst wiederkehren):
- `applySuggestion("haus")` schreibt am Satzanfang **groß** (`Haus `) — die
  Tests setzen deshalb Kontext davor („der “), statt die Groß-/Kleinschreibung
  mit dem eigentlich geprüften Verhalten zu vermischen.
- `autoCorrect("hauss")` wählt deterministisch **`hause`** statt `haus`: gleiche
  Länge (kein Längen-Malus) und höhere Korpusfrequenz. Die Gegenprobe prüft
  deshalb „ersetzt, mit Leerzeichen, Ergebnis ist ein Korpuswort“ statt ein
  hartes `haus ` — die Auswahl-Heuristik der Engine ist nicht Teil dieser Regel.

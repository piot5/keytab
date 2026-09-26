# Changelog

Alle nennenswerten Aenderungen an KeyTab, neueste zuerst.

Das Format folgt [Keep a Changelog](https://keepachangelog.com/de/1.1.0/);
die Versionierung folgt [Semantic Versioning](https://semver.org/lang/de/).

> Die Version wird **nicht** hier gepflegt, sondern in `app/build.gradle.kts`
> (`versionCode`/`versionName`). `scripts/check_docs_drift.sh` (CI-Job "docs")
> prueft bei jedem Push, dass der Kopf dieses Changelogs, der fastlane-Changelog
> und die Gradle-Version zusammenpassen.


## 0.14

- **Terminal entfernt:** Der optionale Terminal-Tab, die Android-Shell und alle zugehörigen Ressourcen, Prozesse und Tests wurden vollständig entfernt. Die App bleibt eine fokussierte IME mit Editor, Dateien, Clipboard und Snippets.
- **Release-Reife:** Release-Builds werden zusätzlich auf Signatur, SHA-256 und APK-Metadaten geprüft; Keystore-Pfadauflösung im Release-Build korrigiert, Dependency-Verification für Lint vervollständigt. Neuer Release-Leitfaden: `docs/RELEASE.md`; Doku, Installations- und Debug/Release-Hinweise vollständig angepasst.
- **Technische Bereinigung:** Terminal-Routing, Panel-Höhenlogik und Editor/Terminal-Schnittstellen wurden auf die verbleibenden App- und Editor-Ziele reduziert. Zusätzlich wurden 47 tote Ressourcen entfernt (40 ungenutzte Strings aus Legacy-Theme-UI, 7 Farben inkl. Night-Varianten) — Lint meldet damit keine ungenutzten Ressourcen mehr, die Detekt-Baseline von 191 auf **181** echte Befunde verkleinert (u. a. `LongMethod`/`CyclomaticComplexMethod` in Service und TabController) und Test-Fakes ohne leere Funktionsblöcke formatiert.

## 0.13

- **Gelerntes Wörterbuch prüfen:** Der Einstellungs-Screen bietet jetzt eine sichere, explizite Bereinigung learned-word-Artefakte an. Die Änderung erfolgt offline, zeigt eine Vorschau und verlangt eine Bestätigung; der Basiswortschatz bleibt unverändert.
- **Autokorrektur-Schalter:** Die automatische Korrektur nach dem Leerzeichen ist wieder direkt in den Einstellungen ein-/ausschaltbar.
- **Dokumentation:** API-, Konfigurations-, Datenschutz- und Installationshinweise auf den In-App-Bereinigungsweg aktualisiert.
- **Tests:** UI-/Bereinigungs- und Autokorrektur-Regressionstests ergänzt; vollständige JVM-Testsuite erfolgreich.

## 0.12

- **Snippet-Workflow überarbeitet:** Der Snippet-Tab enthält keinen „Neu“-Button mehr. „Bearbeiten“ öffnet `keytab_snippets.txt` direkt im Editor-Tab.
- **Clipboard-Snippets benannt:** Beim Hinzufügen aus dem Clipboard wird der erste nichtleere Inhaltsteil als Name verwendet; mehrzeilige Inhalte bleiben escaped erhalten.
- **Editor-/Terminal-Maximierung stabilisiert:** Normalhöhen werden gespeichert und beim Minimieren zuverlässig wiederhergestellt.
- **Qualität:** Lint-Fehler behoben, englische Ressourcen vervollständigt und Android-14-Selected-Photo-Zugriff integriert.
- **Tests:** Regressionstests für Snippet-Editor und Clipboard-Namen ergänzt; Unit-Tests, Lint und Doku-Drift-Gate erfolgreich.

## Unreleased

- **IME-Vertragstests erweitert:** `KeyTabImeEndToEndTest` deckt nun zusätzlich Feldwechsel sowie Activity-Recreation und die Nutzbarkeit der Tastatur nach dem Lifecycle-Wechsel ab. Die Instrumented-Test-CI verwendet ein begrenztes Boot-Wait und gibt bei Fehlern Instrumentierungsstatus und Logcat aus; die API-34-Ausführung wird im nächsten CI-Lauf verifiziert.
- **Phase 8 begonnen: Clipboard-Persistenz auf Coroutines migriert** — `ClipboardPanel` erhält einen service-eigenen `SupervisorJob`-Scope mit `Dispatchers.Main.immediate`; Datei-I/O läuft über `Dispatchers.IO` und wird per `Mutex` serialisiert. Der Scope wird beim IME-Service-Lifecycle beendet. Clipboard-Fehlerpfade bleiben geloggt und liefern lokalisierte UI-Fehler. `ClipboardPanelTest` umfasst jetzt 10 Tests.

- **Qualitäts-Gates geschärft: Kover 20 % → 60 % Zeilen / 45 % Branch** — das
  alte Gate war Deko (20 % lagen schon vor der Testoffensive deutlich unter dem
  Ist-Stand). Gemessen am 22.09.2026: **67,0 % Zeilen** (2947/4401) und **53,8 %
  Branch** (1742/3238) im selben aggregierten `application`-Report, den
  `koverVerify` prüft (`koverLog` nennt 66,96 % Zeilen). Die neuen Schwellen
  liegen 5–7 Punkte darunter und fangen damit echte Regressionen.

- **Doku-Drift-Wächter um Größen- und Sprachzahlen erweitert** — geprüft werden
  jetzt zusätzlich „Test code is `<X>` lines in `<Y>` files against `<Z>` lines of
  main code (`<N>` files)“, die daraus berechnete Test:Main-Ratio, beide
  Zeilenangaben zu `KeyTabImeService.kt` sowie die String-Zahlen von
  `values/` ⇔ `values-en/` inklusive **Parität** (neue Ressource ohne
  Übersetzung ⇒ Gate rot). Genau diese Zahlen waren zuletzt veraltet (README:
  „283 lines“ statt 417, 4.718 statt 7.038 Testzeilen, „176 strings“ statt 170),
  obwohl der Code grün war. Details: `docs/DOCS_DRIFT.md` §5.

- **Performance: Prefix-Vervollständigung nutzt den Char-Index** —
  `SuggestionEngine.completeWord` scannte pro Tastendruck alle ~6.000
  Basiswörter (`for (w in baseFreq.keys) if (w.startsWith(cur))`), obwohl der
  Index `byFirstChar` schon existierte (bisher nur für Fuzzy-Matches genutzt).
  Die Prefix-Suche läuft jetzt über diesen Index (~10× weniger Kandidaten,
  gleiches Ergebnis). Der Index ist zusätzlich **case-insensitiv** aufgebaut,
  damit ein Korpuswort wie „Haus“ bei der Eingabe „hau“ gefunden wird (Test in
  `SuggestionEngineTest`).

- **`LearnedDictionaryApi`: Entscheidung dokumentiert, `internal` gemacht** — die
  API hat außerhalb der Tests keinen Konsumenten, und ein externer Transportweg
  wurde bewusst **abgelehnt** (kein `ContentProvider`/exportierter Service: neuer
  Abflusspfad für genau die Daten, die die App offline und ohne
  Netzwerk-Permission hält; eine Signatur-Permission würde für
  adb/Shizuku-Werkzeuge nicht funktionieren). Optionen-Vergleich in
  `docs/API_INTERFACE_EXTERNAL_CLEANUP.md` §0, Status-Banner in den vier
  Plan-Dokumenten; Entfernung bleibt möglich, falls kein Konsument entsteht.

- **Permissions reduziert** — `READ_MEDIA_AUDIO` und `READ_MEDIA_VIDEO` entfernt
  (Manifest + beide Request-Stellen in `MainActivity` und
  `FileManagerFragment`): kein Codepfad liest Audio- oder Videodaten, für den
  Dateibrowser genügt der Name/Pfad. Es bleiben `READ_EXTERNAL_STORAGE`
  (API ≤ 32) und `READ_MEDIA_IMAGES` (Bilder browsen + eigenes Hintergrundbild).
  Die Feature→Permission-Zuordnung steht jetzt als Kommentar im Manifest.

- **Repo-Hygiene** — 16 `build_*.log` gelöscht; die Root-Artefakte des
  Workspaces (22-MB-Backup-Tar, Git-Bundle, zwei alte APKs, verwaiste
  `KeyAnimations.kt`-Kopie) liegen unverändert in `../archive/` statt im
  Wurzelverzeichnis.

- **Sicherheit: Lernen, Vorschläge und Autokorrektur jetzt auch in Passwort-Feldern
  gesperrt** — Trail und Swipe respektierten
  `TrailLogic.isTrailAllowed` (Passwort-Felder + `IME_FLAG_NO_PERSONALIZED_LEARNING`)
  schon immer; die drei Pfade, die die Eingabe **auswerten oder speichern**, taten
  es nicht:
  - getippte Wörter landeten im User-Dictionary (`WordPredictionManager.onWordCompleted`
    und die Vorschlags-Übernahme `applySuggestion` → `engine.learn` + `persistUserDict`),
  - die Vorschlagsleiste zeigte Wörter (Gating nur über die Pref),
  - `autoCorrectBeforeSpace()` ersetzte beim Space auch in `•`-Feldern ein Wort durch
    einen Wörterbuch-Kandidaten.
  Neu: `TrailLogic.isPersonalizedProcessingAllowed` — bewusst **dieselbe** harte Regel
  wie der Trail (per Test gegen `isTrailAllowed` fixiert, damit kein Pfad eine
  weichere Regel bekommt). Der Service speist sie aus der aktuellen `EditorInfo`
  (`lastEditorInfo` in `onStartInput`/`onStartInputView` → `KeyboardViewFactory.Deps`
  → `WordPredictionManager`); Trail/Swipe bekommen dieselbe Instanz über
  `KeyboardBinder.setEditorInfo`. Ohne Freigabe wird nichts gelernt/persistiert,
  werden keine Vorschläge gerendert (Leiste `GONE`, Chips geräumt) und nicht
  korrigiert; der Bigramm-Kontext wird geleert, damit kein Wort aus dem Feld in die
  nächste Vorhersage leckt. **Auch der Emoji-Katalog** (☺-Button) rendert an
  `updateSuggestions` vorbei und blieb dadurch in einem gesperrten Feld offen bzw.
  sichtbar — `SuggestionController.update()` schließt ihn jetzt über
  `WordPredictionManager.isFieldProcessingAllowed()` (die dynamische Tastengröße und
  das Likely-Highlighting fallen mit den geleerten `currentSuggestions` automatisch
  mit weg, also kein Rest-Kanal über die Tastengröße).
  Tests: neue Suite `WordPredictionManagerPrivacyTest`
  (8 Tests) mit **Gegenproben in normalen Feldern** (sonst könnte ein stumpfes
  `return` die Sperre „bestehen“) + Regel-Gleichheit in `TrailLogicTest` +
  `Emoji-Katalog bleibt in gesperrten Feldern zu` in `SuggestionControllerTest`.
  Suite jetzt **483 Tests in 50 Suiten**, 0 Failures.

- **Einstellungen entlastet + Rot-Färbung raus** — auf Wunsch des Projekt-Eigners:
  - Die Schalter **„Auto-correction"** und **„Circuit preview"** sind aus dem
    Einstellungs-Screen entfernt (Layout, Verdrahtung, 6 Strings in beiden
    Sprachen). Verhalten: Autokorrektur bleibt aktiv (Pref-Default `true`), die
    experimentelle Schaltplan-Preview bleibt aus (Default `false`); beide Werte
    sind weiterhin über die Config-Datei steuerbar (`docs/CONFIG.md`).
  - **Kein rotes Buchstaben-Aufleuchten mehr:** die Korrektur-Warnfärbung
    (`TrailKind.CORRECTED`, `trail_corrected_color` in beiden Farbsets,
    Config-Export/-Import, Reset) ist komplett entfernt. Ein Tippfehler bleibt
    jetzt unmarkiert.
  - **Grün bleibt — und bedeutet jetzt genau eins:** die Buchstaben färben sich
    kurz grün, wenn das getippte Wort **exakt dem obersten Vorschlag entspricht**
    (`TrailKind.ACCEPTED`, gated durch die Pref `trail_trace`). Schalter-Label
    und Hinweistext in beiden Sprachen angepasst
    („✅ Vorschlag-Treffer grün markieren").
  - Tests/Doku nachgezogen: `TrailLogicTest` (Regression „Korrektur-Kandidat
    ergibt keine Markierung mehr"), `TrailPerformanceTest`, README-Abschnitt,
    `docs/CONFIG.md`, KDoc in `TrailLogic`/`TrailManager`/`ThemePrefs`.

- **Test: 20 neue Robolectric-Panel-Tests (P1)** — `SnippetPanelTest` (5),
  `TerminalPanelTest` (10), `FileManagerPanelTest` (5); Lücke aus der externen
  Pruefung (§8.3 Rang 2 „Testpyramide"): die Panels waren nur ueber den reinen
  `FileManagerModel`/`EditorPanel` abgedeckt. Neu geprueft: Snippet-Parser
  (Kommentare, Leerzeilen, fehlendes `=`, `\n`-Escapes, Default-Datei-Erzeugung,
  Tap → Commit + Recent-History), Terminal (Prompt `user@host:~`, cd-Verfolgung
  inkl. Rueckfall auf Home bei ungueltigem Ziel, Insert/Delete/`delete(word)`
  am Cursor, leerer Befehl), Files-Panel (Navigation in Unterordner,
  Back-Stack + Zurueck-Button-Sichtbarkeit, Up-Navigation, Datei-Tipp →
  Pfad-Commit, Persistenz von `fm_dir`/`fm_backstack`). Testsuite jetzt
  **343 Tests in 36 Suiten**, 0 Fehler.

- **i18n: `values-en` vervollstaendigt (176/176 Strings)** — 71 fehlende
  Uebersetzungen ergaenzt (Settings-/Theme-/Snippet-/Editor-/Terminal-Texte,
  Gradient-Modi, Versions-Label). Vorher fiel in englischsprachigen Systemen
  rund ein Drittel der Oberflaeche auf Deutsch zurueck; das war die letzte
  offene Schwaeche der externen Doku-Bewertung (§8.3 Rang 5).

- **Refactor: detekt-Baseline aufgeräumt und neu generiert** — Quick-Wins-Paket
  (staerkster Hebel der externen Pruefung, §8.3): 26x `NewLineAtEndOfFile`
  (fehlende Datei-Newlines, automatisiert), 6x `WildcardImport` → explizite
  Imports, 3x `UnusedPrivateProperty` + 1x `UnusedPrivateMember` (toter Code
  entfernt: `PreviewSection.onChange`, `ThemePrefs.INT_DEF_GRADIENT`,
  `ColorSection.updateColorFromWheel`, ungenutzte Schleifenvariable in
  `LiftSpanTest`), 3x `MaxLineLength` (umbrochen), 1x `VariableNaming`
  (`DEBOUNCE` → `debounce`), 2x `SwallowedException` (`Log.e` ergänzt in
  `FileManagerFragment` + `TerminalPanel`) und der veraltete `PanelsTest`-Eintrag.
  Danach Baseline **neu generiert** (`:app:detektBaseline`): die 156 verbliebenen
  + 42 neue Funde aus dem v0.11-Swipe-Code (CyclomaticComplexMethod in
  `SwipeManager.drawEdges`/`KeyTabImeService.hideKeyboard`, MagicNumbers in der
  neuen Prediction-Logik) = **198 Eintraege**, detekt-Gate wieder gruen.



- **Fix: Autokorrektur verdoppelte/zerstoerte Text statt zu ersetzen** — die
  Vorschlags-Uebernahme entschied allein ueber
  `before.takeLast(n).equals(typed, ignoreCase = true)`. Schlug das nur
  *scheinbar* fehl (Unicode-Normalisierung, kombinierende Umlaute, abweichende
  Feld-Repraesentation), wurde der Vorschlag **angehaengt statt ersetzt**
  (`hauss` → `hauss haus `); eine zu lockere Loesch-Pruefung konnte umgekehrt
  Zeichen zu viel entfernen. Neu: reine, getestete
  `SuggestionReplaceLogic` (NFC-normalisierter Vergleich, `action()`,
  `deleteWorked()`, `endsWith()`), `WordPredictionManager.applySuggestion`
  entscheidet darueber und **fuegt nie ein, wenn das Loeschen nicht
  nachweislich geklappt hat**; die Loesch-Menge ist die **Roh-Laenge im Feld**
  (`rawWordLength`, NFC/NFD-tolerant) statt `typed.length`. (+21 Tests:
  `SuggestionReplaceLogicTest` 17, `WordPredictionManagerSuggestionTest` 6
  inkl. Fake-Feld fuer beide Loesch-Wege).

- **Fix: Schaltplan-Preview stuerzte die App ab** (Dropbox:
  `NullPointerException: Context.getResources() on a null object reference` in
  `View.<init>` ← `SwipeManager$EdgeOverlay` ← `drawEdges` ← `applyPreview` ←
  `updateSwipePreview`). Ursache: Der Container wurde via
  `Button.findViewById(kb_container)` gesucht (ein Button hat keine Kinder →
  immer `null`) und fiel auf `rootView` zurueck; der Context konnte null werden.
  Jetzt: Container ueber den **Root-View**, Context explizit geprueft,
  `EdgeOverlay` verlangt einen **nicht-null** Context, Overlay nur in einen
  **angehaengten, gemessenen** Container und nur bei **≥ 2 Knoten**;
  neuer idempotenter Helfer `removeEdgeOverlay()`.
- **Fix: Swipe-Koordinaten lagen daneben** — `centersFor` bezog die
  Tasten-Zentren auf `kb_container`, waehrend die Touch-Koordinaten aus
  `KeyboardBinder` **fenster-relativ** sind. Die Buchstaben-Erkennung lag damit
  um die Position der Tab-Leiste verschoben (falsche Buchstaben beim Wischen).
  `centersFor` liefert jetzt Fenster-Koordinaten; nur `drawEdges` rechnet die
  Container-Relation um.
- **Swipe: most-likely-Ziele ab der ersten Taste + gruener Trail live** —
  `SwipeManager.seedFirstKey()` belegt beim Druecken die gedrueckte Taste als
  erstes Routen-Sample vor, sodass die Ziele **sofort** sichtbar sind (nicht
  erst nach dem ersten Tastenwechsel). Ein reiner Tap (nur Seeding) bleibt ein
  Tap: neue Methode `hasSwiped()` (≥ 2 Samples) ersetzt `hasSamples()` in der
  Touch-Delegation. Wird ein most-likely-Ziel erreicht, faerbt `KeyboardBinder`
  den Trail gruen (`TrailKind.ACCEPTED`) statt blau (`TYPED`).
- **Swipe-Abschluss schreibt jetzt wie ein normales Wort** — Auto-Commit lief
  ueber rohes `commitText(auto)` (kein Leerzeichen, kein Lernen, keine
  Vorschlaege). Jetzt ueber `SuggestionController.applySuggestion(auto)`:
  **Leerzeichen**, Wort-Lernen und **frische Wortvorschlaege** — konsistent zum
  Tippen und zur Vorschlags-Uebernahme.

- **Konsistenz Trail ↔ Swipe ↔ Autokorrektur** — während des Swipens werden die
  wahrscheinlichen Folge-Tasten (most-likely) als Schaltplan mit Kanten
  angezeigt, damit der Finger dem Pfad folgen kann; wird ein most-likely-Ziel
  erreicht, leuchtet der Trail grün (`TrailKind.ACCEPTED`) statt blau
  (`TYPED`). Die gefahrene Route färbt sich also konsistent zur
  Wortvorhersage. Neu: `SwipePathLogic.isLikelyHit` (rein),
  `SwipeManager.wasLikelyHit`, `applySwipeLikely` zeichnet jetzt Kanten und
  liefert die Likely-Knoten für den nächsten Treffer; `KeyboardBinder` MOVE
  fragt `wasLikelyHit` vor `snap` ab (+7 Unit-Tests: `SwipePathLogicTest`,
  `SwipeManagerTest`).


- **Feature: optionale Emoji-Vorschläge** (Einstellungen-Schalter, **Standard aus**):
  ein eingebetteter Keyword→Emoji-Katalog (de/en, offline, keine neue Permission)
  hängt thematisch passende Emojis hinten an die Wortvorschläge an (max. 2 der
  3 Slots; Wortschläge behalten mindestens einen Slot). Neu: `Prefs.KEY_EMOJI_SUGGESTIONS`,
  `SuggestionEngine.emojiEnabled` + `EmojiModule.emojisFor` (+14 Unit-Tests:
  `EmojiModuleTest`, `EmojiSuggestionsTest`), Config-Key `emoji_suggestions`
  (`keytab_config.txt`, docs/CONFIG.md), Settings-Schalter `sw_emoji`.
- **Termux-Key-Matrix (automatisiert)** — `TermuxKeyMatrixTest` fixiert die
  Key-Verträge des App-Eingabepfads, auf die der Kern-Use-Case „Coding in
  Termux" angewiesen ist: TAB = KEYCODE_TAB-Key-Event (nie `commitText("\t")`),
  Enter = KEYCODE_ENTER, Backspace = KEYCODE_DEL, Text = `commitText`,
  `deleteBeforeKeys` = n × KEYCODE_DEL (Fallback für Felder ohne
  deleteSurroundingText). Die manuelle Device-Matrix (Termux/neovim,
  AndroidIDE, VS Code proot) bleibt offen.
- **Trail-Hot-Path gemessen** — `TrailPerformanceTest` benchmarkt
  `classifyTypedWord` → `autoCorrect` (der Code-Pfad, der bei jedem Tastendruck
  läuft) auf einem synthetischen 6.000-Wort-Korpus: **~2 µs pro Klassifizierung**
  (JVM; ~4 Größenordnungen unter dem 50-ms-Keystroke-Budget), bekanntes Wort
  ~3 µs (Short-Circuit). Mit Hard-Assertion (< 5 ms), damit Performance-
  Regressionen den Build brechen. Device-Frame-Timing bleibt offen.
- **Tests: 208 → 231 in 27 Suiten** — `PanelsTest` in `EditorPanelTest` +
  `ClipboardPanelTest` aufgeteilt (Name = Klasse); Kover re-messen:
  **47,6 % line / 36,4 % branch** (`ime` 42,9 %, `sections` 92,5 %, `file` 78,7 %).


## 0.11


- **Feature: Swipe-Eingabe (Gleit-Eingabe, offline)** — der Finger gleitet über die
  Tastatur; die gefahrene Route wird gesampelt und gegen die bestehende Engine
  bewertet (Teilfolge-Match: Buchstabenfolge des Kandidaten muss in der Route
  enthalten sein, Reihenfolge erhalten, Lücken erlaubt). Score aus Treffer-Quote,
  Wortfrequenz (Basis + User + Bigram), Kompaktheit und Längenanpassung. Bei
  klarer Dominanz (Top ≥ 1.35, ≥ 1.3× Zweitbester) Auto-Commit, sonst die besten
  Kandidaten in der Vorschlags-Leiste (tapbar wie Wortvorschläge). Default **aus**,
  in Passwort-Feldern hart deaktiviert (gleiche Regel wie der Trail). Neu:
  `Prefs.KEY_SWIPE`, `SwipePathLogic` (rein), `SwipeScorer` (rein),
  `SwipeManager` (Overlay + Sampling), Touch-Delegation in `KeyboardBinder`,
  `SuggestionController.showSwipeCandidates`, Config-Key `swipe`.
- **Feature: Schaltplan-Preview (passiv)** — die wahrscheinlichen Folge-Tasten
  des aktuell getippten Worts werden als verbundener Pfad sichtbar (Knoten =
  skalierte Tasten in der Pfad-Farbe `KIND_SWIPE`, Kanten = Verbindungslinien
  `KIND_SWIPE_EDGE` unter den Tasten). Nutzt `SwipePathLogic.previewNodes` aus den
  Top-Vorschlägen; aktualisiert sich bei jedem Suggest-Update. Default **aus**.
  Neu: `Prefs.KEY_SWIPE_PREVIEW`, Theme-Kinds `KIND_SWIPE`/`KIND_SWIPE_EDGE`
  (inkl. Export/Import/Reset), Config-Key `swipe_preview`, Farben
  `swipe_color`/`swipe_edge_color` in `colors.xml` (Hell/Dunkel).
- **Tests: 231 → 288 in 31 Suiten** — neue Suiten `SwipePathLogicTest` (19,
  rein), `SwipeScorerTest` (19, rein), `SwipePerformanceTest` (3, Hot-Path-
  Benchmark: `charAt`/`dedup`/`scorer` mit Hard-Assertion < 5 ms / < 50 ms),
  `SwipeManagerTest` (12, Robolectric: Preview-Färbung, Clear, Prefs, Passwort-Feld,
  Engine-Set, Sampling-Reset).


## 0.10


- **Feature: Snippet-Vorschläge am Satzanfang** — wenn keine Wortvorhersagen
  angezeigt werden und der Cursor am Satz­anfang steht (leeres Feld, oder Text
  endet auf `. ! ?` + Leer/Zeile, und gerade kein Wort getippt wird), zeigt die
  Vorschlags‑Leiste die **3 zuletzt eingefügten Snippets** als wählbare Chips
  (statt der generischen Top‑3‑Wortvorschläge). Ein Snippet‑Tap fügt den Text ein
  und merkt ihn als most‑recent; das Schreiben läuft wie der Snippet‑Tab über
  `commitText` (Editor‑Routing, Auto‑Korrektur‑Bypass für mehrzeilige Snippets).
  Die History ist **lokal in `MODE_PRIVATE`** (keine neue Permission, kein
  Netzwerk) und wird auch nach **Wort‑Löschen via Del** neu ausgewertet (Refresh
  über `deleteLastWord` → `reset` → `update`). Neu: `Prefs.KEY_RECENT_SNIPPETS`,
  `SuggestionEngine.sentenceStart / recentSnippets / recordRecent` (+10 reine
  Unit-Tests in `SuggestionEngineTest`), Snippet‑Tag‑Dispatch
  (`SuggestionEngine.SNIPPET_TAG`) in `SuggestionController`,
  `recordRecent`‑Hook im `SnippetPanel`.


## 0.9.9

Nach dem ausgelieferten 0.9.8 (Tag vom 18.09. 08:13, Commit `f1b4496`) sind zwei
Aenderungssaetze aufgelaufen, die nicht mehr in der Release-APK stecken: der
detekt-Commit (`a5b03c8`) und die hier dokumentierte P3-Runde. **Die lokale
Version ist damit der ausgelieferten 0.9.8 strikt voraus** — 0.9.9 ist das
Release, das diesen Vorsprung ausliefert.

- **Refactor (P3): KeyboardHost in Rollen-Interfaces aufgeteilt** — statt eines
  25-Member-Interfaces gibt es nun `ThemeHost`, `TabHost`, `SuggestionHost` und
  `KeyboardInputHost` (plus minimales Basis-Interface mit nur `context`).
  `ThemeController`, `TabController`, `SuggestionController` und `KeyboardBinder`
  deklarieren nur noch die Rolle, die sie wirklich brauchen (Interface
  Segregation); `KeyTabImeService` implementiert alle vier. Kein Verhalten
  geändert.
- **Refactor (P3): zentraler Prefs-Zugriff** — alle 25 direkten
  `context.getSharedPreferences(...)`-Aufrufe in 12 Dateien laufen jetzt über
  `Prefs.of(context)`. Dateiname (`keytab_prefs`) und Modus (`MODE_PRIVATE`)
  sind damit an genau einer Stelle definiert und können nicht mehr driften.
- **Doku: Changelog aus dem README ausgelagert** — der Changelog lebt jetzt in
  `CHANGELOG.md`; das README verlinkt ihn nur noch. Release Notes und
  Versionshistorie haben damit einen eigenen, auffindbaren Ort.
- **CI: Doku-Drift-Gate** — neuer CI-Job `docs` führt
  `scripts/check_docs_drift.sh` aus und bricht den Build ab, wenn
  versionName/versionCode, `CHANGELOG.md`-Kopf und fastlane-Changelog
  auseinanderlaufen, oder wenn die im README genannten Testzahlen und
  Suite-Namen nicht mehr zum Code passen. Läuft auch im Release-Workflow vor dem
  Keystore-Restore.
- **Versionierung: Single Source of Truth** — die Version wird ausschließlich in
  `app/build.gradle.kts` gepflegt; der fastlane-Changelog für versionCode 23
  wurde nachgezogen (fehlte seit 0.9.7).
- **README-Korrekturen** — Testtabelle und Test-Ratio waren veraltet (nannte 164
  Tests in 19 Suites und zwei gelöschte Klassen `EditorPanelTest`/
  `ClipboardPanelTest`; korrekt sind 194 Tests in 22 Suites).
- **Testqualität: aussagelose Tests entfernt bzw. zu echten Prüfungen gemacht** —
  vier Tests behaupteten nur, dass nichts abstürzt, und ein Test verglich einen
  Wert mit sich selbst:
  - `TopSectionTest`: „`updateThemeIcons` ohne Crash" hatte **null Assertions**.
    Prüft jetzt, dass das aktive Icon den Hervorhebungs-Hintergrund bekommt und
    das andere transparent bleibt (beide Richtungen).
  - `FileManagerFragmentTest`: der alte „Up ohne Elternteil"-Test las den Pfad
    vorher und nachher und verglich ihn mit sich selbst (`assertSame`-Tautologie).
    Ersetzt durch zwei echte Verträge: der letzte Tab ist nicht schließbar
    (Pfad bleibt erhalten) und jeder Tab merkt sich sein eigenes Verzeichnis
    über einen Tab-Wechsel hinweg.
  - `LiftSpanTest`: `assertEquals("...", true, shift < 21)` plus ein
    `assertEquals(4, shift)` auf einen selbst berechneten Wert → `assertTrue`.
  - `PanelHeightsTest`: Debug-`println` und ein Vergleich gegen eine fest
    verdrahtete 164-dp-Altkonstante entfernt, die im Code nicht mehr existiert.
- **Testqualität: `println`-Debug-Rauschen entfernt** — zehn `println`-Aufrufe in
  `ThemeApplierTest` gaben bei jedem Lauf Diagnosewerte aus; in allen Fällen
  trug die direkt folgende Assertion-Message dieselbe Information bereits.
- **Testqualität: flaky Polling-Schleife ersetzt** — `FileManagerFragmentTest`
  wartete bis zu 3 s mit `Thread.sleep(20)` auf den I/O-Pool. Jetzt wird der
  Pool deterministisch über einen Marker-Task abgewartet (`submit{}.get`), was
  den Test von 3 s auf unter 1 s bringt und Zeitabhängigkeit unter CI-Last
  entfernt. `Thread.sleep` kommt im Testbaum nicht mehr vor.


## 0.9.7

- **Trail (typing trail + correction trace, theme settings section „Trail")**: the last clicked letter is highlighted and fades step by step with each new input until it disappears (`trail_steps`, default 5, adjustable 3/5/7/10). Drawn as a **foreground overlay** (`Button.setForeground`), so key background, corner radius and press state stay untouched (earlier bug: `SRC_IN` tint on the shared background drawable made keys invisible). Optional (`trail`, default off), colour via the theme colour wheel.

  **Correction trace** (`trail_trace`, default off): while typing, the letters of the current word are tinted **red** when automatic correction would replace the word, **green** when the dictionary accepts it. Pure classification in `TrailLogic.classifyTypedWord` (unit-tested); the trace is a read-only indicator — it never changes what gets typed.

  **One state per letter (bug fix):** the trace is applied **atomically per word** — `TrailManager.traceWord` clears every previous trace entry first (`clearTrace`, which leaves the plain typing trail untouched), and `snap` never overwrites an existing trace entry of the same letter. Both are required because `steps`/`kinds` are keyed by **letter**, not by occurrence: typing `hauss` had `traceWord("haus")` mark all four letters red, and the second `s` tap then overwrote `kinds['s']` with `TYPED` while `steps['s'] = 0` remained — the key showed a mixed colour. Since every occurrence of a letter maps to the same key, contradictory states were visible as overlay artefacts.

  **Safety:** the trail never appears in password fields or when an app sets `IME_FLAG_NO_PERSONALIZED_LEARNING` — `TrailLogic.isTrailAllowed` enforces this in code, not via preference, because a visible trail over a `•` field would leak keystrokes (`CapsLogic` already excluded the same password variations from auto-capitalisation).
- **Config file**: `trail`, `trail_trace` and `trail_steps` are now settable from `keytab_config.txt` (see `docs/CONFIG.md`). The importer gained the missing `int` branch — without it `trail_steps` would have been silently skipped.
- **Notes editor**: highlight rules extracted into `EditorHighlightLogic` (Android-free, unit-tested); shared executors moved to `KeyTabExecutors`.
- **Trail logic extracted** into `TrailLogic` (Android-free, unit-tested) — the decay formula now has a single source, previously duplicated between `TrailManager` and `ThemePrefs.trailColorWithAlpha`.

## 0.9.6

- **Theme**: dark theme default palette darkened to a deeper gray (`kbd_bg #1a1a1a`, `key_bg #2e2e2e`, `key_pressed #444444`).
- **Theme editor**: key background is now a separate color target ("Taste") — background, key, highlight and text are adjustable independently per theme (previously the key color was coupled to the background override). Fix: the Taste color now reliably recolors **every** key (abc letters, shift/del/enter/space/symbol keys, first highlighted suggestion) — the previous `constantState` drawable matching never fired for inflated key drawables, so those keys kept their default color; matching is now by drawable type (Button + StateListDrawable) plus explicit IDs for the suggestion views.
- **Likely Highlighting (theme settings section „Likely Highlighting")**: toggleable next-key highlighting — the most likely next key glows in an adjustable highlight color; when the typed word reaches the top suggestion (likelihood reached) a satisfying pulse effect (color flash + scale pulse + haptic) fires. Pure logic in `LikelyHighlightLogic` (unit-tested).
- **Tab bar fully themed**: the abc/Notes/Files/Terminal tab cells are now filled completely (flat background, no inset border) instead of leaving a gray rim, and Material's own `colorSurface` gray on the `TabLayout` is replaced — the gray no longer shows through behind/around the tab buttons.
- **Alpha/transparency consistency**: the two top rows (tab row + suggestion strip) used to stack their own `kbd_bg` backgrounds on top of the keyboard background (and the tab bar's own surface color), so with a semi-transparent background/key color they looked solid while the letter rows looked translucent. These container backgrounds are now transparent — the themed background comes from the keyboard root only, so alpha (and a configured gradient) behaves identically in every row.
- **Refactor**: theme application moved into `ThemeApplier` (recursive view-tree recolor, gradients, per-theme colors) — `KeyTabImeService` slims down.
- **CI**: instrumented tests now run on an API-34 emulator (ReactiveCircus runner, KVM-enabled; `testInstrumentationRunner` declared).
- **Fix**: quoting bug in `install_keytab.sh` — APK staging + Shizuku/rish copy + `pm install` is now clean and robust.
- **Quality**: test counts synchronized (75 unit tests across 8 suites at the time, incl. `ThemeApplierTest` for recolor/alpha/transparency behavior), instrumented-test package assertion tolerates the `.debug` suffix.
- **Snippets tab**: new optional tab for named commands/snippets, stored in `keytab_snippets.txt` (one `name = text` per line). Tap inserts, long-press previews; ＋ / ✎ in the panel header add a snippet or edit the backing file inline.
- **Uniform panel heights**: the Terminal tab uses the editor height (its keyboard stays visible as an input row) and tab labels are single-line (`maxLines=1`, 8sp) so long names no longer wrap.
- **Housekeeping**: version bumped to 0.9.6/versionCode 22, local mislabeled `v1.2.0` tag removed, `themedv0.9.5` branch kept (unmerged).

## 0.9.5

- **Theme settings as its own page**: long-press the ☾/☀ key (or use the "Theme settings" button in the app) opens a full settings screen instead of a popup. Every change applies instantly on the next keyboard focus.
- **Color wheel**: pick any color for background, key/pressed-highlight, text — and the two gradient colors — via an HSV color wheel with brightness and alpha sliders (per dark/light theme).
- **Gradient presets**: Grau, Nacht, Ozean, Wald, Abend, Lila — plus custom colors and modes (top→bottom, inverted, radial).
- **Everything recolors**: letter keys (incl. pressed state), the tab bar (abc/Notes/Files/Term), the suggestion strip and popups follow the chosen colors.
- **Icons**: monochrome SW pairs for save (⤓), load (⤒) and clipboard (▤), grouped tightly left in the editor row.
- Default look is unchanged (= 0.9.4) until you set a color.

## 0.9.4

- Release fix: the 0.9.3 tag had been moved after the fact, so the existing GitHub release blocked the CI upload → clean version 0.9.4 (versionCode 20). Content identical to 0.9.3.

## 0.9.3

- **Keys grow for real**: dynamic key sizing now affects the layout weight (actual size + hit area), not just the visual transform. Clipping is also disabled — enlarged keys now spill past the grid cell / container edge instead of being cut off.

## 0.8.0

- **Multi-language support** (modular, latin-script only): 7 languages (de, en, es, fr, it, pt, nl), each with its own frequency corpus and language-specific accent popups. Switch in settings; engine reloads on the fly.
- **Architecture**: refactored into dedicated modules — `WordPredictionManager` (suggestion orchestration), `DynamicKeyScaler` (neighbor-aware key sizing), `LanguageModule` (registry + accents), alongside existing `SuggestionEngine`, `KeyScaleLogic`, `TextEditLogic`.
- Clipboard history moved into a picker dialog (📋 button in Notes tab); inline clipboard list removed.
- Dynamic key sizing: stepped grades (1.30×/1.15×), shrink limited to direct neighbors (0.85×/0.925×).
- Terminal: black theme + standard prompt `user@host:~$` with cd tracking.
- BadToken fix for IME dialogs (proper window token).
- Upgraded unit tests: 58 tests across `SuggestionEngine`, `TextEditLogic`, `KeyScaleLogic`, panels.
## 0.7.2

- Housekeeping: removed stray debug APK from repo root, aligned version metadata (0.7.2, versionCode 14), fastlane changelog added

## 0.7.0

- Maintenance release: word-delete logic fixed (single separator space kept, multi-space gap deleted with the word)
- Case matching: suggestions capitalize on empty input
- CI: unit tests green, actions upgraded to v5
- Release automation: tag `v*` builds a signed APK and publishes a GitHub release

## 0.6.1

- Fix: `SuggestionEngine.topBaseOrder` is now lazily initialized (fixes a crash on instantiation)
- Fix: FileManagerPanel.navigate() null-sicher (Crash bei Navigation behoben)
- Fix: TerminalPanel Shell-Fallback für verschiedene Android-Geräte
- Fix: `SuggestionEngine` thread-safe (`ConcurrentHashMap`)
- Fix: KeyTabImeService.onDestroy() für korrektes Cleanup
- Feature: Build-Skripte (build_keytab.sh, install_keytab.sh)

## 0.6.0

- Word prediction with unigram frequencies, bigrams, user dictionary, prefix autocomplete, fuzzy correction
- Dynamic key sizing driven by suggestion scores
- Optional toggles for suggestions and dynamic keys in settings
- Notes tab merges editor and clipboard; folder browser on load (IME dialog fix)
- Terminal tab label spelled out; accelerating backspace
- Theme fix: sun symbol now visible in light mode
- 19 unit tests for SuggestionEngine

## 0.5.0

- Optional terminal tab with interactive shell, toggleable in settings
- Enter key keeps constant size and position across all tabs
- File manager state (current dir + back-stack) persisted across restarts
- formatSize supports GB and TB
- Long-Press popup extracted into LetterPopup class

## 0.4.0

- Long-press popups with punctuation on letter keys
- Drag selection in popup (Gboard-style)
- Dark/light toggle, persisted
- Number row toggle in settings
- TAB key and dot button in bottom row
- Files tab: long-press context menu

## 0.3.0

- Long-press Backspace deletes whole word
- File I/O runs asynchronously
- Long-press popup uses theme colors
- Text logic extracted into testable class

## 0.2.0

- Character layer (?123) with toggle
- File manager shows files with sizes
- Storage permission handling

## 0.1.0

- Initial MVP: keyboard with TAB key and tabbed file manager


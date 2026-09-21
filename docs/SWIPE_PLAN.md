# Swipe-Plan: Gleit-Eingabe + Schaltplan-Pfad (v0.11)

> **Status: S1–S5 DONE (2026-09-19).** Alle fünf Slices sind implementiert,
> getestet und in den Mainline-Build integriert (Version 0.11, versionCode 26).
> Dieses Dokument spezifiziert das Feature „Swipe“ als Erweiterung des
> bestehenden Likely-/Key-Scale-Systems: **Skalierte, einfarbig gefärbte Tasten
> (wie „most likely“, aber in eigener Farbe) werden mit den nächsten
> wahrscheinlichen Tasten zu einem Schaltplan verbunden** — passiv als
> Prognose-Vorschau, aktiv als echte Gleit-Eingabe.

---

## 1. Ziel und Nutzen

Heute zeigt KeyTab die *eine* wahrscheinlichste nächste Taste als
Likely-Highlight (gestufte Skalierung + Farbe). Der Nutzer muss trotzdem
jede Taste einzeln tippen. Swipe ergänzt daraus zwei Stufen:

1. **Schaltplan-Preview (passiv):** Die wahrscheinlichen Folge-Tasten werden
   als verbundener Pfad sichtbar — Knoten = skalierte, einfarbige Tasten in
   einer *eigenen* Pfad-Farbe (nicht der Likely-Farbe), Kanten =
   Verbindungslinien zwischen aufeinanderfolgenden Prognose-Tasten. Der
   Blick muss nicht mehr einzeln suchen; der „Stromlauf“ zeigt das
   wahrscheinlichste Wort.
2. **Swipe-Eingabe (aktiv):** Der Nutzer gleitet mit dem Finger über die
   Tastatur; die gefahrene Route wird gesampelt und gegen die Engine
   bewertet. Auf Loslassen erscheinen die besten Kandidaten in der
   Vorschlags-Leiste (Auto-Commit bei klarem Ergebnis). „Wort-Wisch“ wie
   bei Gesture-Typing — aber offline, engine-basiert, ohne neues Modell.

Beide Stufen teilen dieselbe Darstellungs-Infrastruktur (Knoten-Farbe +
Kanten) und dieselbe Privatsphäre-Regel wie der Trail (hart im Code,
Passwort-Felder).

---

## 2. IST-Stand: vorhandene Bausteine (werden wiederverwendet, nicht ersetzt)

| Baustein | Datei | Relevanz für Swipe |
|---|---|---|
| Prognose-Scores pro nächstem Zeichen | `SuggestionEngine` (akkumulierte, gewichtete Scores → `KeyScaleLogic`) | **Datenquelle** für Prognose-Kanten (Schaltplan) und Swipe-Kandidaten |
| Gestufte Skalierung + Nachbar-Graph | `KeyScaleLogic.scales(scores, neighborsOf)`, `DynamicKeyScaler.rebuildNeighbors()` | Nachbar-Beziehungen (Zeile ±1, darüber/darunter ±1 Spalte) = **Verdrahtungsplan**; Skalen-Stufen (1.21×/1.15×) gelten auch für Swipe-Knoten |
| Likely-Highlight | `LikelyHighlightLogic` + `SuggestionController.updateDynamicKeys` | Muster für Knoten-Färbung (GradientDrawable, flache Farbe, Restore-Mechanik `likelyHighlighted`) |
| Foreground-Overlay-Technik | `TrailManager` (`Button.setForeground`, keyed by letter, Decay) | Technik für Pfad-Knoten-Markierung; Overlay zerstört Key-Background/Press-State nicht |
| Reine-Logik-Konvention + Performance-Gate | `TrailLogic`, `TrailPerformanceTest` (18 Tests / Hot-Path-Benchmark) | Vorlage: Swipe-Logik wird Android-frei + Micro-Benchmark-Pflicht |
| Theme-Farbsystem | `ThemePrefs` (`KIND_*`, Color-Wheel, per dark/light) | Neue Kinds `KIND_SWIPE` (Knoten) + `KIND_SWIPE_EDGE` (Kanten) integrieren sich in bestehende Theme-UI |
| Passwort-Schutz | `TrailLogic.isTrailAllowed` (4 Tests) | **Regel übernehmen:** kein Swipe-Pfad in Passwort-Feldern / `IME_FLAG_NO_PERSONALIZED_LEARNING` |
| Vorschlags-Leiste + Tags | `SuggestionController`, `SuggestionEngine.SNIPPET_TAG`/`EMOJI_TAG` | Swipe-Kandidaten laufen über dieselben 3 Slots (kein neues Tag nötig — Wörter sind Wörter) |
| Settings + Config-Datei | `SettingsConfig`, `docs/CONFIG.md`, Docs-Drift-Gate in CI | Neue Schalter müssen in beiden Quellen + Changelog landen (CI prüft das) |

---

## 3. Konzept

### 3.1 Begriffe

- **Knoten:** Buchstaben-Taste, die Teil des Pfades ist. Darstellung:
  einfarbig (flache Farbe `KIND_SWIPE`), Skalierung übernimmt die bestehenden
  `KeyScaleLogic`-Stufen (Prognose-Stärke → 1.21×/1.15×).
- **Kante:** Verbindungsbahn zwischen zwei Knoten — **keine simple Gerade**,
  sondern PCB-Leiterbahn (Manhattan + 45°-Ecken, Via-Pads, Junction-Dots,
  2 Lagen — Details §3.5), Farbe `KIND_SWIPE_EDGE`. **Solid** = bereits
  gefahren (aktiver Swipe) bzw. gesichert (Preview ab getipptem Wort),
  **gestrichelt** = reine Prognose.
- **Pfad:** geordnete Zeichenfolge. Zwei Quellen:
  - *Prognose-Pfad* (Preview): `getipptes Wort` → bester Folgebuchstabe →
    dessen bester Folgebuchstabe (Tiefe 2–3, konfigurierbar).
  - *Fahr-Pfad* (Swipe): gesampelte Zeichen der Finger-Route.
- **Schaltplan:** die Gesamtdarstellung — Knoten + Kanten auf dem Tastatur-
  Layout. Visuelles Vorbild ist eine **Leiterplatte (PCB)**: Pads/Vias,
  45°-Leiterbahnen mit runden Ecken, Abzweigpunkte — verbindliche Specs in
  §3.5 (Verdrahtungs-Look).

### 3.2 ASCII-Skizze (Prognose-Pfad beim Tippen von „hau“)

```
 q    w    e    r    t    z    u    i    o    p    ü
                    ╭┄┄┄┄┄┄┄╮
                    ┊   ╭┄┄┄◉ u     ← Prognose: Ring-Pad ◉ + gestrichelte
                    ┊   ┊              45°-Leiterbahn (Lage 2, −3dp)
 a ━━━ s ━━━━━━━━━━ h ┛  └─ Junction-Dot an h (Abzweig zur Prognose)
 ╰ Via-Pads ● auf a, s, h; solide Bahn Lage 1, 45°-Ecken gerundet
```

Lesart: `h` und `a` sind bereits Teil des Worts (solide Bahn, Via-Pads),
`u` ist die Prognose (Ring-Pad, gestrichelte Leiterbahn, skaliert,
Pfad-Farbe statt Likely-Farbe). Verbindliche Darstellungs-Specs: §3.5.

### 3.5 Verdrahtungs-Look (PCB-Ästhetik — verbindlich)

Der Schaltplan muss wie eine **echte Leiterplatten-Verdrahtung** wirken,
nicht wie verbundene Punkte. Alle Geometrie ist reine Funktion (in
`SwipePathLogic` getestet), das Zeichnen übernimmt `SwipeOverlayView`:

1. **Leiterbahnen (Traces), keine Geraden:** Kanten werden als
   Manhattan-Pfad mit **45°-Ecken** geroutet (PCB-Stil): vom Startzentrum
   auf die Ziel-Achse, dann 45°-Diagonale zum Ziel — Ecken als
   Viertelkreis-Bögen abgerundet (Radius 6 dp). Diagonal-Nachbarn
   (häufigster Fall auf QWERTZ) bekommen eine einzige 45°-Linie mit
   gerundeten Enden; orthogonal/L-förmige Verbindungen einen klassischen
   „Z-“ bzw. „L-Lauf“.
2. **Via-Pads an jedem Knoten:** An jeder beteiligten Taste zeichnet das
   Overlay ein **Pad**: gefüllter Kreis (Ø 10 dp) + konzentrischer Ring
   (1,5 dp Strich, 3 dp Abstand). Gefülltes Pad = gesichert/gefahren
   (solid), Ring-Only = Prognose (gestrichelt). Das Pad sitzt auf dem
   Tastenzentrum und bildet visuell den „Löt-Punkt“ der Leiterbahn.
3. **Junction-Dots (Abzweig-Punkte):** Verzweigt eine Bahn (ein Quell-
   knoten, mehrere Ziele — z. B. Tiefe-2-Preview), bekommt die Abzweig-
   stelle einen Punkt (Ø 5 dp) — wie bei echten Bauteil-Pins. Keine
   Verzweigung → kein Dot.
4. **Zwei Lagen (2-Layer-Board):** Prognose- und Fahr-Lage sind zwei
   „Kupfer-Lagen“: Preview-Kanten laufen leicht versetzt (−3 dp Y-Versatz)
   und gestrichelt, Fahr-Kanten solid auf der Hauptlage. Kreuzen sich
   Lagen, gewinnt die obere (Fahr-Pfad) — wie ein Via-Übergang, ohne dass
   die Linien visuell verschmelzen.
5. **Leiterbahn-Dicke nach „Stromstärke“:** Kanten-Stärke skaliert mit der
   Prognose-Stärke (score-relativ, 2 dp schwach → 4 dp stark) — dickeres
   „Kupfer“ = sicherere Verbindung. Fahr-Kanten: konstant 3,5 dp.
6. **Drill-Stop („Lötauge“):** Die aktuell aktive Taste (letzter Pfad-
   punkt) bekommt einen hellen Drill-Punkt (Ø 3 dp) im Pad-Zentrum — der
   „Strom fließt hier“-Indikator, statisch (kein Animations-Budget).

### 3.3 Aktiv-Swipe (Finger)

1. **Start:** Finger-down auf Buchstaben-Taste. Bewegung > Schwellwert
   (Standard 12 dp) *vor* Long-Press-Timeout → Swipe-Start, laufendes
   Long-Press/Popup (`LetterPopup`) wird abgebrochen. Kein
   Schwellwert-Überschreiten → normaler Tap (KeyboardBinder-Pfad, unverändert).
2. **Sampling:** pro Move-Event (~60 Hz) Taste unter dem Finger
   (O(1) via vorberechneter Zentren-Matrix, §4). Konsekutive Duplikate
   dedupliziert. Nur erste Pointer-Instanz; zweite Pointer ignorieren.
3. **Live-Darstellung:** Fahr-Pfad als solide Kanten, aktuelle Taste als
   Knoten hervorgehoben; Prognose-Fortsetzung (bester Folgebuchstabe des
   aktuellen Pfad-Präfix) als gestrichelte Kante — der Schaltplan wächst
   mit der Fingerbewegung mit.
4. **Release:**
   - Pfadlänge < 2 Zeichen → als normaler Tap werten (Fallback, kein
     Wortverlust).
   - Sonst: `SwipeScorer.candidates(path)` → Top-Kandidaten.
     - **Auto-Commit** (Default an): Top-Kandidat Score-Abstand zum Zweiten
       ≥ Faktor 1.35 **und** Pfadlänge ≥ 3 → `applySuggestion(word)`
       (bestehende Route: matchCase, Learn, Shift-Reset,
       Auto-Korrektur-Konsistenz).
     - sonst: Kandidaten in die 3 Vorschlags-Slots rendern (Tap übernimmt
       über den bestehenden `applySuggestion`-Pfad).
5. **Buffer-Regel:** Swipe startet ein **neues** Wort (Buffer-Reset beim
   Start). Mischen Tippen+Swipe pro Wort ist explizit **nicht** vorgesehen
   (Entscheidung §8) — hält Scorer und Undo-Semantik einfach.

### 3.4 Prognose-Preview (passiv, ohne Finger)

Beim normalen Tippen rendert der Schaltplan die Fortsetzung des aktuellen
Worts: Kante von der zuletzt getippten Taste zu den Folge-Prognosen.
Tiefe standardmäßig 1 (nur der nächste Schritt als gestrichelte Kante —
das Likely-Highlight bleibt die „Stufe 3“-Darstellung, der Schaltplan
ergänzt die *Verbindung*). Preview ist ein eigener Schalter und
standardmäßig **aus** (Ruhe im Default-Look, konsistent zur
Emoji-/Trail-Philosophie).

## 4. Architektur

Neue Dateien (Konvention: reine Logik Android-frei, Manager als View-Layer):

```
app/src/main/java/com/piotv/keytab/ime/
├── SwipePathLogic.kt     # REIN: Sampling → Zeichenpfad, Dedup, Hit-Test
├── SwipeScorer.kt        # REIN: Pfad → Kandidaten (Engine + Damerau-Fallback)
├── SwipeManager.kt       # VIEW-LAYER: Knoten-Färbung (Foreground-Overlay à la TrailManager),
│                         #   State-Maschine idle/preview/swiping, Release-Entscheidung
└── SwipeOverlayView.kt   # Custom View über dem Buchstaben-Panel: Kanten zeichnen
                          #   (Paint/Path wiederverwendet, invalidate nur bei Pfad-Änderung)
app/src/test/java/com/piotv/keytab/ime/
├── SwipePathLogicTest.kt # Sampling, Dedup, Hit-Test-Ränder
├── SwipeScorerTest.kt    # Kandidaten, Auto-Commit-Schwelle, Damerau-Korrekturen
└── SwipePerformanceTest.kt # Hot-Path-Benchmark (analog TrailPerformanceTest)
```

### 4.1 Schnittstellen (Entwurf)

```kotlin
object SwipePathLogic {
    /** Taste unter Punkt (Zentren-Matrix, einmal pro View-Build berechnet). */
    fun charAt(centers: Map<Char, Pair<Float, Float>>, x: Float, y: Float,
               toleranceDp: Float, density: Float): Char?

    /** Fahr-Pfad: konsekutive Duplikate entfernen, Start behalten. */
    fun dedupe(path: List<Char>): List<Char>

    /** Prognose-Fortsetzung eines Präfix (Tiefe n) aus den Engine-Scores. */
    fun forecast(prefix: String, nextScores: (String) -> Map<Char, Double>,
                 depth: Int): List<Char>
}

object SwipeScorer {
    /** Kandidaten für einen Fahr-Pfad: exakte Pfad-Wörter zuerst, dann
     *  Damerau-nahe Wörter, Score gewichtet mit Pfad-Kohärenz. */
    fun candidates(path: List<Char>, engine: SuggestionEngine, max: Int = 3)
        : List<SuggestionEngine.Suggestion>

    /** Auto-Commit erlaubt? (Abstand top→second, Mindestlänge). */
    fun autoCommit(cands: List<SuggestionEngine.Suggestion>,
                   minGap: Double = 1.35, minLen: Int = 3): Boolean
}

interface SwipeHost : KeyboardHost {          // Rollen-Interface (Phase-7-Stil)
    fun swipeCandidates(cands: List<SuggestionEngine.Suggestion>)
    fun swipeCommit(word: String)
}
```

### 4.2 Verdrahtung (wer ändert woran)

| Datei | Änderung |
|---|---|
| `KeyTabImeService` | `SwipeHost` implementieren; `SwipeManager` bauen/zerstören (Lifecycle analog TrailManager) |
| `KeyboardBinder` | Touch-Erkennung: Down auf Letter → Potential-Swipe; Move > Threshold → an SwipeManager delegieren + Long-Press/Popup abbrechen; Up ohne Swipe → normaler Tap. **Nur Buchstabenreihen** (Del/Tab/Shift ausschließen) |
| `KeyboardViewFactory` / View-Build | `SwipeOverlayView` über das Buchstaben-Panel (Touch-frei — Events gehen an darunterliegende Buttons weiter); Zentren-Matrix + `DynamicKeyScaler`-Nachbarn dort befüllen |
| `SuggestionController` | Preview-Renderpfad: nach `updateSuggestions` Forecast-Kanten an SwipeManager melden; bei aktivem Swipe rendert der Manager die Kandidaten selbst über dieselben Slot-Views |
| `ThemePrefs` | `KIND_SWIPE = "swipe"`, `KIND_SWIPE_EDGE = "swipe_edge"`, Defaults (Pfad grünlich, Kanten halbtransparent), Color-Wheel-Einbindung |
| `ThemeApplier` | Knoten-Restore in `restoreLikelyKeys`-Manier; Overlay-Farben bei Theme-Wechsel neu laden |
| `SettingsConfig` | `swipe` (bool, Default **false**), `swipe_preview` (bool, false), `swipe_auto_commit` (bool, true), `swipe_threshold_dp` (int, 12), `swipe_depth` (int, 1) + `docs/CONFIG.md` + Changelog (Drift-Gate!) |
| `MainActivity` / Theme-Settings | Schalter „Swipe (Gleit-Eingabe)“ + „Schaltplan-Preview“ im Trail-Abschnitt (`TrailSection`-Muster) |

### 4.3 Datenfluss

```
Finger-Move ─▶ SwipePathLogic.charAt ─▶ dedupe ─▶ SwipeManager (State swiping)
                                                    │
                     ┌──────────────────────────────┘
                     ▼
        SwipeOverlayView (solide Kanten, sofort)
                     │
   Pfad-Ende ändert Taste ─▶ ioExecutor: SwipeScorer.candidates ─▶ mainHandler
                     │
        ┌────────────┴─────────────┐
        ▼                          ▼
  autoCommit==true           Kandidaten → 3 Slots
  applySuggestion(word)      (Tap übernimmt wie gehabt)
        │
        ▼
  reset → Preview-Modus erneut (gestrichelte Prognose fürs nächste Wort)
```

---

## 5. Theme & Look

- **Knoten:** flache einfarbige Füllung (kein Gradient), Eckenradius wie
  Likely-Highlight (8 dp·density). Unterscheidung zum Likely-Highlight
  ausschließlich über die eigene Farbe — genau wie gewünscht („wie most
  likely, aber andere Farbe“). Skalierung: bestehende Stufen.
- **Kanten:** 3 dp Strichstärke, runde Enden (`Paint.Cap.ROUND`), solid α≈230 /
  gestrichelt α≈160 (Dash-Effect 8/6 dp). Zeichnung **hinter** den Buttons
  (Overlay unter den Key-Views) — Taste bleibt immer lesbar; Alternative
  (Entscheidung §8): über allem, dann mit α ≤ 120.
- **Dark/Light + Custom-Theme:** beide Kinds nehmen am bestehenden
  Theme-Export/Import teil (`keytab_theme`-JSON), damit Theme-Teilen funktioniert.

## 6. Privatsphäre & Sicherheits-Regeln (nicht verhandelbar)

1. **Passwort-Felder:** Swipe komplett deaktiviert — gleiche harte Regel wie
   `TrailLogic.isTrailAllowed` (Feld-Flag + `NO_PERSONALIZED_LEARNING`), mit
   **denselben 4 Testfällen** für die Swipe-Pfade gespiegelt. Begründung
   analog `scripts/articles/03-trail-side-channel.md`: eine sichtbare Route
   über `•`-Feldern leakt Zeichenanzahl/Position.
2. **Kein Netz, kein Logging:** Pfad-Sampling lebt nur im RAM, wird nicht
   persistiert; Scorer nutzt ausschließlich die lokale Engine.
3. **Terminal/SSH:** Swipe erzeugt normale `commitText`-Zeichen — funktioniert
   in Termux wie jedes andere Wort, ändert nichts am Routing.

## 7. Performance-Budget

- Budget bleibt **50 ms pro Tastendruck** / 16 ms pro Frame:
  - Move-Event: 1× `charAt` (HashMap-Lookup) + Dedup + Overlay-Invalidate —
    O(1), keine Allokation im Hot Path (Paint/Path-Objekte wiederverwendet).
  - Scoring **nicht** pro Move-Event: nur bei Tastenwechsel und beim Release,
    auf dem `ioExecutor`; Ergebnis via `mainHandler`.
  - Overlay-Invalidate throttle auf max. 1× pro Frame (letzte Koordinate merken).
- `SwipePerformanceTest`: 10 000 Move-Events < X ms (Kalibrierung an
  TrailPerformanceTest), Dedup + charAt O(1)-Nachweis.

## 8. Entscheidungs-Punkte (offen — vor Implementierung festzurren)

| # | Frage | Empfehlung |
|---|---|---|
| 1 | Kanten **unter** den Tasten (lesbar) oder **über** allem (Schaltplan-Look schärfer)? | Unter den Tasten, α-Optimierung später |
| 2 | Swipe bei getipptem Teil-Wort: fortsetzen oder reset? | Reset (neues Wort) — einfacher, vorhersehbar |
| 3 | Auto-Commit-Schwellwert: fix 1.35 oder konfigurierbar? | Fix im ersten Release, Konfig nur bei Nutzer-Feedback |
| 4 | Prognose-Tiefe der Preview: 1 oder 2? | 1 (Ruhe), 2 nur als „Extrem“-Setting |
| 5 | Swipe im Terminal-Tab erlauben? | Ja — commitText-Routing ist identisch |
| 6 | Default-Farbe: grünlich (auffällig) oder bläulich (dezent)? | Grünton, klar unterscheidet vom Likely-Default |

## 9. Umsetzungs-Reihenfolge (empfohlene Slices)

| Slice | Inhalt | Gate | Status |
|---|---|---|---|
| S1 | `SwipePathLogic` + `SwipeScorer` (rein) + Tests | alle grün, keine Android-Imports | ✅ done (19+19 Tests) |
| S2 | `SwipeManager` + Overlay + Knoten-Färbung (Preview-only) | Schaltplan sichtbar beim Tippen, Theme-Wechsel stabil | ✅ done |
| S3 | Touch-Delegation in `KeyboardBinder` + Auto-Commit | Swipe tippt „haus“ in Termux korrekt | ✅ done |
| S4 | Settings (`swipe`, `swipe_preview`, …) + Theme-Kinds + Color-Wheel | Drift-Gate grün, CONFIG.md aktuell | ✅ done |
| S5 | Performance-Benchmark + Passwort-Tests + README/CHANGELOG | 50-ms-Budget nachgewiesen, 4 Passwort-Tests grün | ✅ done (`SwipePerformanceTest`, 4 Passwort-Tests in `SwipePathLogicTest`) |

Schätzung: S1 ~1 h, S2 ~2–3 h, S3 ~2 h (Touch-Konflikte), S4 ~1 h, S5 ~1 h.

## 10. Risiken

- **Touch-Konflikte** (Long-Press-Popups, Del-Repeat, Tab): größtes Risiko —
  S3 bewusst isoliert; Fallback „kein Threshold → normaler Tap“ muss in jedem
  Fall intakt bleiben (Regressionsschutz: bestehende Binder-Tests).
- **Kleiner Bildschirm/Kalibrierung:** 12 dp Threshold + Toleranz-Rand beim
  `charAt` (Taste gilt getroffen ab 40 % Überlappung) — in Tests fixiert.
- **Score-Verfügbarkeit:** Engine noch beim Laden (`engineLoading`) →
  Schaltplan/Preview schlicht aus; Swipe-Fallback = normaler Tap.
- **Umlaute/ß:** Zentren-Matrix umfasst alle sichtbaren Buchstaben-Tasten
  inkl. Umlaut-Reihe; `ß`/Akzent-Popups sind beim Swipe nicht erreichbar
  (dokumentieren), Umlaute direkt auf der Reihe ok.

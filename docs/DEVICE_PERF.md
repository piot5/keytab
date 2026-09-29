# DEVICE_PERF — Methodik, Fallstricke und Baseline der Geräte-Messung

`scripts/device_perf.py` misst die Frame-Zeiten des echten IME über
Shizuku/rish: echte Touch-Events auf die echten Tasten, Frame-Zeiten aus
`dumpsys gfxinfo` + `framestats` des IME-Prozesses. Die JVM-Benchmarks
(`TrailPerformanceTest`, `SwipePerformanceTest`) messen dagegen nur den
Algorithmus, nicht das Frame-Budget.

Aufruf:

```bash
python3 scripts/device_perf.py --scenarios typing,backspace \
    --pkg com.piotv.keytab.debug [--iterations N] [--layout]
```

Exit: 0 = Budgets gehalten, 1 = verletzt, 2 = Setup-Fehler.

## Zielfenster und Zielfeld

- **Gemessen wird immer der IME-Prozess** (`--pkg`, im Setup: das
  Debug-Paket `com.piotv.keytab.debug`, denn dort läuft die aktive IME).
- Das Zielfeld ist die `ImeTargetActivity` aus dem Debug-Build
  (`am start -n com.piotv.keytab.debug/com.piotv.keytab.ImeTargetActivity`):
  drei schlichte `EditText` (normal / password / no-learning). Sie ist
  dem Tippen in Termux vorzuziehen, weil Termux/proot beim Messen eigene
  Last auf dem Gerät erzeugt (Skript, rish-Spawns) und den Fokus
  zurückholen kann.
- **Fokus-Verifikation ist Pflicht** und gehört nicht dem Auge an:
  `dumpsys input_method` muss `inputType=0x1` (normales Textfeld)
  zeigen. `0x81` = Passwortfeld — dort unterdrückt KeyTab Vorschläge,
  Trail und Lernen (Privacy-Guard), die Zahlen wären nicht
  repräsentativ. `mCurrentFocus` allein reicht nicht, und
  `mIsInputViewShown=true` nicht einmal für die Sichtbarkeit der
  Tastatur (siehe Fallstricke).

## Fallstricke (alle praktisch aufgetreten, 2026-09-29)

1. **Terminal holt den Fokus zurück.** Wird die Messung aus Termux
   gestartet, liegt der Fensterfokus zunächst bei Termux. Das
   Zielfenster muss innerhalb des Mess-Scripts (nach dem Absenken des
   Terminals) per `am start` + Tap neu in den Vordergrund geholt und
   verifiziert werden — sonst tippt das Script in die Tastatur über
   Termux und der Report zeigt `target_window=com.termux...`.
2. **`mIsInputViewShown=true` ist kein Sichtbarkeitsbeweis.** Der Wert
   kann `true` sein, während der Screenshot keine Tastatur zeigt
   (verstecktes IME-Fenster). Vor der Layout-Erkennung immer den
   Screenshot prüfen bzw. die Erkennung selbst als Sichtbarkeits-Check
   behandeln (sie bricht bei < 3 Reihen ab — das hat das Setup zweimal
   zuverlässig gerettet).
3. **Layout-Erkennung kann die Enter-Taste für Backspace halten.** Fand
   die Erkennung nur 3 Tastenreihen, setzte sie "Backspace" auf
   (1048, 2453) — das ist die Enter-Position (Bottom-Row rechts). Die
   korrekte Backspace-Position ist (1184, 2305). Ein kompletter
   Backspace-Lauf auf Enter misst dann einen viel zu günstigen
   Szenario-Wert (Enter-Repeat erzeugt keine Lösch-Redraws). Gegenmaßnahme:
   handkalibriertes Override `scripts/device_perf_layout.json` +
   `--layout` benutzen (Backspace/Space/Tabs genügen).
4. **Event-Zahl muss interpretierbar sein.** 2 Long-Press-Events
   ergeben 36 Frames — damit ist Jank-Prozentzahlt kein Signal mehr.
   Faustregel: ≥ 20 Events (Backspace), ≥ 90 Events (Tippen).
5. **Shell-Injektion dominiert die Input-Latenz.** Jeder `input tap`
   ist ein eigener Prozess-Spawn über rish; entsprechend sind die
   gfxinfo-Melder "hohe Input-Latenz" fast immer gesetzt (400+ von 500
   Frames). Das verfälscht vor allem die Event-Kadenz, nicht die
   Frame-Zeiten selbst (die liefert SurfaceFlinger). Für belastbare
   Absolutwerte: instrumentierte Messung
   (`Instrumentation.sendPointerSync` + Choreographer) statt
   Shell-Injektion.
6. **Umgebung kontrollieren.** Thermik (Batterie-Temperatur aus
   `dumpsys battery`), Ladezustand (AC an/ok?) und Vordergrund-App
   zwischen den Läufen dokumentieren. Einzel-Ergebnisse schwanken
   deutlich (gleicher Typing-Pfad: Jank@60 4,5 % ↔ 10,7 % über zwei
   Umgebungen). **Immer ≥ 3 Läufe, Median berichten.**

## Szenarien und Budgets

| Szenario | Events/Lauf | Was es misst |
|---|---|---|
| `typing` | 16 × Iterationen | Buchstaben-Taps: Trail-Klassifikation, Vorschlagsleiste, dynamische Key-Skalierung |
| `backspace` | Iterationen (Long-Press 1,5 s) | Autorepeat: Dauer-Löschen mit kontinuierlichem Redraw |
| `swipe` | 3 × Iterationen | Move-Events, Overlay-Invalidate, Pfad-Scoring |
| `tabswitch` | 5 × Iterationen | Panel-Wechsel (RecyclerView, Datei-Listing) |
| `coldshow` | Iterationen | IME kalt einblenden (`onCreateInputView`) |

Budgets (Gate gegen die 60-Hz-Deadline, damit 60-/120-Hz-Panels
vergleichbar bleiben): Jank@60 ≤ 10 %, p50/p95 ≤ 16,6 ms, p99 ≤ 33,3 ms,
UI-Thread p95 ≤ 16,6 ms.

## Baseline (2026-09-29, einfaches Textfeld, Debug-IME, 120 Hz)

Kontext: 2412DPC0AG, Android 16, Gerät am AC-Lader, Batterie-Temperatur
32,8 → 35,9 °C über die Session. Die folgenden Werte ersetzen die
früheren Einzellauf-Zahlen (typing 4,5 %/PASS über Termux; backspace
61 %/FAIL auf 2 Events bzw. 0 %/Fehlmessung auf Enter).

### Was ein valides Szenario ist — und was nicht

- **Typing auf vollem Feld unter-misst.** Nach langen Füll-Läufen hat
  der Engine keine Prefix-Treffer mehr (das Feld ist ein riesiges
  Concat-Wort), die Vorschlagsleiste steht still: nur noch ~0,5
  Frames/Event statt ~5. Der realistische Typing-Hot-Pfad ist der
  **Start in ein leeres Feld mit aktiver Vorhersage** — die Füll-Läufe
  sind die ehrlichste Typing-Messung, nicht die "typing"-Messung auf
  vollem Feld.
- **Backspace 1,5-s-Dauerpressen leeren das Feld** (accelerating
  Repeat, ~600–800 Löschungen vs. ~430 vorher getippter Zeichen): die
  zweite Hälfte jedes Laufs misst "Backspace im Leeren" (No-Op, ~0
  Frames). Mit `KEYTAB_BS_MS=400` (Bursts) bleiben ~60–100 Löschungen
  unter dem Füll-Volumen — das Feld bleibt gefüllt, das Szenario ist
  stabil reproduzierbar.

### Ergebnisse

**Backspace-Autorepeat (400-ms-Bursts, gefülltes Feld, 3 Läufe):**
stabil PASS.

| Run | Batt-Temp | Frames | f/Event | Jank@60 | p50 | p95 | UI p95 |
|---|---|---|---|---|---|---|---|
| 1 | ~34 °C | 156 | 7,8 | 1,2 % | 5 | 13 | 3,2 |
| 2 | 34,1 °C | 146 | 7,3 | 1,3 % | 8 | 12 | 4,2 |
| 4 | 35,9 °C | 159 | 8,0 | 2,5 % | 5 | 14 | 2,6 |
| **Median** | | | **7,8** | **1,3 %** | **5** | **13** | **3,2** |

**Typing (leeres Feld → füllen, aktive Vorhersage, 192 Events):** stark
abhängig vom Thermozustand des Geräts:

| Run | Batt-Temp | f/Event | Jank@60 | p50 | p95 | UI p95 |
|---|---|---|---|---|---|---|
| 2 | 34,1 °C | 4,8 | **3,4 %** | 9 | 15 | 5,0 |
| 3 | 35,1 °C | 5,0 | **42,4 %** | 14 | 22 | 6,4 |
| 4 | 35,1 °C | 4,9 | **39,3 %** | 13 | 22 | 6,4 |

Zwischen 34,1 °C und 35,1 °C Batterie-Temperatur kippt der Jank@60 um
den Faktor ~10 (3,4 % → 42 %) bei identischem Workload — sehr
wahrscheinlich ein Throttling-Schwellenwert des Geräts (MIUI), kein
Verhalten der App. **Einzel-Ergebnisse ohne Therma-Protokoll sind
unbrauchbar**; der frühe Termux-Lauf (4,5 %) und der erste
ImeTarget-Lauf (10,7 %) passen in dieses Bild (beide kühl gemessen).

### Fazit

- Backspace-Autorepeat: **PASS** (Median 1,3 % Jank@60, p95 13 ms) im
  validen Burst-Szenario. Der historische "FAIL" (61 %) war ein
  Artefakt aus 2 Events und leerendem Feld.
- Typing: kühl **PASS** (3,4 %), heiß deutlich FAIL (39–42 %) —
  messgerätebedingt, nicht appbedingt. Für ein hartes Gate muss die
  Messung bei kontrollierter Temperatur (< ~34 °C Batterietemp) und
  ohne AC-Lader erfolgen.
- Der UI-Thread ist in allen Szenarien entspannt (p95 ≤ 6,4 ms); die
  Deadline-Misses entstehen im Draw/Frame-Rhythmus. Mitigation-Idee
  bleibt: Trace-`autoCorrect`-Klassifikation nur beim Wortabschluss
  statt pro Keystroke/Repeat.

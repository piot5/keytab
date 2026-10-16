# Third-party notices

KeyTab selbst steht unter der **MIT-Lizenz** (siehe [`LICENSE`](LICENSE)).
Dieses Dokument listet die Inhalte, die **nicht** unter MIT stehen und deshalb
eigene Bedingungen mitbringen. Es ist die vollständige Liste: KeyTab hat keine
Netzwerk-Permission und lädt zur Laufzeit nichts nach — was hier nicht steht,
ist auch nicht im APK.

---

## 1. Wortfrequenz-Korpora (CC-BY-SA-4.0)

Betroffene Dateien (im APK unter `assets/`):

| Datei | Sprache |
|---|---|
| `app/src/main/assets/de_freq_top6000.txt` | Deutsch |
| `app/src/main/assets/en_freq_top6000.txt` | Englisch |
| `app/src/main/assets/es_freq_top6000.txt` | Spanisch |
| `app/src/main/assets/fr_freq_top6000.txt` | Französisch |
| `app/src/main/assets/it_freq_top6000.txt` | Italienisch |
| `app/src/main/assets/nl_freq_top6000.txt` | Niederländisch |
| `app/src/main/assets/pt_freq_top6000.txt` | Portugiesisch |

- **Quelle:** [FrequencyWords](https://github.com/hermitdave/FrequencyWords)
  (Wortlisten aus den OpenSubtitles-2018-Untertiteln), Dateien `*_50k.txt`.
- **Lizenz:** Creative Commons Attribution-ShareAlike 4.0 International
  (**CC-BY-SA-4.0**) — <https://creativecommons.org/licenses/by-sa/4.0/legalcode>
- **Namensnennung:** „FrequencyWords“ von hermitdave, lizenziert unter
  CC-BY-SA-4.0. In der App sichtbar in der Store-Beschreibung
  (`fastlane/metadata/android/en-US/full_description.txt`) und im README.
- **Änderungen gegenüber der Quelle:** auf die 6 000 häufigsten Einträge je
  Sprache gekürzt, auf eine Zeile je Wort reduziert (`<wort> <häufigkeit>`,
  Leerzeichen-getrennt, absteigend nach Häufigkeit sortiert), Umlaute/Diakritika
  unverändert.
- **Share-alike:** Die Korpora bleiben CC-BY-SA-4.0. Wer sie (auch in Teilen)
  weitergibt oder verändert, muss sie unter CC-BY-SA-4.0 belassen und diese
  Namensnennung mitliefern. Der **App-Code** (MIT) und die **Korpora**
  (CC-BY-SA-4.0) werden getrennt lizenziert; die App nutzt sie als Daten-Assets.

## 2. Emoji-Zeichen (Unicode)

Der Emoji-Katalog (`ime/EmojiModule.kt`) besteht ausschließlich aus
**Unicode-Zeichen** (U+1F600-Folge usw.). Es ist **keine** Emoji-Schriftdatei
gebündelt — die Darstellung kommt vom System-Emoji-Font des Geräts. Für die
Unicode-Zeichen selbst gilt die
[Unicode-Lizenz](https://www.unicode.org/license.txt).

## 3. Bibliotheken (Apache-2.0, nicht mitgeliefert)

AndroidX, Material Components, Kotlin-Stdlib und kotlinx-coroutines stehen unter
**Apache-2.0**. Sie werden als Gradle-Abhängigkeiten aus Google Maven bzw.
Maven Central eingebunden (im Repo liegt **kein** vendored Fremdcode) — die
Lizenztexte liefert Gradle mit den Artefakten aus. Eine Übersicht der Versionen
liegt in `gradle/libs.versions.toml`, die Prüfsummen in
`gradle/verification-metadata.xml`.

## 4. Wortlisten aus Nutzereingaben

Das gelernte Wörterbuch („User-Dictionary“) enthält **nur** Wörter, die die
nutzende Person selbst getippt hat. Es verlässt das Gerät nicht (keine
`INTERNET`-Permission) und ist mit `allowBackup="false"` von Backups
ausgenommen.

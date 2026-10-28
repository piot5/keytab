# Third-party notices

KeyTab itself is under the **MIT license** (see [`LICENSE`](LICENSE)).
This document lists content that is **not** MIT and therefore carries its own terms.
It is the complete list: KeyTab has no `INTERNET` permission and loads nothing at runtime — what is not listed here is not in the APK.

---

## 1. Word-frequency corpora (CC-BY-SA-4.0)

Files affected (in the APK under `assets/`):

| File | Language |
|---|---|
| `app/src/main/assets/de_freq_top6000.txt` | German |
| `app/src/main/assets/en_freq_top6000.txt` | English |
| `app/src/main/assets/es_freq_top6000.txt` | Spanish |
| `app/src/main/assets/fr_freq_top6000.txt` | French |
| `app/src/main/assets/it_freq_top6000.txt` | Italian |
| `app/src/main/assets/nl_freq_top6000.txt` | Dutch |
| `app/src/main/assets/pt_freq_top6000.txt` | Portuguese |

- **Source:** [FrequencyWords](https://github.com/hermitdave/FrequencyWords) (word lists from OpenSubtitles 2018), files `*_50k.txt`.
- **License:** Creative Commons Attribution-ShareAlike 4.0 International (**CC-BY-SA-4.0**) — <https://creativecommons.org/licenses/by-sa/4.0/legalcode>
- **Attribution:** "FrequencyWords" by hermitdave, licensed under CC-BY-SA-4.0. Visible in the app in the store description (`fastlane/metadata/android/en-US/full_description.txt`) and in the README.
- **Changes versus source:** shortened to the 6,000 most frequent entries per language, one word per line (`<word> <frequency>`, space-separated, descending by frequency), umlauts/diacritics unchanged.
- **Share-alike:** The corpora remain CC-BY-SA-4.0. Whoever passes them on or modifies them must keep them under CC-BY-SA-4.0 and include this attribution. The **app code** (MIT) and the **corpora** (CC-BY-SA-4.0) are licensed separately; the app uses them as data assets.

---

## 2. Emoji characters (Unicode)

The emoji catalog (`ime/EmojiModule.kt`) consists exclusively of **Unicode characters** (U+1F600 series etc.). No emoji font file is bundled — rendering comes from the device's system emoji font. The Unicode characters themselves are covered by the [Unicode license](https://www.unicode.org/license.txt).

---

## 3. Libraries (Apache-2.0, not vendored)

AndroidX, Material Components, Kotlin Stdlib, and kotlinx-coroutines are under **Apache-2.0**. They are pulled in as Gradle dependencies from Google Maven and Maven Central (no vendored third-party code in the repo) — license texts are delivered by Gradle with the artifacts. A version overview is in `gradle/libs.versions.toml`, checksums in `gradle/verification-metadata.xml`.

---

## 4. Learned word list from user input

The learned dictionary ("User-Dictionary") contains **only** words typed by the user. It never leaves the device (no `INTERNET` permission) and is excluded from backups via `allowBackup="false"`.

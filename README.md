# KeyTab

**Tastatur-App (IME) mit TAB-Taste + Dateimanager mit Tabs** – 100 % Kotlin, Ready-to-build Android-Projekt.

## Features

### ⌨️ KeyTab Keyboard (IME)
- Vollwertige Bildschirmtastatur als `InputMethodService`
- **TAB-Taste** (sendet `KEYCODE_TAB` – ideal für Termux/SSH-Shells)
- Shift-Umschaltung, Backspace, Enter, Space
- Keine Netzwerkberechtigung, keine Datensammlung

### 📂 Dateimanager mit Tabs
- Mehrere Browser-Tabs (jeder Tab merkt sich sein Verzeichnis)
- Navigation, Ordner wechseln, Dateien öffnen (VIEW-Intent)
- Material 3 UI (TabLayout + RecyclerView + ViewPager2)

## Build

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Voraussetzungen: JDK 17, Android SDK (compileSdk 34). SDK-Pfad in `local.properties`
(`sdk.dir=...`) oder Umgebungsvariable `ANDROID_HOME`.

> On-Device-Build (ARM64 / Proot / Termux): Googles `aapt2` ist x86_64-only.
> ARM-Build-Tools von <https://github.com/lzhiyong/android-sdk-tools> nutzen und in
> `~/.gradle/gradle.properties` setzen:
> `android.aapt2FromMavenOverride=/pfad/zu/aapt2`

## Installation

1. APK installieren
2. *Systemeinstellungen → Sprachen & Eingabe → Bildschirmtastatur verwalten* → **KeyTab** aktivieren
3. In einem Textfeld Tastatur wechseln → **KeyTab**

## Struktur

```
app/src/main/java/com/piotv/keytab/
├── MainActivity.kt          # Tabs: Dateien / Tastatur
├── file/FileManagerFragment.kt  # Tab-Dateimanager
└── ime/KeyTabImeService.kt  # Tastatur-IME (TAB!)
```

## Lizenz

MIT – siehe [LICENSE](LICENSE)

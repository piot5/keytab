# Reddit Promotion Post (r/androidapps & r/Android)

Stand: 2026-09-26

## 1. r/androidapps (Self-Promotion Megathread oder [DEV]-Post)

### Titel
```text
[DEV] KeyTab: Open-source, zero-permission keyboard with tabbed file manager, editor, and offline prediction
```

### Text (Markdown)
```markdown
Hi everyone,

I developed KeyTab, an open-source (MIT) Android keyboard built in 100% Kotlin for developers, Termux users, and privacy-focused users.

**Features:**
KeyTab embeds a tabbed workspace directly into the keyboard strip:
- Tabbed File Manager: Browse local folders asynchronously and insert paths directly into your active input field, or open files via View Intent.
- Dev-friendly layout: Dedicated TAB key (great for Termux/SSH), reliable Shift/Caps-lock handling, and long-press popups.
- Notes & Snippets: Built-in local editor with save/load, plus quick-insert snippets.
- Clipboard: Persistent history for up to 50 copied items.
- Offline Prediction: On-device n-gram models, fuzzy correction, and optional dynamic key scaling with a strict 50ms per-keystroke performance budget.

**Privacy & Code Quality:**
- Zero network permissions: No INTERNET permission in the manifest. Keystrokes physically cannot leave your device.
- No ads, no trackers, no analytics, allowBackup disabled.
- Fully open source, verified by 470+ unit/contract tests, detekt, and Android Lint.

**Links:**
- GitHub: https://github.com/piot5/keytab
- Latest signed APK: https://github.com/piot5/keytab/releases/latest

Feedback, questions, and feature requests are very welcome.
```

---

## 2. r/Android (Weekly "Saturday APPreciation" Thread)

Hinweis: Auf r/Android nur als Kommentar unter dem hervorgehobenen Moderator-Kommentar posten.

```markdown
**KeyTab**: Open-source, zero-permission keyboard with tabbed file manager, editor, and offline prediction

**Links:**
- GitHub: https://github.com/piot5/keytab
- Latest signed APK: https://github.com/piot5/keytab/releases/latest

**Overview:**
KeyTab is an open-source (MIT, 100% Kotlin) keyboard designed for developers, Termux users, and privacy-minded users. It integrates a local productivity workspace directly into the keyboard strip without requiring app switches:
- Tabbed File Manager: Browse local directories asynchronously and insert paths into active input fields, or open files via View Intent.
- Dev-focused layout: Dedicated TAB key (ideal for Termux/SSH), Shift/Caps lock state machine, and long-press accent popups.
- Notes & Snippets: Integrated editor with save/load and quick-insert snippets.
- Clipboard: Persistent local history for up to 50 items.
- Offline Prediction: Strictly local n-gram models, fuzzy correction, and optional dynamic key scaling (<50ms budget).

**Privacy:**
- No INTERNET permission: Keystrokes cannot leave the device.
- No analytics, no ads, no trackers, allowBackup=false.
- Backed by 470+ unit/contract tests, detekt, and Android Lint.
```

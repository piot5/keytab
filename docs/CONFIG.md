# Configuration (`keytab_config.txt`)

All persistent options from the two settings pages are editable through `keytab_config.txt` in the app's external file directory. The Config button shows the absolute path and adds missing keys with current values. Existing lines, comments and values are preserved.

Android permissions and the system keyboard selection are system actions — not grantable via this text file.

## Supported keys (compact)

| Key | Type | Default | Meaning |
|---|---|---|---|
| `num_row` | bool | `true` | Show number row |
| `dark_mode` | bool | `true` | Dark theme |
| `suggestions` | bool | `true` | Word suggestions |
| `clip_tab` | bool | `true` | Clipboard tab |
| `snippet_tab` | bool | `true` | Snippet tab |
| `swipe` | bool | `false` | Gesture input |
| `likely` | bool | `true` | Likely-key highlighting (old on-disk name `gaming_mode` from ≤ v0.9.6 is migrated on read) |
| `trail` | bool | `false` | Typing trail |
| `trail_trace` | bool | `false` | Suggestion-match marking |
| `trail_steps` | int | `5` | Trail fade steps |
| `language` | str | `de` | `de` \| `en` \| `es` \| `fr` \| `it` \| `pt` \| `nl` |
| `bg_image_uri` | str | empty | `content://`-URI or absolute path |
| `bg_image_fill` | str | `fit` | `fit` \| `cover` \| `stretch` |
| `gradient_color1/2` | str | `default` | Gradient colors (`#RRGGBB` or `default`) |
| `max_scale` | float | `1.21` | Dynamic key scaling upper bound |

Missing or invalid values leave the existing setting unchanged. Unknown keys are preserved when the file is rewritten.

### Renamed on-disk names (≤ v0.9.6)

The likely-key highlighting used to be called "gaming" — a misnomer that never described a game theme, so the on-disk names were renamed on 2026-09-15. Old values are not lost: they are read and renamed once (`ThemePrefs.migrateLegacyNames`), old theme exports keep importing.

| Old on-disk name | Now | Handling |
|---|---|---|
| `gaming_mode` (switch) | `likely` | migrated on read |
| `theme_dark_gaming` / `theme_light_gaming` (colour) | `theme_dark_likely` / `theme_light_likely` | read fallback + migrated on read; old lines in an existing `keytab_config.txt` are still applied on import |
| `gaming_effect` (removed in v0.18) | — | deleted on read |

## Example

```ini
language = en
swipe = true
trail = true
trail_steps = 7
bg_image_fill = cover
max_scale = 1.30
```

Missing keys are filled with defaults on next settings open.

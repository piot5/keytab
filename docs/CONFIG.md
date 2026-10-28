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
| `gaming_mode` | bool | `true` | Gaming theme |
| `trail` | bool | `false` | Typing trail |
| `trail_trace` | bool | `false` | Suggestion-match marking |
| `trail_steps` | int | `5` | Trail fade steps |
| `language` | str | `de` | `de` \| `en` \| `es` \| `fr` \| `it` \| `pt` \| `nl` |
| `bg_image_uri` | str | empty | `content://`-URI or absolute path |
| `bg_image_fill` | str | `fit` | `fit` \| `cover` \| `stretch` |
| `gradient_color1/2` | str | `default` | Gradient colors (`#RRGGBB` or `default`) |
| `max_scale` | float | `1.21` | Dynamic key scaling upper bound |

Missing or invalid values leave the existing setting unchanged. Unknown keys are preserved when the file is rewritten.

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

# hakuX color themes — format version 1

Settings → Display → Color theme is a drop-down with eight palettes. Light / dark mode remains a
separate choice (system, light, dark). Each theme supplies both palettes and
applies to Compose screens, the shader browser, pause window and in-game
framework dialogs. The game picture and user-configured touch controls are
not recolored. Android file pickers and OS-owned widgets retain system styling.

Choose **Export current theme** to save a template, or edit one of these eight
JSON files. In Advanced5.4+, **Import themes JSON** lets you select one or
several files in the Android picker. Extract theme ZIPs first. A single import
also selects the theme; a batch leaves your current selection active.
Imported themes are copied into the app and remain in the drop-down after
switching presets or restarting. The original JSONs are no longer needed for
selection, but keeping them is useful as a backup. Clearing app data or
uninstalling removes the saved catalog.

The drop-down shows Xbox, Satisfactory, Halo inspired, Frutiger Aero, Midnight,
Amber Terminal, Pocket LCD and Synthwave. Only the active palette's preview
strip is shown below it. The menu scrolls on smaller screens. A selected
imported theme remains as an additional entry marked “imported”, including
when its name matches a built-in. Same-name variants receive numbered imported
labels; exact duplicates are skipped. Built-ins are never overwritten. Invalid
files are reported individually and do not block other selected files.
The previously active custom theme is migrated automatically on upgrade.

A file has exactly `version` (number 1), `name` (1–64 printable characters),
`light` and `dark`. Each palette has exactly these opaque #RRGGBB colors:

| Key | Use |
| --- | --- |
| background | Page and pause-window background |
| surface | Cards, dialogs and normal surfaces |
| raised | Raised panels, buttons and screenshot placeholders |
| text | Main text |
| muted | Supporting and disabled text |
| accent | Main controls, focused selections and active switches |
| secondary | Secondary Material controls |
| outline | Borders and dividers |

All eight keys are required in both palettes. Hexadecimal letters may be
upper or lower case. Transparency, image paths, fonts, scripts, web addresses,
extra keys and files over 32 KiB are rejected. Text must contrast at least
4.5:1 with background, surface and raised; muted text must contrast 3:1.
Button text on a solid accent is chosen automatically as black or white.
Error colors remain the standard readable light/dark error colors.

Invalid imports leave the existing theme unchanged. A damaged saved theme
falls back to Xbox. Theme names/colors are data only; nothing is downloaded
or executed. Built-in files can be exported, edited and shared without assets.
All four color-only palettes are original combinations inspired by their
names; no game art, logos or fonts are included.

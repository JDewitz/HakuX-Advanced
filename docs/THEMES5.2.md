# Advanced5.2 — theme drop-down and extra palettes

Based on Advanced5.1, commit 6434b30297db3deaad4292aa78b1f5090ecf7600.

Settings → Display → **Color theme** now uses one read-only drop-down. It
shows the selected name and marks the active entry, with a single color
preview strip below the field. Its scrolling menu is bounded to 320 dp.
Import/export controls and the separate light/dark/system setting remain.

Four new color-only presets join the original four:

| Preset | Colors |
| --- | --- |
| Midnight | Navy, periwinkle and cool blue |
| Amber Terminal | Charcoal, amber and warm gold |
| Pocket LCD | Muted olive/green, inspired by older handheld displays |
| Synthwave | Deep purple, pink and cyan |

Xbox, Satisfactory, Halo inspired and Frutiger Aero keep their previous colors.
Each preset includes a light and dark palette. The same selection continues
across Compose screens and in-game menus. This does not change the emulator
picture or add CRT effects; those remain separate shader settings.

The current imported theme is represented as an extra “imported” entry. A
custom theme named Xbox is not mistaken for the Xbox preset: selection is
based on the complete palette, not just its name. As before, one custom
selection is saved; keep the JSON if you want to reimport it after selecting
a built-in. Format version 1, eight required RGB roles and size/contrast
validation are unchanged. All eight templates are in themes/.

## Installation

Version: 1.3.0-advanced5.2-themes, code 18.
Package: com.rfandango.haku_x.themes / hakuX Themes Test.
The saved private test signing key was recovered, so this APK uses the same
certificate as Advanced5.1 Themes Test and can update it without uninstalling.
Its existing theme, settings, console data and imported selection are retained.
It remains a separate app from the older Shaders Test and official hakuX.

This update changes theme UI/catalog/tests and version metadata only. All
packaged native libraries are reused from Advanced5.1 and checked byte-for-byte.
No private signing material is included in the source or patch outputs.

## Checks and handoff

The palette checks cover all eight presets' light/dark readability and export
round trips. Additional selector tests cover built-ins without duplicates,
custom selection inclusion, and imported names colliding with built-in names.
Build and validation results accompany the APK. No Android device is attached;
physical controller/touch navigation and visual behavior still need testing.

The general source patch targets Advanced5.1. A smaller UI-only delta for
rfandango changes the common theme files, tests, template files and format
notes, without changing package/version/signing or emulator code. It targets
the theme implementation supplied in the previous 1.3.0 handoff, including
its isolated themes-only variant; it is not a patch for bare, unmodified 1.3.0.
Use git apply --check before applying it to an independently edited tree.

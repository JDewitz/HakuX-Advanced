# Advanced5.5 — live theme scrolling

The existing bounded theme drop-down remains scrollable with touch and mouse
wheel. While scrolling, the app previews themes immediately. The preview row
is highlighted; a check still identifies the saved selection. The text field
and color swatches follow the preview. Keyboard/controller focus also previews
the focused entry. Pointer input returns control to scrolling after keyboard
navigation. Scroll progress spans the complete catalog, including both ends.
Opening the menu positions it near the saved selection without saving a change.

Tap/activate a theme to save it. Dismiss, Back or leaving Display settings
cancels the preview and restores the saved theme. Preview changes are kept in
memory only: no per-scroll disk writes or catalog edits. If the process exits
while previewing, the saved selection is still the one loaded next time.
Preview applies to the Compose settings/app UI; native in-game dialogs read
the saved theme when opened. Light/dark mode remains independent.

Persistent imported themes and multi-select import from Advanced5.4 remain.
The JSON format and existing nostalgia pack do not change.

## Handoff

Based on Advanced5.4 commit 461c620. Version 1.3.0-advanced5.5-themes,
versionCode 21, package com.rfandango.haku_x.themes / hakuX Themes Test.
Changes are confined to the theme preview/settings code and version metadata.
All native libraries are reused from the previous build. The private signing
key is not included in source or patches.

ThemePreviewScroll has three boundary/round-trip/coverage tests. Existing
catalog and palette tests remain. Compilation/unit tests do not establish UI
behavior on a device: test touch flings, mouse wheel, D-pad focus, first/last
entries, Back/dismiss, navigation away, import/name collisions and reopening.

For Java21/SDK36 frontend builds, reuse native libraries from the APK as
documented in THEMES5.4.md and run in android/:

    bash ./gradlew -PthemeTestApp=true testDebugUnitTest assembleDebug

Use the existing private Themes Test key separately to produce update APKs.

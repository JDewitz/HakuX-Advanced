> Current update: see [Advanced5.2](THEMES5.2.md) for the drop-down selector, expanded palette set and update-compatible test APK.

# Advanced5.1 — shared UI themes

Based on Advanced5 / official 1.3.0, commit 9e89648. UI changes only. Native
libraries are reused byte-for-byte from the previously delivered Advanced5
APK; shaders, fast forward, states, trainers and the emulator core are retained.

## Use

Settings → Display now contains Xbox, Satisfactory, Halo inspired, and
Frutiger Aero palette choices, with swatches. Light / dark mode is independent.
The selected palette applies across the library, settings, shader browser,
two-pane pause menu and in-game dialogs. Controller focus uses the same accent.
Import/export uses the Android system file picker. See themes/README.md and
its four JSON templates for the editable format and contrast requirements.

Android-owned file pickers and certain OS-native widget details keep system
styling. The game renderer, screenshot pixels and custom touch-control colors
are not changed. This release offers colors, not background images or layout
skins. Changes are loaded when an in-game dialog next opens; an already-open
pause window does not restyle itself mid-dialog.

## App identity and signing

The supplied test APK is `com.rfandango.haku_x.themes` / **hakuX Themes Test**.
It installs alongside the working Shaders Test and official apps. The old
workspace's signing key was lost during reset; this APK therefore uses a new
private development key and does not claim to update the previous install.
Add your game folder to this separate app; its settings and Xbox HDD are
separate. The existing app's data is untouched. Use the existing HDD backup
and restore controls if you want to copy console data into this test app.

Source builds default to the previous `.shaders` identity. Build with
`-PthemeTestApp=true` to use the separate themes identity and preserve the
already-stripped native libraries. Version name is 1.3.0-advanced5.1-themes,
version code 17. A build with the original signing key can update the original
test app; that key is not included in this source archive.

## Implementation and validation

ThemeSpec.kt defines eight semantic colors per light/dark palette, bounded
JSON validation and contrast checks. ThemeStore.kt atomically persists the
complete selected theme in the app's files directory, shared by both app
processes. Compose observes AppTheme.spec; framework dialogs read the same
file when opened. Invalid saved data falls back to Xbox. ThemeSettings.kt
handles preset selection and import/export. ThemeViews.kt styles framework
buttons, toggles, text, lists and focus without touching screenshot images.
PauseMenu.kt uses the same semantic roles instead of hard-coded colors.

Unit tests cover all built-in round trips/contrast, accent foregrounds,
custom colors/names, schema versions, missing/unknown fields, bad types,
alpha rejection, unreadable palettes, size limits and control characters.
The accompanying validation file records build/test results. No Android
device is attached; installation, touch/controller navigation and visual
appearance still require device testing.

The portable Git archive retains prior history on branch advanced-themes.
It contains no private signing key or native build toolchain. For a full source
build use android/README.md and the existing pinned native dependencies. For
UI-only work, copy native libraries from the Advanced5 APK into jniLibs and
build with the separate test property. Keep native libraries with matching
Advanced5 source if redistributing; hakuX retains its GPL license.

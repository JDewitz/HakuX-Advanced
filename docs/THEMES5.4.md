# Advanced5.4 — persistent themes and multi-select import

Built on Advanced5.3 (2560218). This update changes theme storage/UI and Android
version metadata. The native libraries are reused from Advanced5.3.

Settings → Display → Import themes JSON now opens a multi-select document
picker. Extract the nostalgia ZIP, select several JSONs (long-press/select all
as supported by your Android file provider), then confirm. Each valid custom
theme is copied into the app and stays in Color theme after switching themes,
choosing a built-in, restarting or deleting the source JSON.

A single imported JSON is selected immediately, as before. Importing several
fills the menu without changing the current theme. The summary reports added,
already-available and failed files; failures identify their selection number
and reason (up to five details). A malformed file does not block valid files.
Exact duplicates do not create more entries. Different themes sharing a name
remain distinct; their imported menu labels are numbered. Built-ins keep their
original names and colors. Imported content identical to a built-in reuses it.

The currently active custom theme from previous versions is automatically
preserved in the catalog on upgrade. Earlier imports that were already
replaced cannot be recovered automatically: import their JSONs once more.
The catalog is app data: uninstalling or clearing data deletes it. Keep the
original pack as a backup. Removal/renaming UI and ZIP import are not added.

## Implementation

ThemeCatalog.kt stores validated themes independently under files/ui-themes/
using SHA-256 identities derived from the normalized name and both palettes.
Names are never paths. A temporary file is flushed and renamed into place;
interrupted temporary files are ignored. An unreadable/invalid/oversize entry
does not discard other entries. Reimport repairs a corrupt matching entry.
Same-name collisions do not overwrite palettes. The selected complete theme
continues to use ThemeStore's existing AtomicFile for both app processes.

AppTheme exposes imported catalog state and preserves the old selection before
switching. ThemeSettings uses OpenMultipleDocuments and processes file reads,
validation and catalog writes on IO. Individual files retain the existing
32 KiB/schema/name/color/contrast validation. No persisted URI permissions are
needed because the validated theme is copied, not linked to its source URI.
The v1 theme JSON format is unchanged.

Seven catalog tests cover persistence across instances and built-in selection,
exact duplicate/built-in handling, colliding names and labels, interrupted or
corrupt/oversize files, repair by reimport, path-like names/legacy selection,
and invalid import rejection. Existing theme/schema/library tests also apply.
Device testing must check Android provider multi-selection, small-screen menu
scrolling, controller focus, migration and app restart. No Android device was
available for this build.

## Build and handoff

Version 1.3.0-advanced5.4-themes, versionCode 20.
Themes Test package remains com.rfandango.haku_x.themes.
Private signing material is excluded from source and patch artifacts.
For frontend builds copy the eleven native libraries from the supplied APK
into android/app/src/main/jniLibs/arm64-v8a/, configure Java21 and SDK36, then:

    cd android
    bash ./gradlew -PthemeTestApp=true testDebugUnitTest assembleDebug

Use the existing private Themes Test signing key separately to produce an
update-installable APK. See accompanying validation results for build checks.
The source patch targets Advanced5.3, not bare official 1.3.0.

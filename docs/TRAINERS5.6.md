# Advanced5.6 — folder-backed EvoX trainer browser

Based on Advanced5.5 plus the roadmap-only commits. Version
1.3.0-advanced5.6-trainers, Android versionCode 22; package
com.rfandango.haku_x.themes / hakuX Themes Test.

## Use

- Global browser: Settings → Trainers.
- A specific game: long-press its library card (controller Y/Menu) → Trainers.
  Mods & cheats → Trainer browser is another entry point.
- Choose folder, grant read access, and let the recursive .etm scan complete.
  Extract trainer ZIP/RAR collections first. Case-insensitive .etm filenames are
  supported; unrelated files are ignored. Import files also accepts multiple
  individual trainers and keeps their cached entries across folder rescans.
- Search by internal trainer name, note, source filename/path or hexadecimal
  title ID. Landscape/wide screens show list on left and options on right.
  Narrow screens switch between list and options with the top button.
- Opening from a game filters by its embedded title ID. The global browser
  shows all trainers; choose a matching library game/revision in its details.
- Select a row to browse. Press Use this trainer for this game to select it,
  then turn on individual options and Enabled on next fresh start. A new
  trainer starts disabled. Returning to a previously selected alternative
  restores its remembered options and enabled state. Only one ETM is selected
  per game revision. Browsing rows alone does not change that selection.
- Close the game completely and start it fresh for changes to apply. Loading
  a save state preserves the injected code captured in that state.

The name displayed first comes from ETM metadata, not filename guessing.
Source paths, embedded title IDs and a short binary hash distinguish variants.
A title ID match does not prove region or executable-revision compatibility.
Wrong-title binaries are blocked. Existing native ETM execution is unchanged.
The library wrench still represents configured next-launch state, not proof a
trainer ran successfully. Trainers may contain unconditional injected code.

## Folder refresh and storage

Scan on browser entry/resume, explicit Refresh, and debounced change events
from providers that support Android content notifications. Continuous background
watching and a full pre-launch rescan are not part of this build. Refresh is the
fallback when a provider does not send events. Recursive scans are bounded to
32 levels / 20000 document entries; an incomplete traversal preserves the
previous index. Individual invalid files are reported and skipped.

Identical binaries at different paths merge into one entry. Same-name different
binaries remain distinct. Trainers are parsed before being copied into a
content-addressed private cache; no source file is modified. An unavailable
folder leaves the cached catalog available with an explanatory message. A
successful scan drops removed folder-only entries from the current catalog;
active per-game copies remain stable. Individually imported entries persist.
Temporary/offline storage does not erase saved per-game trainer choices.

The existing selected trainer is retained on upgrade and appears in the game's
browser even if absent from the chosen folder. choices.json keeps the old active
file/enabled/options fields for the launch pipeline and adds a history object
keyed by trainer binary hash. Each game's revision has independent history.
Clearing the current selection disables it while retaining remembered options.
No simultaneous trainer stacking is introduced.

Checksums detect a damaged cached/selected binary. Scan/import operations are
serialized and selected binaries are copied into the existing per-game storage,
so rescanning/replacing source files cannot alter a running session's snapshot.
No native library, trainer runtime ABI or game image changes are included.

## Device test checklist

1. Update-install over Advanced5.5 without uninstalling; confirm the previously
   selected ETM and options remain. Confirm themes and other settings remain.
2. Open Trainers from a game and globally. Select the extracted folder, test
   nested folders, uppercase .ETM, search, duplicates and invalid files.
3. Select a matching trainer, select options, enable, close browser and reopen.
   Restart app and check again. Launch the game fresh and verify actual effects.
4. Choose trainer B, then A. Check separate remembered options; only the selected
   trainer should be configured for that game/revision. Test disabling/clearing.
5. Change files using another app, return and test automatic scan; use Refresh if
   the provider does not send notifications. Remove source media and confirm
   cached entries/choices survive. Add media back and rescan.
6. Test landscape two-pane focus with D-pad and touch; portrait list/details;
   long option names, scrolling and navigation Back. No Android hardware was
   attached, so these UI/runtime checks remain pending.

## Developer handoff

TrainerCatalog.kt handles bounded reads, parsing, content identity, stable
cache/index and duplicate sources. TrainerLibrary.kt handles the selected SAF
tree and recursive scans. TrainerStore.kt retains its native-facing API while
adding remembered alternatives and checksum checks. TrainerBrowser.kt provides
the two-pane/adaptive UI, notifications, filtering and selection. Existing
settings, game context menu and navigation routes lead to that browser.

Ten storage tests cover trainer switching/history, revision isolation, wrong
IDs, old-config migration, clearing, duplicate files, same-name alternatives,
cache corruption, independent launch copies and bounds/invalid binary rejection.
They use synthetic classic-ETM fixtures, not real injected-code execution.
Existing schema, UI-data and trainer parser tests continue to apply.

Java21/SDK36 frontend build, with native libraries copied from the previous APK:

    cd android
    bash ./gradlew -PthemeTestApp=true testDebugUnitTest assembleDebug

The private signing key is excluded from public source and patch archives.
Use the saved Themes Test key separately for update-installable builds.

Patch packs are now the second roadmap priority. Their resolver can reuse this
catalog's title-ID display/matching approach, adding executable/update hashes
and stronger revision matching. Neither compatibility patch loading nor new
Halo/Snowblind fixes are implemented by this trainer-browser update.

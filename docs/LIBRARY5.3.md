# Advanced5.3 library use and handoff

Version 1.3.0-advanced5.3-library, Android versionCode 19.
Package com.rfandango.haku_x.themes, label hakuX Themes Test.
Signed with the existing Themes Test certificate; install over Advanced5.2
without uninstalling. Keep the old APK and back up important console data
before device testing. This is a test build; physical-device QA is pending.

Open the menu icon between Refresh and Settings to choose Cards, List, Stats,
Info / Play cards or Icons. Selection persists. Search applies in all views.
Tap a game to launch; long press or controller Y/Menu opens existing actions.
Info / Play cards also has explicit Play and Options buttons.

Wrench = ETM enabled for the next fresh launch. Gear = saved per-game
configuration/shader override or enabled built-in mods/cheats. See ROADMAP.md
for exact scope. Loading a previous state does not prove a trainer was applied.
Stats shows known image storage and configuration, not playtime or progress.

## Test checklist

- Switch through all five modes, restart, and confirm the selected mode stays.
- Search in each mode; Stats totals should follow visible results.
- Test portrait/landscape and long names on a small handheld screen.
- Test tap, long press, D-pad/A, Y/Menu, and the explicit Options button.
  Options must not launch a game. Existing state/settings/mods actions must work.
- Import and enable a valid ETM: wrench appears on return. Disable/remove it:
  wrench disappears. Reopen the app and check again. Check disabled and invalid imports.
- Add/reset per-game settings, advanced profile and shader overrides; check the
  gear. Global changes alone must not add gears to default games. An explicit
  per-game Off is still a custom override. Test a game with both badges.
- Check all eight themes in light/dark mode, icon visibility and focus outlines.
- Confirm existing library deduplication, dashboard and XISO conversion behavior.

## Developer handoff

Branch advanced-library-views; parent Advanced5.2 commit
0ac9ff4496d12f21b04a7453085517d96d1db89f. The accompanying source tar.gz contains
a portable local Git checkout and history. No push or external contact was made.
The patch is against Advanced5.2, not unmodified official 1.3.0. Review/apply
with git apply --check before using it in a separately edited checkout.

Files: LibraryPresentation.kt implements mode IDs and the off-main-thread
configuration index; LibraryLayouts.kt renders the selector/badges/layouts;
LibraryScreen.kt composes the selected view; LibraryViewModel.kt refreshes the
index on scan/resume. LibraryPresentationTest.kt checks data contracts.
The Stats view is metadata-only. No telemetry store or native input device was
added. Native libraries match Advanced5.2 byte-for-byte.

Follow docs/FORK.md and the repository build notes for native prerequisites.
For a frontend-only rebuild, reuse the eleven native libraries from this APK:
extract lib/arm64-v8a/*.so into android/app/src/main/jniLibs/arm64-v8a/.
With Java21/SDK36 configured, in android/ run:

    bash ./gradlew -PthemeTestApp=true testDebugUnitTest assembleDebug

The private Themes Test keystore is intentionally excluded. An ordinary local
debug key makes a separately signed build and cannot update the signed test
APK unless the developer supplies the existing key outside the source tree.
No key material should be committed.

Current tests/build/signature/native comparison results are in the accompanying
VALIDATION-library5.3.txt. ROADMAP.md records follow-up priorities and research.

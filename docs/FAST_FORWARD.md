# Mappable fast forward for hakuX 1.2.0

This change is based on the official `v1.2.0` tag of
<https://github.com/rfandango/hakuX>, commit
`e11ee60b90742a368a08f7f5339af10048a63a04`.

It carries forward the previous experiment's uncapped-vblank fast forward,
with a configurable key. The dashboard/kernel experiment is excluded:
`native/kernel/` is identical to official 1.2.0. This does not add dashboard,
BIOS or IPL compatibility.

## Using it

1. Install the supplied arm64 APK. The app is **hakuX Fast Forward**, package
   `com.rfandango.haku_x.fastforward`. It installs alongside official hakuX
   and has separate settings and HDD data. For existing console contents,
   use **Xbox hard disk → Back up everything** in your original app, then
   **Restore a backup** in this app. Restore replaces the destination app's HDD.
   Add your ISO folder in this app as well.
2. Open **Controllers → Fast-forward key**. Press and release a keyboard key
   or controller button. The setting is global and applies to that Android
   keycode on all devices; it is not tied to a player or controller profile.
3. **Clear** disables the hotkey; **Restore F9** restores its default. Changes
   save immediately. If an existing controllers file has no new setting,
   F9 is the default. A cleared setting remains cleared after restarting.
4. During play, press the assigned key once to enable fast forward, again
   to disable it. A toast reports the mode. The in-game menu also offers
   **Enable fast forward / Disable fast forward**.
5. The assigned key is reserved for the emulator: neither its press nor its
   release is sent to Xbox input or the normal Back/Guide menu handling.
   Pick a spare button if you still need the existing Xbox mapping.

Only discrete keys/buttons are captured. Axis movement, chords, per-device
hotkeys, hold-to-fast-forward and a speed multiplier are outside this change.
Android may reserve Home, Power, volume keys or vendor buttons before the app
receives them. F9 is the default, not a second permanent shortcut after remapping.
The media-fast-forward key works if you assign it; it is not hardcoded.

## Exact runtime behavior

The native setter `nc_dev_nv2a_set_fast_forward(bool)` atomically stores a
runtime override. `uncapped()` in `native/dev/nv2a.c` returns true while this
override is enabled. Disabling it resumes the existing preference/property
logic (`HAKUX_HLE_UNCAP`, `debug.hakux.uncap`) without rewriting settings.

This is the same uncapped-vblank mechanism as the earlier test. It removes
normal vblank pacing through the core's existing uncapped path. It does not
multiply the emulated clock and does not promise 2× or any other fixed speed.
The result depends on frame waiting, the workload and host performance. A title
that is already uncapped, CPU/GPU limited, or governed by another timing source
may show little or no speedup. Existing uncapped-mode audio/timing behavior
also applies. Pause behavior is unchanged.

The mode is transient, starts off in a new game process, and is not a saved
preference or savestate field. The binding persists; the active mode does not.

## Implementation map

| File | Change |
| --- | --- |
| `native/dev/dev.h`, `native/dev/nv2a.c` | Atomic override and restoration of existing pacing |
| `native/app/app_main.c` | `NativeCoreActivity.nativeSetFastForward` JNI bridge |
| `NativeCoreActivity.kt` | Toggle, toast, menu item, hotkey before Xbox routing; binding reload on resume |
| `input/FastForwardHotkey.kt` | Consumes configured key; one toggle per fresh down, independent device press state |
| `data/Controllers.kt` | Optional global keycode in the existing atomic-replacement JSON file |
| `ui/controllers/ControllersScreen.kt` | Key capture, clear and restore-F9 UI using the existing activity input window |
| `ui/controllers/ControllerMappingScreen.kt` | Release only its own capture listener so navigation cannot clear a newer hotkey listener |
| `res/values/strings.xml` | User-facing labels and instructions |
| `android/app/build.gradle.kts` | Separate debug identity/version; JVM JSON test dependency |
| `tools/native/app-libs.sh` | Select native strip tool for Linux/macOS/Windows instead of assuming Windows |
| `tests/native/fast_forward/`, Android test sources | Native pacing and key/configuration regression coverage |

Paths to Kotlin files above are relative to
`android/app/src/main/java/com/rfandango/haku_x/`.

The saved field is top-level `fastForwardKey` in `files/controllers.json`:
missing means F9, a positive integer means that Android keycode, JSON null means
unbound. Existing slot/profile/name fields are retained. Official 1.2.0 ignores
the extra field; if that version rewrites the file it may discard the binding.
The library and game processes read the file independently as before.

Fresh keydown toggles once. Repeats, duplicate downs, canceled events and
key releases do not toggle. The key is still consumed while the control editor
suppresses toggling. Focus loss/pause clears pressed bookkeeping to avoid a
lost release leaving the hotkey stuck. Other keycodes follow the original path.
The capture UI consumes both down and up and commits on release so the capture
press does not activate the settings screen beneath it.

## Rebuilding and reviewing

The source bundle includes patched hakuX, the required FEX sources, native
dependencies, existing licenses, a unified patch and `build-fastforward.sh`.
The adrenotools KGSL patch is already applied to the included dependencies.

Install JDK 21, Android SDK platform 36, Build Tools 36.1.0, NDK 29.0.14206865,
CMake (3.20+), Ninja, and a C compiler for the host test. Set `JAVA_HOME`,
`ANDROID_HOME`, and optionally `ANDROID_NDK`. Run `bash build-fastforward.sh`
from the source bundle root. Standard SDK/JDK/Gradle/Maven build tools and
artifacts are not bundled. See `hakux/android/README.md` for upstream details.
The supplied rebuild script is tested on Linux x86_64.
The script passes the JDK path explicitly because upstream's gradle.properties
contains a machine-specific Java path. Network access is needed for Gradle.

To review/apply only the patch, clone the official repository at the exact
commit above and run `git apply fastforward-1.2.0.patch`. Initialize its FEX
submodule and required nested submodules, and supply native dependencies as
listed in `android/README.md`. The script and complete dependency copies are
provided by the full source bundle. `git diff -- native/kernel` must be empty.
The patch contains the feature, tests, documentation and explicitly identified
debug/build packaging changes. No dashboard kernel patches are included.

Native build: Release arm64-v8a, Android native API 28. APK: debug-signed,
min Android 9/API 28, target API 36. The debug identity is for side-by-side tests;
it cannot upgrade the official signed app. To upstream, maintainers can retain
the feature changes and keep their own version, package and signing configuration.

## Validation and device test checklist

Automated checks: `bash tests/native/fast_forward/run.sh` checks the actual
native override with a capped base and an uncapped base. Android
`testDebugUnitTest` covers hotkey repeats/duplicates, consumption, remapping,
clearing, canceled/suppressed presses, independent devices, focus reset, legacy
JSON defaults, and custom/cleared configuration round trips. The existing
Android unit tests also run. Build results accompany the deliverables.

The Release arm64 native build passed, all 18 Android JVM unit tests passed,
and both native pacing scenarios passed. Patch application was checked against
a pristine copy of the official base.

No Android device was attached for this build. The following remains manual:

- Launch a previously working title at normal settings; record normal FPS/speed.
- Toggle F9 twice; check toast, visible pacing and restoration of normal behavior.
- Remap to a spare controller button, restart the app, and check persistence.
  Confirm old F9 no longer toggles and the new button does not press an Xbox input.
- Hold the button, release it, then press again: exactly one toggle per press.
- Clear the binding and restart; confirm the menu still works but the key does not.
- Restore F9, test pause/resume and focus loss while holding a key, and reconnect
  the controller. Test normal uncapped mode as well as capped mode.
- Test the key-capture screen with touch and controller/keyboard input. Confirm
  Clear, Cancel and Restore F9 work on your device's screen size. Navigate into
  a controller mapping and back, then confirm hotkey capture still works.
- If anything fails, report device/controller, assigned keycode, title, normal
  uncapped setting, expected/observed behavior, and **Settings → Export logs**.
  The native log includes `fast-forward on/off (uncapped vblank)`.

## Suggested upstream description

Adds a mappable global fast-forward toggle to hakuX 1.2.0. The hotkey is
configured under Controllers, defaults to F9, supports clearing/restoring, and
is consumed before Xbox input routing. The in-game menu provides the same
control. Native implementation temporarily enables the existing uncapped
vblank path and restores normal pacing preferences when disabled. No guest
clock multiplier or kernel compatibility changes are included.

hakuX remains GPL-3.0-or-later; dependency licenses remain in their source trees.

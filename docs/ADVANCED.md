# hakuX Advanced 1 — feature map and maintainer handoff

Version: **1.2.0-advanced1**, versionCode **9**. Based on official hakuX
`v1.2.0`, commit `e11ee60b90742a368a08f7f5339af10048a63a04`, plus the mappable
fast-forward change and this Advanced menu. No dashboard/kernel experiment
is included. The kernel sources still match official 1.2.0.

## Install and enter the menus

The APK updates our previous **hakuX Fast Forward** installation. It uses the
same `com.rfandango.haku_x.fastforward` application ID and development signing
key; the new displayed name is **hakuX Advanced**. Install over the existing
build to keep its console data and settings. The separately signed official
app remains separate. No uninstall or HDD restore is needed for this update.

- **Settings → Advanced & experimental**: global startup profile, all emulator
  hotkeys, hold/toggle mode, quick-save slot and export of recorded files.
- **A game's settings → Advanced profile for this game**: its startup overrides.
- **In-game menu → Advanced & experimental**: live presentation filters,
  current-game startup profile, driver overrides, benchmarks, screenshots,
  memory tools and renderer dumps.

CPU/RAM/driver changes say **restart required**. They are saved for the next
fresh game launch and do not resize RAM or reconfigure the JIT mid-session.
Live presentation changes are temporary overrides; restore launch settings
from that menu. The core polls presentation overrides every 64 refreshes, so
a visible change can take about a second at 60 Hz.

## Feature map

| Area | Implemented in this build | Defaults / limits |
| --- | --- | --- |
| Fast forward | Existing uncapped engine, mapped key, toggle or hold mode | F9; toggle mode. Hold restores the prior mode on release/focus loss. No fixed speed multiplier. |
| Emulator hotkeys | Quick save, quick load, pause/resume, screenshot, benchmark | Extra actions start unbound; globally mapped Android keycodes, not axes/chords. |
| Startup profiles | Global profile and per-game profile snapshots | Retail 64 MB, reduced x87, precision control on, Arm AFP optimization on. |
| Xbox memory | 64/128 MB selector | Development-kit RAM is experimental, not a universal performance improvement. |
| CPU precision | Full 80-bit x87; reduced-x87 precision control; Arm AFP optimization | FEX still detects supported host CPU features. Full x87 supersedes reduced precision control. |
| GPU workarounds | All 12 existing named quirks: Auto / Force on / Force off | Auto is the driver's normal behavior. Apply before GPU creation at next start. |
| Live filters | FXAA on/off; SGSR/bilinear; averaged downsampling on/off; reset | Uses the existing presentation controls; no restart required. |
| Benchmarks | 10-second CSV sampling, native profiling request, metadata and mean sampled FPS | Warmup before sampling; menus/pause end an active run as partial. Not a per-frame trace. |
| Screenshots | PNG copied from the game surface | Excludes Android dialogs/control overlays; availability depends on PixelCopy success. |
| Memory tools | RAM snapshot, frame-counter comparison, u16/u32/f32 search, peek, one-time poke, matrix search, selectable result window | Commands execute on the guest thread after resuming; recent results bounded to 32 KiB; native search reports up to 300 matches. |
| Renderer inspection | Next-frame render-target dump; texture-ID dump | Raw core output, not a replacement-pack editor. A texture must upload again to be dumped. |
| Export | Advanced output ZIP from the running game or launcher settings | PNGs, benchmark CSVs and renderer dump files. In-game export also includes recent memory results. No HDD/shader cache. 512 MiB limit. |

The memory UI is a first usable search/peek/poke tool. It does **not** yet
provide iterative changed/unchanged value filtering, a persistent cheat list,
freeze-address controls, or a graphical texture-replacement editor. Rewind,
frame advance, slow motion and fixed 2×/3× timing are not implemented or shown
as working toggles. Existing shipped Mods & cheats remain available unchanged.

## Profiles, mappings and reset behavior

Games without their own Advanced profile follow the global profile. Editing
one creates an explicit snapshot of the effective global profile. Later
global edits do not alter that snapshot. **Use global startup profile** removes
the game's override. **Reset startup profile** restores global defaults while
keeping hotkeys, quick slot and hold/toggle preference.

Fast-forward retains the prior `controllers.json` key for compatibility.
Other hotkeys and startup profiles are in `files/advanced.json`:

- `global`: default startup profile.
- `games`: full profiles keyed by title ID and disc version, or the existing
  path-based game key when no title ID is available.
- `keys`: emulator action name → Android keycode.
- `holdFastForward`: defaults false.
- `quickSlot`: 1–10, defaults 1.

The launcher and game are separate processes. The Advanced file's writes reload JSON under an OS
file lock, synchronize same-process writers, write and sync a temporary file,
then rename it into place. Reads reopen the atomic file; no shared-preferences
cache is used. Key mappings reload when the game activity resumes.

Capture waits for both press and release. Assigned keys are consumed before
Xbox input, Back or Guide handling. Repeat/duplicate/canceled downs do not
activate actions. Duplicate emulator bindings are rejected in both Advanced
and the legacy Controllers fast-forward setting. Android-reserved system keys
may not reach the app; choose a spare controller button or keyboard key.

Quick save overwrites the selected slot, as stated in its setting. Quick load
checks availability and an existing slot before calling the existing loader.
Savestates still require the supported push-buffer renderer. Changes to RAM
size/CPU modes are experiments; use a fresh launch when comparing profiles.
Manual pause remains in effect when a dialog is dismissed: press its mapped
key again or choose **Resume manually paused game** from the in-game menu.

## Benchmarks and recorded files

Use a repeatable scene. Resume after selecting **Record 10-second benchmark**;
recording starts after 750 ms of warmup and samples native statistics roughly
every 100 ms. Opening a menu or pausing the activity stops an active recording,
with the reason and elapsed duration written into the CSV. An armed recording
waits for gameplay; leaving the activity cancels it. Check the final comment
for **complete** before comparing full runs.

CSV fields: elapsed milliseconds, the core's reported FPS, average and last
frame milliseconds, `gpu_busy_ms`, and the current fast-forward state. The
summary is the arithmetic mean of sampled FPS, **not** a per-frame percentile
or a count of individual rendered frames. `gpu_busy_ms` is the existing
continuous GPU-backlog age used by OverlayPacer on raised-priority GPU queues;
it is not GPU frame time, and returns zero on other queue configurations.
Native profiling supplies additional timings/hot guest addresses in the logs.
Cancellation requests are processed when the native renderer next polls.

Metadata includes the build, device model, game key, actual launch arguments
for graphics/startup experiments, temporary filter override, and initial
fast-forward flag. Dumps or other background work can distort a benchmark;
record clean comparison runs. Native profiling itself has overhead.

Outputs go to this app's external-files `advanced/` folder (internal-files
fallback) and the core's `files/native/hle/hle_shadow/` dump folder. Screenshots
and CSVs are committed by temporary-file rename; exports skip temporary files.
Use **Export Advanced files as ZIP** during play or **Export recorded Advanced
files** in launcher Advanced settings. Recent scanner results are in memory
while the game process lives; scanner output and native profiling also persist
through **Settings → Export logs**. Wait for a renderer dump to finish before
exporting; the original dump writer is asynchronous.

The memory menu queues work and resumes gameplay. Reopen **Memory results**
after a short run to inspect it. Native FIFO capacity is 16 commands; a full
queue reports rejection. Integer searches accept decimal or `0x` hexadecimal
and normalize to decimal. Float searches reject NaN/infinity. Peek/poke
addresses and poke values are hexadecimal. Peek displays 64 bytes aligned down
to a 4-byte boundary. Poke validates address/width/value, then asks to review
the exact write. The core validates every destination byte before writing;
an unmapped page boundary causes no partial write. Successful writes use the
existing dirty tracking. Poke changes RAM once; it is not a freeze/cheat entry.

## Native implementation

`native/core/advanced.c` provides an allowlisted, mutex-protected registry,
a separate bounded memory-command FIFO and a bounded scanner-result buffer.
It does not write Android system properties or need ADB/root. The shared
`hle_prop_get` consults local values before existing debug-property behavior.
Explicit empty values mask an external property. A local GUI command can
trigger on its first poll; initial external properties retain baseline semantics.

JNI methods:

- `nativeSetAdvanced(name, value): Boolean`: rejects unknown names or values
  too long for the property buffer; memory commands also reject a full FIFO.
- `nativeAdvancedReport(): String`: takes a synchronized copy of recent scanner
  output. The logging collector selects only `hakuX-native-scan`.

RAM/CPU options are validated `advanced=...` launch arguments. RAM uses the
existing `NC_RAM_MB` path. CPU switches use the existing FEX environment and
local property controls. The complete quirk specification is passed through
the environment **before device creation**, avoiding Android's short property
limit and ensuring initialization-only workarounds actually apply. Its local
debug-property value is masked so stale device properties cannot override the
selected startup profile. Other debug controls remain as before.

Live filters use `debug.hakux.present`; performance capture uses the existing
profiler plus a cancellation command; dumps use existing renderer controls.
Memory operations are drained on the guest thread by the existing scanner,
not by JNI on the UI thread. The UI exposes the real core behavior rather
than introducing an unrelated guest clock or BIOS execution path.

## Source map and build

New Kotlin files under `advanced/`: `AdvancedStore`, `AdvancedProfile` (in the
same file), `EmulatorHotkeys`, `AdvancedScreen`, `AdvancedMenu`,
`AdvancedArtifacts`, and `MemoryCommand`. `NativeCoreActivity` integrates
runtime tools, actions, startup arguments and explicit pause. Navigation and
global/per-game settings link the new screen. The Controllers screen also
rejects conflicting emulator key assignments.

Native changes: `core/advanced.{c,h}`, logging collection, the shared property
helper, memory-scanner queue/boundary validation, profiler cancellation, JNI
and startup option wiring. No `native/kernel/` changes.

The full source bundle includes required FEX and native dependency sources,
existing licenses, the cumulative patch against official 1.2.0 and an
incremental patch against the previous fast-forward snapshot. The latter's
base is `b0adf7e` in the working history; the full base hash is listed in the
build validation file. For upstream review, use the cumulative patch against
`e11ee60b90742a368a08f7f5339af10048a63a04`.

Use JDK 21, Android SDK 36, Build Tools 36.1.0, NDK 29.0.14206865, CMake 3.20+,
Ninja and a host C compiler. Set `JAVA_HOME`, `ANDROID_HOME` and optionally
`ANDROID_NDK`, then run `bash build-advanced.sh` from the bundle root. This
build script is tested on Linux x86_64. Standard SDK/JDK/Gradle/Maven artifacts
are not bundled. The adrenotools patch is already applied in included sources.
Native build is Release arm64-v8a/API 28; APK is debug-signed, target API 36.
The package/version/debug-name choices can be omitted when upstreaming.

Tests:

- `bash tests/native/advanced/run.sh`: property allowlist, first GUI command,
  concurrent reads, queue ordering/backpressure, bounded results, actual poke
  boundary/endianness/dirty tracking, and original fast-forward restoration.
- `./gradlew testDebugUnitTest`: hotkey actions, repeats/cancellation, multi-device
  hold/release/focus reset, memory input validation, full quirk serialization,
  profile inheritance/reset/persistence and concurrent store writes, plus the
  original tests.

The final validation file records build/test results. No Android device is
attached here; UI behavior, actual game compatibility and speed changes need
on-device testing. Start with defaults and test one experiment at a time.

## Device test sequence

1. Install as an update; check the existing HDD, controls and F9 mapping remain.
2. Open global Advanced; bind spare keys for pause, screenshot and quick save/load.
   Test held keys/repeats, then enable hold-to-fast-forward and test focus loss.
3. Test quick save/load in a spare slot and an empty-slot load. Confirm manual
   pause survives menu dismissal and can be resumed from the menu.
4. Run a complete benchmark, export it, then run one interrupted by a menu.
   Check the complete/partial comments, screenshots and exported ZIP.
5. Test FXAA/SGSR/bilinear/reset in game. Confirm temporary filters reset on a
   fresh game process and launch graphics settings remain intact.
6. Create a per-game RAM/CPU profile, restart and inspect the log/settings;
   verify another game still follows global defaults. Restore the game to global.
   Compare profiles with fresh game launches rather than loading unlike states.
7. Test individual GPU overrides, restart, and return each to Auto afterward.
8. Snapshot RAM, resume, compare, and search a known changing value. Test peek;
   review and apply a one-time poke only to an address you intend to change.
   Reopen results after resuming. Check Export logs for the full scanner output.
9. Request a renderer dump, allow it to finish, export from the game and again
   from launcher settings. Texture capture needs a valid ID and another upload.
10. Report the build, game/disc version, changed settings, observed result, logs
    and benchmark CSV where useful. Do not interpret one game's success as
    validation of a global compatibility override.

hakuX remains GPL-3.0-or-later; third-party components retain their licenses.

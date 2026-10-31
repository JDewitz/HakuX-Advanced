# hakuX Advanced 2 — frame-skip fast forward and two-pane savestates

**Version:** 1.2.0-advanced2, versionCode 10. **Package:**
`com.rfandango.haku_x.fastforward`, displayed as hakuX Advanced.
Install over Advanced1 or the earlier Fast Forward build using the matching
development signing key. Existing console data, key mappings and savestates
remain in the same locations. This is still based on official hakuX 1.2.0;
the dashboard/kernel experiment is excluded.

## Using this version

Press Android Back or tap the Xbox jewel to open the new pause window:

- **Left:** Resume, Fast forward, Allow FF frame skipping, Advanced tools,
  touch-control editor/visibility, controller assignment, cheats where present,
  and exit. The left panel scrolls on shorter screens.
- **Right:** all ten savestate slots, each with a screenshot (when available),
  date/time in the device's locale, and Save/Overwrite, Load and Delete buttons.
  The browser scrolls independently. Delete requires confirmation; Overwrite
  explicitly replaces that slot. Load is disabled for empty slots. Native
  compatibility checks still apply when loading an occupied slot.

The game remains paused under the browser and its nested dialogs. Saving,
loading or deleting refreshes the displayed metadata where appropriate.
Resume clears an explicit manual pause. Back closes the pause window using
the existing dialog dismissal behavior; a prior manual pause remains until
explicitly resumed. Touch and standard Android focus/D-pad navigation are used.
The layout remains left/right in portrait as well as landscape; landscape is
more spacious. Closing the browser stops pending thumbnail work and releases
its UI references.

## Frame skipping during fast forward

**Default: off.** Enable **Allow FF frame skipping** in the pause window, or
**Settings → Advanced & experimental → Allow frame skipping in fast forward**.
Then activate Fast forward using its switch or mapped key. Hold/toggle mode
continues to work as before. The preference is global, persists across launches,
and changes immediately from the pause window. Setting changes from launcher
Advanced settings are applied when the game activity resumes.

This is an experimental **scanout/presentation skip**, not a codec overclock
or full draw-skipping renderer:

1. Both the temporary Fast forward mode and the permission must be on.
2. The render thread keeps roughly one display copy per 33.33 ms (about 30 FPS)
   and omits intervening scanout copies. An explicit renderer capture/dump can
   bypass the gate.
3. If all display-image slots are occupied, it drops that presentation request
   immediately instead of waiting for a slot. Held or in-flight GPU images are
   never reclaimed for this purpose.
4. All guest draw commands, render targets, video decoding, GPU submissions,
   timeline values, fences and guest flip bookkeeping remain intact.
5. Disabling Fast forward or its frame-skip permission restores the original
   display path. Ordinary uncapped mode by itself does not activate this option.

The existing uncapped-vblank mechanism remains unchanged. The new path does
not change CPU frequency, guest timers, audio clocks, video timestamps or movie
playback rate. It can reduce scanout-copy/overlay-presentation work and display
queue stalls. It cannot promise faster playback if decoding, guest scheduling,
audio synchronization or another clock is the limiting factor. Do not interpret
a roughly 30-FPS displayed preview as proof that guest execution is slower.

The implementation deliberately retains rendering dependencies: blindly
skipping complete frames or draw calls could discard textures/render targets
that a later frame or the guest reads. This first implementation avoids that
kind of corruption while allowing excess presentation work to be dropped.

## How to test the movie slowdown

Choose the same cutscene and compare:

1. Normal pacing, Fast forward off.
2. Fast forward on, frame-skip permission off.
3. Fast forward on, frame-skip permission on.

Compare wall-clock time to reach a known point in the movie, visible smoothness,
and audio synchronization, as well as the FPS overlay. The benchmark CSV now
has `ff_scanout_skips`, a cumulative count since the game process started;
subtract the initial count from the final one to count skips during a run.
It also records the permission at recording start. Turning Fast forward off
logs the cumulative count. Use Export logs and the Advanced benchmark/export
controls from the existing build for reports. Report the game/disc version and
which movie was tested; different video paths can have different bottlenecks.

## Savestate data and thumbnail handling

The `.hxs` file format, ten per-game slots and existing save/load API remain
unchanged. Dates shown are the state file's modification time, as in the prior
slot browser; the backup/restore path preserves those timestamps. Orphan images
without a state are not displayed. Missing images use a placeholder.

New thumbnails preserve aspect ratio and fit within 480×270 pixels. They are
written to a temporary JPEG and renamed after completion. If a successful state
save cannot capture/write its image, the old thumbnail is removed rather than
being displayed as if it belonged to the new state. The state itself remains
saved. Browser thumbnails decode on a worker with bounded sampling, and stale
results are discarded after refresh/close. Existing larger JPEGs still work.

The Save/Overwrite action uses the existing native saver and its pause/parking
mechanism; progress blocks conflicting actions. The browser refreshes only
after the save/thumbnail operation finishes. Loading now also uses a blocking
progress dialog while the native compatibility check runs, avoiding concurrent
slot edits. Deletion retains the prior confirmation and cache cleanup behavior.
Unsupported renderers display an explanation and disable save/load; existing
slots can still be inspected or deleted. Quick-save/load mappings and the
configured quick slot remain available outside the pause window.

## Implementation map

| File | Change |
| --- | --- |
| `native/core/fast_forward.{c,h}` | Atomic mode/permission flags, generation-aware 30-Hz scanout gate and skip counter |
| `native/dev/nv2a.c` | Connect existing Fast forward activation to the gate; log the skip count on deactivation |
| `hw/xbox/hle/vk/back.c` | Omit excess scanout copies while retaining normal submit/fence processing; bypass for renderer captures |
| `hw/xbox/hle/vk/display.{c,h}` | Optional nonblocking display-slot allocation; drop without reusing held images |
| `native/app/app_main.c` | JNI setters and skip-counter getter |
| `advanced/AdvancedStore.kt`, `AdvancedScreen.kt` | Persist and edit the default-off `allowFrameSkip` field |
| `advanced/AdvancedMenu.kt` | Benchmark metadata and cumulative skip column |
| `PauseMenu.kt` | Two-pane full-screen pause dialog, switches, slot cards and asynchronous thumbnails |
| `SaveSlotInfo.kt` | State-owned metadata, orphan-image handling and thumbnail sampling helper |
| `SaveStates.kt` | Slot metadata/delete access, save completion callback, compact atomic thumbnails and guarded loading |
| `NativeCoreActivity.kt` | Open the browser on Back/jewel, preserve menu pause bookkeeping, wire switches and Resume |

Kotlin paths are relative to
`android/app/src/main/java/com/rfandango/haku_x/` unless otherwise noted.
`native/kernel/` remains identical to official 1.2.0. No BIOS or dashboard
compatibility changes are included.

The gate's mode flags are atomic; changes have generation numbers so an off/on
transition between render calls still permits a fresh frame. Its timestamp
state belongs exclusively to the render thread. There is no added host sleep.
The frame-skip permission and active mode must both be true. A no-slot drop
and a rate-gated omission both increment the diagnostic counter. Host tests
exercise the actual gate, including inactive/default behavior, transitions,
capture bypass and counters. GPU integration still requires device testing.

## Build, patches and handoff

The source bundle includes the full modified source, FEX and required native
dependencies with existing licenses, a cumulative patch against official
`e11ee60b90742a368a08f7f5339af10048a63a04`, and an incremental patch against the
Advanced1 snapshot. Exact hashes and validation results are in the validation
file. `docs/ADVANCED.md` describes the other Advanced features; this document
supersedes its pause-menu, benchmark-column and build instructions.

Set `JAVA_HOME` to JDK 21 and `ANDROID_HOME` to Android SDK 36 with Build Tools
36.1.0 and NDK 29.0.14206865. With CMake, Ninja and a host C compiler installed,
run `bash build-advanced2.sh` from the bundle root. The script uses non-incremental
Kotlin compilation to avoid stale incremental symbols when applying successive
patches. Native build: Release arm64-v8a/API 28. APK: development/debug signed,
minimum API 28 and target API 36. Standard SDK/JDK/Gradle/Maven tools are not
bundled. The adrenotools patch is already applied in included dependencies.

Checks:

- `bash tests/native/frame_skip/run.sh`: gate policy plus all prior native
  property/queue/memory-write and fast-forward restoration checks.
- `testDebugUnitTest`: the previous 25 tests plus state/thumbnail metadata and
  landscape/portrait sampling tests; persistence coverage includes the new flag.
- Native Android build, APK assembly/signature/certificate continuity, patch
  applicability and exact packaged native-library comparison.

No Android device is attached here. Manual tests remain necessary for the
browser's layout/controller behavior, actual save/load screenshots, held-image
GPU paths and whether frame skipping improves a particular movie.

## Device checklist

1. Install over Advanced1; verify its console data, mappings and old states.
2. Press Back: left controls and right screenshot/date browser should appear.
   Test touch, D-pad navigation, scrolling, Back and Resume in both orientations.
3. Save an empty slot, overwrite it after changing the scene, and check the new
   date and image without closing the browser. Load it and confirm the scene.
4. Delete a slot: cancel once, then confirm; only that slot should disappear.
5. Test an empty slot, a missing/corrupt preview and incompatible state. Errors
   should leave the browser usable. Test quick-save/load mappings as well.
6. Toggle touch controls repeatedly without leaving the browser, open/close
   Advanced and controller submenus, and enter the touch-control editor.
7. Compare the same movie using the three modes above; test toggling permission
   and Fast forward independently. Verify turning either off stops new skips.
8. Test hold-to-fast-forward release and focus loss, then request a renderer dump
   with skipping enabled. Export logs/CSV and report visible/audio results.

hakuX remains GPL-3.0-or-later; third-party sources retain their licenses.

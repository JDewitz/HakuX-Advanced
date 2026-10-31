# Advanced3: remove FF frame skipping

Version 1.2.0-advanced3, versionCode 11. Same application ID and signing identity
as the supplied Advanced2 APK. Back up saves before testing an experimental build.

Removed the frame-skip switches from Advanced settings and the pause sidebar,
the store accessor, JNI methods, native skip policy, and scanout-copy suppression.
Old `allowFrameSkip` JSON values are ignored; no save/config deletion is required.
Benchmark exports no longer contain the removed skip setting/counter. Normal
uncapped fast forward, mapped keys/hold mode and the two-pane savestate browser
remain. The Xbox kernel and save format are unchanged.

## RetroArch shaders: assessed, not implemented in this release

The appropriate target for our Vulkan renderer is RetroArch Slang (`.slangp`
presets plus `.slang` sources). Current hakuX has built-in presentation filters,
not a RetroArch preset loader. Dropping a preset into app storage will not load it.

A practical implementation would integrate librashader's Vulkan runtime through
its C API after game scanout and before final presentation in
`hw/xbox/hle/vk/swapchain.c` (`hle_present_end`). First verify a pinned version
cross-compiles for Android arm64 and that its Vulkan requirements match our
instance/device features and image/command-buffer ownership. This is an
integration proposal, not verified Android compatibility.

Then add preset-pack import into app-private storage (retaining relative source,
include and LUT paths), global/per-game selection, parameter controls, and Off
plus a compile-error fallback to the normal presenter. Imported archives need
bounded extraction and path traversal rejection. Compile and destroy shader
resources only with correct GPU synchronization and recreate them after resize
or surface loss. Multipass presets need intermediate images, scaling/formats,
frame counters and optional history/feedback, not just a fragment shader.

Validate first with pass-through, simple scanlines and sharpening; compare
aspect ratio, rotation, colors, resize/recreation and GPU time. Next test a
multipass preset, LUTs, parameters and history. Heavy CRT presets may reduce
speed; postprocessing cannot accelerate guest video decoding. Avoid stacking
FXAA/upscaling until filter ordering has been explicitly defined.

Primary references (reviewed 2026-10-31):
- https://docs.libretro.com/development/shader/slang-shaders/
- https://github.com/SnowflakePowered/librashader

## Validation

Native allowlist/queue, memory, fast-forward policy and vblank checks;
Android JVM tests and arm64 debug APK build. No Android device or GPU runtime
test was available here. See the accompanying validation report for actual results.

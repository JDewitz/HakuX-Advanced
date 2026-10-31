# Advanced4 Slang shader test

Version 1.2.0-advanced4-shaders, versionCode 12. App name: hakuX Shaders Test.
Package: com.rfandango.haku_x.shaders. This installs alongside Advanced3 with
separate app data. The old workspace and debug signing key were lost during a
reset; this APK cannot update the earlier signed Advanced3 package. Keep that app
installed for comparison. Use your own stable signing key for future fork releases.
This build keeps the existing kernel, state format, mapped fast forward and save
browser. FF frame skipping remains removed. Shaders default Off.

## Device test

1. Set up a game/HDD in the shader test app. Existing Advanced3 app data is not
   automatically transferred to this separate Android package.
2. Press Back → Advanced & experimental → RetroArch Slang shaders.
3. Choose Preset for this game → builtin-v1/passthrough.slangp, then resume.
   Picture should look unchanged. Check Shader status in the same menu.
4. Try scanlines.slangp and sharpen.slangp. Change Strength in Parameters for this game.
5. Test portrait/landscape, aspect ratio, background/resume and a state load.
6. Turn shaders Off for this game restores normal filters. That explicitly masks
   any global preset. Use global settings removes the game's override.

Global preset/parameters apply to games without overrides. Per-game selections
are independent snapshots, including parameter values. Editing per-game parameters
creates an override when the game inherited global settings. Reset parameters
restores preset defaults. Up to 64 declared parameters are exposed.

The first compilation can cause a stall after Resume. Shader status reports
returned load, compilation and rendering errors; these fall back to normal
presentation. Retry selected preset requests a new compilation after a failure.
Failures are not automatically retried on every refresh. A GPU driver hang or
loss cannot be recovered by a compiler-error fallback.

## Import

Use Import shader pack ZIP. Include .slangp files, .slang sources, includes and
lookup textures, retaining relative directories. Import does not activate a
preset. Legacy .glslp/.cgp files are not supported. The included three examples
are original CC0 shaders, not copies of RetroArch CRT packages.

Archives extract to a private staging folder and publish only on success.
Limits: 4096 entries, 256 MiB expanded total, 64 MiB per file. Unsafe/duplicate
paths are rejected and failed staging is removed. A complete external shader
repository may exceed limits; include the relevant folders and dependencies.
Files survive updates to this test app, but uninstalling it removes private data.

## Implementation and limits

This uses librashader 0.12.1, commit 07ac790cdb9826f37cf8e1daf5011f03e3c4daa7,
through its C Vulkan API. The native module is hw/xbox/hle/vk/slang.c. It renders
the game scanout into a viewport-sized RGBA8 intermediate before hakuX's final
Xbox gamma, rotation and letterbox pass. While Slang is active, built-in FXAA,
SGSR and averaged downsampling are bypassed. Off restores their settings.
This first integration requires native Vulkan 1.3. SDR output only.

Librashader handles multipass targets, scaling/formats, LUTs, parameters and
history/feedback. That capability does not establish Android preset compatibility;
GPU/driver tests remain necessary. Expensive CRT presets may reduce speed.
Postprocessing does not overclock guest video decoding.

The Vulkan loader/device/graphics queue are hakuX's own, including a custom driver
when selected. Fixed-size requests/status are mutex-protected. Only the presenter
thread owns the chain. Replacing resources waits for earlier presenter submits;
initialization synchronizes the shared queue. Three runtime flight slots match
three presenter slots. Explicit image barriers separate shader output writes
from presentation reads. A failed frame's partial command recording is discarded.

Repeated front frames reuse the last shader output, so history advances only
when a new front frame is observed. FrameCount counts observed front frames;
OriginalFPS is 60 (the native vblank model); time delta uses observed time and
resets after a pause. Frames missed by presentation are not reconstructed.
Shader history is not part of savestates; temporal effects can briefly retain
pre-load content. Cache is disabled for the first integration, so changing a
preset or parameters rebuilds the chain. No background compilation is included.

## Comparison with Google's supplied Advanced4

Its source and Git archives were reassembled and inspected. Its five native checks
passed. It uses built-in C effects and a limited fragment-source adapter. The
provided renderer loads only the last pass of a parsed multipass preset, ignores
includes, and does not implement a full preset runtime. The named CRT effects are
simplified implementations. Its dependency bundle/build setup were useful; its
adapter and effects are not combined with this librashader implementation.

## Build and source

Use Linux x86_64, JDK 21, SDK 36, Build Tools 36.1.0, NDK 29.0.14206865,
CMake/Ninja, Rust 1.99.1 and target aarch64-linux-android. The native dependencies
and corresponding cached Rust crate sources are included in the source tar.gz;
SDK/JDK/Gradle/Rust toolchains are separate. Run its build-advanced4.sh from the
extracted bundle, setting JAVA_HOME and ANDROID_HOME. It sets CARGO_HOME to the
packaged registry. For a checkout, prepare-deps.sh then build-slang.sh fetch the
pinned runtime and build it before the root README's CMake commands.

build-slang.sh sets the library SONAME and 16 KiB ELF maximum page size.
app-libs.sh copies the exact CMake-linked runtime and NDK libc++ shared library
needed by its shader compiler. License files and dependency notices are retained.
The runtime offers MPL-2.0 OR GPL-3.0; C headers retain MIT. hakuX remains GPL.

The incremental patch targets Advanced3 source, not Google's already modified
Advanced4. Start from Advanced3 or use the complete source bundle. The accompanying Git archive preserves the supplied history, with our branch
`advanced-slang` based on its Advanced3 commit `1303d21`, and tag
`advanced-1.2.0-4-slang`. Google's `advanced` branch remains available. No remote was
created or pushed. Do not apply this patch over Google's Advanced4 as if it were
an incremental update to that implementation.

## Validation boundary

See the accompanying validation report for actual results. Native/JVM checks and
host SPIR-V compilation do not validate Android GPU rendering. On-device testing
of gamma, orientation, resize, temporal effects, performance and custom drivers
remains outstanding. This is a shader test build, not a compatibility guarantee.

References:
- https://github.com/SnowflakePowered/librashader/tree/07ac790cdb9826f37cf8e1daf5011f03e3c4daa7
- https://docs.libretro.com/development/shader/slang-shaders/

## Advanced4.1: download official shaders

VersionCode 13 updates the existing hakuX Shaders Test installation, with the same
signing key. RetroArch Slang shaders → Download shaders (Libretro) downloads
https://codeload.github.com/libretro/slang-shaders/zip/refs/heads/master over HTTPS.
Progress, cancellation and retry-on-error are available. Once installed, choose
a preset from Preset for this game or Global preset; download does not activate it.

The compressed download is limited to 256 MiB; installation permits up to 20,000
entries, 256 MiB expanded total and 64 MiB per file. Manual import retains its
4,096-entry limit. Paths and duplicate entries are checked. Network timeouts,
failed imports and cancellation clean up temporary files. No authentication or
GitHub account is required.

Snapshots use a content hash in their folder name: downloading the identical
ZIP reuses its installation. A changed upstream pack installs as another snapshot,
leaving older selections valid. Includes, lookup textures, licenses and relative
paths are retained. All preset files are listed (up to 50,000 across packs).

For the real-archive extraction test, set HAKUX_SHADER_TEST_ZIP to a downloaded
ZIP before running JVM tests. It is skipped when no archive is supplied.

## Advanced4.2: Graphics browser and OSD actions

VersionCode 14 updates the existing Shaders Test app with the same signing key.
Open Settings → Graphics → Shaders, a game's Graphics → Shaders, or the
in-game pause menu → Graphics. All open the same folder browser. Browse pack
→ category → preset, use Up/Back, or search within the current folder. The
selected preset is marked. GitHub's archive wrapper is skipped automatically.
Packs provides Download and Import ZIP through the Android file picker.
Global and per-game choices remain separate; Use global clears a game override.
The browser is a separate normal Android activity and does not load the emulator
in the launcher process. Returning to a game applies the selection via the existing
onResume hook. Existing in-game parameter/status controls remain in Advanced.

The fast-forward toast is removed. A static >> OSD label appears at the top right
only while fast forward is active, independent of the FPS overlay setting.

Keys in the browser opens existing global emulator-action mapping. Bind Next
shader to any spare key/button; it is unbound by default. Each press advances
through direct preset siblings in the selected folder, alphabetically, wrapping
at the end. It does not jump between categories or descend into subfolders.
With shaders Off it starts at the built-in examples, or the first installed folder
if examples are absent. After choosing a category/preset, cycling stays there.
A four-second OSD name identifies the requested preset. Hold repeats are ignored
and both key edges are consumed before Xbox input. No shader-change toast is used.

Preset enumeration and config changes for the key run on a single worker, off the
UI thread. Requests are serialized. Shader compilation still happens when the
presenter applies the request and can stall; shader history/save formats and the
Vulkan runtime are unchanged. The next-shader request creates a per-game override.
Native libraries are unchanged in this Android-only update. No device UI/GPU test
was available here. See the release validation report for actual automated results.

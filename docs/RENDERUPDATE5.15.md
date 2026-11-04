# RenderUpdate — Advanced5.15

Version `1.3.0-advanced5.15-renderupdate`, versionCode 32. Same Android package
and signing certificate. This is a renderer-enhancement test batch.

## In this APK

Open **Options → Graphics → RenderUpdate**, or the corresponding per-game
Graphics setting. The same control is in the shared Shaders browser, including
when opened from a paused game. Choose:

- Off: clear the shader selection for this scope.
- Adaptive sharpening: one output-sized pass, strength 0–100%, default 25%.
- SMAA 1x: upstream SMAA spatial anti-aliasing, four passes including input copy.
- SMAA 1x + sharpening: SMAA followed by our sharpen pass, five passes total.

Per-game overrides can use global settings again. Existing shader selections
remain unchanged until Apply. This uses the shader slot: it replaces the active
CRT/custom preset rather than automatically stacking with it. Internal resolution
scaling remains available. The normal FXAA and SGSR presentation branches are bypassed while
Slang is active; these settings do not stack on top of RenderUpdate.

The bundled pack installs without a download. It appears as RenderUpdate in the
shader browser and supports the existing root trash action. A deleted pack stays
removed until explicitly restored by applying a RenderUpdate mode. Normal imports
and shader selection remain available. On a running game, return/resume to apply.
The existing runtime reports failures under Shader status and falls back to normal
presentation; native Vulkan 1.3 remains a Slang requirement in this core.

The sharpening kernel uses a five-tap unsharp term with contrast-adaptive gain
and bounded local overshoot. It preserves sampled alpha. It is an original CC0
filter, not AMD CAS/RCAS. It cannot add missing geometry or replacement texture
artwork. Neither SMAA nor sharpening restores temporal motion information.

## Source and validation

SMAA wrappers, common shader and lookup textures are pinned from libretro's
slang-shaders at `53deb2efcb79f4fc3e94e9602d5974b4b64e8f94`. Original headers,
SMAA attribution and license are bundled in the assets and licenses directory.
The common shader's legacy comment encoding/line endings were normalized to
UTF-8/LF with trailing whitespace removed for loading; its algorithms are unchanged. Our presets use explicit
linear/clamp texture sampling and add the optional sharpening pass.

All ten vertex/fragment stages compile as Vulkan SPIR-V and pass spirv-val.
The validator also checks all three presets, pass aliases, source paths and PNG
lookups. Run `python3 tests/renderupdate/validate-shaders.py` with glslangValidator,
spirv-val and Pillow available. JVM checks cover option persistence/inheritance,
strength validation, pack deletion/restore and failed installation staging.

This validates packaging and compilation. Android GPU output quality, frame cost,
shader loading through librashader on each driver, rotation and resume still need
device tests. Native libraries and the emulator's sampling/draw code are unchanged.

## Development pipeline

| Stage | Work | Acceptance before exposing it |
| --- | --- | --- |
| 1: this test | SMAA 1x + adaptive sharpening | Compiler/preset checks; device comparison next |
| 2: anisotropy | Native sampler policy, per-game Off/2x/4x/8x/16x controls | Clamp to device limit, respect compatible formats/point sampling, include effective level in sampler cache keys, rebuild the native core and verify output |
| 3: texture packs | Stable content IDs, texture dump/export, per-game replacements | Correct mip chains, alpha, animated/render-target exclusions, memory budget and safe fallback |
| 4: frame pacing | Profile pipeline compilation, GPU timings and resolution changes | Demonstrate a real bottleneck and improve frame-time measurements; resolution scaling cannot fix CPU limits |
| 5: depth effects | Optional per-game ambient occlusion | Reliable depth access/reconstruction without broken HUD/transparency |
| Research | Temporal reconstruction, HDR, advanced lighting | Required motion/history/color information and hardware support; no controls shipped yet |

The Vulkan device feature for anisotropy exists, but the current draw sampler
constructor does not enable anisotropy or include its level in the cache key.
A UI-only toggle would have no effect. The saved workspace has the Android APK
build environment and prebuilt native libraries, not the complete NDK/FEX/native
dependency build tree, so that native rebuild is a separate next step. Stages 2+
are planned; none is represented as active in this APK.

## First device comparison

Keep the same game, scene, internal scale and driver. Start with shaders Off and
native FXAA Off. Compare sharpening at 25%, SMAA alone, and SMAA + sharpening at
25%; check text/HUD, diagonal edges, fine textures, motion shimmer and frame time.
Try a second game and resume/rotate once. If performance falls, try sharpening
alone. The existing benchmark/status/export tools can capture evidence. This
update does not claim a Snowblind skybox fix or change trainer/online policies.

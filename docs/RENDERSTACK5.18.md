# Render stages and HUD · Advanced 5.18

The performance HUD now includes a small **FX:** line at levels 1–4. It reads the native runtime status, rather than assuming a saved request loaded successfully. It shows the active preset filename, a two-stage `pre → post` label, pending status, or normal-presentation fallback. Long names are ellipsized. No preset-file reads or extra redraw loop run in the HUD.

## Pre-shader stage plus post-render Slang

**Pause → Advanced → RenderUpdate** configures the pre-shader stage. **Post-render RetroArch Slang shaders** selects the CRT/custom preset separately. Global defaults and per-game overrides are independent for each stage.

Open **Pause → Advanced → Experimental rendering stack**, read the performance warning and choose **I understand · enable** to process both in the same frame:

`Game color image → RenderUpdate → CRT/custom Slang → normal final gamma/rotation/letterboxing`

This combines both stages inside one existing Slang filter chain. It does not change the native Vulkan draw renderer or enable the separate native FXAA/SGSR branches underneath Slang.

| Selection | Stacking OFF (default) | Stacking ON |
| --- | --- | --- |
| Pre only | RenderUpdate | RenderUpdate |
| Post only | Post-render preset | Post-render preset |
| Both | Post-render preset takes priority | RenderUpdate, then post-render preset |
| Neither | Normal native presentation | Normal native presentation |

The opt-in is global. Disabling it keeps both selections and restores the single-stage priority policy. RenderUpdate Off disables only the pre stage; Post-render Off disables only the post stage. Existing RenderUpdate selections migrate to the pre stage. Explicit old per-game Off settings remain Off for the migrated pre stage; migration never enables stacking.

Stacking adds GPU work, intermediate image memory and shader compilation. It can reduce FPS and increase heat. A CRT preset that expects low-resolution input may look worse after an upscaler, and may run its source-sized passes at a much higher resolution. Start with adaptive sharpening → builtin scanlines or FSR EASU → builtin passthrough, then test your preferred CRT. Keep the same scene, resolution, HUD level and driver when comparing.

## Supported composition and safeguards

The composer resolves preset references and dependencies within the private shader folder, preserves LUT paths/defaults, offsets indexed pass settings and PassOutput/PassFeedback references, and redirects post-stage Original/OriginalSize to the RenderUpdate output. Included post shader sources are copied and rewritten inside a hidden content-addressed cache; imported source packs are not edited.

Stacks are limited to 16 passes, 64 runtime parameter overrides, 256 copied source dependencies and 8 MiB of source text. Reference cycles, external dependencies, parameter/texture/alias collisions, imported-uniform configurations and unsupported OriginalHistory/legacy PassPrev bindings are rejected. Cached copied chains are bounded to four. Complex presets can therefore be blocked even when they work alone.

A blocked composition applies the post-render preset by itself and reports the reason in the menu. The HUD labels an active post preset **Stack blocked**. Native compile/draw failures use the existing normal-presentation fallback and are reported by the native status. No unsupported preset is claimed to be working as a two-stage chain.

Host tests cover migration/opt-in, independent overrides, pass/input/include rebasing, references, bounds, dependency rejection, collisions, cache invalidation and all 24 combinations of bundled RenderUpdate modes and builtin post presets. APK build/signature checks and unchanged native binaries are verified. Real Android GPU output, complex third-party CRT behavior, pause/resume and frame/memory cost still require device tests.

## Further lighting experiments

The present Slang bridge receives the final color image, not a scene depth/normal/material buffer. Recommended next experiments are:

| Experiment | Feasibility and limits |
| --- | --- |
| Low-resolution bloom/glare | Color-only, feasible in this shader path. Can emphasize existing lights/specular highlights, including reflected highlights. It adds glow, not new reflections or volumetric geometry. Use low strength and reduced-resolution blur to control cost. |
| Screen-space light shafts | A color-only approximation is possible, but proper occlusion/light positions are missing. A generic version can leak through HUD/bright surfaces; it needs per-game testing and a clear approximation label. |
| Depth-aware fog / ambient occlusion | Requires native depth capture and valid projection/depth reconstruction, plus HUD/transparency exclusions. A native-core experiment rather than a switch in this build. |
| Screen-space reflections | Needs usable depth and surface normals; robust methods also need roughness, camera/projection information and often motion/history. Existing original-Xbox material data is not automatically a modern G-buffer. Higher effort and GPU cost; not enabled here. |

References: NVIDIA GPU Gems 3, chapter 13 (post-process light scattering and occlusion); AMD FidelityFX SSSR integration/input requirements. Bloom and light shafts are proposed next tests, not features added by this update.

- https://developer.nvidia.com/gpugems/gpugems3/part-ii-light-and-shadows/chapter-13-volumetric-light-scattering-post-process
- https://gpuopen.com/manuals/fidelityfx_sdk/techniques/stochastic-screen-space-reflections/

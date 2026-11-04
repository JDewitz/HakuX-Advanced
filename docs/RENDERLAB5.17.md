# RenderLab · Advanced 5.17

Open **Pause → Advanced → RenderUpdate · upscaling / AA / budget**. Choose This game or Global defaults, select a mode, then Apply. Existing graphics/shader-browser controls have the same options. Changes apply in the running game; per-game overrides and Use global work as before. The app keeps the game paused during installation and shader requests.

| Mode | Passes | Tradeoff |
| --- | ---: | --- |
| Off | 0 | Normal native FXAA/upscaler launch settings |
| Adaptive sharpening | 1 | Existing bounded local-contrast enhancement |
| Fast bicubic | 1 | Five-sample spatial interpolation; Soft/Balanced/Crisp shapes |
| FXAA | 1 | Output-resolution AA with configurable search work |
| FSR 1 EASU only | 1 | Edge-adaptive upscaling; skips the RCAS pass |
| FSR 1 EASU + RCAS | 2 | Upscaling followed by robust contrast-adaptive sharpening |
| FXAA + FSR 1 | 3 | AA at source resolution, then EASU/RCAS at output resolution |
| SMAA 1x | 4 | Existing source-sized edge/search passes and output-sized blend |
| SMAA + sharpening | 5 | SMAA followed by the original sharpening filter |

All enabled modes occupy the Slang shader slot and replace custom/CRT presets. Native FXAA and SGSR are bypassed while Slang is active; this menu does not silently stack them. Requires the existing Vulkan 1.3 Slang runtime. Shader status reports loading/fallback failures. Off restores the normal path, including any existing temporary native presentation overrides.

## Budget and quality controls

- **AA search budget:** Fast/Balanced/Quality changes actual shader work. FXAA search caps are 8/16/32 steps. SMAA horizontal/vertical caps are 8/16/32, diagonal caps 4/8/16, with contrast thresholds .15/.10/.05. Fast can miss smaller/longer edges; Quality preserves prior SMAA settings. This reduces search work, not the game's draw count.
- **FXAA subpixel smoothing:** On removes more small-scale aliasing; Off keeps edge AA while reducing the subpixel blur component. It can help HUD/fine-detail clarity but may increase shimmer. It is a quality choice, not a promised speed improvement.
- **Bicubic shape:** Soft uses B=1/C=0, Balanced B=1/3/C=1/3, Crisp B=0/C=.5. These use the same five-sample approximation; Crisp can produce halos/ringing.
- **Sharpness:** The slider applies only to sharpening modes. FSR's inverse RCAS setting is mapped so moving right means sharper. FSR film grain is explicitly off. Existing adaptive sharpening is an original filter; it is not AMD CAS.

FSR 1 is spatial reconstruction: it uses the current color image without motion vectors or prior-frame history. It cannot restore unseen texture artwork, fix incorrect skybox rendering, create real simulation frames, or remove CPU bottlenecks. No FSR 2/3, DLSS, frame generation, VRS or automatic dynamic resolution is claimed here.

The main rendering-budget experiment is **lower internal game resolution plus better spatial reconstruction**. Change the existing internal resolution setting separately, cold-start if needed, and compare it to the higher-resolution baseline. Source-sized FXAA can reduce AA pixel work compared with doing AA at display resolution when the source is smaller. EASU-only removes one full sharpening pass compared with FSR 1 plus RCAS. These are tradeoffs to measure, not measured speed gains. At 1x on a game already limited by the CPU or a fixed 30 fps cap, upscaling will not turn it into a full-speed 60 fps game. Every post-processing mode adds some GPU work.

## Device test sequence

1. Use the same save/scene, driver, HUD level and internal resolution for each comparison. Start with shaders Off. Keep native FXAA off for a clean comparison.
2. Try Fast bicubic Balanced, FSR EASU only, then FSR 1 with 25% sharpness. Look at diagonal edges, floor textures, fine wires, text/HUD and motion shimmer.
3. Compare FXAA + FSR 1 at Fast versus Quality search budget; switch subpixel smoothing off to assess text/detail. If upscaling a lower-resolution source, compare against standalone output-sized FXAA too.
4. Compare SMAA Fast/Balanced/Quality and the optional sharpening pass. Watch frame times, not only average FPS. The existing 10-second benchmark/export tools can record comparisons; use HUD level 2 or turn it off consistently.
5. If currently using 2x–4x internal resolution, repeat at a lower scale with FSR. Record both settings and quality loss. Do not compare different scenes as evidence of an optimization.
6. Check resume, global/per-game inheritance, shader status, pack deletion/restore, and a second game. New modes remain experimental until Android GPU testing.

## Packaging and validation

The existing built-in pack keeps its `renderupdate-v1` root so saved paths remain valid. Its completion revision becomes 2. An old installed pack upgrades on normal builtin installation; explicit deletion is respected until a user applies/restores it. All expected presets must stage successfully before replacing installed files. Shader parameters continue to serialize in global/per-game selections.

FSR headers/wrappers, NVIDIA FXAA and the Hyllian bicubic port are pinned to libretro/slang-shaders `53deb2efcb79f4fc3e94e9602d5974b4b64e8f94`. See the bundled RENDERLAB-SOURCES.txt and license notices. FSR wrapper texel fetches are explicitly clamped at image edges because sampler wrapping does not clamp texelFetch. FXAA adds runtime search and subpixel controls. FFX header algorithms and bicubic shader math are unchanged.

The host validator compiles 18 vertex/fragment stages to SPIR-V, runs spirv-val and checks all eight presets, SMAA aliases/lookups, source-sized FXAA ordering and default grain-off settings. JVM tests cover selections, parameters, inheritance, upgrade/deletion and failed staging. Native libraries remain unchanged; host shader compilation does not prove Android driver compatibility or visual quality.

Primary references:
- https://gpuopen.com/fidelityfx-superresolution/
- https://github.com/libretro/slang-shaders/tree/53deb2efcb79f4fc3e94e9602d5974b4b64e8f94/edge-smoothing/fsr
- https://github.com/libretro/slang-shaders/blob/53deb2efcb79f4fc3e94e9602d5974b4b64e8f94/anti-aliasing/shaders/fxaa.slang

## Next native-core batch

First establish GPU execution timings and CPU/GPU bottlenecks. Then evaluate adaptive resolution with hysteresis and game-safe render-target recreation, anisotropic sampler filtering with correct cache keys/device limits, and pipeline-cache/stutter improvements. Texture replacement needs stable content identities and a memory budget. Temporal reconstruction needs reliable motion/depth/history data. These require a reproducible full native build and device tests; they are not exposed as nonfunctional toggles in this APK.

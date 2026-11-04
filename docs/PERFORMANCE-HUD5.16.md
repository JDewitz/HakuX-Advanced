# Performance overlay · Advanced 5.16

Open **Pause → Advanced → Performance overlay** while a game is running. Choose a level; the change applies immediately and is remembered globally. It works alongside RenderUpdate and imported shaders.

| Level | Display |
| --- | --- |
| 0 | Off |
| 1 | Small FPS counter |
| 2 | Compact horizontal bar: FPS, average frame time, app CPU, system/app RAM, battery |
| 3 | Detailed panel with colored resource rows and average/last frame times |
| 4 | Full panel, battery temperature, Android thermal status, sampled frame-time graph |

The appearance follows the Steam Deck/MangoHud approach: translucent black background, monospace values, green FPS/battery, blue CPU, purple memory and yellow graph. The overlay stays at the top left and does not capture touches. Fast-forward `>>`, shader-name feedback and the separate debug hitch diagnostic remain available. The launcher **Show FPS** switch controls visibility too; enabling it restores the remembered positive level, or level 1 after Off.

## Metric definitions

- FPS and average/last frame times come from existing native presentation counters. They do not measure emulation-speed percentage.
- CPU APP is process CPU time divided by elapsed time and the number of logical CPUs visible to the runtime. It is neither total device CPU usage nor guest CPU utilization.
- SYS RAM is Android device-wide used/total physical memory; APP RSS is resident process memory from `/proc/self/status`.
- Battery watts are the absolute battery current times voltage for the whole device, including charging. They are not application power. Battery temperature is not GPU temperature. Android thermal status is a severity category, not a clock speed.
- GPU utilization and VRAM usage show N/A because the core does not expose them. The native GPU busy queue/wait counter is deliberately not presented as utilization.
- Missing/unsupported host readings show `--` or N/A.

The graph covers 30 seconds, sampling the last completed frame at most four times a second. It is **not a complete per-frame trace**; no 1% low or percentile claims are made. Horizontal references mark 16.7 and 33.3 ms, and the vertical scale expands to include spikes. Sampling stops while a menu, manual pause or background activity pause holds the game. Gaps represent pauses rather than invented frame times.

## Cost and verification

Levels 1–2 redraw at most twice a second; levels 3–4 at most four times a second. Existing OverlayPacer gating remains in place. Host process/memory/battery queries run on a single worker at most once a second, only at levels 2–4 during gameplay. History has a fixed 160-sample bound. No native binaries change.

Unit tests cover persistence and legacy visibility, invalid counters, CPU normalization/reset and history bounds. APK assembly verifies Android integration. Device checks still needed: all levels, resume/relaunch persistence, launcher visibility switch, touch/gamepad controls, portrait/narrow display scaling, shader/fast-forward overlay coexistence, and frame pacing with HUD off versus level 4. Compare the same scene and settings; the overlay itself has a small rendering cost.

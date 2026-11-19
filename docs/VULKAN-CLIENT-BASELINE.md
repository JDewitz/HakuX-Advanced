# Vulkan client baseline

The diagnostics start from HakuX-Advanced 5.19.3 (`5a04012`). Use
**Settings → Export logs** after a test run. The export includes the app build,
device and Android version in `session.txt`, plus the per-game log.

| Log entry | What it records |
| --- | --- |
| `Vulkan driver requested` | Selected driver package name and version metadata when available, or system driver. Compare it with the native loader result and the actual Vulkan driver identity. |
| `vk: gpu=` | GPU name, vendor and device IDs, Vulkan API and driver versions, and pipeline cache UUID. The UUID identifies cache compatibility. Record the exact driver package or its SHA-256 separately. |
| `vk: driver` | Vulkan driver name, ID and reported driver information. |
| `vk: local read:` | Extension advertisement, queried feature, eligibility under the current Vulkan 1.3 restriction, and whether device setup requests framebuffer fetch. |
| `fbfetch: effective=` | Effective framebuffer fetch selection after descriptor setup. An earlier descriptor error identifies the operation and Vulkan result if initialization falls back. |
| `quirks: active=` | Active workarounds, driver defaults, the override source, and workarounds explicitly forced on or off. An override can repeat a driver's default. |

For each run, record the source commit, driver package, game revision, scene,
render scale, postprocessing preset, and cold or warm shader cache. Capture the
original failure scene before experimenting with defaults.

| Driver track | Baseline scenes | Status |
| --- | --- | --- |
| G52 stock Mali and MaliSX2 | Title start, gameplay, suspend/resume and presentation pacing | Pending hardware |
| G57 stock Mali and MaliSX2 | Morrowind loading and gameplay, Mortal Kombat: Shaolin Monks geometry | Pending hardware |
| G615 stock Mali and MaliSX2 | Title start, gameplay and shader preset switching | Pending hardware |
| Pinned Turnip and stock Qualcomm | Halo floor lighting, Blood Wake boat selection, Fatal Frame afterimages, Jade Empire Focus Mode | Pending hardware |

Keep the title revision, scene and settings fixed between runs. Keep Jade
Empire's existing Sagacious default active for the first baseline, and record
whether it disables framebuffer fetch. Test changes one at a time and capture
screenshots when output differs.

Host validation covered Vulkan C syntax and format checks with the pinned Volk
and patched adrenotools headers, and simulated driver defaults, launch-time
overrides, live overrides and reset to defaults. Android assembly and device
log export are pending. Device-fault collection and Vulkan 1.1 local read are
follow-up changes.

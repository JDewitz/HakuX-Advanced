# HakuX-Advanced 5.2 — we renderin xisos

Live experimental testing build, based on Advanced 5.19.3. Feedback is optional.
The in-house device pool is RP5, RP Flip 2, EX8, x28, RP6, RP Nova and 477m.
These are available test targets. This release has no device-specific pass
record yet. Earlier reports that some games look or run better are anecdotal.

## Changes from production 5.19.3

- Reuse pooled framebuffer-fetch descriptor sets for the same color/depth image
  views and layouts during one command buffer. Reset cache entries with their
  command buffer. Failed descriptor allocation/reset skips the affected draw.
- Allow an opt-in local-read path on Vulkan 1.1/1.2 when dynamic rendering,
  synchronization2 and dynamic-rendering local read are supported. Retry device
  creation with the existing fallback when the extension path is rejected.
- Record driver identity, cache UUID, supported/selected local read, effective
  descriptors and explicit workaround overrides in exported logs.
- Optional device-fault count reporting on actual device loss.
- Read XDVDFS directory chains beyond 64 levels, with a visited bitmap and an
  explicit stack bounded by the directory extent. This addresses files reported
  missing in certain repacked/trimmed images, including the reported Burnout 3
  Tracks/tlist.bin case. Ordinary image trimming does not necessarily trigger it.
- Identify the app as 5.2 “we renderin xisos”. Experimental builds use manual APK
  installation and suppress the production updater.

## Rendering paths and methods

| Path/method | Selection and status |
| --- | --- |
| Vulkan 1.3 dynamic rendering with local read | Existing default where supported and title rules permit framebuffer fetch |
| Vulkan 1.1/1.2 extension local read | Experimental, opt-in at process startup; requires the queried features and KHR commands |
| Local read unavailable or disabled | Existing fallback handling, including compatibility render passes/synchronization where needed |
| Framebuffer-fetch descriptors | Push descriptors when usable; pooled sets otherwise, with reuse in this branch |
| Shader generation below Vulkan 1.3 | Existing Vulkan 1.1 / SPIR-V 1.3 target |
| Postprocessing | Existing RenderUpdate/Slang presets and explicit renderer/post-shader stacking control |
| Device-fault diagnostics | Optional extension/feature-gated count query; reports the original failed Vulkan operation |

Experimental startup switches, for testers who have adb:

```sh
adb shell setprop debug.hakux.localread 1
adb shell setprop debug.hakux.devicefault 1
adb shell am force-stop com.rfandango.haku_x.themes
```

Set each property to 0 and restart the app to disable it. These experiments
remain off by default. A device advertising a feature still needs visual and
stability testing. GPU-fault record contents and vendor binaries are not captured.

## Current compatibility workarounds

Actual selection appears in the game's exported log. GPU identity and driver
rules select defaults; explicit Advanced overrides can change them.

| Rule | Current trigger/purpose |
| --- | --- |
| Sagacious | Jade Empire title ID 4D53006D, all revisions by default; disables framebuffer fetch. Cached shader prebuild defaults off unless explicitly overridden |
| Halo high kernel pool | Existing Advanced Auto rule for Halo 4D530004 revision 8 on base 1.3.0 |
| vi_after_pipeline | Qualcomm proprietary driver; reapply dynamic vertex input after pipeline binding |
| state_after_pipeline | Available override; no driver default |
| eq_depth_step | Turnip; compensate equal-depth multipass mismatch |
| lrz_clear | Turnip; preserve depth clear before backdrop/depth-tested draws |
| blend_const_reset | Turnip; restore blend constants after depth-only pipelines |
| no_push_desc | Arm proprietary blob, excluding MaliSX2; use pooled descriptors |
| ds_same_load | Mali vendor; match depth/stencil load operations |
| z_eq_w | Mali vendor; move vertices slightly inside the z=w boundary |
| fresh_clear | Drivers outside stock Qualcomm/Turnip; initialize fresh attachments |
| swap_w32 | Older PowerVR driver versions; align presentation width |
| no_timestamps | Arm blob on G615/G52; avoid queries that do not resolve |
| present_cpu_wait | Conservative default except Turnip and Arm-blob G52 |
| vb_align | Mali-G57; repack misaligned attributes |
| no_wbuf | Mali-G57; avoid the documented W-buffer hang |
| no_sem_wait | Identified Arm-blob releases below r40; poll timeline counters |

The G57 predicates for vb_align/no_wbuf remain broad. PR #45's command-buffer
occlusion reset and PR #46's additional MaliSX2 vertex alignment are follow-up
candidates and are not bundled here. Workarounds are not removed on the basis of
a successful boot.

## Practical in-house testing

| Device | This build |
| --- | --- |
| RP5 | Awaiting report |
| RP Flip 2 | Awaiting report |
| EX8 | Awaiting report |
| x28 | Awaiting report |
| RP6 | Awaiting report |
| RP Nova | Awaiting report |
| 477m | Awaiting report |

Use whichever device and game are already in reach. A short report containing
device, driver, game and “better / same / worse / crash” is useful. Export logs
after a failure if practical. There is no requirement to complete a matrix.

Suggested spot checks are Halo campaign loading/lighting, Jade Empire Focus
Mode and Burnout 3 boot plus one race with an existing XISO. Other working
XISOs are useful regression checks. Quake demo playback provides a repeatable
scene for pacing checks, with its exact engine build recorded.

When comparing speed, keep the same scene, resolution and driver, and compare
warm shader caches. Brief ordinary-play observations should be labeled as
observations. Any measured gain needs comparable frame-time runs. Note missing
objects, flicker, corrupt geometry, audio/input changes and background/resume
failures even if FPS improves.

## Attribution and validation

The directory reader fix and its synthetic chain test come from
pwnedbygary's upstream PR #47, commit
14c11e565a8aab37944bed090d8df638e6893ad4.
The 76-entry chain passes the host fixture and address/undefined-behavior checks.
LeakSanitizer cannot run under the host tracer, so leak detection was disabled
for that sanitizer run.

The preceding renderer package reports 15 HLE host suites, descriptor reuse
and allocation/reset failure fixtures, 162 JVM tests (one skipped), and shader
tool validation. Those are historical results for that package. Current build
results are supplied separately in VALIDATION.json.

AI Use Discolosure: AI was used heavily in this project.

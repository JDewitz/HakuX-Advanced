# HakuX-Advanced 5.20 — Goin Commando

AI Use Discolosure: AI was used heavily in this project.

## Changes

- Republic Commando's movie stutter has been patched with built-in 60 FPS
  guest-present pacing. It activates automatically for title ID 4C410013,
  across all revisions. No external patch import is needed.
- Promote the private 5.2 renderer work: reuse framebuffer-fetch descriptor
  sets within command buffers; retain fallback paths and driver workarounds.
- Include deep XDVDFS directory traversal for affected trimmed/repacked XISOs.
  Working trimmed XISOs need no conversion. This addresses directory layout
  handling, not every possible disc-reading problem.
- Retain opt-in Vulkan 1.1/1.2 dynamic-rendering local-read support when the
  required extensions/features are present. It remains off by default.
- Include driver/cache/path diagnostics and opt-in device-fault count reporting.
- Retain Sagacious for Jade Empire, Halo's existing pool-placement profile,
  trainer indexing, shader management, themes and the existing Advanced features.
- Restore normal stable-release update checks; versionCode increases to 42.

## Install

Install the arm64 APK over the existing HakuX-Advanced installation. It uses
package com.rfandango.haku_x.themes and the existing signing certificate.
Do not uninstall first. Fully exit games before installation and cold start
afterward. Disable the older Republic Commando 30/60 imported test patches to
use the built-in default. A still-enabled 30 FPS test patch overrides the rate
offline; disabling that import restores the built-in 60 FPS rate.

## Limits and testing

The pacing fix affects the entire matching game, including gameplay. It
limits guest present requests over time, not unique decoded movie frames;
queued bursts can briefly exceed the target. Fast-forward cannot bypass it.
Built-in compatibility fixes remain usable online; trainer/guest-mod blocking
and save-state loading policy are retained. Online timing is not validated.

This is a live/testing release. The movie fix is implemented and host-tested;
playback and performance of this exact APK still need device confirmation.
No claim of universal driver compatibility or 60 FPS gameplay is made.
The in-house test pool is RP5, RP Flip 2, EX8, x28, RP6, RP Nova and 477m;
these are available test targets, not a list of devices passing this release.
A short device/driver + better/same/worse/crash report is sufficient.

See VALIDATION.json in the release kit for build checks, test counts, APK hash
and signing identity. See docs/EXPERIMENTAL5.2.md for render paths, defaults,
current workarounds and optional testing switches. Existing theme imports are
retained; this kit does not add separate downloadable nostalgia theme packs.

## Release identity

Normal release: not a prerelease. Tag: advanced-1.3.0-5.20.
Android version: 1.3.0-advanced5.20-goin-commando; versionCode 42.
Official base remains 1.3.0. Prepared locally; publishing is a separate action.

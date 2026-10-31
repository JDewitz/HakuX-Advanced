# Advanced5 — official hakuX 1.3.0 integration

Official release: https://github.com/rfandango/hakuX/releases/tag/v1.3.0
Upstream commit: a90f01ee4629977b36a76175cbd781d383056293
Fork parent: a2c7fa5 (Advanced4.3 trainers).
Local branch: advanced-1.3-integration. No remote was created or pushed.

The merge incorporates the full tagged 1.3.0 source, rather than selecting
individual fixes. It retains our mappable uncapped fast forward, static >>
indicator (no toast), shader folder browser/downloader/import, parameters,
mappable Next Shader, two-pane save-state browser, advanced profiles/tools,
and experimental classic EvoX ETM importer/options/boot execution.

## Install and use

APK: hakuX-1.3.0-advanced5-arm64.apk, version code 16.
Application ID remains com.rfandango.haku_x.shaders, display name
hakuX Shaders Test, with the same development signing certificate as 4.3.
It updates the existing test app and retains its private settings/data.
The official app remains a separate application.

Existing fork controls remain in the same places:
- Settings → Graphics → Shaders; per-game Graphics → Shaders.
- Global action mappings: Fast Forward and Next Shader (Next Shader is
  unbound by default). Fast Forward remains toggle/hold, without frame skipping.
- Back/in-game menu: the two-pane save browser and advanced tools.
- A game's library menu → Mods, cheats & trainers: import classic .etm,
  choose options, enable, then start the game fresh. XBTF is unsupported.
  See ADVANCED4.3.md for trainer details and limitations.

The official 1.3.0 additions are available:
- The library's Dashboard button launches the installed MS dashboard.
  On first use, provide your own dashboard 5960 files as a ZIP.
- Settings → Network → Xbox Live setup guides dashboard/Insignia registration
  and account setup. Network → Connection selects Insignia, LAN or XLink Kai.
- Installed Xbox Live title updates participate in the official boot path.
- Official game, graphics, timing, controller-editor and networking fixes
  are merged as released. Their runtime behavior has not been verified here.

No Xbox dashboard, account or trainer payloads are bundled. No network account
was configured or registered during development. Trainer compatibility still
requires the correct game region/revision: a matching title ID alone cannot
establish compatibility, particularly after installing a title update.
Use trainers in offline testing; leave them disabled when testing Xbox Live.

## Integration details

The two textual conflicts were the app version and launch arguments. The
version is now 1.3.0-advanced5; both the fork's advanced-profile arguments and
official dashboard argument are retained. Other upstream changes merged
without textual conflicts and were reviewed around graphics, hotkeys, boot,
mods and state handling.

Trainer configuration resets at every core boot, including disc-less boots.
Fresh dashboard launches and relaunches to the MS dashboard skip game trainer
installation. Game trainers retain their native title-ID validation, and
save-state loads skip fresh trainer execution.

The new NAT dependency is unmodified libslirp 4.7.0 at
26be815b86e8d49add8c9a8b320239b9594ff03d, matching official 1.3.0.
The fork's tools/native/prepare-deps.sh now fetches that exact revision from
the qemu/libslirp mirror; licenses/libslirp.license.txt retains its copyright
and source-file notices. FEX and librashader revisions are unchanged.

Host HLE tests needed two build-only corrections on Linux: designated
HlePbSink initializers for the added run_map field, and explicit libm linkage.
The runtime implementation was not altered for those test fixes.

## Save states

Official 1.3.0 writes KERN v3 and PBD v2 and retains readers for earlier
versions. Its compatibility paths for old kernel-pool layouts were retained,
as was our state browser. The existing trainer state behavior also remains:
loading a state restores the trainer's saved memory/hooks; changing current
trainer switches does not rewrite that state. Use fresh starts to test a new
trainer selection.

Older-state loading and new-state gameplay have not been tested on a device.
Keep normal game saves and older state copies before testing migration.
Newer states are not promised to load in the older 1.2.0 fork. Android may
require uninstalling to downgrade, which deletes private app data.

## Build and validation

Use android/README.md and tools/native/prepare-deps.sh for the native
sources/toolchain; build the pinned librashader runtime using
 tools/native/build-slang.sh as described in ADVANCED4.md. The portable Git
archive contains the merged source/history and dependency gitlinks, not the
SDK/NDK, private signing key, or external dependency checkouts.

Validation results are in the accompanying VALIDATION-advanced5.txt. The
checks cover ARM64 native compilation, APK assembly and signature, JVM and
native regressions, 15 HLE host suites, state layout hashes, trainer parser
and independent x86 execution, and starter shader compilation.

No Android device, game images, GPU, dashboard or Insignia account was
available. Device gameplay, shader rendering, dashboard setup, online play,
and real save-state migration remain untested. This is an unofficial
experimental test build. The previous shader/trainer branches and tags are
retained in the source archive.

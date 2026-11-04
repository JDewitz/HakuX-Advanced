# HakuX-Advanced project status — 2026-11-03

Current update: RenderUpdate / Advanced5.15, `1.3.0-advanced5.15-renderupdate`,
versionCode 32. Bundled SMAA 1x and contrast-adaptive sharpening use the existing
Vulkan Slang runtime, with global/per-game controls under Graphics. Effects are
opt-in and occupy the shader slot. Native binaries are unchanged; anisotropic
filtering, texture replacement and depth/temporal features are planned, not
active. Shader SPIR-V compilation/validation passed; device rendering/performance
checks remain pending. See RENDERUPDATE5.15.md.

User confirmation: Advanced5.14 trainer indexing works very well on device.
Shader pack deletion was previously confirmed. These reports do not establish
RenderUpdate shader/device compatibility.


Previous update: Advanced5.14, `1.3.0-advanced5.14-trainerindex`, versionCode 31.
Trainer imports and explicit folder refreshes persist parsed metadata. Opening
trainer selection and matching games use the saved title-ID index without
reopening the complete ETM pack. Old catalogs migrate once; chosen executable
bytes are still checked on selection/start. See TRAINER-INDEX5.14.md.


Previous update: Advanced5.13, `1.3.0-advanced5.13-stateload`, versionCode 30.
Online protection no longer blocks save-state loading from the library, pause
menu, mapped actions, debug requests or relaunch. Trainer/cheat injection,
memory tools and imported-patch state compatibility rules are unchanged.
See STATES5.13.md for scope and validation.


Previous update: Advanced5.12, `1.3.0-advanced5.12-shaderdelete`, versionCode 29.
Shader pack roots have a trash icon and confirmation dialog. Purging removes
presets, sources and textures, clears affected global/game selections to Off,
and refreshes the browser. Built-in examples stay removed after deletion.
Native libraries and online compatibility policy are unchanged. See SHADERS5.12.md.

Previous update: Advanced5.11, `1.3.0-advanced5.11-onlinecompat`, versionCode 28.
Protected online sessions now have a separate compatibility-only manifest path
for exact-content release-reviewed patches. The registry starts empty. Halo
revision 8’s built-in Auto fix remains eligible online; trainers, cheats and
gameplay mods remain blocked. Native libraries are unchanged. Android APK assembly passed; 117 JVM tests passed,
zero failed and one optional skip. Signature/package/native library checks passed.
Actual online device testing remains pending. See ONLINE5.11.md.

Previous update: Advanced5.10, `1.3.0-advanced5.10-patches`, versionCode 27.
Imported guarded patches have a folder browser, per-game toggles, preflight
checks, conflict rejection and protected-online blocking. Native libraries are
unchanged. Save-state use is blocked in imported-patch sessions. Device testing
is pending. Android compilation/APK assembly passed; 109 JVM tests passed, zero
failed and one optional shader-archive test skipped. The final APK has the
existing certificate and 11 unchanged native libraries. See PATCHES5.10.md.

Previous update: Advanced5.9.1, `1.3.0-advanced5.9.1-halo8`, versionCode 26.
Auto enables the Halo pool workaround for user-reported title/revision
`4D530004.8` on emulator base 1.3.0. Explicit On/Off choices remain unchanged.
Other revisions are not enabled. See HALO5.9.1.md for evidence and use notes.

Previous update: Advanced5.9, `1.3.0-advanced5.9-compat`, versionCode 25.
Adds Auto / On / Off pool-layout compatibility controls, legacy migration,
identity/revision/build matching, effective-setting reasons and launch logging.
In 5.9, the Halo automatic rule was disabled. Native libraries are reused unchanged
from the tested 5.8 APK. See COMPATIBILITY5.9.md for behavior and validation.

Latest device-tested runtime commit: 9bc08fd (Advanced5.8).
Tag: advanced-1.3.0-5.8-halo. APK versionCode 24.
Android package: com.rfandango.haku_x.themes.
Signing certificate SHA-256:
1037f9d3ba4617d3e75b3ce8cd02cd3968f58aefa025017523e843e78d277a81.

The Advanced5.8 APK remains the reference for the initial successful device
test. Advanced5.9 uses the HakuX-Advanced display name, the same update package
and signing certificate, and the higher versionCode 25. Preserve the exact 5.8
APK for reproducing the earlier result.

Advanced5.9 validation: Android compilation; 99 JVM tests passed, zero failures,
one optional shader-archive skip; native pool/Advanced/fast-forward checks passed.
GitHub CI run 41491779396 passed Android and native jobs for source `9fdfc6c`.
The Android Gradle configuration now uses JAVA_HOME rather than a fixed host
path. APK assembly and signature/package verification passed. All 11 native libraries
match the 5.8 APK byte-for-byte. Device UI navigation, real-settings upgrade and
repeat Halo tests remain pending. The APK checksum is in COMPATIBILITY5.9.md.

Build validation: native arm64 and Android builds pass; JVM 92 passed, one
optional external shader-archive test skipped. Native pool/Advanced and trainer
parser/x86-wrapper checks passed. The unchanged shader runtime was reused from
Advanced5.7; a full Rust rebuild was not run for the 5.8 APK.

Device result: the user reports “Bingo. Works.” after receiving the Halo
high-pool test instructions. This is an initial success report, not repeat
counts, campaign completion, or successful-run log verification. OFF/ON cold
boots and wider RH testing remain outstanding. The baseline official 1.3.0
and Advanced5.7 logs share EIP 0018110f / guest address 0000000c following
4 KiB allocation failures. The exact failing request constraints still need
post-instrumentation capture.

Prepared: native-only upstream patch/report package, RH pack containing the
exact APK and source, instructions, checksums and results template. No upstream
message or PR has been posted.

GitHub sync completed on 2026-11-02:
https://github.com/JDewitz/HakuX-Advanced. Private visibility and default branch
`main` were verified. The prepared source head `7cf98cb`, full upstream/fork
ancestry and all 17 Advanced tags are present. Host checks passed for that head
(Actions run 41420948980). Temporary transfer branches were removed. No new APK
was built by this sync; signing material remains outside Git.

The earlier dashboard/kernel experiment is excluded. The Halo revision 8 Auto rule uses user-reported identity and success evidence;
independent logs and repeat tests remain pending. General patch
packs, Steel Battalion device emulation and RetroAchievements remain planned. See ROADMAP.md.

# HakuX-Advanced project status — 2026-11-03

Current update: Advanced5.9.1, `1.3.0-advanced5.9.1-halo8`, versionCode 26.
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

# HakuX-Advanced project status — 2026-11-02

Latest tested runtime commit: 9bc08fd (Advanced5.8).
Tag: advanced-1.3.0-5.8-halo. APK versionCode 24.
Android package: com.rfandango.haku_x.themes.
Signing certificate SHA-256:
1037f9d3ba4617d3e75b3ce8cd02cd3968f58aefa025017523e843e78d277a81.

The latest distributed APK predates the repository branding/docs update. It is
not rebuilt or renamed internally by changing this repository. Preserve its
exact bytes for reproducing the initial user test. New APKs need a higher
versionCode before distribution and the same private signing key for updates.

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

The earlier dashboard/kernel experiment is excluded. Automatic per-game
compatibility rules, general patch packs, Steel Battalion device emulation and
RetroAchievements are planned, not implemented. See ROADMAP.md.

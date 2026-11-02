# Advanced5.8 — Halo allocation investigation

This update appends the standalone mainline Halo investigation commits to
Advanced5.7. The mainline patch series remains independent and can be handed
to upstream without the Advanced frontend. The experimental placement is off
by default; this is not a confirmed Halo repair.

## Install and test without ADB

The APK uses the same `com.rfandango.haku_x.themes` application ID and signing
certificate as Advanced5.7, with versionCode 24. Install over the existing app.
Do not uninstall: that removes the app's private settings and Xbox HDD data.

1. Select Halo's per-game Advanced settings in the library. Under CPU & memory,
   enable **High kernel pool placement** for Halo only. The paused game's
   Advanced menu also exposes it under **Startup profile for this game**.
2. Keep **128 MB Xbox RAM off** for this comparison. Fully exit the game to
   the library, then start it again. Do not load an emulator save state.
3. Start the campaign and check whether the bay renders beyond the intro.
   If it crashes, reopen the app and use the existing **Export logs** action.
4. Repeat with the placement toggle off and export that session too. Report
   what happened with each setting. A few extra frames alone are not a pass.

The toggle also exists globally; prefer a per-game profile for this test.
It cannot alter a running session. Old profiles default to off, JSON import
and export retain the value, and **Use global** restores normal inheritance.
Every launch passes an explicit `pool=high` or `pool=default` to the core.

## Changes and boundaries

- Failed contiguous allocation reports now include the requested address
  bounds, alignment, protection, guest caller and tracking-table occupancy.
- The placement experiment moves only the initial 64 KiB kernel pool from
  low RAM to high RAM. It retains the pool size and later growth policy.
- The native log identifies `pool: boot ... mode=high-experimental` or
  `mode=default`; recorded savestate placement takes precedence.
- Online protection remains enforced. This changes the emulator's allocation
  layout; it adds no trainer, cheat or guest executable patch. Existing
  network/trainer/state-load restrictions are unchanged.

High placement can block other games' fixed allocations. Keep it off for
unrelated titles until tested. The allocation-failure → null-access sequence
is established in official and Advanced logs, but the causal link and pool
placement hypothesis still require device confirmation.

## Continuing development

Keep these commits on top of Advanced5.7; do not replace the mainline base or
discard the trainer, shader, theme, input or online-protection history. The
two standalone native commits are followed by one Advanced UI/version commit.
The Git archive and `hakuX-advanced5.8-from-5.7.patch` preserve that increment.

Rebuild the native library and refresh jniLibs before assembling Android.
The build uses the pinned FEX and native sources restored from the earlier
source archive, plus the 1.3.0-pinned libslirp revision. The unchanged
librashader runtime is reused from the Advanced5.7 APK, with its corresponding
source in the earlier Slang source archive. No signing key goes into Git or
the source handoff. Use the existing signing configuration privately.

See `halo-allocation-investigation.md` for the mainline hypothesis, host tests
and rollback procedure. Device testing is pending at delivery; build/test
results and certificate verification accompany the APK in VALIDATION-halo5.8.txt.

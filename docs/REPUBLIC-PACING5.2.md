# Republic Commando present pacing test

This build adds two importable compatibility patches for title ID 4C410013
(Republic Commando), across all certificate revisions on emulator base 1.3.0.
The actual XBE certificate is checked again by native code before pacing starts.
Other title IDs keep pacing disabled.

## Install and select

1. Install the signed 5.2 rc-pacing APK over the existing Advanced test build.
2. In Settings → Game patches, import republic-present-60.hakuxpatch and
   republic-present-30.hakuxpatch, or add their containing folder.
3. Select Republic Commando and enable only the 60 FPS pacing patch.
4. Fully exit the game and cold start. Try the same slow movie.
5. If it still drags, disable 60, enable 30 and cold start again.
6. Disable both and cold start to return to the previous behavior.

Enabling both rates blocks both as conflicting options. File metadata cannot
apply the option to another title or request an arbitrary rate. These patches
select an emulator setting and contain no game-memory writes. Patch choices
remain saved per game and content hash.

The existing online protection policy treats this unreviewed timing experiment
as offline-only. Save states are supported: the current patch selection
establishes pacing before loading a state, and pause clears pending pacing debt.
For a reproducible comparison, use a cold start.

## Implementation

The decoder records guest present requests at swaps and direct/overlay
presentation. It wakes the guest CPU so the next kernel safe point can pay
the accumulated frame time. The decoder and render threads do not sleep for
the limiter. The pacing wait uses host monotonic time and allows guest clocks
and audio to continue. It does not use the guest-clock hold API.

The target is 60 or 30 guest present requests per second over time. A queued
burst can exceed that rate briefly. Debt is bounded at 250 ms and sleeps use
2 ms slices to notice pause. The first frame after boot or a long idle is
immediate. The limiter applies during gameplay as well as movies. Fast-forward
does not bypass a selected pacing patch. High present counts can include
repeated pictures, so this does not measure unique decoded movie frames.

This experiment tests whether excessive guest presentation contributes to the
reported extremely slow movies while the overlay shows 400+ FPS. It is not a
confirmed fix. Shader settings being off does not identify the root cause.
The known uncapped behavior was reported in xemu issue #1013 and xbased #351.
Those reports do not prove the cause of this movie slowdown.

## Testing and reporting

Host tests exercise the actual pacing entry points with controlled time:
title and rate validation, steady 30/60 pacing, queued requests arriving during
a wait, bounded burst debt, pause/resume, long idle and disabled pacing.
Address/undefined-behavior sanitizer checks pass with leak detection disabled
under the host tracer. JVM tests cover title/revision matching, conflicting
rates, disabled choices, state loading, offline/online policy and rejected
guest writes. Build-specific counts and certificate checks are in VALIDATION.json.

Device playback remains unverified. Any of RP5, RP Flip 2, EX8, x28, RP6,
RP Nova or 477m can be used. A report with device, driver, selected rate,
movie duration compared with wall time, and whether audio slows or skips is
enough. Export logs after the run if practical. The launch log includes
"present pacing: title=4c410013 requested=60 active=60 FPS" (or 30).

## References

- https://github.com/xemu-project/xemu/issues/1013
- https://github.com/crazyriddler/xbased/issues/351

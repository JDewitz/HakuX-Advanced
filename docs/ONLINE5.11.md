# Advanced5.11 — compatibility fixes during protected online play

Version `1.3.0-advanced5.11-onlinecompat`, versionCode 28. Same update package,
signing certificate and native libraries as Advanced5.10.

## What to use for Halo

Keep **Online play protection On** and Halo's **High kernel pool placement** on
**Auto**. The built-in rule for `4D530004.8` remains enabled on the 1.3.0 core.
That fix changes initial kernel allocation placement and does not need a trainer
or an imported patch. Networking remains available; trainer/cheat choices are
preserved for offline use but their binaries and commands are not loaded online.

Do not disable online protection to enable Halo's built-in fix. This update does
not add a new Halo runtime fix or establish successful Insignia gameplay on a
device; that still needs testing.

## Imported compatibility patches

Advanced5.10 blocked all imported patches online. Advanced5.11 adds a separate
compatibility-only path for patches reviewed in the bundled release registry.
An approval binds the complete file SHA-256, a deterministic fingerprint of the
parsed metadata/operations, patch ID, exact title ID/revisions and emulator base,
plus a review reference and rationale. Missing/ambiguous/revoked approvals,
changed files or payloads, wrong revisions and unavailable sources fail closed.

**The production registry currently contains no imported game fixes.** Test
approvals exist only in JVM fixtures and are not shipped as permissions. The
Halo no-op guard probe remains unapproved. A real fix must be reviewed and added
to the source registry in an app update before it can run online. There is no
user toggle or file-supplied `onlineSafe` flag that bypasses review.

The patch details panel shows online eligibility. Approval does not turn a patch
on: the existing per-game enabled toggle, source availability, expected original
XBE bytes, conflicts, limits and cold-start requirements still apply. Unreviewed
enabled choices are blocked online and cannot create conflicts that disable an
otherwise eligible reviewed patch. Offline behavior is unchanged.

## Implementation boundary

`OnlinePatchRegistry.kt` owns the bundled approval records and the canonical
binary semantic fingerprint. Fingerprinting does not depend on JSONObject key
ordering, so it is stable across Android and the JVM.

`ModRun.write` branches before reading the legacy mod/trainer/cheat selections.
Protected sessions call patch preparation with no active built-in gameplay mods
or trainers. The protected serializer revalidates each admitted typed entry and
reconstructs only guarded `patch/default.xbe` lines. It ignores callback manifest
strings entirely: ETM, cheat/code, file replacement, refresh-rate and unchecked
patch commands cannot be smuggled through that callback. No generic allow-mods
flag is enabled. An empty reviewed payload produces no manifest argument.

The existing protected-session restrictions on memory tools and state loading
remain. Imported-patch sessions retain the 5.10 cold-start/state restrictions.
Built-in compatibility rules remain independent of trainer mode. Native binaries
and the native mod interpreter have not changed.

## Review requirements for future approvals

Review the exact manifest and operations against the identified game revision,
establish the compatibility problem and evidence that the fix addresses it, and
check that it does not provide trainer/gameplay advantages. Keep rendering fixes
under the same review: a label alone does not establish online suitability.
Record evidence/rationale in the approval, compute both fingerprints for the
exact file, add targeted tests, and distribute the registry change with the app.
Removing an approval disables that imported payload on future protected starts.

## Validation

Regression tests cover approved synthetic manifests, forbidden command injection
through callbacks, edited files/forged hash fields, game/build/revision mismatch,
revocation/duplicates, conflict/state restrictions and rejection of self-approval
fields. The tests also check that Halo's built-in Auto rule remains enabled while
the protected policy continues to block trainers, memory tools and state loads.
Device-level networking/Insignia validation remains pending.

Build/validation: Android APK assembly succeeded; **117 JVM tests passed, zero
failed and one optional shader-archive test skipped**. The signed final APK
package/versionCode 28 and the existing certificate were verified, and all 11
native libraries match the 5.10 APK byte-for-byte.

APK: `HakuX-Advanced-1.3.0-5.11-onlinecompat-arm64.apk`

SHA-256: `c3724ab1c5c3a3ef8db9af2570e78978ca6234187781cd0d60d7c59f427fd4cb`

Signing certificate SHA-256: `1037f9d3ba4617d3e75b3ce8cd02cd3968f58aefa025017523e843e78d277a81`

# Compatibility and current testing

## 5.20.1 Preview 1 — 5.2.h integration

5.2.g received a user-reported successful Rogue Trooper loading result. The
retained diagnostic run shows repeated disc reads completing and successful
Partition5 `Bank_en.xwb` writes returning `STATUS_PENDING` (`0x103`). This supports
an I/O completion-convention workaround; it does not establish an XMV decoder
fault or a general event-scheduler defect.

5.2.h removes the diagnostic dependency and exposes a per-game, per-filename
rule through the existing mod browser. This preview integrates that source
patch into Advanced 5.20 without the experimental trace patch. The generalized
5.2.h behavior and this exact APK still require a device test.

| Compatibility | Selection |
| --- | --- |
| Rogue Trooper sound-bank loading | Optional mod, title/revision 5343000E.1; off by default |
| Republic Commando pacing | Built-in 60 guest presents/sec for 4C410013, all revisions |
| Jade Empire Sagacious | Existing title defaults and per-game override |
| Halo pool placement | Existing Auto/On/Off profile; Auto matches 4D530004 revision 8 |

## Rogue Trooper test

1. Install this APK over Advanced; retain the existing installation/data.
2. Select offline mode in Advanced. Protected online sessions exclude these mods.
3. For Rogue Trooper revision 1, enable **Mods & cheats → Sound bank loading fix**.
4. Fully exit and cold start. Check that loading completes and sound/gameplay work.
5. Disable the mod and cold start for a comparison if useful. A completed sound
   cache may hide the original symptom; record whether the cache was already warm.

The option arms after a queued asynchronous disc read of the configured leaf
filename with a completion event. Matching successful asynchronous writes of
1–131072 bytes to that filename directly under Partition5 retain their normal
host write, IOSB completion and event/APC behavior, but return pending. Failed,
partial, synchronous, unrelated-title and unrelated-path operations retain their
normal results. This is a scoped compatibility convention, not general async I/O.

Another title can use the same opt-in mechanism via its own mod rule, documented
in mods/README.md. Save-state and online restrictions follow the existing mod
policy. Use cold starts for validation; the arming flag is not serialized.
No signing keys, build caches or diagnostic archives belong in the Git package.

## Build checks

The arm64 native build, Android assembly and existing host checks pass. JVM
results: 166 tests, zero failures/errors, one optional skip. The new native
fixture checks rule parsing, disabled defaults, title/path matching, arming and
reset behavior; it also passes address/undefined-behavior sanitizers. These
checks do not replace the pending Rogue Trooper device test.

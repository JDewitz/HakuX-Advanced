# Advanced5.9.1 — Halo CE revision 8 Auto rule

Version: `1.3.0-advanced5.9.1-halo8`, versionCode 26.
Update package and signing certificate are unchanged from Advanced5.8/5.9.

The user reported that Advanced5.9 runs well and its interface looks correct,
then supplied Halo's certificate title ID `4D530004` and revision `8`.
Together with the prior successful high-pool workaround report, this identifies
the first user-reported working revision. This is user-supplied identity and
device evidence, not independently verified logs or a full-campaign test.

Auto now requests `pool=high` only for `4D530004.8` on emulator base `1.3.0`
(including this Advanced build). Other revisions, titles, unknown/path identities
and different emulator bases stay Off. Explicit Off overrides the rule; On
continues to force the workaround regardless of identity. No native code changed.

Open Halo's Advanced settings → Game compatibility → High kernel pool placement,
choose **Auto**, retain retail **64 MiB**, fully exit and cold start. The summary
should read **Auto → On** and the launch log should name `halo-ce-high-pool-v1`.
Saved explicit On/Off choices are retained; this update does not change them to
Auto. A loaded emulator state keeps its recorded pool layout, so test from a
fresh start. Repeat cold boots and post-fix logs remain useful follow-up evidence.

Regression coverage checks that the shipped catalog enables revision 8 only,
rejects revision 7/9 and other titles/builds, respects Off, and emits `pool=high`
through the real startup-profile arguments. Existing migration and matching
checks remain in place. Native libraries are reused from the tested 5.8 APK.

Validation: Android compilation and JVM tests passed: 99 passed, zero failures,
one optional shader-archive skip. Native code and libraries are unchanged.

Final APK signature, package and versionCode 26 verified; all 11 native libraries
are byte-for-byte identical to the tested 5.8/5.9 native core.

APK: `HakuX-Advanced-1.3.0-5.9.1-halo8-arm64.apk`

SHA-256: `e4bf7a0b6ce4550ced0c5180f69ca7a0f1cbc03becafec267c971f5ec64da7a2`

Signing certificate SHA-256: `1037f9d3ba4617d3e75b3ce8cd02cd3968f58aefa025017523e843e78d277a81`

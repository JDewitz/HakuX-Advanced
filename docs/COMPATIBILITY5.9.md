# Advanced5.9 — game compatibility profiles

Version: `1.3.0-advanced5.9-compat`, versionCode 25.
Update package: `com.rfandango.haku_x.themes`. Use the existing signing key.

## Use

Open a game's Advanced settings, then **Game compatibility → High kernel
pool placement**. The same choice is available from the in-game Advanced menu.

| Choice | Next fresh launch |
| --- | --- |
| Auto | Apply only an approved bundled rule matching certificate title ID, exact revision and emulator base version. Currently Off for every game. |
| On | Force high initial pool placement for this startup profile. This is the existing Halo experiment. |
| Off | Use the normal initial pool placement, even if a future automatic rule matches. |

The screen shows the chosen mode, resolved On/Off result and reason. The in-game
window distinguishes the session's startup request from the saved next-start
choice. Editing does not change the running core. Fully exit and start again;
an emulator save state retains its recorded allocation layout and can supersede
the startup request. Use cold starts for comparisons.

Halo CE (`4D530004`) is the first catalog entry. Its automatic rule is disabled
and its approved revision set is empty. There is one successful user report for
the original workaround, without enough revision-specific logs or repeat tests
to approve Auto. Existing bundled Halo frame-rate mods refer to revision 9;
that does not establish that revision 9 was tested with this workaround.

For Halo testing: use retail **64 MiB**, choose **On**, cold boot, start a new
game and test past the intro movie into the bay. Compare against **Off** and
collect both logs. **Auto** should currently behave like Off. Record the title
ID/revision, device, graphics driver and whether each run was a cold boot.
Do not interpret one successful load as campaign-wide validation.

## Migration and inheritance

`advanced.json` startup profiles now store `poolMode`: `AUTO`, `ON` or `OFF`.
Legacy `poolHigh: true` becomes On; legacy false becomes Off; missing settings
become Auto. A malformed new mode fails closed to Off and never falls back to
a legacy true value. Saving the profile writes the new field and preserves the
other startup choices. Migration happens on read; no destructive bulk rewrite.

Profiles keep their existing snapshot behavior. A game without its own startup
profile follows the global profile. Editing a game creates a snapshot; later
global changes do not alter it. **Use global startup profile** removes the game
snapshot, including its compatibility choice. A global On deliberately forces
this experiment for games inheriting that profile; per-game On is recommended
for testing Halo.

## Implementation and extending the catalog

`CompatibilityProfiles.kt` is the pure resolver and bundled rule catalog.
Identity comes from the existing XBE-certificate-derived game key, never a
filename, trainer name or folder name. Path-only/invalid identities cannot
activate an automatic rule. Rules require exact title ID, an explicit revision
allowlist and the exact base emulator version. Optional XBE hash constraints
fail closed if the launch path cannot supply the hash; this release supplies
certificate ID/version only. Multiple matching automatic rules also fail closed.

Each rule records a stable ID, reason, evidence, emulator base, restart
requirement and online policy. A new pool-layout approval needs verified game
identity, repeatable OFF/ON results, post-fix logs and regression checks. Populate
the approved revisions and enable `automatic` in a reviewable source change.
Do not approve every revision merely because a title ID matches. The catalog
currently handles one pool-layout workaround; future workaround types need
their own resolver/application path. This is not a downloaded patch-pack loader.

Native startup receives exactly one `pool=high` or `pool=default` argument.
The Android log records game key, app build, matched rule (if any), mode, result
and reason. Benchmark launch metadata now includes `pool=`. Native allocation
behavior, state compatibility and the Halo fix itself are unchanged from 5.8.

The pool-layout experiment is allowed in protected online sessions because it
changes initial allocation placement rather than injecting guest cheats. It
does not relax online trainer/mod/memory-tool or state-load restrictions. Future
patch types need their own explicit online policy; do not assume this permission
applies to executable patches.

## Validation

Regression coverage includes shipped Auto remaining disabled; exact identity,
revision/build/hash matching; unknown identities; conflicting rules; explicit
override priority; legacy migration; persistence; and global/game inheritance.
The native pool-placement and Advanced/fast-forward host checks remain required.
Android device UI navigation, upgrades with real user settings and repeat Halo
OFF/ON cold boots still require tester validation.

Android compilation and JVM tests passed for source `9fdfc6c`: 99 passed,
zero failures, one optional shader-archive skip. Native pool/Advanced/fast-forward
checks passed locally and on GitHub. Actions run 41491779396 passed both jobs.
CI now compiles Android UI and runs JVM tests on future pushes without signing
secrets. No upstream message or PR has been sent as part of this update.

APK packaging and signature verification passed. All 11 packaged native libraries
are byte-for-byte identical to Advanced5.8. Package/version and the existing
signing certificate were checked on the final APK, not only in build settings.

Artifact: `HakuX-Advanced-1.3.0-5.9-compat-arm64.apk`

SHA-256: `1789fabefa54414b619f1b646ad0d03c044eff22070a8e827e3ffcf0ce972645`

Signing certificate SHA-256: `1037f9d3ba4617d3e75b3ce8cd02cd3968f58aefa025017523e843e78d277a81`

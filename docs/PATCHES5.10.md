# Advanced5.10 — importable game patches

Historical 5.10 online restriction below is superseded by ONLINE5.11.md: reviewed
compatibility imports have a dedicated protected-online path; the release
registry currently has no imported game-fix approvals.

Version `1.3.0-advanced5.10-patches`, versionCode 27. Same update package
`com.rfandango.haku_x.themes` and existing signing certificate. Native libraries
are unchanged from 5.8/5.9/5.9.1. Halo revision 8 Auto behavior is retained.

## Use

Open **Settings → Game patches**, or long-press a game → **Mods & cheats →
Game patches**. Import one or multiple `.hakuxpatch` JSON files, or select a
folder. Select the exact game revision and a patch, then use its **Enabled for
this game** toggle. The catalog scrolls independently from the details panel;
portrait switches between list and details. Search and Show incompatible patches
help inspect a larger collection.

Each toggle is remembered by certificate title ID/revision and the patch's full
SHA-256 content hash. A replacement file/version does not inherit enablement.
Imports retain an internal copy. Folder patches are rescanned when the browser
resumes, on Refresh, and before every fresh launch. Missing/unreadable folder
sources remain unavailable even when a cached copy exists. Unlinking a folder
does not remove separately imported copies or stored choices. Scans are bounded;
this release does not continuously watch providers in the background.

In-game **Advanced → Imported game patches** shows what was loaded in that
session and lets you change next-launch toggles. A changed toggle or folder
cannot change the running native manifest. Imported content selections are
pinned across guest XBE relaunches; a cold start reads the latest choices.
The library's gear badge also indicates configured patch choices.

**Loaded at launch** means the guarded operations entered the native manifest,
not proof of every native write. The log confirms individual applications or
rejections. The browser distinguishes Off, Enabled for next cold launch,
Incompatible, Conflicting, Unavailable and Blocked by online protection.

## First-release limits

- Protected online sessions receive no imported patch manifest. All imported
  patches are offline-only; there is no manifest field that can bypass this.
- This format patches file-backed sections of the disc's `default.xbe` only.
  XBE headers, other executables, title updates, replacement files, runtime
  memory scripts, native GPU fixes and arbitrary executable plugins are not
  supported. Detected installed title updates block imported patches.
- Every operation requires matching original bytes and equal-length replacement
  bytes. All operations in a pack are checked against the disc before admission;
  the existing native loader checks each operation again. If the source changes
  during launch, native checks can reject individual operations. Pack-wide atomic
  execution is not promised. The original disc file is never written.
- Overlapping imported operations, duplicate enabled IDs, declared conflicts,
  enabled built-in mods writing the same ranges/replacing default.xbe, and an
  active trainer block the affected imports. There is no last-writer-wins override.
- Imported-patch sessions use cold starts only. Saving/loading emulator states is
  blocked while imported operations are loaded. A launch directly into a state
  does not load imported patches. Full patch-aware state fingerprints are future
  work; this limit prevents producing silently incompatible patched states.
- UI navigation, provider behavior and actual game effects need Android device
  tests. No Project Snowblind fix is included or claimed.

## Format 1

```json
{
  "schema": 1,
  "id": "author.example-fix",
  "name": "Example guarded patch",
  "version": "1.0",
  "author": "Author",
  "description": "Purpose, evidence and restart instructions",
  "titleId": "FFFF0001",
  "revisions": [1],
  "emulatorBase": "1.3.0",
  "conflicts": ["author.alternative-fix"],
  "patches": [
    {
      "xbe": "default.xbe",
      "address": "0x11000",
      "expected": "01 02 03 04",
      "replacement": "04 03 02 01"
    }
  ]
}
```

This example uses a synthetic identity, not a real game fix. IDs use lowercase
letters/digits/dot/underscore/hyphen, at most 64 characters. Required metadata
must be nonempty; unknown fields/schema versions are rejected. Title IDs are
eight hexadecimal digits; revisions are explicit unsigned 32-bit integers.
Addresses are 32-bit `0x` hex strings, and bytes are space-separated hex pairs.
`conflicts` is optional; all other fields shown are required. The only supported
emulator base is currently 1.3.0, including Advanced builds of that core.

Limits: 256 KiB per file, 1–256 writes per pack, 1–256 bytes per write, at most
32 enabled packs/256 writes admitted per launch, 512 imported catalog entries,
and folder scans up to 8 nested levels/2048 entries. Overlapping writes inside
a pack, overflowing addresses, malformed UTF-8 and unsafe executable paths are
rejected. Cache files are hash-checked again before use.

`examples/patches/halo8-guard-probe.hakuxpatch` is a harmless no-op probe for
testing import, toggles and byte rejection with Halo revision 8. Expected and
replacement bytes are identical. It is not a Halo fix; normally preflight rejects
its synthetic bytes. Keep it disabled for ordinary gameplay. The independent
Halo pool-placement compatibility setting is unchanged.

## Validation and handoff

Unit coverage includes identity/build matching, malformed/unsafe imports, size
limits, persistent choices, changed-content versions, concurrent writers,
online/state/trainer blocks, declared/range/built-in conflicts and exact byte
guards. A synthetic XISO/XBE exercises the real certificate/section reader and
checks that the source image remains unchanged. Real patch effects still need
game/device testing.

Android compilation/APK assembly passed. JVM tests: **109 passed, zero failures,
one optional shader-archive skip**. Final APK signature, package/versionCode 27
and all 11 unchanged native libraries were verified. Device UI and game-effect
validation remain pending.

APK: `HakuX-Advanced-1.3.0-5.10-patches-arm64.apk`

SHA-256: `5c11b39c893cc36190291138f02fda71c1ca17ad37ed33200a63e4ed0eb95012`

Signing certificate SHA-256: `1037f9d3ba4617d3e75b3ce8cd02cd3968f58aefa025017523e843e78d277a81`

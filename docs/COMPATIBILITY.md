# Compatibility

| Workaround | Selection |
| --- | --- |
| Rogue Trooper sound-bank loading | Optional mod for 5343000E.1; off by default |
| Republic Commando pacing | Built-in 60 guest presents/sec for 4C410013, all revisions |
| Jade Empire Sagacious | Existing title defaults and per-game override |
| Halo pool placement | Existing Auto/On/Off; Auto matches 4D530004 revision 8 |

## Rogue Trooper — 5.21

Select Offline mode, enable **Mods & cheats → Sound bank loading fix**, then
fully exit and cold start. Protected online sessions exclude this mod.

The selected rule arms after a queued asynchronous disc read of its filename
with a completion event. Successful asynchronous writes of 1–131072 bytes to
that filename at the root of cache partitions 3, 4 or 5 retain their normal
host write and IOSB/event/APC completion, but return pending (0x103).
Failed/partial writes, synchronous operations, system/data partitions, nested
paths, other filenames and mismatched titles retain their normal behavior.

A new game can opt into the same mechanism through its own mod rule; see
[mod format](../mods/README.md). Existing mod save-state restrictions apply.
Use cold starts for validation: the arming flag is not serialized.

## Assessment and testing

The successful 5.2.g diagnostic run used Partition5. The first regular preview
failed with the mod enabled and offline mode selected because that console
used Partition4; the carried-over .h guard accepted only Partition5.
Preview 2 accepted all three Xbox cache partitions and received a successful
user test on 2026-11-21. This supports a scoped I/O completion workaround; it
does not establish a universal XMV decoder or event-scheduler fix.

Release 5.21 promotes that implementation and reuses its exact native binaries.
The new guard tests cover all cache partitions, rejected system/data and nested
paths, malformed rules, title matching, arming and resets. ASan/UBSan and the
existing host checks passed for Preview 2. Current Android/updater test results
and binary identity checks are in the release kit's VALIDATION.json.

Current online compatibility exception: see [ONLINE5.11.md](ONLINE5.11.md).

Save-state policy update: Advanced5.13 permits loads with online protection On.
Any blanket online state-load restriction below is historical; see STATES5.13.md.
Trainers, cheats and gameplay mods remain blocked in protected sessions.

# Advanced5.7 — online play protection

User requirement: trainers must never run during Xbox online play. This build
uses mutually exclusive sessions instead of attempting to detect a match after
injected code is already present.

## Controls and behavior

Settings → Advanced & experimental → Online play protection. Global, default ON,
including upgraded installations. It is not a per-game override. The in-game
Advanced menu also offers the next-session choice and shows the pinned current
mode. Its change cannot alter the running session.

| Session | Xbox networking | Trainer/cheat/guest-mod injection | State loads / memory tools |
| --- | --- | --- | --- |
| Protection ON | Follows configured Off, Insignia/NAT, LAN or Kai | Blocked, even when networking happens to be Off | Blocked |
| OFFLINE trainer mode | Forced net=off, regardless of configured mode | Available as previously configured | Available |

Fully exit to the library and start the game fresh after changing modes. Trainer
flags/options remain saved; their *effective* state is blocked online. The
browser shows BLOCKED and disables activation/options while protection is on.
The game library does not show the enabled-trainer wrench in this mode;
detailed views identify the blocked trainer. Mods/cheat settings explain the
restriction. Network settings explain the offline override.

Guest mods are blocked with trainers because the shared mod manifest supports
arbitrary guest executable patches/file replacement. Keeping that injection path
available would bypass the trainer-only block. Theme colors, postprocessing
shaders and presentation filters are not guest-code injection and remain usable.
Future compatibility packs will need an explicit reviewed online policy.

Every state load is blocked in protected sessions: library loads, pause-browser
loads, quick-load/debug state requests and direct startup state extras. Untagged
states can contain injected code, so only disabling current trainer choices is
insufficient. A direct core start requesting a blocked state strips the request
and starts clean with a notice; the library load action refuses it. Saving
states remains available; using them requires an offline session.

## Enforcement boundary

OnlinePlayPolicy is an immutable launch snapshot. It is propagated through
RelaunchActivity for title XBE changes, restarts and state-load restarts. Changing
the setting during an injected offline session cannot enable its network. A
new NativeCoreActivity session in an already-used core process is redirected
through the existing restart coordinator and that old process is terminated
before another VM boots, preventing process-global native cheat/manifest state
from leaking between sessions. A genuine new cold launch uses the current
setting. Offline state restoration has no host NIC backend to restore.

Protected launches do not produce/pass a guest-mod manifest at all; ModRun.write
also rejects a protected policy before any trainer/mod IO. The in-game cheat
setter and memory-command/poke UI are gated by the pinned session. The network
arguments and LAN multicast lock use that same snapshot, not a live setting.
Normal Online play protection ON is independent of whether the phone has Wi-Fi
or an Insignia login is currently established; the session is protected before
any connection can happen.

The native NC_NET environment override is evaluated after ordinary CLI options.
An offline request therefore checks inherited NC_NET and debug.hakux.env/env2
using Android getprop. Any override, or failure to verify those properties,
falls back to a protected session with injection/state loads blocked and a
notice. The property reads are bounded by a timeout. Existing debug memscan
values are baselined by the native core; the app does not queue memory tools
online. Deliberate adb/root/native-code tampering is not remote attestation and
cannot be secured by an application preference; server anti-cheat remains a
separate matter. Likewise, this does not undo modifications already written to
user-owned game binaries or saves outside the managed injection pipeline.

No new native feature/ABI is required: the app enforces the existing net=off,
clean-process, no-manifest and no-state-load startup boundaries. Native libraries
are reused and compared to Advanced5.6. This is not an update to a later unseen
rfandango build; it is based on the Advanced5.6/official-1.3.0-derived source,
which already includes Insignia/NAT networking. Upstream integration must retain
these boundaries if it introduces other launch/network/trainer paths.

## Required device test matrix

1. Update-install; verify protection defaults ON even with a previously enabled
   trainer. Check Advanced globally and from the in-game menu.
2. In protected mode, start a game with ETM, built-in cheats and guest mods saved
   as enabled. Confirm no trainer effects; inspect launch log for protected mode
   and absence of mods=, then verify Insignia/LAN/Kai as appropriate.
3. Try library/pause/quick/debug state loads. None may load protected memory.
   Try the Advanced memory tools; commands must be refused.
4. Select OFFLINE mode and cold launch with network configured as Insignia, LAN
   and Kai in turn. Trainers should work, but the emulated NIC must have no link.
   Loading an old state must not create a host network backend.
5. Switch the *next* mode from inside a running offline trainer session. Resume
   and trigger an XBE relaunch: it must remain offline. Exit to library and
   cold-launch online: trainer effects must be absent.
6. Repeat the reverse transition and Android activity recreation/process reuse.
   Check process-restart behavior and no retained trainer/cheat state.
7. Test native NC_NET/debug overrides: an offline trainer launch must fall back
   to protected mode and refuse injections. Remove overrides to test offline.

No Android hardware or Insignia account/session was attached for these tests.
Compilation and unit tests do not verify real NIC behavior, JNI lifecycle or
actual ETM effects; device testing is required before distributing this as an
online fairness guarantee.

## Handoff

Version 1.3.0-advanced5.7-online, code 23. Same Themes Test package and signing
certificate. Source patch targets Advanced5.6 (9e435c0). Six policy tests cover
legacy/invalid defaults, online prohibition, all-network offline override,
mutual exclusion, parent-mode pinning and native debug overrides/failure cases.
Use the existing Java21 / SDK36 frontend build instructions; the private signing
key is kept separately. No messages or files were sent to the developer.

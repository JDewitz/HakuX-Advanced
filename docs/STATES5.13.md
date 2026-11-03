# Advanced5.13 — allow save-state loading with online protection

Version `1.3.0-advanced5.13-stateload`, versionCode 30.

Online play protection no longer disables save-state loading. Library slot
loads, pause-browser loads, mapped quick loads, debug requests and state
relaunches keep the selected state instead of rejecting it or cold-starting.
The Advanced menus now describe this behavior. The existing session mode stays
pinned through relaunch; loading a state does not turn protection off.

Trainer/cheat/gameplay-mod injection, memory tools and unreviewed imported
patches remain blocked in protected sessions. Offline trainer sessions still
force networking off. Halo's built-in compatibility Auto rule is unchanged.
Imported patches still require cold starts; their separate save/load restriction
is unchanged. Native snapshot validation and game/core compatibility checks
remain in place.

This removes a blanket permission restriction, not a native snapshot-format or
network-state change. Restoring a snapshot does not restore a remote server
connection or guarantee continuation of a live multiplayer match. Existing
snapshot RAM is not inspected or scrubbed for earlier trainer effects; use
trainer-free snapshots for protected online testing.

The native libraries, signing certificate and Android application ID are
unchanged. Device verification should cover library and pause loads with online
protection On, mapped quick load, and protection remaining On after relaunch.

# hakuX Advanced roadmap

Updated 2026-11-01. Working fork based on official hakuX 1.3.0.
This is a proposed development order, not a dated release commitment or a claim
that rfandango has adopted these changes upstream.

## Current baseline

| Feature | Status / limits |
| --- | --- |
| Official 1.3.0 integration | Current fork baseline; test separately from mainline |
| Mappable fast forward | Implemented; small >> OSD; toast removed; FF frame-skip switch removed |
| Advanced feature options | Implemented; experimental timing/render controls still need per-game testing |
| Save state browser | Implemented with dated slots/screenshots; cross-build compatibility is not promised |
| RetroArch shader browser/download | Implemented experimental compatible shader subset; arbitrary desktop shaders are not guaranteed |
| evoX ETM trainers | Implemented experimental importer/runtime; game revision and option compatibility must be checked |
| Shared themes | Implemented across app/pause screens; eight built-ins plus persistent imported themes; multi-select JSON import in Advanced5.4 |
| Theme signing backup and handoff | Existing private backup is separate from public source packages |

## This update: Advanced5.3 library

Implemented in source and built for testing; Android device validation pending.
Choose the library menu icon beside Refresh to select a persistent layout:

| View | Behavior |
| --- | --- |
| Cards | Existing compact card layout |
| List | One compact row per game, with format and known file size |
| Stats | Library totals plus per-game metadata, storage, trainer/config status |
| Info / Play cards | Detailed cards with Play and Options buttons |
| Icons | Dense icon grid with game titles |

All layouts retain search, normal launch, long-press options and controller
Y/Menu options. Layout selection survives restarting the app. Icons use existing
disc icons/fallback artwork; no cover art is downloaded by this change.
The Stats summary follows the visible, filtered, deduplicated library. Storage
is the sum of positive known image sizes for those entries, not total disk use.
It does not yet track playtime, sessions, FPS, or achievement completion.

A wrench over the game icon means an imported ETM trainer is enabled for the
next fresh launch. This is configuration state, not verification that a patch
is running or compatible. Disabled/imported-only trainers do not show it.
A gear means saved per-game settings, an advanced or shader override (including
an explicit Off override), or enabled built-in mods/cheats. Global defaults
alone do not mark every game. Both badges can appear together.
Badges refresh on return to the library and on rescan. Invalid trainer imports
are reported in detailed views and do not appear as enabled trainers.

## Advanced5.4: persistent theme library

Multi-select JSON import stores each valid custom palette in the app. Imported
themes stay in the drop-down across preset changes and restarts. The previous
active custom selection is migrated. Exact duplicates are skipped; different
same-name palettes remain separate with numbered imported labels. A bad file
does not block the rest. Single import selects the theme; batch import preserves
the current selection. ZIPs must be extracted before selecting JSON files.

## Next priorities

| Order | Work | Acceptance target |
| --- | --- | --- |
| 1 | Device/controller QA of library modes and themes | Touch, D-pad, portrait/landscape, long names, empty/large libraries; no unintended launch from Options |
| 2 | Real play statistics | Persist per-game session count, last played and playtime; define pause/background/crash behavior first |
| 3 | Library polish | Sorting/filtering by trainer/custom setup, favorites, optional user-selected cover art and richer info |
| 4 | Per-game input profiles | Named profiles; Bluetooth/USB keyboard buttons and gamepad axes; deadzones, invert, clear conflicts, import/export |
| 5 | Steel Battalion accessory prototype | Native guest device emulation, then host mappings and an optional touch panel; separate experimental milestone |
| Research | RetroAchievements | Coordinate Xbox support and maintenance with RA/upstream before promising online achievements |

Continue compatibility, shader and ETM testing alongside these priorities.
Keep each native experiment separate from frontend-only changes for diagnosis.

## Input profiles and Steel Battalion

Keyboard/gamepad-to-standard-Xbox-pad mapping is feasible near-term. A
per-game profile editor should provide bind capture, axis thresholds, analog
calibration, momentary/toggle actions, multiple host inputs, a preview and safe
reset on disconnect/focus loss. Test simultaneous keys and hotkey collisions.

Steel Battalion also needs its special controller presented to the guest.
The current native XID implementation exposes a standard Duke-style gamepad
with a 20-byte input report. Adding host keys alone does not emulate the
Steel Battalion device. The native work includes its USB/XID identity,
descriptors and input/output reports, pedals, sticks, dials, switches and gear
state, plus game-level tests. Start with one supported game/revision and a
known protocol capture or fixture. Bluetooth keyboard digital controls can
provide access, but analog sticks/pedals need usable axis mapping or ramps.
No Steel Battalion support is implemented in this update.

## RetroAchievements feasibility

Technically possible, but currently a coordinated platform integration project,
not a login switch. No ready-to-enable Xbox/hakuX support was verified in the
official sources reviewed. The official Xbox-support discussion remains a
relevant starting point; status must be rechecked before implementation.

An initial spike should establish an agreed game/revision identification
scheme, safe guest-memory reads and per-frame rcheevos evaluation. Production
work also needs authentication/token handling, achievement UI, offline queueing,
state serialization, authoring/debugging support, actual achievement sets and
an agreed maintenance owner. RA platform/content acceptance is an external
dependency. Hardcore additionally requires enforcing the current RA rules,
including blocking trainer/cheat use and state loads; do not label a prototype
Hardcore-compliant without review. Fast-forward policy must be assessed against
the then-current requirements rather than assumed forbidden.

Primary references checked 2026-10-31:
- https://github.com/RetroAchievements/rcheevos/wiki/rc_client-integration
- https://retroachievements.org/forums/topic/29989
- https://docs.retroachievements.org/general/hardcore-compliance-requirements.html
- https://xemu.app/docs/controller/ (comparison only; local hakuX source determines its own device support)

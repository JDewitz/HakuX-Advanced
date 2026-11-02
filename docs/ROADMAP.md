# HakuX-Advanced roadmap

Updated 2026-11-02. Working fork based on official hakuX 1.3.0.
This is a proposed development order, not a dated release commitment or a claim
that rfandango has adopted these changes upstream.

## Current milestone: Advanced5.8

The opt-in Halo high initial kernel pool placement workaround is built and has
one successful user report. Native/Android builds and automated checks passed;
RH repeatability testing and post-fix logs are pending. The standalone native
patch series and report are ready for upstream review; nothing has been sent.

The project is now named HakuX-Advanced. Private GitHub sync is complete at
https://github.com/JDewitz/HakuX-Advanced, with `main` as the default branch,
upstream/fork ancestry and 17 Advanced tags preserved, and host checks passing
for the prepared source head. See GITHUB.md for the sync record.

## Next: game compatibility profiles

Implement per-workaround Auto / On / Off settings with user overrides taking
precedence. Match XBE title ID plus verified revision/hash constraints; never
filename substrings. Record provenance, reason, evidence, emulator version,
restart requirement and online-play policy. Expose the effective choice in
settings and logs. Keep the current Halo rule opt-in until RH checks support
an automatic rule. These profiles are separate from ETM trainers and cannot
bypass online protection. Additional pluggable game patches follow this work.
Steel Battalion input/controller work and RetroAchievements remain later items.

## Current baseline

| Feature | Status / limits |
| --- | --- |
| Official 1.3.0 integration | Current fork baseline; test separately from mainline |
| Mappable fast forward | Implemented; small >> OSD; toast removed; FF frame-skip switch removed |
| Advanced feature options | Implemented; experimental timing/render controls still need per-game testing |
| Save state browser | Implemented with dated slots/screenshots; cross-build compatibility is not promised |
| RetroArch shader browser/download | Implemented experimental compatible shader subset; arbitrary desktop shaders are not guaranteed |
| evoX ETM trainers | Implemented experimental importer/runtime; game revision and option compatibility must be checked |
| Shared themes | Implemented across app/pause screens; eight built-ins plus persistent imported themes; multi-select import in Advanced5.4 and live scroll/focus preview in Advanced5.5 |
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

## Advanced5.5: live theme preview

Built and unit-tested: scrolling/focusing previews theme colors in memory.
Tap commits; Back/dismiss/leaving settings restores the saved selection.
Imported themes remain available. Android gesture/focus testing is pending.

## Advanced5.7: online play protection

Global Advanced toggle, default ON. Protected sessions block all trainer,
cheat and guest-mod injection, save-state loading and memory tools before any
online connection. Turning it off selects an offline trainer session with all
Xbox networking forced off. The immutable mode is pinned across XBE/state
relaunches; changing the next-session preference cannot network-enable a running
trainer session. Full cold exit/launch is required to switch. Saved trainer
choices remain intact but show as blocked online. Native NC_NET debug overrides
or failure to check them force a protected fallback. Reusing an old native
process is redirected through a clean process restart. This is implemented in
the app launch/injection/load boundaries; real-device Insignia and lifecycle
validation is required before relying on it. See ONLINE5.7.md for the full matrix.

## Next priorities

| Order | Work | Acceptance target |
| --- | --- | --- |
| 1 | Trainer browser — Advanced5.6 device testing | Folder scan, searchable two-pane UI, embedded-ID filtering, remembered trainer alternatives and next-launch options |
| 2 | Pluggable compatibility/mod packs | User-selected patch folder; game/revision matching, validation, toggles, conflict reporting, reversible launch-time application |
| 3 | Halo regression investigation | Reproduce reported 1.2.0 → 1.3.0 regression on the same disc/update/device/settings; isolate cause, then deliver a verified workaround pack or core fix |
| 4 | Project Snowblind graphics investigation | Capture reproducible rendering issues; test existing quirks first, add a narrow renderer fix if required; publish a pack only after validation |
| Alongside | Device/controller QA | Themes/live preview, library modes, save browser, shaders and ETM; touch/D-pad, portrait/landscape, large libraries |
| 5 | Real play statistics | Persist per-game session count, last played and playtime; define pause/background/crash behavior first |
| 6 | Library polish | Sorting/filtering by trainer/custom setup, favorites, optional user-selected cover art and richer info |
| 7 | Per-game input profiles | Named profiles; Bluetooth/USB keyboard buttons and gamepad axes; deadzones, invert, conflicts, import/export |
| 8 | Steel Battalion accessory prototype | Native guest device emulation, then host mappings and optional touch panel; separate experimental milestone |
| Research | RetroAchievements | Coordinate Xbox support and maintenance with RA/upstream before promising online achievements |

Continue compatibility, shader and ETM testing alongside these priorities.
This is a proposed fork roadmap, not a dated commitment or upstream agreement.

## Pluggable game patches and compatibility profiles — proposed

Goal: install/share game fixes and mods without rebuilding the APK or editing
original game images. Extend the existing mods pipeline instead of introducing
a second unrelated XBE/file-overlay engine. Today mods/ is packaged as assets;
external discovery and the manifest/profile manager are not implemented.

Proposed logical folder layout (Android folder selection via the system picker):

    patches/<TITLEID>.<VERSION>/<patch-id>/manifest.json
    patches/<TITLEID>.<VERSION>/<patch-id>/files/...
    profiles/<profile-id>.json
    trainers/<user-organized folders>/*.etm

The earlier BIOS-folder suggestion is superseded by a trainer folder/browser.
Compatibility profiles configure current CPU/GPU settings; no external BIOS
boot feature is planned in this feature set.

Matching and lifecycle:
- Match disc/XBE title ID and certificate version; refine by region, executable
  hash and installed title-update hash/version when a pack depends on them.
  A title ID or filename alone is insufficient for revision-specific writes.
- Manifest records pack ID/version, author, purpose, tested emulator versions,
  game identities, optional GPU/driver scope, dependencies and conflicts.
- Refresh the folder catalog on app resume, explicit Refresh and before launch;
  use change notifications where the Android document provider supports them.
  Freeze the resolved pack list for each running session; changes take the next
  fresh launch. This does not promise background monitoring for every provider.
- Show matched, enabled, applied, skipped and incompatible states separately.
  Add a library badge/details list and a launch log of effective packs/profiles.

First supported payloads should be constrained data: existing per-game settings
and exposed renderer quirks, expected-byte-checked XBE changes, and file
replacements/overlays. Keep compatibility fixes, optional mods and trainers
separate in the UI. Reuse existing title-update patch support. No arbitrary
native plugins or downloaded executable code are required for this first stage.

Validate expected bytes/hashes before applying. Report overlapping writes,
file replacements and contradictory profile settings; do not silently inherit
the bundled mods' current later-folder-wins behavior for compatibility packs.
Keep original images unchanged; toggling a pack off reverses its effect on the
next launch. Save states should record the active pack/profile fingerprint and
warn or refuse when a load is incompatible. Diagnostic exports should include
pack versions/hashes, effective settings and emulator/device information.

Halo and Snowblind are initial investigation targets, not generated fixes.
The Halo regression is user-reported and has not been reproduced here. Start
with an official 1.2.0/1.3.0 comparison using the same disc/update and settings,
then bisect relevant changes. For Snowblind, obtain a reproducible scene and
renderer capture/logs, then test depth/shader/state quirks against evidence.
A data pack can activate an existing workaround or patch guest code; it cannot
replace missing/broken native renderer or kernel behavior on its own. If a new
core hook is needed, ship that fix first and let the manifest target supporting
versions. Prefer a general core correction when the defect affects many games.

Acceptance for the loader: matched/wrong revisions, title updates, conflicting
packs, changed folder contents, invalid manifests, toggle-off rollback, original
image integrity and state fingerprint handling. Fix-specific validation must
show the affected scenes improving without regressing normal gameplay.

## EvoX-style trainer browser — Advanced5.6 test build

Implemented for testing: a user-selected trainer folder, recursive scanning on
browser resume/Refresh, and debounced provider notifications where supported.
Multiple individual file imports are also supported. Continuous background
monitoring and a full pre-launch rescan remain follow-ups.

Provide a dedicated two-pane Trainers window: searchable game/trainer list on
the left; trainer name, notes, source filename, compatibility details, enable
switch and individual option toggles on the right. Open it globally or from a
game's context menu with that game's matches prefiltered. Long lists use a
browser, not an unbounded drop-down. A small selector inside the details panel
can choose between multiple trainers for the same game.

Display the ETM's internal trainer name and labels when available, retaining the
filename as secondary information. Read embedded title IDs for matching; do not
activate a trainer solely because its filename resembles a game. ETM metadata
does not reliably establish exact region/revision compatibility: surface this
as unverified unless a separate manifest supplies a tested match.

Remember option/enabled choices by game revision and trainer content hash so
switching trainers retains each one's choices. Start with one active ETM per
game; list alternatives without stacking their executable patches. Preserve the
currently imported trainer during migration. Multiple-trainer execution is not
assumed compatible. Index new/changed files by content, avoid duplicate entries,
report invalid/missing files and retain user choices for temporarily offline
storage. Snapshot/copy the chosen binary for launch so a folder edit cannot
change a running session's trainer.

Use the existing ETM runtime. Switches configure the next fresh launch. Arbitrary
EvoX injected code cannot be assumed reversible at runtime: turning an option
off or loading a prior state does not guarantee earlier injected patches are
removed. The UI should label configured, matched and applied states distinctly.
The library wrench indicates configured next-launch state; the game context
menu opens the browser. A direct clickable badge is a follow-up. Add trainer identity/options to
state compatibility fingerprints alongside patch profiles.

Advanced5.6 supplies this browser, folder catalog and remembered alternatives;
selected binaries remain compatible with the existing ETM runtime. The currently
imported ETM remains visible for its game. Device UI and actual trainer effects
need testing. Save-state pack/trainer fingerprints are a later follow-up.
See TRAINERS5.6.md for entry points, behavior and test instructions.

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

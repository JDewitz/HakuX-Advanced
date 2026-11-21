# HakuX-Advanced

An unofficial Android arm64 fork of [rfandango/hakuX](https://github.com/rfandango/hakuX).
AI was a significant factor in the development of this project.
Original authors, contributors and dependency licenses retain their attribution.

## Current test build

**5.20.1 Preview 2**, integrating the 5.2.h cache-write compatibility work into
Advanced 5.20. Android version: `1.3.0-advanced5.20.1-preview.2-cache-write`,
versionCode **44**. Device testing is pending; this is not a published release.

Install the signed arm64 APK over Advanced. The separately installed 5.2.h
experimental app is a different package and keeps separate data/settings.

For Rogue Trooper revision 1 (`5343000E.1`), select offline mode and enable
**Mods & cheats → Sound bank loading fix**, then cold start. It is off by default.
See [compatibility and test notes](docs/COMPATIBILITY.md).

## Features

- Advanced rendering and Slang shaders, shader browsing and pack management.
- Mappable emulator hotkeys, performance HUD and screenshot save-state browser.
- Shared importable themes, game-library layouts, trainer indexing and game mods.
- Per-game compatibility profiles and protected online mode with trainer blocking.

[Changelog](CHANGELOG.md) · [Compatibility](docs/COMPATIBILITY.md) ·
[Mod format](mods/README.md) ·
[Rendering reference](docs/EXPERIMENTAL5.2.md)

Test feedback: device/driver, game revision, option on/off and result.
Public release/update packages will be prepared after APK testing.

# HakuX-Advanced

AI Use Discolosure: AI was used heavily in this project.

HakuX-Advanced is an unofficial experimental fork of
[rfandango/hakuX](https://github.com/rfandango/hakuX), an original Xbox emulator
for arm64 Android. The original authors and dependency contributors retain
all attribution and licenses. This project is not an official hakuX release.

The current source is **1.3.0-advanced5.11-onlinecompat** (versionCode **28**), built on
**official hakuX 1.3.0**. It retains the fork's history from the original 1.2.0
base and the subsequent 1.3.0 integration. The default development branch is
`main`; `upstream` tracks rfandango/hakuX. The repository starts private.

## Current features

- Mappable fast forward (toggle/hold), small `>>` indicator, and next-shader hotkey.
- Advanced global and per-game memory, CPU and GPU-workaround settings.
- Pause menu with dated screenshot save-state slots; mapped save/load actions.
- Experimental RetroArch Slang shaders, folder browser, ZIP import and download.
- Shared app/pause themes, imported presets, multi-select import and live preview.
- Cards, list, stats, info/play and icon library layouts, with trainer/config badges.
- Experimental EvoX ETM trainer import and folder-backed per-game trainer browser.
- Online protection: trainers/cheats blocked; built-in fixes and release-reviewed compatibility patches remain eligible online.
  The imported-patch approval registry is currently empty.
- Importable guarded XBE patch packs, folder scanning and persistent per-game toggles.
- Optional high initial kernel pool placement for the Halo CE 1.3.0 crash.

Fast-forward frame skipping was removed. Auto/On/Off compatibility profiles now
control the pool-layout experiment, with an effective-setting reason and launch
logging. Auto enables the Halo pool workaround for the user-reported revision
`4D530004.8` on the 1.3.0 core; other revisions remain off.
The first guarded default.xbe patch-pack loader is implemented; native/rendering
patch plugins and broader formats remain planned.

## Latest validation

Advanced5.11 passed Android compilation/APK assembly and its JVM suite: **117 passed, zero
failures and one optional shader-archive skip**. Native pool/Advanced/fast-forward
checks and Android/native GitHub CI passed for the unchanged 5.9 baseline. This update reuses
the native libraries from the tested Advanced5.8 APK; it does not rebuild the
native core. The Halo workaround still has an initial successful 5.8 user report;
the user also reports that 5.9 runs well and looks correct. Testing the 5.9.1
Auto rule, repeatable cold boots and broader compatibility checks remain pending.

For Halo, choose **On** for **High kernel pool placement** in the per-game Advanced
settings, retain retail **64 MiB**, fully exit and launch fresh. Compare OFF/ON;
do not use an emulator save state for that comparison. New profiles default to
Auto (On for Halo revision 8 on the 1.3.0 core; otherwise Off); existing explicit
choices are preserved.
Recorded state placement takes precedence when a state is loaded.

## Documentation

- [Online compatibility policy and approval registry](docs/ONLINE5.11.md)
- [Importable patch browser and format](docs/PATCHES5.10.md)
- [Halo revision 8 Auto update](docs/HALO5.9.1.md)
- [Compatibility profiles and Halo controls](docs/COMPATIBILITY5.9.md)
- [Current project status and release record](docs/PROJECT-STATUS.md)
- [Roadmap](docs/ROADMAP.md)
- [Halo test instructions and implementation](docs/HALO5.8.md)
- [First user test and upstream handoff](docs/HALO5.8-USER-VALIDATION.md)
- [Online protection](docs/ONLINE5.7.md)
- [Trainer browser](docs/TRAINERS5.6.md)
- [Shader runtime](docs/ADVANCED4.md)
- [Theme system](docs/THEMES.md)
- [Fork history and attribution](docs/FORK.md)
- [Native/Android build prerequisites](android/README.md)
- [Private GitHub setup and maintenance](docs/GITHUB.md)
- [Contributing](CONTRIBUTING.md)

## Building and testing

Use Linux x86_64, JDK 21, Android SDK 36, Build Tools 36.1.0,
NDK 29.0.14206865, CMake >=3.20, Ninja, and a Rust toolchain compatible with the
pinned librashader source and its aarch64-linux-android target. The dependency
helper pins native sources; follow android/README.md for the full native build.
Build libhakuxcore.so and refresh jniLibs before packaging the Android app.

```bash
bash tools/native/prepare-deps.sh /path/to/native-deps
bash tools/native/build-slang.sh /path/to/native-deps
# Configure/build the native core as documented in android/README.md.
bash tools/native/app-libs.sh
bash tests/native/mm/run-pool-boot-test.sh
bash tests/native/advanced/run.sh
cd android
./gradlew -Dorg.gradle.java.home="$JAVA_HOME" -Pkotlin.incremental=false \
  testDebugUnitTest assembleDebug
```

The debug application ID remains **com.rfandango.haku_x.themes**, preserving
updates from Advanced5.7/5.8. Its displayed name is now **HakuX-Advanced** in
source; the previously tested APK is unchanged and retains its old label.
The former `themeTestApp` property remains accepted but is no longer needed
for choosing the current debug package identity.

Use the existing signing key privately when making an update. A newly generated
key cannot update the distributed APK. No signing key or password belongs in
Git, Actions logs, source packages or releases. The source-only setup/branding
update is not a newly built or device-tested APK.

## License and distribution

HakuX-Advanced remains **GPL-3.0-or-later**. See [COPYING](COPYING),
[LICENSE](LICENSE), and [licenses](licenses). Keep complete corresponding source
and dependency licenses available when distributing binaries, including private
RH testing distributions. Keeping development private does not change those
existing licenses. Preserve the original authors' credits.

The repository excludes Xbox games, BIOS/dashboard files, user HDD/save data,
private signing material and generated APKs. Supply your own game dumps.

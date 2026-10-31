# hakuX Advanced — unofficial experimental fork

An experimental fork of [rfandango/hakuX](https://github.com/rfandango/hakuX),
based on official **v1.2.0** (`e11ee60b90742a368a08f7f5339af10048a63a04`).
The original project, its authors and third-party contributors retain their
credits and licenses. This fork is not an official hakuX release and is not
affiliated with Microsoft.

hakuX runs original Xbox games on arm64 Android using a FEX-based x86 JIT,
a reimplemented Xbox kernel and an NV2A-to-Vulkan rendering path, with an
MCPX audio implementation ported from xemu. See the original
[upstream README](docs/upstream/README-v1.2.0.md).

## Fork additions

- Mappable fast-forward toggle and hold mode using the existing uncapped path.
- Advanced global/per-game RAM, CPU-precision and GPU-workaround profiles.
- Mappable quick save/load, pause, screenshot and benchmark actions.
- Live presentation filters, benchmark CSVs, memory tools and renderer dumps.
- A two-pane pause menu with a ten-slot screenshot/date savestate browser.
- Experimental RetroArch Slang runtime, ZIP import, global/per-game presets and parameters.

Current shader test build: **1.2.0-advanced4.2-shaders**, versionCode **14**. The Android application ID
is `com.rfandango.haku_x.shaders`; its displayed name is **hakuX Shaders Test**.
This installs alongside Advanced3 because its original debug signing key was lost
when the workspace reset. It has separate app data; Advanced3 remains installed.
The original official app uses its own identity. The dashboard/kernel experiment
is excluded: `native/kernel/` matches official v1.2.0.

## Status

The initial mappable fast-forward build was reported working on a user's
Android device. The later Advanced controls and pause
browser have build and automated-test coverage; device behavior and per-game
compatibility still need testing. The current source passed 37 Android JVM
tests and native policy/queue/memory checks. These are not Android UI tests.

Advanced3 removes the experimental fast-forward frame skipping. Fast forward
uses the original uncapped path; it does not overclock movie decoding or audio clocks.
Start with default settings and compare one experiment at a time.

- [Shader implementation and device testing](docs/ADVANCED4.md)
- [Advanced features and implementation](docs/ADVANCED.md)
- [Advanced3 removal and shader integration assessment](docs/ADVANCED3.md)
- [Advanced2 historical notes](docs/ADVANCED2.md)
- [Original fast-forward handoff](docs/FAST_FORWARD.md)
- [Fork history and release notes](docs/FORK.md)
- [Building the Android app](android/README.md)
- [Contributing and reporting problems](CONTRIBUTING.md)

## Build and test

Android builds require an **x86_64 Linux Android toolchain**: JDK 21, SDK 36,
Build Tools 36.1.0, NDK 29.0.14206865, CMake, Ninja, and Rust 1.99.1
with its `aarch64-linux-android` standard library target. The DietPi package is
intended for Git publishing; it does not promise an Android build on an ARM Pi.

```bash
bash tools/native/prepare-deps.sh /path/to/native-deps
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK="$ANDROID_HOME/ndk/29.0.14206865"
bash tools/native/build-slang.sh /path/to/native-deps
cmake -S native -B build/native/core -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DNC_DEPS=/path/to/native-deps \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5
cmake --build build/native/core --target hakuxcore --parallel 6
bash tools/native/app-libs.sh
bash tests/native/advanced/run.sh
cd android
./gradlew -Dorg.gradle.java.home="$JAVA_HOME" -Pkotlin.incremental=false \
  testDebugUnitTest assembleDebug
```

The dependency helper initializes the required FEX submodules and fetches exact
native dependency revisions. The default debug signing key is generated locally;
it is not in Git. A locally rebuilt APK will not update an APK signed by someone
else's key. Keep your own signing key private and stable for your releases.
Source distributions and releases must retain the applicable licenses.

## Git layout

`upstream` points to the original project. The Slang work is on **advanced-slang**, descended
from the official v1.2.0 commit. Fork-specific tags use **advanced-** prefixes.
Do not force-push or replace the original project's history. The portable
DietPi archive has a compact/shallow boundary at the unchanged official base;
its README explains how to fetch older history when wanted. A real GitHub fork
retains the full upstream ancestry on GitHub.

No Xbox BIOS, dashboard, game images, save data, signing keys or APK build
outputs are included in this Git repository. Obtain your own game images.

## License and attribution

This fork remains **GPL-3.0-or-later**, like hakuX. See [COPYING](COPYING),
[LICENSE](LICENSE), and [licenses/](licenses/). Files and dependencies from
other projects retain their original licenses. Distributing a modified binary
requires making its complete corresponding source available under its license.

Experimental trainer branch: see [Advanced4.3 trainer notes](docs/ADVANCED4.3.md) for classic EvoX ETM import, per-game options and boot execution. Android game testing is pending.

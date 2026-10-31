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
- Optional presentation-frame skipping during fast forward.
- A two-pane pause menu with a ten-slot screenshot/date savestate browser.

Current build: **1.2.0-advanced2**, versionCode **10**. The Android application ID
is `com.rfandango.haku_x.fastforward`; its displayed name is **hakuX Advanced**.
The original official app uses its own identity. The dashboard/kernel experiment
is excluded: `native/kernel/` matches official v1.2.0.

## Status

The initial mappable fast-forward build was reported working on a user's
Android device. The later Advanced controls, frame-skip integration and pause
browser have build and automated-test coverage; device behavior and per-game
compatibility still need testing. The current source passed 27 Android JVM
tests and native policy/queue/memory checks. These are not Android UI tests.

Frame skipping omits excess host scanout copies, preserves guest rendering and
fence submissions, and does not overclock movie decoding or audio clocks.
Start with default settings and compare one experiment at a time.

- [Advanced features and implementation](docs/ADVANCED.md)
- [Advanced2 frame skipping and pause browser](docs/ADVANCED2.md)
- [Original fast-forward handoff](docs/FAST_FORWARD.md)
- [Fork history and release notes](docs/FORK.md)
- [Building the Android app](android/README.md)
- [Contributing and reporting problems](CONTRIBUTING.md)

## Build and test

Android builds require an **x86_64 Linux Android toolchain**: JDK 21, SDK 36,
Build Tools 36.1.0, NDK 29.0.14206865, CMake and Ninja. The DietPi package is
intended for Git publishing; it does not promise an Android build on an ARM Pi.

```bash
bash tools/native/prepare-deps.sh /path/to/native-deps
export JAVA_HOME=/path/to/jdk21
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK="$ANDROID_HOME/ndk/29.0.14206865"
cmake -S native -B build/native/core -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release -DNC_DEPS=/path/to/native-deps \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5
cmake --build build/native/core --target hakuxcore --parallel 6
bash tools/native/app-libs.sh
bash tests/native/frame_skip/run.sh
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

`upstream` points to the original project. Our work is on **advanced**, descended
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

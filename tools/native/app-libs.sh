#!/usr/bin/env bash
# Puts the native core's app libraries (libhakuxcore.so, libSDL3.so and
# adrenotools' hook libraries from the native build) into the app's jniLibs,
# stripped, for assembleDebug to pack.
# They are build outputs (gitignored), refreshed by running this after
# `cmake --build build/native/core --target hakuxcore`.
#
#   tools/native/app-libs.sh [native build dir]
set -euo pipefail
here=$(cd "$(dirname "$0")/../.." && pwd)
build=${1:-$here/build/native/core}
ndk=${ANDROID_NDK:-/c/Users/USER/AppData/Local/Android/Sdk/ndk/29.0.14206865}
case "$(uname -s)" in
  Linux*) host=linux-x86_64; exe= ;;
  Darwin*) host=darwin-x86_64; exe= ;;
  *) host=windows-x86_64; exe=.exe ;;
esac
strip="$ndk/toolchains/llvm/prebuilt/$host/bin/llvm-strip$exe"
out="$here/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$out"
"$strip" -o "$out/libhakuxcore.so" "$build/libhakuxcore.so"
"$strip" -o "$out/libSDL3.so" "$build/sdl3/libSDL3.so"
# adrenotools' hook libraries: custom Vulkan drivers (Turnip) load through
# them from the app's native library directory.
for h in main_hook file_redirect_hook gsl_alloc_hook kgsl_context_hook hook_impl; do
  if [ -f "$build/adrenotools/src/hook/lib$h.so" ]; then
    "$strip" -o "$out/lib$h.so" "$build/adrenotools/src/hook/lib$h.so"
  fi
done
slang=$(sed -n 's/^HAKUX_LIBRASHADER:FILEPATH=//p' "$build/CMakeCache.txt")
: "${slang:?Missing HAKUX_LIBRASHADER in native CMake cache}"
"$strip" -o "$out/liblibrashader_capi.so" "$slang"
"$strip" -o "$out/libc++_shared.so" "$ndk/toolchains/llvm/prebuilt/$host/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"
ls -la "$out"

#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
: "${1:?Usage: bash tools/native/build-slang.sh /path/to/native-deps}"
: "${ANDROID_NDK:?Set ANDROID_NDK}"
deps=$(cd "$1" && pwd)
src="$deps/librashader"
revision=07ac790cdb9826f37cf8e1daf5011f03e3c4daa7
if [ ! -f "$src/Cargo.toml" ]; then
 mkdir -p "$src"; git -C "$src" init -q
 git -C "$src" remote add origin https://github.com/SnowflakePowered/librashader.git
 git -C "$src" fetch --depth 1 origin "$revision"; git -C "$src" checkout --detach FETCH_HEAD
fi
if [ -d "$src/.git" ] && [ "$(git -C "$src" rev-parse HEAD)" != "$revision" ]; then
 echo "Wrong librashader source revision" >&2; exit 1
fi
host=linux-x86_64
case "$(uname -s)" in Darwin*) host=darwin-x86_64 ;; esac
bin="$ANDROID_NDK/toolchains/llvm/prebuilt/$host/bin"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$bin/aarch64-linux-android28-clang"
export CC_aarch64_linux_android="$bin/aarch64-linux-android28-clang"
export CXX_aarch64_linux_android="$bin/aarch64-linux-android28-clang++"
export AR_aarch64_linux_android="$bin/llvm-ar"
export RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-soname,liblibrashader_capi.so -C link-arg=-Wl,-z,max-page-size=16384"
(cd "$src"; cargo build --locked --release --target aarch64-linux-android --jobs "${CARGO_BUILD_JOBS:-6}" -p librashader-capi --no-default-features --features runtime-vulkan)
cmp "$repo/native/third_party/librashader.h" "$src/include/librashader.h"

#!/usr/bin/env bash
# Fetch corresponding public sources at the exact revisions used for this fork.
set -euo pipefail
repo=$(cd "$(dirname "$0")/../.." && pwd)
: "${1:?Usage: bash tools/native/prepare-deps.sh /path/to/native-deps}"
mkdir -p "$1"
deps=$(cd "$1" && pwd)
fetch() {
  local path=$1 url=$2 revision=$3
  if [ ! -d "$path/.git" ]; then
    if [ -e "$path" ] && [ -n "$(ls -A "$path")" ]; then
      echo "Refusing to replace a non-Git directory: $path" >&2; return 1
    fi
    mkdir -p "$path"; git -C "$path" init -q; git -C "$path" remote add origin "$url"
  fi
  if [ -n "$(git -C "$path" status --porcelain)" ]; then
    echo "Dependency has local changes; keep them or choose a fresh directory: $path" >&2; return 1
  fi
  git -C "$path" fetch --depth 1 origin "$revision"
  git -C "$path" checkout --detach FETCH_HEAD
}
git -C "$repo" submodule update --init external/fex
git -C "$repo/external/fex" submodule update --init \
  External/fmt External/range-v3 External/rpmalloc External/unordered_dense \
  External/xxhash Source/Common/cpp-optparse
fetch "$deps/libslirp" https://github.com/qemu/libslirp.git 26be815b86e8d49add8c9a8b320239b9594ff03d
fetch "$deps/volk" https://github.com/zeux/volk.git 0b17a763ba5643e32da1b2152f8140461b3b7345
fetch "$deps/glslang" https://github.com/KhronosGroup/glslang.git b5782e52ee2f7b3e40bb9c80d15b47016e008bc9
fetch "$deps/spirv_reflect" https://github.com/KhronosGroup/SPIRV-Reflect.git ef913b3ab3da1becca3cf46b15a10667c67bebe5
fetch "$deps/vma" https://github.com/GPUOpen-LibrariesAndSDKs/VulkanMemoryAllocator.git 1d8f600fd424278486eade7ed3e877c99f0846b1
fetch "$deps/SDL3" https://github.com/libsdl-org/SDL.git 829a65d769d935c4852f8159e964312c0957260a
fetch "$deps/root/subprojects/nv2a_vsh_cpu" https://github.com/abaire/nv2a_vsh_cpu.git 561fe80da57a881f89000256b459440c0178a7ce
fetch "$deps/adrenotools" https://github.com/bylaws/libadrenotools.git 8fae8ce254dfc1344527e05301e43f37dea2df80
git -C "$deps/adrenotools" submodule update --init --recursive
git -C "$deps/adrenotools" apply "$repo/tools/native/adrenotools-kgsl-context-priority.patch"
echo "Dependencies ready at $deps. The adrenotools patch is applied."

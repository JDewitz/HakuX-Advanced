#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p build/advanced-tests
"${CC:-cc}" -O1 -D__ANDROID__ -DHAKUX_NATIVE_CORE -Itests/native/fast_forward/include -I. native/core/advanced.c tests/native/advanced/advanced_test.c -lpthread -o build/advanced-tests/registry
build/advanced-tests/registry
"${CC:-cc}" -O1 -ffunction-sections -fdata-sections -DHLE_HOST_TEST=1 -I. -Inative/compat/glib -Itests/native/fast_forward/include \
 tests/native/advanced/memory_test.c -Wl,--gc-sections -lpthread -o build/advanced-tests/memory
build/advanced-tests/memory
bash tests/native/fast_forward/run.sh

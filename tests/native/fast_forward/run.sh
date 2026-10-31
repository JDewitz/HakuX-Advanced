#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p build/fast-forward-tests
"${CC:-cc}" -O1 -ffunction-sections -fdata-sections -DHLE_HOST_TEST=1 \
 -I. -Inative/compat/glib -Itests/native/fast_forward/include \
 tests/native/fast_forward/fast_forward_test.c -Wl,--gc-sections -lpthread \
 -o build/fast-forward-tests/fast_forward
build/fast-forward-tests/fast_forward
build/fast-forward-tests/fast_forward uncapped

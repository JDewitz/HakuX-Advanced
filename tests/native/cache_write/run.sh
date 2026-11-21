#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
"${CC:-cc}" -O1 -ffunction-sections -fdata-sections -DHLE_HOST_TEST=1 \
  -I. -Inative/compat/glib -Itests/native/fast_forward/include \
  tests/native/cache_write/cache_write_test.c -Wl,--gc-sections -o "$test_dir/cache_write_test"
"$test_dir/cache_write_test"

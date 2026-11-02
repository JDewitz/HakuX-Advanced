#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
test_dir=$(mktemp -d)
trap 'rm -rf "$test_dir"' EXIT
"${CC:-cc}" -std=gnu11 -D_GNU_SOURCE -include stdio.h -O1 -g -Wall -Wextra -Werror -I. \
  -ffunction-sections -fdata-sections -Wl,--gc-sections \
  tests/native/mm/pool_boot_test.c native/kernel/pool.c native/core/vm.c \
  -ldl -o "$test_dir/pool_boot_test"
"$test_dir/pool_boot_test"

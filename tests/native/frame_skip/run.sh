#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p build/frame-skip-tests
"${CC:-cc}" -O1 -I. native/core/fast_forward.c tests/native/frame_skip/frame_skip_test.c -o build/frame-skip-tests/policy
build/frame-skip-tests/policy
bash tests/native/advanced/run.sh

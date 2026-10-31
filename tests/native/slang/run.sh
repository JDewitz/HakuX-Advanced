#!/usr/bin/env bash
set -euo pipefail
repo=$(cd "$(dirname "$0")/../../.." && pwd)
: "${1:?Usage: bash tests/native/slang/run.sh /path/to/librashader-source}"
slang=$(cd "$1" && pwd)
work="$repo/build/slang-tests"
mkdir -p "$work/src"
cp "$repo/tests/native/slang/main.rs" "$work/src/main.rs"
python3 - "$slang" "$work/Cargo.toml" <<'PY'
import sys,json
from pathlib import Path
src=Path(sys.argv[1])
Path(sys.argv[2]).write_text('[package]\nname="hakux-shader-check"\nversion="0.1.0"\nedition="2021"\n[workspace]\n[dependencies]\n'+
 '\n'.join(f'{n}={{path={json.dumps(str(src/n))}, default-features=false'+(', features=["glslang-in"]' if n=='librashader-reflect' else '')+'}' for n in ['librashader-reflect','librashader-preprocess','librashader-presets'])+'\n')
PY
cp "$repo/tests/native/slang/Cargo.lock" "$work/Cargo.lock"
cargo run --locked --release --manifest-path "$work/Cargo.toml" -- \
 "$repo/android/app/src/main/assets/shaders/passthrough.slang" \
 "$repo/android/app/src/main/assets/shaders/scanlines.slang" \
 "$repo/android/app/src/main/assets/shaders/sharpen.slang"

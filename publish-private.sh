#!/usr/bin/env bash
# Create a private standalone GitHub repository and push preserved Git history.
set -euo pipefail
cd "$(dirname "$0")"
command -v gh >/dev/null || { echo 'Install GitHub CLI and run gh auth login first.' >&2; exit 1; }
gh auth status --hostname github.com >/dev/null
owner=${1:-$(gh api user --jq .login)}
[[ "$owner" =~ ^[A-Za-z0-9][A-Za-z0-9-]*$ ]] || { echo 'Invalid GitHub owner.' >&2; exit 1; }
[[ "${owner,,}" != rfandango ]] || { echo 'Use your account, not upstream.' >&2; exit 1; }
target="$owner/HakuX-Advanced"
[[ "$(git branch --show-current)" == main ]] || { echo 'Check out main first.' >&2; exit 1; }
[[ -z "$(git status --porcelain)" ]] || { echo 'Commit or stash local changes first.' >&2; exit 1; }
if [[ "$(git rev-parse --is-shallow-repository)" == true ]]; then
  git -c fetch.recurseSubmodules=false fetch --unshallow --no-tags upstream v1.2.0
fi
url="https://github.com/$target.git"
if git remote get-url origin >/dev/null 2>&1; then
  [[ "$(git remote get-url origin)" == "$url" ]] || { echo 'Existing origin differs; refusing to replace it.' >&2; exit 1; }
fi
if ! gh repo view "$target" --json isPrivate >/dev/null 2>&1; then
  gh repo create "$target" --private --description 'Unofficial hakuX 1.3.0 fork with advanced controls, themes, shaders, trainers and game compatibility experiments.'
fi
[[ "$(gh api "repos/$target" --jq .private)" == true ]] || { echo 'Target is not private; refusing to push.' >&2; exit 1; }
gh auth setup-git --hostname github.com
# A new empty target is expected. Do not overwrite an unrelated existing repo.
heads=$(git ls-remote --heads "$url")
if [[ -n "$heads" ]]; then
  remote_main=$(git ls-remote "$url" refs/heads/main | cut -f1)
  [[ "$remote_main" == "$(git rev-parse main)" ]] || { echo 'Target already has different commits; inspect before pushing.' >&2; exit 1; }
fi
git remote get-url origin >/dev/null 2>&1 || git remote add origin "$url"
git push -u origin main
while IFS= read -r tag; do
  git push origin "refs/tags/$tag:refs/tags/$tag"
done < <(git tag --list 'advanced-*')
gh repo edit "$target" --default-branch main
[[ "$(gh api "repos/$target" --jq .private)" == true ]]
gh repo view "$target" --json url,isPrivate,defaultBranchRef

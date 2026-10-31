#!/usr/bin/env bash
# Push this prepared source branch and only the fork's own release tags.
set -euo pipefail
cd "$(dirname "$0")"
if [ "${1:-}" = --help ] || [ "$#" -ne 1 ]; then
  echo "Usage: ./publish-fork.sh <your-fork-git-url>"
  echo "Create a real fork first and authenticate Git on this machine. No force push is used."
  exit 0
fi
url=$1
case "$url" in
  *github.com/rfandango/hakuX|*github.com/rfandango/hakuX.git|*github.com:rfandango/hakuX|*github.com:rfandango/hakuX.git)
    echo "Use your own fork, not the original upstream repository." >&2; exit 1 ;;
esac
if [ -n "$(git status --porcelain --ignore-submodules=all)" ]; then
  echo "Commit or stash local source changes before publishing." >&2; exit 1
fi
if git remote get-url origin >/dev/null 2>&1; then
  if [ "$(git remote get-url origin)" != "$url" ]; then
    echo "origin already points elsewhere. Inspect git remote -v before changing it." >&2; exit 1
  fi
else
  git remote add origin "$url"
fi
heads=$(git ls-remote --heads origin)
if [ -z "$heads" ] && [ "$(git rev-parse --is-shallow-repository)" = true ]; then
  echo "This remote is empty. Prefer a real fork of rfandango/hakuX." >&2
  echo "For an empty repository, first run: git fetch --unshallow upstream" >&2
  exit 1
fi
git push -u origin refs/heads/advanced:refs/heads/advanced
for tag in advanced-1.2.0-fastforward1 advanced-1.2.0-1 advanced-1.2.0-2; do
  git push origin "refs/tags/$tag:refs/tags/$tag"
done
echo "Pushed advanced and three fork-specific tags."
echo "Set advanced as your fork's default branch if you want it shown on the repository home page."

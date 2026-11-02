# Private GitHub setup — HakuX-Advanced

The private standalone repository is
https://github.com/JDewitz/HakuX-Advanced, owned by `JDewitz`.
Private visibility and default branch `main` were verified on 2026-11-02. GitHub public forks cannot independently become private:
https://docs.github.com/en/pull-requests/reference/forks#visibility-of-forks
Keeping a standalone private copy preserves Git ancestry, authors and licensing.

Default development branch: `main`. Upstream remote:
https://github.com/rfandango/hakuX.git, with direct upstream pushes disabled.
Do not initialize the GitHub target with a generated README, license or gitignore;
all are already in this checkout. Do not replace or squash the upstream history.

## Publish

After authenticated GitHub access is available, from this checkout run:

```bash
bash publish-private.sh
```

The optional first argument selects an owner/organization you are authorized to
use. Otherwise the script gets the authenticated login. The script creates a
private repository if absent, verifies private visibility before pushing, pushes
`main` and `advanced-*` tags, sets the default branch, and checks privacy again.
It refuses a public target, conflicting origin, dirty tree, or an unrelated
nonempty repository. It never force-pushes or changes existing collaborators.

The full local preparation has restored upstream ancestry. A compact portable
archive may retain a shallow boundary at the original v1.2.0 commit to avoid
shipping hundreds of megabytes of unrelated historical objects. The publishing
script restores that ancestry with `git fetch --unshallow` before an empty
repository push. This downloads public upstream history and needs network access.

## Ongoing work

- Keep changes on `main` or short feature branches with reviewable commits.
- Fetch upstream and review changes before merging; do not silently rebase a
  shared branch. Keep upstream-native compatibility fixes independently usable.
- Record tested source commits, APK version and signing certificate for releases.
- Raise Android versionCode before publishing a new APK; branding alone did not
  rebuild the existing Advanced5.8 APK.
- Host checks run in Actions on push/PR. This workflow uses read-only repository
  permissions and no signing secrets; it does not prove Android/device behavior.
- APKs and source distributions belong in release assets, not source commits.
  A private repository is not a substitute for supplying corresponding source
  when distributing binaries outside its authorized collaborators.
- Signing key/passwords, local Android configuration, user game/HDD/save files,
  raw logs and private trainer collections remain outside Git. Existing private
  key backups are separate from this project.

The intro disclosure is intentionally reproduced exactly as requested:
`AI Use Discolosure: AI was used heavily in this project.`

Sync completed on 2026-11-02. The prepared source head
`7cf98cbfe42fa5ee3a9942299d51c19e66344af9` and all 17 `advanced-*` tags
were transferred with the full upstream/fork ancestry preserved. Host checks
passed for that head in Actions run 41420948980. Temporary transfer branches
were removed. The tested Advanced5.8 APK and private signing key were not
uploaded to Git. Subsequent documentation commits record this verification.

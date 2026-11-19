# HakuX-Advanced 5.20 — Goin Commando

This is the complete manual release kit, prepared as a **normal release**,
not a prerelease. Nothing has been pushed, uploaded to GitHub, or published.

## 1. Inspect and verify locally

Extract this ZIP to a folder. Open PowerShell in that folder:

```powershell
py prepare-release.py
```

This checks all packaged checksums and release metadata without authentication,
Git, or network access. Read RELEASE-NOTES.md and VALIDATION.json. Install the
APK over Advanced to test; do not uninstall first. Cold start Republic Commando.
No external pacing patch is needed. Disable the previous 30/60 imported pacing
test patches to use the built-in 60 FPS default.

The separate Git-update ZIP contains a readable cumulative patch, source
snapshot and Git bundle. source.bundle is incremental from 5.19.3 commit
5a04012cd8706f57f3e0574a491f0a1702880264. It contains all changes from 5.19.3,
including the private 5.2 renderer/XISO work and the Republic Commando fix.
The snapshot excludes generated binaries and submodule contents; Git submodule
references preserve dependency versions. No signing key is included.

## 2. Stage later, only when ready

Staging pushes the prepared source to main and creates the annotated release
tag, then uploads assets into a draft. It does not publish the release.
Do not run this step until you intend to upload/push.

If needed, install Git and GitHub CLI, then open a new PowerShell window:

```powershell
winget install --id Git.Git -e
winget install --id GitHub.cli -e
```

Authenticate on your own computer:

```powershell
gh auth login --hostname github.com --git-protocol https
gh auth status
py prepare-release.py --stage
```

Choose the account with write access to JDewitz/HakuX-Advanced. The script
requires remote main to be exactly the prepared 5.19.3 base or the prepared
release commit; it stops if main has changed. It uses a temporary repository,
never force-pushes, and uploads one APK. It stops for an existing release/draft
instead of overwriting one. If a push succeeds and an upload fails, review the
remote tag/main and draft before rerunning; existing matching refs are reused.

## 3. Review and publish as a release

Open https://github.com/JDewitz/HakuX-Advanced/releases and edit the draft.

- Title: HakuX-Advanced 5.20 · Goin Commando
- Tag: advanced-1.3.0-5.20
- Check the notes, APK filename and GitHub APK SHA-256 against VALIDATION.json.
- Leave **Set as a pre-release** unchecked.
- Select **Set as the latest release**, then publish when ready.

Equivalent publish command, after reviewing the staged draft:

```powershell
gh release edit advanced-1.3.0-5.20 --repo JDewitz/HakuX-Advanced --draft=false --prerelease=false --latest
```

Publishing a stable release with this valid APK makes it eligible for Advanced's
existing updater. There is no separate feed file to upload. Existing 5.19.x
updater-capable installations select 5.20 and accept versionCode 42 with the same
package/certificate. Older private 5.2 experimental APKs intentionally disabled
update checks; sideload this APK once on those installations. Device-side
update-download/install confirmation remains pending. Ordinary Android install
confirmation still applies. No updater rollout occurs while the release is a
draft.

## Manual source inspection in an existing checkout

From an up-to-date checkout containing the exact 5.19.3 base, use an absolute
path to the extracted kit:

```powershell
git bundle verify "D:\path\to\kit\source.bundle"
git fetch "D:\path\to\kit\source.bundle" refs/heads/release/5.20-goin-commando
git switch -c review/goin-commando FETCH_HEAD
git diff 5a04012cd8706f57f3e0574a491f0a1702880264..HEAD --stat
```

These commands review locally; they do not push or create a GitHub release.

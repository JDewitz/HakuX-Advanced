# Prepare and publish a preview

The 5.19 Preview 1 binary is frozen at `cf309722217cfa16c10d1651122c802d7f1f03ff`, tag `advanced-1.3.0-5.19-preview.1`. The prepared release branch `release/advanced-5.19-preview1` points there. Later release-documentation commits on main do not change that APK/tag target. See [release notes](releases/5.19-preview1.md).

Extract the supplied release kit. It includes the existing signed APK, dependency source archive, compact Git source bundle, checksums, validation report, installation/build guides and publisher. No signing material is included. The source-bundle helper preserves its recorded shallow boundary and checks connectivity; a plain clone of that compact bundle is not the documented restore route.

To verify/plan without mutations:

```bash
python3 tools/release/publish-draft.py --kit /path/to/extracted/kit
```

To create the matching annotated tag and upload a draft prerelease with an authenticated GitHub CLI:

```bash
gh auth login
python3 tools/release/publish-draft.py --kit /path/to/extracted/kit --create-draft
```

The script checks hashes, repository privacy and any existing tag's target; it never force-updates a tag, overwrites a release or publishes automatically. `--create-tag-only` prepares the tag for a manual web upload instead. Review the draft, mark it a prerelease, keep Latest off and publish separately when ready. The kit's PUBLISH-TO-GITHUB.md describes the web route and exact assets.

Use a new immutable tag for any replacement binary, and increase Android versionCode for an installable update. Keep APK source SHA, certificate, asset hashes, native/dependency versions, host validation and device results explicit. GitHub-generated source ZIPs omit dependency submodules; attach the prepared dependency source archive and build instructions. Keep repository visibility unchanged.

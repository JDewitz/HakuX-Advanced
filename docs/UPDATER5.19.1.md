# Advanced updater hotfix — 5.19.1

The inherited updater queried rfandango/hakuX and expected the official application ID. Advanced now queries only JDewitz/HakuX-Advanced. Install 5.19.1 manually over 5.19 once, preserving the package and signing key; the old updater cannot discover this fix.

Settings → Updates → Include preview updates selects stable plus preview APKs (default on for this testing fork), or stable Advanced APKs only. Changing the channel clears the previous update offer. Both startup and manual checks use this setting. No GitHub token is stored or required in the app: public release downloads are anonymous.

Discovery reads up to five pages of 100 GitHub releases and chooses the highest newer Advanced version, independent of publication order. Drafts, unrelated tags, theme releases, missing/ambiguous APKs, wrong asset paths, incomplete uploads, missing SHA-256 digests, and APKs above 128 MiB are ignored. A check will not see releases older than this bounded 500-release window. Each API page is limited to 2 MiB.

## Maintainer release contract

- Stable tags: `advanced-1.3.0-5.19.1`, `advanced-1.3.0-5.20`, etc.
- Preview tags: `advanced-1.3.0-5.20-preview.1`. Mark preview releases as GitHub prereleases too.
- APK version names: `1.3.0-advanced5.19.1-updater` (stable) or `1.3.0-advanced5.20-preview.1`. Descriptive suffixes are allowed after the Advanced number. Preview numbers in APK metadata must match their tags.
- APK assets: `HakuX-Advanced-1.3.0-5.19.1-updater-arm64.apk`, or `HakuX-Advanced-1.3.0-5.20-preview.1-arm64.apk`. Exactly one matching arm64 APK per release. No x86 or official-build APKs.
- Keep `com.rfandango.haku_x.themes` and the existing private signing certificate for this release line. Increase Android versionCode on every update; 5.19.1 is code 39, following 5.19 code 38. Increase the Advanced version/tag too; replacing an APK under the same version is not an update mechanism.
- GitHub must expose the uploaded asset's `sha256:` digest. The updater checks it before accepting the APK.
- Theme-only releases use separate tags, e.g. `themes-nostalgia-v1`; they cannot hide an older eligible app release.

Before opening Android's installer, downloaded APKs must match the current package and signer, have a strictly larger Android version code, have a newer Advanced version, and match the selected release version. Package/signer/version checks run again immediately before installer handoff. These checks apply to debug builds too. Android still asks the user before installation. Failed partial downloads are removed; no silent update installation is added. Certificate rotation is not implemented; a different signer is rejected.

No native emulator libraries or rendering settings changed. This frontend hotfix retains the byte-identical native libraries from the tested 5.19 APK. The updater-selection tests cover ordering, channels, source identity, bad assets and malformed releases. Actual Android installer interaction and acceptance/rejection of signed APKs need device testing.

## Device checks

1. Manually install 5.19.1 over 5.19 without uninstalling; confirm settings, saves and themes remain.
2. Check updates with preview updates on and off: no same-version update should be offered.
3. For an end-to-end test, publish a strictly newer testing version with a matching tag/filename/versionCode and the same key, then check, download, and accept Android's install prompt. Test cancellation as well.
4. Confirm a theme-only release does not cause an update offer. Do not modify published app binaries just to test rejection; use separate test releases.

The one manual install cannot prove a future automatic download/install round trip. Keep that distinction in test reports.

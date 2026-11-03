# Advanced5.12 — shader pack deletion hotfix

Version: `1.3.0-advanced5.12-shaderdelete`, versionCode 29.

Open Options → Graphics → Shaders and go Up to **Shader packs**. Each pack
folder has a trash icon. Confirm Delete to remove that pack and all its presets,
shader sources, includes and textures. Nested category folders have no delete
button. Cancel leaves files and selections unchanged.

Global and per-game selections inside the deleted pack become Off, with their
parameters cleared. Affected game overrides remain explicitly Off rather than
inheriting a different global shader. Other packs and settings are preserved.
The browser refreshes after deletion. When opened from a paused game, resume
applies the updated selection through the existing shader-settings path.

Deleting the built-in examples also records a local suppression marker, so
opening Shaders does not immediately reinstall them. Imported/downloaded packs
can be restored by importing/downloading again. Purging is permanent.

Deletion accepts only an immediate pack folder beneath app-private shaders.
A symlink used as a pack root is rejected; links inside a pack are removed
without following their targets. Filesystem errors are reported, the browser
refreshes, and affected selections stay Off; retry if a partial folder remains.
Import/download and selection actions are disabled while deletion runs.

The online protection rules, Halo Auto fix, native binaries, application ID and
signing certificate are unchanged from Advanced5.11. Device UI/game-resume
verification remains pending. Automated checks cover whole-pack removal,
sibling preservation, symlink targets, traversal rejection and selection cleanup.

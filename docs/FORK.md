# Fork provenance and release notes

Upstream: https://github.com/rfandango/hakuX
Base tag: v1.2.0
Base commit: e11ee60b90742a368a08f7f5339af10048a63a04

Our feature commits are descendants of that base; their history is retained.
The fork does not contain the earlier dashboard/kernel experiment.

| Fork snapshot | Changes | Validation |
| --- | --- | --- |
| advanced-1.2.0-fastforward1 | Mappable uncapped fast forward | Native checks, 18 JVM tests; user reported working on device |
| advanced-1.2.0-1 | Advanced profiles, live tools and mapped actions | Native checks and 25 JVM tests; Android build passed |
| advanced-1.2.0-2 | Optional scanout skipping and two-pane savestate browser | Native checks and 27 JVM tests; Android build passed; device validation pending |
| advanced-1.2.0-3 | Remove FF frame skipping; retain mappable FF and pause browser | Native checks and 27 JVM tests; Android build passed; device validation pending |

Advanced2 includes fork documentation/publishing helpers. Those did not
change the runtime code of the previously supplied Advanced2 APK. No APKs or
private development signing keys are tracked. Publish binaries as release
assets with the corresponding source tag and retain license material.

The FEX submodule remains pinned to the original project's fork at
52018bf46a8279b22bce738548ab3f40db467064. It has not been modified by this fork.
Native dependencies are pinned in tools/native/prepare-deps.sh. For earlier
implementation details see ADVANCED.md, ADVANCED2.md, ADVANCED3.md and FAST_FORWARD.md.

The Advanced4 Slang test uses librashader 0.12.1 and a separate Android application
identity. Google's supplied Advanced4 was reviewed; its dependencies and build
setup were reused. Its single-pass adapter and preset implementations were not
merged into this runtime. See ADVANCED4.md.

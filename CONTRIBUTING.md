# Contributing

Base changes on the `advanced` branch. Keep compatibility experiments optional
and default to existing upstream behavior. Avoid combining unrelated kernel,
renderer, input and UI changes in one patch.

For a bug report, include the fork version, Android device/GPU/driver, game title
and disc version, exact changed settings, what should happen, what happened,
and reproducible steps. Attach Settings → Export logs. For timing problems,
include a benchmark CSV and wall-clock behavior; displayed FPS alone is not
guest execution speed. Do not attach copyrighted Xbox software or private data.

Validation for changes in these areas:

```bash
bash tests/native/frame_skip/run.sh
cd android
./gradlew -Dorg.gradle.java.home="$JAVA_HOME" -Pkotlin.incremental=false testDebugUnitTest assembleDebug
```

Rebuild and refresh native JNI libraries before assembling when native code
changes. See README.md and android/README.md. Record device checks separately
from JVM/host tests. No automated check currently validates the actual Android
pause layout, GPU behavior, movie speed or game compatibility.

Retain upstream attribution and licensing. Keep signing keys, local SDK paths,
APKs, game files, console data, logs and captures out of commits.

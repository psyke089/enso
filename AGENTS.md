# AGENTS.md

Guidance for coding agents working in this repository.

## Project

Enso is a private, fully offline Android meditation timer and interval gong, built with Kotlin and Jetpack Compose. Minimum Android 7.0 (API 24); package `app.enso.meditation`.

- [SPEC.md](docs/SPEC.md) — product specification.
- [README.md](README.md) — human-facing overview.
- [docs/ARTWORK.md](docs/ARTWORK.md) — provenance and image-generation prompts for the ink control icons.

## Release status

This is a private app that will likely never be published to a real app store. Builds are distributed as APK assets on [GitHub releases](https://github.com/psyke089/enso/releases), not through an app store.

Release signing is configured locally, not in version control. `app/build.gradle.kts` loads the gitignored `keystore.properties` from the repo root when it exists:

```properties
storeFile=/home/psyke/.android/enso-release.jks
storePassword=…
keyAlias=enso
keyPassword=…
```

Keep the keystore and `keystore.properties` out of the repository. Without that file `assembleRelease` still builds, but produces an unsigned APK; debug builds are unaffected either way.

The shipped artifact is the release-signed `app/build/outputs/apk/release/enso-<versionName>.apk`. `app-debug.apk` is only for local installs and device testing, and it cannot replace an installed release-signed copy (signature mismatch) — uninstall first when switching.

### Versioning and release flow

- Bump `ensoVersionCode` (integer, +1 per release) and `ensoVersionName` (semver) at the top of `app/build.gradle.kts`, and update the release badge in `README.md`.
- Tags are annotated `vX.Y.Z`; release titles are `Enso X.Y.Z`. Attach the release APK and write notes in the established format (highlights, version numbers, install instructions, known limits).
- `origin` has two push URLs: GitHub (`psyke089/enso`) and Codeberg (`k4r/enso`); a plain `git push origin main --follow-tags` updates both.

## Build, test and run

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleRelease   # signed enso-<versionName>.apk, see Release status
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant app.enso.meditation android.permission.POST_NOTIFICATIONS  # API 33+ test device only
adb shell am instrument -w app.enso.meditation.test/androidx.test.runner.AndroidJUnitRunner
```

## Device testing

A physical Android phone is normally attached over USB debugging; check with `adb devices`. Develop and verify on that real device.

Use an emulator or simulator **only** if no physical device is available **and** the user has explicitly approved it — don't assume emulator use is allowed.

Keep device interaction non-destructive and limited to Enso, following [SPEC.md](docs/SPEC.md) §59–61: don't factory-reset the phone, erase storage, touch unrelated apps or data, or run destructive adb commands. If the device is unavailable, continue with build and unit-test verification and report that device testing could not be completed.

Instrumented tests need Enso in the foreground. If `EnsoSmokeTest` fails with `Activity never becomes requested state` or a `compose.waitUntil` timeout, check whether another app on the phone took focus mid-run (`adb logcat` → `Displayed app` / `Lifecycle status change`): the activity gets stopped and the Compose wait times out. That is device interference, not an app defect — rerun instead of force-stopping the other app.

## Scratch space and the sandbox

- Use the repository's `./tmp/` directory for scratch files, screenshots, exports and anything temporary. **Prefer `./tmp` over the system `/tmp`**, which can be ephemeral or restricted for sandboxed commands. `./tmp` is gitignored, so copy anything that must be versioned into a tracked path.
- `./tmp` already holds reference assets, device screenshots and source audio. Treat it as input material, not part of the app.
- Gradle stores its caches in `~/.gradle` (outside the repo) and the Android build may touch `~/.android`. Under a restricted file sandbox the first build can require broader write access.

## Conventions

- Keep the app fully offline: no `INTERNET` permission, accounts, analytics, ads or remote assets.
- Gong recordings live in `app/src/main/res/raw`; generated ink control icons live in `app/src/main/res/drawable-nodpi`. See [docs/ARTWORK.md](docs/ARTWORK.md).
- Timer behaviour is a deterministic state machine driven by `elapsedRealtime()`; preserve the existing persistence and restoration semantics. Reboot continuation is intentionally unsupported.
- The UI intentionally stays in the warm washi/paper theme regardless of system dark mode.
- Accessibility is out of scope: Enso is a private hobby app for the author and friends. Don't propose or implement accessibility work — screen-reader semantics, contrast ratios, touch-target minimums, font-scaling support — unless explicitly asked.
- Tests: `TimerEngineTest`, `CircleRotationTest` and `SayingsTest` run on the JVM; `EnsoSmokeTest` and `EnsoArtworkStoreTest` are instrumented.

<p align="center">
  <img src="docs/enso-header.png" alt="A sumi ink enso painted on textured washi paper" width="240">
</p>

<h1 align="center">Enso</h1>

<p align="center">A quiet, offline meditation timer and interval gong for Android.</p>

<p align="center">
  <a href="https://github.com/psyke089/enso/releases"><img src="https://img.shields.io/badge/release-v1.7.0-blue?logo=github&logoColor=white" alt="Release v1.7.0"></a>
  <img src="https://img.shields.io/badge/vibe_coded-100%25-critical?logo=github&logoColor=white" alt="No human review">
  <img src="https://img.shields.io/badge/human_review-none-green?logo=github&logoColor=white" alt="Human review: none">
</p>

<p align="center">
  <img src="docs/enso-screenshot-idle.png" alt="Enso idle screen: a five minute countdown inside a hand-drawn ink enso on warm paper, with brush and settings controls above" width="300">
</p>

A minimal meditation timer in Kotlin and Jetpack Compose. Android 7.0+ (API 24), portrait-only, entirely offline: no internet permission, accounts, analytics or ads. Behaviour is specified in [SPEC.md](docs/SPEC.md).

## What it does

**Trace time.** Rotate the ink circle clockwise to add minutes, counterclockwise to take them back: 5 minutes to 24 hours, in steps of 1, 2, 5, 10 or 15. Tap either side of it to set repetitions; zero means it never ends. The circle tracks elapsed against total time, and the marks below fade one by one as intervals pass.

**Paint an enso.** A full-screen sumi canvas with tunable ink, saved over the standard artwork. On a day you have not painted, the canvas opens by itself on your first visit — close it and it waits until tomorrow. Hardcore Mode leaves exactly one way forward: the checkmark.

**Sit with it.** Nine bells for start, end and repeat; twenty sayings that never land back to back; one calm screen for start, pause, resume, reset. The countdown also lives on the lock screen, so the phone can stay down. Hide the clock or the saying, silence the haptics, keep the screen awake. English and German.

## Install

Grab the signed APK from [releases](https://github.com/psyke089/enso/releases) and open it on the phone, or:

```sh
adb install -r enso-1.7.0.apk
```

Release builds are self-signed, so a debug build cannot be installed over one (uninstall first).

## Build

```sh
./gradlew assembleRelease   # app/build/outputs/apk/release/enso-1.7.0.apk
./gradlew testDebugUnitTest lintDebug
```

`keystore.properties` in the repo root enables release signing; without it the APK is unsigned. See [AGENTS.md](AGENTS.md) for device and instrumented-test commands.

## License

[MIT](LICENSE).

The nine gong recordings in `app/src/main/res/raw` come from [Pixabay](https://pixabay.com/) under the [Pixabay Content License](https://pixabay.com/service/license-summary/), which requires no attribution. The adjustment tick is generated locally by `tools/generate_adjustment_tick.py`.

<p align="center">
  <img src="docs/enso-header.png" alt="A sumi ink enso painted on textured washi paper" width="240">
</p>

<h1 align="center">Enso</h1>

<p align="center">A quiet, offline meditation timer and interval gong for Android.</p>

<p align="center">
  <img src="https://img.shields.io/badge/code_review-none%2C_100%25_vibes-critical" alt="No human review">
</p>

<p align="center">
  <img src="docs/enso-screenshot-idle.png" alt="Enso idle screen: a five minute countdown inside a hand-drawn ink enso on warm paper" width="300">
</p>

Enso is a minimal meditation timer built with Kotlin and Jetpack Compose. It runs entirely offline on Android 7.0+ (API 24), has no internet permission, accounts, analytics or ads, and is specified in [SPEC.md](SPEC.md).

## What it does

- **Set any duration** from 5 minutes to 24 hours. Tap the left or right half of the ink circle to subtract or add 5 minutes; hold to repeat with gradually increasing speed. The duration is saved after every adjustment.
- **One calm screen** for Start, Pause, Resume and Stop, using hand-drawn sumi ink controls with accessible action labels.
- **Choose your gongs.** Separate Start, End and Continuous gongs, each pickable from nine bundled bell recordings and previewable in the settings sheet.
- **Rotating sayings.** Twenty original Zen-inspired lines appear on launch, at the start of each session and at every continuous interval, with a soft fade and never an immediate repeat.
- **Simple preferences.** Keep Screen Awake, Vibration and Show Saying apply immediately. The gong choice is captured when a session starts and applies to the next one.

## Reliability

Timing is driven by `elapsedRealtime()` rather than wall-clock time, so audio and UI refreshes cannot accumulate drift. Session state is persisted in a single Preferences DataStore and restored exactly across screen-off, activity recreation and process death; a reboot discards it. While a session is running, a quiet foreground notification offers Pause/Resume and Stop, and a partial wake lock keeps timing accurate with the screen off. Pausing releases both. Idle Enso runs no service and holds no lock.

## Build and run

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. No release signing configuration is supplied.

`TimerEngineTest` and `DurationHoldTest` cover the timer state machine, boundaries, restoration and 10,000 intervals without drift; `EnsoSmokeTest` exercises the Compose controls, DataStore writes and lifecycle transitions on a device.

## Assets

The screen uses local washi, ink-circle, seal and landscape artwork. Start/Resume, Pause and Stop are three original PNGs generated with the built-in image generation tool; see [ARTWORK.md](ARTWORK.md) for the prompts and paths. The nine gong recordings are user-supplied MP3s bundled in `app/src/main/res/raw`. Audible quality, focus/DND behaviour and screen-off playback still need checking by ear on a physical device.

## License

[MIT](LICENSE)

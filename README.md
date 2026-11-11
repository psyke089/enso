<p align="center">
  <img src="docs/enso-header.png" alt="A sumi ink enso painted on textured washi paper" width="240">
</p>

<h1 align="center">Enso</h1>

<p align="center">A quiet, offline meditation timer and interval gong for Android.</p>

<p align="center">
  <a href="https://github.com/psyke089/enso/releases"><img src="https://img.shields.io/badge/release-v1.4.0-blue?logo=github&logoColor=white" alt="Release v1.4.0"></a>
  <img src="https://img.shields.io/badge/vibe_coded-100%25-critical?logo=github&logoColor=white" alt="No human review">
  <img src="https://img.shields.io/badge/human_review-none-green?logo=github&logoColor=white" alt="Human review: none">
</p>

<p align="center">
  <img src="docs/enso-screenshot-idle.png" alt="Enso idle screen: a five minute countdown inside a hand-drawn ink enso on warm paper" width="300">
</p>

Enso is a minimal meditation timer built with Kotlin and Jetpack Compose. It runs entirely offline on Android 7.0+ (API 24), has no internet permission, accounts, analytics or ads, and is specified in [SPEC.md](docs/SPEC.md).

## What it does

- **Set any duration** from 5 minutes to 24 hours. Trace the ink circle clockwise to increase the duration, counterclockwise to decrease it. The ink follows your finger, stops at the duration limits and stays still during the countdown. Choose 1, 2, 5, 10 or 15 minutes per step in Settings (default: 5). The duration is saved after every adjustment.
- **Tap the circle to choose repetitions.** Left subtracts one, right adds one, down to 0 for unlimited repetitions. Small brush marks below the circle show the repetitions; each completed countdown fades one mark from the right. A quiet elapsed / total time display inside the circle tracks finite sessions; unlimited sessions show a larger hand-painted infinity mark.
- **Subtle selection feedback.** A soft click and short vibration mark each duration or repetition change. Switch both off with Haptic Feedback, independently of interval-end Vibration. Session buttons have no selection feedback.
- **One calm screen** for Start, Pause, Resume and Reset, using hand-drawn sumi ink controls with accessible action labels.
- **Choose your gongs.** Separate Start, End and Repeat gongs, each pickable from nine bundled bell recordings and previewable in the settings sheet.
- **Rotating sayings.** Twenty original Zen-inspired lines appear on launch, at the start of each session and at every repeated interval, with a soft fade and never an immediate repeat.
- **Simple preferences.** Keep Screen Awake, Vibration, Show Time and Show Saying apply immediately. Hide the time displays for a quiet circle with only the repetition brush marks. Gong sound preferences apply immediately; duration and repetition count stay locked until the session ends.

## License

[MIT](LICENSE)

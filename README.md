# Enso

An offline Android meditation timer and interval gong. Kotlin / Compose, minimum Android 7.0 (API 24), package `app.enso.meditation`. [SPEC.md](SPEC.md) is the product specification.

Tap the left or right half of the ink circle to subtract or add five minutes. Hold to repeat, with gradually increasing speed. Durations range from five minutes to 24 hours and save after every adjustment. Start, Pause, Resume and Stop share a single screen.

The settings sheet contains Start Gong, End Gong, Continuous Gong, Keep Screen Awake and Vibration. Continuous Gong is captured when a session starts; changing it during a session applies to the next meditation. The other preferences apply immediately. Pausing releases both screen-awake behavior and the session CPU wake lock.

## Implementation

- `TimerEngine.kt` is a deterministic state machine driven by `elapsedRealtime()`. Repeating deadlines advance from previous boundaries, with pauses shifting the remaining deadline. UI refreshes and audio duration cannot accumulate timing drift.
- `SessionController` owns one engine for the application. Mutations are serialized, and boundary state is saved before audio/vibration side effects. Recomposition and activity recreation never create timer events.
- `EnsoStore` uses one Preferences DataStore for settings, selected duration and session anchors. It writes on adjustments/transitions/boundaries, not every second. A boot-count check discards sessions after a phone reboot. Restoring consumes past boundaries silently and preserves future interval alignment.
- `MeditationService` uses one command/tick worker and a quiet foreground notification with Pause/Resume and Stop. It exists only for an active (including paused) session. A partial CPU wake lock is held only while running so ordinary screen-off operation can keep timing. This favors timing reliability over battery use during a long meditation; idle Enso holds no lock and runs no service.
- Android 14+ declares the [special-use foreground service type](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), with its timer purpose documented in the manifest. Distribution through an app store may require review of that declaration. Android 13+ asks for notification permission on Start; denial does not block meditation.
- The UI stays in the warm paper theme regardless of system dark mode. Canvas fallback artwork is static, deterministic and replaceable. Timer text, semantics and controls are real Compose UI. Backup/transfer is disabled; no internet permission, accounts, analytics or remote assets are used.

## Assets

The current ink circle, paper fibers, distant ink wash and adaptive launcher icon are original procedural/vector fallbacks. Final painted artwork can replace `ui/InkArtwork.kt` and the launcher resources without affecting interaction or timing. No final commissioned artwork has been supplied.

**No gong recording is bundled yet.** Playback intentionally does nothing when resources are missing. Supply licensed or original OGG/WAV recordings at:

- `app/src/main/res/raw/gong_start.ogg`
- `app/src/main/res/raw/gong_end.ogg`

Alternatively, `app/src/main/res/raw/gong.ogg` is the shared fallback for both. WAV uses the same base names. Playback uses alarm audio attributes, transient audio focus, async preparation and cleanup on completion/error/timeout. Verify volume, focus/DND interaction, screen-off playback and the complete decay on a physical device after adding recordings. No placeholder binary sound is fabricated.

## Verification

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell pm grant app.enso.meditation android.permission.POST_NOTIFICATIONS # API 33+ test device only
adb shell am instrument -w app.enso.meditation.test/androidx.test.runner.AndroidJUnitRunner
```

`TimerEngineTest` and `DurationHoldTest` cover defaults, five-minute steps/bounds, state locking, elapsed-time countdowns, pause/resume/stop, normal and continuous completion, once-only events, 10,000 delayed interval boundaries without drift, restoration, settings and hold cancellation/acceleration. `EnsoSmokeTest` exercises actual Compose controls, DataStore writes, activity recreation and background/foreground transitions on a device. It restores the preferences and duration it changes and leaves the timer idle.

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. No release signing configuration is supplied.

See [VERIFICATION.md](VERIFICATION.md) for the completed build, test and physical-device checks and their limits.

Remaining manual coverage: Android 7–12 and 14+ devices, TalkBack, large font sizes, long screen-off/overnight runs and vendor battery restrictions. Android may terminate user-stopped or force-stopped apps; restoration on a later launch cannot recover sounds that were not played. Reboot continuation is intentionally unsupported. Audio cannot be audibly verified until a recording is provided.

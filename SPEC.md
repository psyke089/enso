# Enso — Product Specification

## 1. Product

Enso is a minimalist Android meditation timer and virtual meditation gong.

Package name:

`app.enso.meditation`

Enso should feel calm, tactile, timeless and extremely focused.

It is not a productivity dashboard, habit tracker, social app or wellness platform.

It is essentially:

- a beautiful meditation timer
- a virtual meditation gong
- a continuous interval gong when desired

The application must work completely offline.

There should be no onboarding flow.

Launching Enso should take the user directly to the timer.

---

# 2. Technology

Use the existing Android project.

Technology:

- Kotlin
- Jetpack Compose
- Material 3 only where useful
- Kotlin DSL / `build.gradle.kts`
- Minimum SDK 24
- Existing package name: `app.enso.meditation`

Prefer standard Android and Jetpack APIs where they are sufficient.

Avoid unnecessary dependencies.

Do not introduce:

- accounts
- cloud services
- analytics
- advertisements
- tracking
- network access
- unnecessary permissions

Do not change the package name.

---

# 3. Product Philosophy

Enso should feel intentionally small.

Every visible UI element must justify its existence.

Prefer:

- gesture
- space
- typography
- subtle state changes
- direct interaction

over:

- menus
- cards
- toolbars
- dashboards
- rows of buttons
- unnecessary screens

The timer itself should feel like the application.

The interface should feel calm even before a meditation begins.

Do not add functionality merely because Android or Material provides a component for it.

---

# 4. Core Experience

The main screen is centered around a large hand-painted Enso brush circle containing the selected or remaining meditation time.

When no session is active, the Enso circle is both:

- the primary visual element
- the duration selector

When a session is active, the Enso becomes primarily a calm visualization of the meditation.

The user can:

- select the meditation duration directly on the Enso
- start a meditation
- pause it
- resume it
- stop it
- hear a gong when meditation begins
- hear a gong when an interval ends
- optionally repeat the interval indefinitely using Continuous Gong

The normal workflow should require essentially no navigation.

---

# 5. Visual Identity

The visual direction is traditional Japanese ink painting and calligraphy interpreted as a modern Android interface.

The screen should feel like ink painted onto Japanese washi paper.

Primary visual characteristics:

- warm off-white / beige washi paper background
- subtle organic paper texture
- charcoal / sumi black ink
- large imperfect hand-painted Enso brush circle
- dry-brush edges
- visible brush texture
- natural ink irregularities
- restrained typography
- subtle Japanese calligraphic influence
- optional small vermilion / red seal accent
- subtle sumi-e landscape or ink-wash decoration
- generous negative space
- extremely little visual clutter

The interface must not look like a default Android application.

Avoid:

- glossy effects
- neon colors
- strong synthetic gradients
- generic Material cards
- excessive dividers
- generic settings-dashboard styling
- floating panels everywhere
- unnecessary rounded rectangles
- visual noise
- decorative animation for its own sake

The result should feel handcrafted, organic and meditative.

---

# 6. Visual Hierarchy

The primary visual hierarchy should be:

1. the Enso itself
2. selected / remaining time
3. primary timer action
4. secondary timer action when needed
5. subtle app identity
6. discreet settings access
7. decorative artwork

Decorative elements must never compete with the timer.

---

# 7. Japanese Visual Elements

Japanese writing may be used sparingly as a decorative visual element.

Do not fabricate meaningless Japanese characters.

Any Japanese text used must be real and semantically appropriate.

For example:

`今ここ`

meaning approximately:

"here and now"

is acceptable.

A restrained vermilion seal may accompany such an element.

Japanese decorative elements must never become visual clutter.

---

# 8. Artwork and Assets

Use real Compose UI for:

- timer text
- controls
- settings
- state
- gestures
- interaction

Do NOT use a complete screenshot or mockup image as the application background.

Decorative assets may include:

- Enso brush circle
- washi paper texture
- sumi-e landscape
- ink wash
- seal
- app icon artwork

The intended final visual assets should ideally include:

1. a transparent hand-painted Enso brush circle
2. a subtle seamless or large-format washi paper texture
3. a transparent or softly blended sumi-e landscape / ink wash
4. a simple Enso launcher icon

If final artwork is not yet available:

- create clean temporary fallback visuals
- preserve the intended layout
- structure the UI so assets can easily be replaced later

Do not block development because final artwork is missing.

Do not generate fake Japanese calligraphy through ordinary fonts if it looks artificial.

If no suitable calligraphy artwork exists, prefer restrained normal typography until the real asset is available.

---

# 9. Theme

The primary visual theme is warm washi paper with sumi ink.

Do not automatically transform Enso into a generic dark Material theme when Android dark mode is enabled.

For v1, Enso may intentionally retain its paper theme regardless of system light/dark mode.

System status and navigation bars should visually integrate with the paper background.

Use edge-to-edge layout correctly.

Respect all system insets.

---

# 10. Main Screen

The main screen should contain only what is necessary.

Primary elements:

- subtle Enso identity / title
- discreet settings control
- large Enso brush circle
- selected or remaining time inside the circle
- minimal Start / Pause / Resume control
- Stop control while a session is active

Do not show preset duration buttons.

Do not show a row such as:

`5 / 10 / 15 / 20 / 30 / 45 / 60`

The large Enso circle itself is the duration selector.

---

# 11. Duration Selection

Duration is adjusted directly by interacting with the Enso circle.

Duration adjustment is available only when no meditation session is active.

The Enso conceptually contains two large invisible interaction zones.

Left half:

- tap decreases duration by exactly 5 minutes

Right half:

- tap increases duration by exactly 5 minutes

Example:

`5 → 10 → 15 → 20 → 25 → 30 → ...`

Minimum duration:

`5 minutes`

Maximum duration:

`24 hours`

Interaction zones should be large and forgiving.

The user should not need to precisely hit a small icon.

---

# 12. Duration Visual Hints

Duration adjustment must remain discoverable without destroying the minimal appearance.

Very subtle hints are acceptable while no session is running.

Examples:

- faint `−` on the left
- faint `+` on the right
- tiny brush marks
- understated chevrons
- subtle opacity changes when touching either side

These hints must remain secondary to the Enso and timer.

Avoid obvious large plus/minus buttons outside the circle.

The screen should remain calm when the user is not interacting.

---

# 13. Press and Hold Duration Adjustment

Holding either side of the Enso repeatedly adjusts the duration.

Hold left:

- continuously decrease by 5-minute steps

Hold right:

- continuously increase by 5-minute steps

Expected behavior:

- a normal tap performs exactly one 5-minute adjustment
- holding initially repeats relatively slowly
- continuing to hold gradually increases repetition speed
- releasing immediately stops adjustment

Acceleration should change the frequency of adjustments.

Each individual adjustment must still be exactly 5 minutes.

Do not suddenly start changing by 30-minute or 60-minute jumps.

The interaction should feel smooth and predictable.

Respect:

- minimum 5 minutes
- maximum 24 hours

Never allow:

- negative durations
- overflow
- unreasonable duration values

---

# 14. Duration During Active Sessions

Duration adjustment must be disabled while the timer is:

- running
- paused

Touching the left or right half of the Enso during an active or paused session must never silently modify the session duration.

After the session is stopped, duration adjustment becomes available again.

---

# 15. Duration Persistence

The selected duration must be persisted locally.

The last selected duration becomes the default the next time Enso is opened.

Example:

The user selects:

`35 minutes`

The user closes Enso.

The user opens Enso later.

Enso should display:

`35:00`

The default value of 10 minutes should only be used if no previous duration has ever been stored.

Persist the selected duration immediately after adjustment.

Do not wait for application shutdown.

Use the same local persistence solution used for application settings.

---

# 16. Timer Display

For durations below one hour use:

`MM:SS`

Examples:

`05:00`

`35:42`

For durations of one hour or more use:

`H:MM:SS`

Examples:

`1:00:00`

`2:35:17`

Timer typography should remain visually stable as digits change.

Avoid horizontal jumping.

Use tabular / fixed-width numerals where practical.

The timer should remain visually dominant inside the Enso.

---

# 17. Idle State

When no meditation is active:

- display the selected duration
- allow duration adjustment
- show the primary Start control
- hide unnecessary session controls
- keep the Enso mostly calm and static

There should not be separate Reset or Stop controls when they have no purpose.

---

# 18. Start

When Start is pressed:

- start a new meditation session
- freeze duration selection
- optionally play the Start Gong
- establish the timer timing reference
- transition the UI into Running state

The timer begins immediately when Start is pressed.

Do not wait for the gong sound to finish before beginning the countdown.

The Start Gong should sound exactly once for a newly initiated session.

It must not replay because of:

- Compose recomposition
- rotation
- lifecycle events
- state restoration
- opening settings

---

# 19. Running State

While running:

- show remaining time prominently
- disable duration adjustment
- provide Pause
- provide Stop
- keep all secondary UI visually quiet

The running screen should become even calmer than the idle screen.

Avoid unnecessary text such as:

- "Timer running"
- "Meditation in progress"
- "Current session"

The visual state itself should communicate this.

---

# 20. Pause

Pause must:

- freeze the remaining duration accurately
- prevent progression toward the next interval
- preserve remaining time
- transition the main action to Resume

Pause must not:

- reset the interval
- play another Start Gong
- trigger an End Gong

---

# 21. Resume

Resume must:

- continue from the exact remaining duration
- establish a new monotonic timing deadline
- avoid cumulative timing drift

Resume must not restart the full selected duration.

---

# 22. Stop

Stopping an active or paused meditation must:

- terminate the active session
- terminate Continuous Gong repetition
- cancel active timer/background work
- stop session-specific wake behavior
- return the displayed timer to the selected duration
- re-enable duration adjustment

Stopping manually must NOT play the End Gong.

After stopping, Enso returns to its calm Idle state.

A separate Reset button is unnecessary if Stop already produces this behavior.

---

# 23. Timer Architecture

Timer accuracy is important.

Do NOT implement the timer by simply subtracting one second from a variable every second.

Use a monotonic time source such as:

`SystemClock.elapsedRealtime()`

or an equivalent appropriate monotonic clock.

Calculate remaining time from actual elapsed time and an absolute deadline.

The UI may refresh approximately once per second.

The underlying timer must NOT depend on the number of UI ticks that occurred.

This prevents drift caused by:

- thread scheduling
- Compose rendering
- app backgrounding
- device load
- delayed coroutine execution

There must be exactly one source of truth for an active meditation session.

Prevent duplicate timers from running simultaneously.

Critical timing logic must not live directly inside Composable functions.

---

# 24. Continuous Gong

Settings contain an option named:

`Continuous Gong`

Continuous Gong turns Enso into a repeating interval gong.

Example:

Selected duration:

`10 minutes`

Result:

`10:00`
→ `00:00`
→ Gong
→ `10:00`
→ `00:00`
→ Gong
→ `10:00`
→ ...

This continues indefinitely until the user explicitly stops the session.

---

# 25. Continuous Gong Timing

Continuous Gong must avoid cumulative drift.

Do NOT implement repetition as:

"when this timer finishes, wait another N minutes"

Repeated boundaries should be based on a stable session timing anchor.

Conceptually:

`interval 1 = start + duration`

`interval 2 = start + duration × 2`

`interval 3 = start + duration × 3`

and so on,

while correctly accounting for paused time.

Audio playback duration must not affect interval timing.

The next interval must never begin only after the previous gong audio finishes.

A 10-minute Continuous Gong should remain aligned to ten-minute intervals even after running for many hours.

---

# 26. Continuous Gong Behavior

When Continuous Gong is enabled:

- reaching zero triggers one interval completion event
- optionally play the End Gong
- optionally vibrate
- immediately continue into another interval of the same duration
- repeat indefinitely until explicitly stopped

The Start Gong must play only when the user initially starts the complete session.

It must NOT replay at every Continuous Gong interval.

Pause pauses the current interval.

Resume continues that interval.

Stop terminates the entire repeating session.

Continuous Gong should be subtly indicated on the main screen while enabled.

Do not add a large banner or status panel.

---

# 27. End Gong and Continuous Gong

The `End Gong` setting controls whether a gong sound plays at an interval boundary.

This includes:

- the final boundary of a normal meditation
- every interval boundary during Continuous Gong

Therefore:

Continuous Gong ON + End Gong ON:

- repeat timer
- play gong every interval

Continuous Gong ON + End Gong OFF:

- repeat timer silently

Continuous Gong controls repetition.

End Gong controls sound.

Keep these responsibilities separate.

---

# 28. Missed Intervals and Restoration

If the app process is temporarily destroyed or recreated while a Continuous Gong session is active:

- restore the active session when reasonably possible
- calculate the current interval from the original timing reference
- do not shift future intervals merely because restoration occurred late

Do NOT replay a burst of previously missed gong sounds.

Example:

If three interval boundaries passed while Enso could not play sound, restoring the app must NOT immediately play three gongs.

Restore the current timer position and continue normally.

Only future real-time boundaries should generate new completion events.

---

# 29. Background Behavior

A running meditation should continue accurately when:

- Enso is backgrounded
- the user opens another app
- the display turns off
- the screen locks

The user should not be required to keep Enso visible for the timer to function correctly.

Use an Android mechanism appropriate for reliable active-session timing and completion.

Do not use WorkManager for precise meditation interval timing.

If a foreground service or another ongoing mechanism is required for reliable behavior, it may be active only while a meditation session is active.

Do not leave unnecessary background components running while Enso is idle.

If an ongoing foreground notification is required:

- keep it quiet
- keep it minimal
- do not use notification sound
- do not use notification vibration
- expose only genuinely useful session information/actions

Request only permissions that are genuinely required.

---

# 30. Device / Process Restart Behavior

Ordinary application lifecycle changes should not destroy an active session.

Persist enough state to restore an active session when reasonably possible.

An actual Android device reboot does NOT need to preserve an active meditation session in v1.

Do not add boot receivers merely to continue a timer after a complete phone reboot unless explicitly required later.

---

# 31. Active Session Persistence

If practical, persist enough information to reconstruct an active meditation session.

Relevant information may include:

- selected interval duration
- session state
- timing anchor / deadline
- accumulated paused duration
- Continuous Gong state relevant to that session

Do not persist changing remaining seconds every second if the current position can instead be reconstructed from timing anchors.

Prefer reconstructable state over frequent storage writes.

---

# 32. Gong Audio

Enso uses local audio assets.

Logical sounds:

- Start Gong
- End / Interval Gong

The initial version may use the same recording for both.

The audio implementation should make it easy to support multiple gong recordings later.

Expected location:

`app/src/main/res/raw/`

Prefer:

- OGG
- WAV

Use a high-quality meditation / temple gong recording.

Do not download random copyrighted audio.

Do not add network audio playback.

Do not require internet connectivity.

---

# 33. Missing Audio Assets

Development must not fail merely because the final gong recording has not yet been provided.

If the expected audio asset does not exist:

- the project must still compile
- the timer must still function
- audio playback should gracefully do nothing
- leave a clear implementation note describing the expected asset name and location

Do not add compile-time references to nonexistent resources.

Do not fabricate random binary audio assets merely to make the build pass.

---

# 34. Gong Playback Correctness

Audio must never influence timer accuracy.

A single timer boundary may trigger at most one gong playback.

Recomposition must not replay audio.

Lifecycle restoration must not replay audio.

Opening settings must not replay audio.

Rotation must not replay audio.

Ensure completion events are consumed exactly once.

---

# 35. Settings

Settings should be accessible through one discreet control.

Prefer:

- compact bottom sheet
- lightweight dialog
- similarly minimal interaction

over a deep navigation hierarchy.

Settings:

- Start Gong
- End Gong
- Continuous Gong
- Keep Screen Awake
- Vibration

Persist all settings locally.

Settings survive application restarts.

Use a simple modern Android persistence solution such as DataStore unless there is a strong reason not to.

The settings UI must visually belong to Enso.

Do not suddenly display a generic bright Material settings screen that clashes with the paper / ink design.

---

# 36. Default Settings

Suggested defaults:

- selected duration: 10 minutes
- Start Gong: ON
- End Gong: ON
- Continuous Gong: OFF
- Keep Screen Awake: ON
- Vibration: OFF

These defaults may only be changed if a concrete implementation or platform reason requires it.

---

# 37. Start Gong

Setting:

`Start Gong`

When enabled:

play the start gong exactly once when the user explicitly starts a new meditation session.

When disabled:

begin silently.

Continuous Gong repetition must never trigger another Start Gong.

---

# 38. End Gong

Setting:

`End Gong`

When enabled:

play the gong when an interval completes.

When disabled:

interval completion is silent.

This applies both to:

- normal sessions
- Continuous Gong intervals

---

# 39. Keep Screen Awake

Setting:

`Keep Screen Awake`

When enabled:

keep the display awake while an active meditation is running.

Do not keep the screen awake while Enso is idle.

Remove screen-awake behavior immediately when:

- the session stops
- the session completes without Continuous Gong
- the setting is disabled

Paused behavior may keep the screen awake if the session remains active, but this must be intentional and consistent.

Do not keep wake behavior active unnecessarily.

---

# 40. Vibration

Setting:

`Vibration`

When enabled:

perform one subtle vibration when an interval completes.

The vibration should feel calm.

Do not use:

- aggressive alarm patterns
- repeated buzzing
- long vibration sequences

Continuous Gong should produce at most one subtle vibration per interval boundary.

---

# 41. Lifecycle and State

Compose recomposition must not:

- restart timers
- duplicate timer jobs
- replay audio
- reset duration
- recreate completion events

Configuration changes must not create duplicate sessions.

Keep timer/business logic separate from Composable rendering.

Use ViewModel or another appropriate state holder where it provides a clear lifecycle benefit.

---

# 42. UI State Model

At minimum, the timer should clearly represent states equivalent to:

- Idle
- Running
- Paused

Continuous Gong is a behavior/settings flag, not a separate screen state.

UI behavior should follow the state model consistently.

Idle:

- duration adjustable
- Start available

Running:

- duration locked
- Pause available
- Stop available

Paused:

- duration locked
- Resume available
- Stop available

---

# 43. Architecture

Keep the architecture intentionally small.

Reasonable separation includes:

- timer/session state
- Compose UI
- settings persistence
- audio playback
- active-session/background handling

Do not introduce architecture merely for architectural fashion.

Avoid unnecessary:

- repository layers
- use-case classes
- dependency injection frameworks
- service locators
- interfaces with only one trivial implementation
- architectural ceremony

Prefer readable code over abstraction.

Enso is a small application.

Its source code should reflect that.

---

# 44. Controls

Controls should be minimal.

Idle:

- one prominent Start control

Running:

- Pause
- Stop

Paused:

- Resume
- Stop

Avoid showing controls that currently do nothing.

Do not permanently display Reset, Pause, Stop and Start simultaneously.

The visual hierarchy should remain obvious without labels everywhere.

---

# 45. Enso Interaction

The Enso is the heart of the application.

Idle:

- visual meditation object
- duration display
- duration input surface

Running:

- remaining time display
- meditation visualization

Paused:

- remaining time display
- subtly communicate paused state

Do not transform the Enso into a conventional circular fitness progress ring.

If progress visualization is used, it must remain subtle and organic.

The original hand-painted nature of the Enso should remain visually dominant.

---

# 46. Animation

Animations should be subtle and slow.

Acceptable examples:

- gentle state transitions
- subtle fade between controls
- slight breathing-like visual movement while running
- restrained evolution of ink opacity
- extremely subtle movement of the Enso
- calm sheet transitions

Avoid:

- spinning rings
- bouncing controls
- celebratory effects
- particle effects
- aggressive progress animations
- constant motion demanding attention

Respect Android animation/accessibility preferences where practical.

The user should be able to look at the screen without the animation demanding attention.

---

# 47. Touch Feedback

Touch feedback should be restrained.

Avoid obvious bright default Material ripples if they clash with the visual identity.

Use subtle feedback appropriate to ink and paper.

For duration adjustment, gently indicate which half of the Enso is being touched.

Feedback must not obscure the timer.

---

# 48. Accessibility

Minimalism must not reduce usability.

Provide comfortable touch targets.

Invisible left/right duration zones must expose meaningful accessibility semantics.

Examples:

Left zone:

`Decrease meditation duration by 5 minutes`

Right zone:

`Increase meditation duration by 5 minutes`

Interactive icons require appropriate content descriptions.

Important state must not be communicated through color alone.

Maintain sufficient ink / paper contrast.

Timer text must remain readable with reasonable system font scaling.

---

# 49. Typography

Typography should complement the Japanese ink aesthetic without becoming theatrical.

Use a restrained, highly legible typeface for:

- timer digits
- settings
- controls

Brush/calligraphy styling should primarily belong to:

- Enso artwork
- optional title artwork
- small decorative elements

Do not render important UI text using a difficult-to-read fake brush font.

Timer readability is more important than stylistic purity.

---

# 50. App Icon

The intended launcher icon is:

a simple black ink Enso circle on warm washi paper.

It should remain recognizable at small sizes.

Avoid:

- text
- complex landscape artwork
- tiny Japanese characters
- generic lotus meditation icons

The Enso itself should be the identity.

Support Android adaptive icon resources.

---

# 51. No Network

Enso must function entirely without internet access.

Do not request the `INTERNET` permission unless a future explicitly requested feature requires it.

Do not:

- load remote fonts
- fetch artwork remotely
- stream audio
- contact analytics services
- perform network synchronization

Everything required for meditation should exist locally on the device.

---

# 52. Privacy

Enso stores only local application state required to function.

No telemetry.

No analytics.

No advertising identifiers.

No behavioral tracking.

No remote crash analytics in v1.

No account.

No profile.

No cloud.

---

# 53. Non-Goals

Do NOT add:

- login
- registration
- profiles
- cloud synchronization
- social features
- friends
- meditation statistics
- streaks
- achievements
- gamification
- leaderboards
- subscriptions
- advertisements
- analytics
- telemetry
- tracking
- meditation content library
- guided meditation streaming
- AI features
- journal
- calendar
- habit system
- unnecessary navigation
- unnecessary screens

Enso is a timer and a gong.

Keep it that way.

---

# 54. Unit Tests

Add meaningful deterministic tests for timer/session logic.

At minimum cover:

- initial selected duration
- duration increase by exactly 5 minutes
- duration decrease by exactly 5 minutes
- minimum duration of 5 minutes
- maximum duration of 24 hours
- duration cannot change during Running
- duration cannot change during Paused
- normal countdown calculation
- pause
- resume
- stop
- normal completion
- Continuous Gong interval rollover
- repeated interval calculation
- no cumulative interval drift
- exactly one completion event per boundary
- exactly one gong event per eligible boundary
- Start Gong does not repeat on Continuous Gong rollover
- restoration does not replay old completion events
- persisted selected duration restoration

Focus tests on deterministic state/timing logic.

Do not spend excessive effort testing Compose rendering details.

---

# 55. Duration Hold Behavior Tests

The underlying duration-adjustment logic should be testable independently from gesture rendering where practical.

Verify:

- repeated increase uses 5-minute increments
- repeated decrease uses 5-minute increments
- lower limit is respected
- upper limit is respected
- adjustment stops when interaction ends

Exact UI gesture timing does not require fragile instrumentation tests unless necessary.

---

# 56. Build and Code Quality

Inspect the existing project before making changes.

Reuse the existing project structure where reasonable.

Remove leftover template UI such as:

`Hello Android`

Keep code idiomatic and understandable.

Do not introduce unnecessary dependencies.

Do not make unrelated changes.

Do not change the package name.

Do not commit changes to Git.

Do not add generated build output to Git.

Do not edit `.gitignore` unless required for a concrete reason.

Do not add:

- secrets
- API keys
- signing keys
- keystores

---

# 57. Implementation Autonomy

Work through the implementation autonomously.

Do not stop after every file to ask for confirmation.

When a minor implementation decision is unspecified:

choose the simplest reasonable solution consistent with this specification.

When artwork or audio is missing:

implement the surrounding functionality cleanly and continue.

Do not abandon implementation because a final decorative asset has not yet been supplied.

---

# 58. Priority Order

When requirements appear to conflict, prioritize them in this order:

1. timer correctness
2. no duplicate timer/gong events
3. reliable user interaction
4. reliable lifecycle/background behavior
5. simplicity
6. calm/minimal visual design
7. decorative polish

Do not sacrifice timer correctness for animation or visual effects.

---

# 59. Development / Verification Environment

A physical Android phone is connected to this development machine using USB debugging.

It should normally be visible through:

`adb devices`

The connected Android device may be used for non-destructive development verification.

When practical:

- verify device availability using `adb devices`
- build the debug APK
- install the debug APK on the connected device
- launch Enso on the device
- inspect Logcat / adb output for crashes
- inspect logs for obvious runtime failures
- perform reasonable smoke testing of the application

The device is a development target.

Treat it carefully.

Do NOT:

- factory-reset the device
- modify unrelated user data
- uninstall unrelated applications
- change unrelated global device settings
- erase storage
- perform destructive adb commands
- manipulate unrelated applications
- reboot the phone unnecessarily

Only interact with Enso and development-related Android facilities required to verify Enso.

If the device is unavailable or adb access fails:

- continue with build/test verification
- do not treat device unavailability as an application build failure
- report that physical-device verification could not be completed

---

# 60. Physical Device Smoke Test

When the connected Android device is available, perform a reasonable smoke test.

At minimum verify:

- Enso installs successfully
- Enso launches without crashing
- main screen renders
- duration can be increased
- duration can be decreased
- Start begins a timer
- Pause pauses it
- Resume continues it
- Stop returns to Idle
- settings can be opened
- settings can be changed
- app can be backgrounded and restored without obvious failure

Use a short test duration or appropriate internal/test mechanisms when necessary.

Do not waste development time waiting for long real-time intervals merely to test timer logic.

Long-duration and Continuous Gong timing correctness should primarily be validated through deterministic automated tests.

---

# 61. Runtime Log Verification

During physical-device verification:

inspect relevant logs for:

- crashes
- uncaught exceptions
- repeated service creation
- duplicate timer behavior
- obvious lifecycle errors
- audio playback exceptions
- persistence exceptions

Do not treat unrelated operating-system log noise as an Enso bug.

---

# 62. Definition of Done

The first usable version of Enso is complete when:

- the application launches successfully
- the default Android template UI is gone
- the Enso timer screen exists
- the washi / sumi visual direction is clearly established
- duration is adjustable through left/right Enso interaction
- tap changes duration by exactly 5 minutes
- hold repeatedly changes duration and accelerates
- selected duration persists across app restarts
- Start works
- Pause works
- Resume works
- Stop works
- normal timer completion works
- Continuous Gong works
- Continuous Gong does not accumulate timing drift
- timer survives ordinary Android lifecycle/background transitions reasonably
- settings persist
- audio architecture exists
- missing final audio assets do not break the build
- Keep Screen Awake works
- vibration option works
- no unnecessary network/account/tracking functionality exists
- meaningful deterministic timer tests exist
- the debug APK builds successfully
- physical-device verification has been attempted when adb is available

---

# 63. Completion Procedure

Before declaring implementation complete:

1. Review all modified and newly created files.

2. Look specifically for:
   - lifecycle bugs
   - concurrency bugs
   - duplicate timers
   - duplicate completion events
   - duplicate gong events
   - unnecessary background work

3. Run unit tests.

4. Run Android lint where practical.

5. Run:

   `./gradlew assembleDebug`

6. Fix all build errors.

7. Repeat tests/build until successful.

8. Run:

   `adb devices`

9. If the connected Android device is available:
   - install the debug APK
   - launch Enso
   - perform the physical-device smoke test
   - inspect relevant logs for obvious runtime failures

10. Do not perform destructive or unrelated operations on the connected device.

11. Do not commit anything to Git.

12. Summarize:
    - what was implemented
    - important architectural decisions
    - tests added
    - build result
    - device-test result
    - remaining limitations
    - missing visual assets
    - missing gong audio assets
    - anything requiring manual testing

The final implementation summary must not claim functionality was tested if it was not actually tested.

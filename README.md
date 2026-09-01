# NowPill

A Material 3 Expressive "live activity" pill for Android — inspired by Samsung's Now Bar.

## What it does
- Floating pill, docked top-left, showing whichever trackers you enable:
  music now-playing, live internet speed, stopwatch, countdown timer, download progress.
- Fluid morph/crossfade animations; **animation speed is user-adjustable (0.5x–2x)**
  from the in-app slider.
- Fully draggable; remembers where you leave it.
- Trackers are individually toggleable in Settings.
- Built with Kotlin + Jetpack Compose, Material 3 dark expressive theme,
  targets Android 16 (API 36), works from Android 10 (API 29) up.

## Important limitation: the real lock screen
Since Android 10, **no third-party app is allowed to draw a floating overlay
on top of the actual lock screen** — that capability (Samsung's Now Bar,
Pixel's Now Playing) is reserved for OEM system apps. Any app on the Play
Store claiming to put a live overlay pill directly on your lock screen is
either a system app, or (more commonly) doing what this project does:
using a real `Activity` with `setShowWhenLocked()` that opens **on top of**
the lock screen when tapped/launched — not a persistent background overlay.
That's what `LockPillActivity` in this project does.

## Permissions this app asks for
- **Display over other apps** — required for the floating pill.
- **Notification access** — required so the app can detect what's
  currently playing and which downloads are active (Android doesn't expose
  a generic "now playing" API without it).

## Project structure
- `PillOverlayService` — the always-on foreground service that draws the pill.
- `overlay/PillContent.kt` — the actual Compose UI + animations.
- `tracker/` — network speed, stopwatch, timer logic.
- `service/MediaListenerService.kt` — reads media/download notifications.
- `MainActivity` — settings screen (permissions, tracker toggles, animation speed slider).
- `LockPillActivity` — lock-screen companion view.

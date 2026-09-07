# Olauncher | Minimal AF Launcher

AF stands for Ad-Free! :D

A minimal Android launcher: no icons, no ads, no distractions. Your apps as
text, a handful of gestures, and an optional daily wallpaper.

> Fork of [Olauncher](https://github.com/tanujnotes/Olauncher), licensed under
> GPLv3. Files in this repository have been modified from the original; the main
> addition is the widgets screen described below.

## Widgets

Swipe right on the home screen to open a customizable widgets page:

- **Calendar** — month view with your calendar events marked
- **Events** — upcoming events from your device calendars (asks for calendar permission)
- **Year progress** — a dot grid showing how much of the year has elapsed
- **Obsidian note** — renders a Markdown note you pick; tap to open it in Obsidian

Tap **Edit** to add, remove, reorder or resize widgets. The layout is saved on device.

## Gestures

- Double tap to lock the screen
- Swipe left to open an app, swipe right for the widgets screen
- Swipe down for notifications (or search)

## Privacy

No accounts, no analytics, no tracking. Full details in [PRIVACY.md](PRIVACY.md).

- The Calendar and Events widgets read your device calendar locally; nothing leaves the device.
- The Obsidian widget reads only the single file you choose.
- The optional daily wallpaper downloads an image from the internet, only while enabled.

## Build

Standard Android project:

```
./gradlew assembleDebug
```

Requires the Android SDK; set `sdk.dir` in `local.properties`.

## License

[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html), same as the original project.

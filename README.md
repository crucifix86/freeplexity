# FreePlexity

A clean, lightweight Android TV Plex client. No bloat, no streaming channels, no forced logins — just your library and a proper player.

## Features

- **Home screen** with Continue Watching, On Deck, and Recently Added rows
- **Library grid views** sorted alphabetically with proper catalogue browsing
- **Media3/ExoPlayer** playback — direct play first, automatic transcode fallback
- **Subtitle & audio track selection** with codec info (AC3, EAC3, DTS, AAC, etc.)
- **Cast & crew** with photos pulled from Plex metadata
- **Auto-play next episode** with cross-season support
- **Watch progress sync** — progress reports back to your Plex server in real time
- **Search** across all libraries
- **Settings** for transcode quality, audio passthrough, subtitle size

## Requirements

- Android TV device (any box running Android TV / Google TV)
- Plex Media Server on your local network

## Building

Open the project in Android Studio and build, or from the command line:

```
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

Install via ADB:

```
adb install app-debug.apk
```

## Setup

On first launch, FreePlexity connects directly to your Plex server. Edit `AuthActivity.kt` with your server's IP and Plex token.

You can grab your Plex token from your server's `Preferences.xml` or from `localStorage.getItem("myPlexAccessToken")` in the Plex web player's browser console.

## Tech Stack

- Kotlin
- AndroidX Leanback (TV UI framework)
- Media3 / ExoPlayer (playback)
- OkHttp (networking)
- Glide (image loading)
- Plex REST API

## License

Personal use.

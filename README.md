# FreePlexity

A clean, lightweight Android TV Plex client. No bloat, no streaming channels, no forced logins — just your library and a proper player.

## Features

- **Home screen** with Continue Watching, On Deck, and Recently Added rows
- **Library grid views** sorted alphabetically with A-Z quick jump
- **Media3/ExoPlayer** playback — direct play first, automatic transcode fallback
- **Subtitle & audio track selection** with codec info (AC3, EAC3, DTS, AAC, etc.)
- **Cast & crew** with photos pulled from Plex metadata
- **Auto-play next episode** with cross-season support
- **Watch progress sync** — progress reports back to your Plex server in real time
- **Search** with live results as you type
- **Settings** for transcode quality, audio passthrough, subtitle size
- **OTA updates** — app checks GitHub for new releases and installs them

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

On first launch, the setup wizard asks for your server address and offers two ways to connect:

### Option 1 — Connect Without Token (recommended for home use)

This requires telling your Plex server to allow your local network without authentication:

1. Open your Plex server's web UI (e.g. `http://192.168.1.100:32400/web`)
2. Go to **Settings > Network**
3. Click **Show Advanced**
4. In **"List of IP addresses and networks that are allowed without auth"**, add your local network:
   ```
   192.168.1.0/255.255.255.0
   ```
   (adjust if your network uses a different subnet)
5. Save and restart Plex

Now in FreePlexity, enter your server address (e.g. `192.168.1.100:32400`) and hit **"Connect Without Token"**. Done — no accounts, no cloud, fully local.

### Option 2 — Connect With Plex Account

If you don't want to change your server settings:

1. Enter your server address and hit **"Connect With Plex Account"**
2. A 4-character code appears on screen
3. On your phone or computer, go to **plex.tv/link** and enter the code
4. The app stores the token locally — it only contacts Plex servers during this one-time setup

### Signing out / changing server

Go to **Settings > Sign Out** in the app to clear your connection and re-run the setup wizard.

## Pushing an Update

The app has built-in OTA updates. Users see a red badge on the settings icon when a new version is available, and can download + install from within settings.

To publish a new version:

### 1. Bump the version numbers

In `app/build.gradle.kts`, increment both values:

```kotlin
versionCode = 6        // must be higher than previous
versionName = "1.5"    // display name
```

### 2. Update `version.json` in the repo root

```json
{
  "versionCode": 6,
  "versionName": "1.5",
  "apkUrl": "https://github.com/crucifix86/freeplexity/releases/latest/download/freeplexity.apk",
  "changelog": "Description of what changed"
}
```

The `versionCode` here must match `build.gradle.kts`. The `apkUrl` always points to `/latest/download/` so it auto-resolves to the newest release.

### 3. Build the APK

```bash
./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk freeplexity.apk
```

### 4. Commit and push

```bash
git add -A
git commit -m "v1.5 - description of changes"
git push
```

### 5. Create a GitHub release

```bash
gh release create v1.5 freeplexity.apk --title "FreePlexity v1.5" --notes "Description of changes"
```

The APK **must** be named `freeplexity.apk` in the release — that's what the download URL expects.

### How it works

- On app launch, `AppUpdater` fetches `version.json` from the `main` branch on GitHub
- Compares the remote `versionCode` against the installed app's version
- If remote is higher, shows a red **!** badge on the settings gear
- In settings, "Update Available" appears with the changelog
- User clicks it — APK downloads with progress, then the system installer opens

## Tech Stack

- Kotlin
- AndroidX Leanback (TV UI framework)
- Media3 / ExoPlayer (playback)
- OkHttp (networking)
- Glide (image loading)
- Plex REST API

## License

Personal use.

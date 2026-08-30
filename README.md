# TV Home

An Android TV app that creates launcher channels from any Stremio-compatible manifest (xperience-app.com, aiometadatafortheweebs, etc.) and syncs them to the Android TV home screen.

## How It Works

```
1. User adds manifest URL
       │
       ▼
2. App fetches manifest.json → parses catalog list
       │
       ▼
3. User selects which catalogs to enable (toggle switches)
       │
       ▼
4. "Sync Channels Now" → fetches catalog data (sequential, one at a time)
       │
       ▼
5. Launcher channels created in Android TV (Projectivy / Leanback)
       │
       ▼
6. User taps channel → opens content in Nuvio or Stremio
```

## Features

- **Manifest-Based** — Add any Stremio-compatible manifest URL (no API keys needed)
- **Android TV Home Screen Integration** — Creates launcher channels (works with Projectivy Launcher, Android TV Leanback)
- **Simple UI** — Add manifest → select catalogs → sync
- **Deep Linking** — Opens content in Nuvio or Stremio
- **Background Sync** — Automatic updates every 6 hours (on boot, on network change)
- **Composable UI** — Modern Jetpack Compose settings interface
- **Extensible** — Easy to add more playback providers

## Getting Started

### 1. Add a Manifest URL

After installing the app, click **"Manage Manifests"** and add a manifest URL:

```
https://xperience-app.com/manifest/{profileId}/{token}/manifest.json
https://example.com/stremio/{appId}/manifest.json
```

The app will automatically fetch the manifest and show all available catalogs.

### 2. Select Catalogs

Each catalog becomes a potential TV channel. Toggle on the catalogs you want to see.

### 3. Sync Channels

Click **"Sync Channels Now"** to fetch content and create launcher channels.

### 4. Watch

Open your Android TV launcher — you'll see the new channels with all the content.

## Deep Links

### Nuvio
```
nuvio://movie/{id}
nuvio://tv/{id}
```

### Stremio
```
stremio://detail/movie/tmdb:{id}
stremio://detail/series/tmdb:{id}
```

## Architecture

```
                    Manifest URL
                       │
                       ▼
              ┌─────────────────┐
              │ XperienceClient │  (fetch manifest + catalogs)
              └──────┬──────────┘
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
    Catalog Cache  Compose UI   Launcher Channels
        │            │            │
        │            │            │
        └───────┬────┘            │
                ▼                 │
          DeepLink Router         │
             │    │              │
             ▼    ▼              │
           Nuvio Stremio         │
                           (Projectivy)
```

## Tech Stack

- **Kotlin** 2.1.0
- **Jetpack Compose** (Material 3)
- **OkHttp** + Gson (HTTP client)
- **WorkManager** for background sync
- **DataStore** for preferences
- **Android TV Channels** (androidx.tvprovider)

## Project Structure

```
app/src/main/
├── java/com/makeran218/recommendtmdb/
│   ├── MainActivity.kt        # Main TV UI + ViewModel
│   ├── ManifestModels.kt      # Manifest/catalog data models
│   ├── ManifestRepository.kt  # URL storage + caching
│   ├── XperienceClient.kt     # HTTP client for manifest/catalogs
│   ├── DeepLinks.kt           # Playback provider abstraction
│   ├── LauncherChannels.kt    # Android TV channel management
│   ├── SyncWorker.kt          # Background sync (WorkManager)
│   ├── SyncScheduler.kt       # Sync scheduling
│   ├── Preferences.kt         # Settings (playback app, display type)
│   └── BootReceiver.kt        # Boot-time sync trigger
├── res/
│   ├── values/
│   │   ├── colors.xml
│   │   ├── strings.xml
│   │   └── themes.xml
│   ├── drawable/
│   │   ├── banner.png         # Android TV banner
│   │   └── ic_launcher_foreground.png
│   ├── mipmap-*/              # App icons (mdpi through xxxhdpi)
│   └── xml/
│       ├── backup_rules.xml
│       └── data_extraction_rules.xml
└── AndroidManifest.xml
```

## CI/CD

This project uses GitHub Actions. Every push to `main` or `master` triggers:

1. Checkout code
2. Set up JDK 17
3. Run `./gradlew assembleDebug`
4. Upload APK as artifact

View builds at **Actions** tab in your GitHub repo. Download the APK from the run's artifacts.

## License

MIT

## Playback App Package Names

| App | Package Name |
|-----|-------------|
| Nuvio | `com.nuvio.tv` |
| Stremio | `com.stremio.one` |

These are the correct package names for Android TV versions of the apps.

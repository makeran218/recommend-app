# Recommended App

An Android TV app that displays TMDB content (movies, TV shows) and Netflix catalog as rows on the Android TV home screen, with deep linking to playback apps like **Nuvio** and **Stremio**.

## Features

- **Android TV Home Screen Integration** - Displays content as launcher channels (works with Projectivy Launcher, Android TV Leanback)
- **10 Content Rows** - Trending, Latest, Popular movies & TV shows + Netflix rows
- **Deep Linking** - Opens content in Nuvio (`nuvio://tmdb/...`) or Stremio (`stremio://detail/...`)
- **Background Sync** - Automatic updates via WorkManager (every 6 hours, on boot, on network change)
- **Netflix Integration** - Popular, New Movies & TV Shows available on Netflix US
- **Compose UI** - Modern Jetpack Compose settings interface
- **Extensible** - Easy to add more playback providers (Kodi, Plex, etc.)

## Content Categories

### TMDB Rows
| Category | TMDB Endpoint | Description |
|----------|--------------|-------------|
| Trending Movies | `/trending/movie/week` | Weekly trending movies |
| Trending TV | `/trending/tv/week` | Weekly trending TV shows |
| Latest Movies | `/discover/movie` (90-day window) | Recently released movies |
| Latest TV | `/discover/tv` | Recently released TV shows |
| Popular Movies | `/discover/movie` | Most popular movies |
| Popular TV | `/discover/tv` | Most popular TV shows |

### Netflix Rows (US Region)
| Category | TMDB Endpoint | Description |
|----------|--------------|-------------|
| Netflix Popular Movies | `/discover/movie` + Netflix filter | Popular movies on Netflix US |
| Netflix Popular TV | `/discover/tv` + Netflix filter | Popular TV shows on Netflix US |
| Netflix New Movies | `/discover/movie` + Netflix filter | Top rated movies on Netflix US |
| Netflix New TV | `/discover/tv` + Netflix filter | Top rated TV shows on Netflix US |

All Netflix rows filter by:
- `watch_region=US` — US catalog only
- `with_watch_providers=8` — Netflix only
- `with_watch_monetization_types=flatrate` — Subscription included (no rental/buy)

## Architecture

```
                    TMDB API
                       │
                       ▼
              ┌──────────────┐
              │ Retrofit API │
              └──────┬───────┘
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
    Room Cache   Compose UI   Launcher Channels
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

## Setup

### 1. Get a TMDB API Key

1. Go to [https://www.themoviedb.org/](https://www.themoviedb.org/)
2. Sign up / Log in
3. Go to Settings → API → Create an API Key
4. Copy your API key

### 2. Set the API Key

After installing the app on your Android TV, open it and enter your TMDB API key in the app's settings screen. You can get an API key at [https://www.themoviedb.org/settings/api](https://www.themoviedb.org/settings/api).

### 3. Build

```bash
./gradlew :app:assembleDebug
```

### 4. Install on Android TV

```bash
# Connect to your Android TV
adb connect TV_IP_ADDRESS:5555

# Install the app
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or use Android Studio's Run button with an Android TV emulator.

## Deep Links

### Nuvio
```
nuvio://tmdb/movie/{tmdb_id}
nuvio://tmdb/tv/{tmdb_id}
```

### Stremio
```
stremio://detail/movie/tmdb%3A{tmdb_id}
stremio://detail/series/tmdb%3A{tmdb_id}
```

### Adding a New Provider

Create a new class implementing `PlaybackProvider`:

```kotlin
class MyProvider : PlaybackProvider {
    override fun buildUri(item: TmdbItem): Uri {
        return Uri.parse("myapp://content/${item.type}/${item.id}")
    }
    override val scheme: String = "myapp"
    override val displayName: String = "My App"
}
```

Then register it in `DeepLinks`:

```kotlin
private val providers: Map<String, PlaybackProvider> = mapOf(
    "nuvio" to NuvioProvider(),
    "stremio" to StremioProvider(),
    "myapp" to MyProvider()
)
```

## Launcher Channel Integration

The app creates Android TV `PreviewChannel` entries that are visible to:

- **Projectivy Launcher** - Full channel support
- **Android TV Leanback** - Channel rows on home screen
- **Google TV** - May vary by device/version

Channel structure in Projectivy:
```
Recommended App
 ├── Trending Movies
 │    ├── Movie A
 │    ├── Movie B
 │    └── Movie C
 ├── Trending TV
 │    ├── Show A
 │    └── ...
 ├── Netflix Popular Movies
 │    ├── Netflix Movie A
 │    └── ...
 └── Netflix New Movies
      ├── Netflix Movie X
      └── ...
```

## Tech Stack

- **Kotlin** 2.1.0
- **Jetpack Compose** (Material 3)
- **Retrofit** + OkHttp + Gson
- **Room** for local caching
- **WorkManager** for background sync
- **DataStore** for preferences
- **Android TV Channels** (androidx.tvprovider)

## Project Structure

```
app/src/main/
├── java/com/makeran218/recommendtmdb/
│   ├── MainActivity.kt        # Main TV UI + ViewModel
│   ├── TmdbClient.kt          # Retrofit API client
│   ├── DeepLinks.kt           # Playback provider abstraction
│   ├── LauncherChannels.kt    # Android TV channel management
│   ├── SyncWorker.kt          # Background sync (WorkManager)
│   ├── SyncScheduler.kt       # Sync scheduling
│   ├── Preferences.kt         # Settings + category config
│   └── BootReceiver.kt        # Boot-time sync trigger
├── res/
│   ├── values/
│   │   ├── colors.xml
│   │   ├── strings.xml
│   │   └── themes.xml
│   ├── drawable/
│   │   ├── banner.png         # Android TV banner (1000x300)
│   │   └── ic_launcher_foreground.xml
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

## Roadmap

- [x] Basic TMDB API integration
- [x] Compose UI
- [x] Deep linking (Nuvio, Stremio)
- [x] Launcher channels
- [x] Background sync
- [x] Netflix integration
- [x] GitHub Actions CI
- [ ] Hero/Backdrop section
- [ ] Detail pages
- [ ] Favorites
- [ ] Search
- [ ] Genre filtering
- [ ] Watch Next integration
- [ ] Custom list support
- [ ] Per-row item limits

## License

MIT

## Playback App Package Names

| App | Package Name |
|-----|-------------|
| Nuvio | `com.nuvio.tv` |
| Stremio | `com.stremio.one` |

These are the correct package names for Android TV versions of the apps.

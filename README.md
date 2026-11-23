# UAPlay for Android / Android TV

**Native Android port in progress.** This is an actual port of the UAPlay-nx C catalog and provider engine—not the old Android VideoView demo.

## Features already in the Android codebase

- Original C code for TMDB catalog, ranking, title details and country filters
- Original search and source-resolution parsers for UAFlix, UASerials, Eneyida, UAFilm, UAKino, Mikai, AnimeON and Movy
- Original episode extraction and source audio/subtitle link retrieval
- Original IPTV playlist parser, including country selection
- Original local favorites and watch-progress library
- Bundled UAPlay offline movie/series/top lists (works without TMDB API key)
- Java Android TV/phone interface with first-run mode choice, settings, TV remote controls
- Media3 ExoPlayer with HLS/progressive support (replaces the failed VideoView test player)
- Supports both 64-bit and 32-bit ARM devices; requires Android 6.0+

**Limitations / unfinished parts:** Android TV hardware playback and all external providers have not yet been tested on a real device. Native Switch SDL2 visuals have not been recreated pixel-for-pixel. Switch download queue, theme/wallpaper subsystem, SubDL downloader and self-updater are not yet fully ported. Some sites may block or modify their players. The previous small preview APK is obsolete.

## Getting the APK

An automated debug-signed APK is produced by the [Build Android APK workflow](https://github.com/Small182/UAPlay-apk/actions/workflows/android-apk.yml). The main branch builds publish the APK to:

[Download UAPlay Android APK](https://raw.githubusercontent.com/Small182/UAPlay-apk/main/apk/UAPlay-Android-TV-test.apk)

For development builds, use the APK artifact attached to the workflow run on the \`feature/native-catalog-player\` branch. A successful build verifies compilation, not video playback on your device.

## How to use

On first launch choose **Television** for Android TV / Google TV, or **Phone / tablet** for a touchscreen. In Settings you can change this later.

The catalog initially shows bundled UAPlay titles. For a **live TMDB catalog and searching**, open Settings > TMDB and enter your TMDB *API v3 key*. This is saved only in the app's private storage, not posted to GitHub.

Choose a movie or series, then a provider, and for a series select a season and episode. Playback uses Media3, which supports HLS and native Android media track selection. The exact codecs available depend on the TV. For a provider with multiple offered audio links, use the Dub button in the player.

**Remote:** D-pad moves through the catalog, OK selects items / pauses or resumes playback, Left/Right during playback seeks ±10 seconds, Back closes the player or returns to the previous page.

## Building from sources

Requirements: Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Android SDK 35, Android NDK 27.2, CMake 3.22.

Run \`gradle :app:assembleDebug\` from the repository root. Source files are under \`app/src/main/cpp/core\` (ported C modules) and \`app/src/main/java/com/small182/uaplay/android\` (Android interface and HTTP transport).

Optionally provide a TMDB v3 API key as the **\`TMDB_API_KEY\`** GitHub Actions secret to inject it during the build. Do not hardcode secrets into a public source repository.

Original Switch project: [UAPlay-nx](https://github.com/Small182/UAPlay-nx). This repository is separate from Switch/PS5.

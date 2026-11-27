# YOFECT PLAY

Arabic-first Android movie and series discovery app built with Kotlin and Jetpack Compose.

## Current app capabilities
- Arabic-first RTL interface with dark/light themes, home, search, favorites, settings and TMDB catalog metadata.
- TVmaze, Kitsu, Internet Archive and Wikimedia Commons discovery metadata.
- In-app Media3 player for direct video files returned by the open-media sources.
- Internet Archive playback lookup checks the item's metadata for an openly licensed file and selects a supported MP4, M4V, WebM or OGV file when available.
- Wikimedia Commons playback uses its direct media URL only when the returned file metadata has an accepted open license.
- Native player controls support seek, pause/resume, and audio/subtitle track selection when those tracks exist in the media file.
- TMDB watch-provider availability links remain external; TMDB is metadata and does not supply streams.

## Build
Current app version: `0.3.1` (`versionCode: 4`).
GitHub Actions builds a debug APK on each push to `main`.
Artifact name: `YOFECT-PLAY-debug-apk`.

## Playback limitations
- This does not unlock every commercial film or series. The public sources contain only media that is actually hosted there and whose metadata indicates an accepted open license.
- TVmaze, Kitsu and TMDB are metadata/catalog sources, not episode or movie stream providers.
- A title can appear in search/catalog without having a playable video file. The app checks for a direct media file and does not show a fake play button when none is found.
- Format, codec, server availability, audio tracks, subtitles and quality depend on the actual source file. The player cannot add a language or HD stream that the file does not contain.
- Commercial catalogs require a licensed content provider and appropriate embedding/streaming rights. No provider credentials or commercial catalog subscription are bundled in this project.

## Attribution
This product uses the TMDB API but is not endorsed or certified by TMDB.

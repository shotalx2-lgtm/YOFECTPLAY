# YOFECT PLAY

Arabic-first Android movie and series discovery app built with Kotlin and Jetpack Compose.

## Current app capabilities
- Branded launch screen, first-run onboarding flow with skip/next/start actions, Arabic right-to-left layout, and a custom YOFECT PLAY launcher icon.
- User-selectable dark and light themes, persisted on the device; TMDB API key entry is masked and saved locally.
- Dark Arabic interface with home, search, personal favorites, and settings.
- Trending, popular movie, and popular series shelves.
- Separate discovery shelves for Arabic, Turkish, Indian, and international productions.
- Live TMDB search, Arabic descriptions, posters, backdrops, years, original-language labels, ratings, genres, and series seasons/episode metadata when supplied by TMDB.
- Favorite list persisted locally on the device.
- Egypt streaming-provider availability from TMDB Watch Providers when data exists, with links to the listed provider/availability page.
- TMDB API key can be supplied as the GitHub Actions secret `TMDB_API_KEY` or entered in the app Settings screen. A locally entered key is stored on the device.

## Build
Current app version: `0.2.0` (`versionCode: 2`).
GitHub Actions builds a debug APK on each push to `main`.
Artifact name: `YOFECT-PLAY-debug-apk`.

## Important limitations
- TMDB supplies metadata and artwork, not movie files, episode streams, or streaming rights.
- This repository does not yet include licensed in-app video playback, a real HD quality selector, user accounts/authentication, subscription/payments, or a production-signed release APK.
- Playback can only be added for providers that authorize YOFECT PLAY to embed or stream their content. Where TMDB reports availability, the app links to the provider/availability destination rather than pretending that a stream is embedded.

## Attribution
This product uses the TMDB API but is not endorsed or certified by TMDB.

# YOFECT PLAY
Android application foundation built with Kotlin and Jetpack Compose.

Current features:
- Arabic dark interface with home, search, library, and profile tabs
- TMDB popular movie and TV metadata integration when an API key is configured
- Arabic metadata language, posters, overview, original language and release year
- Arabic, Turkish and international language filters

GitHub Actions builds a debug APK on every push to main.
Configure a repository Actions secret named TMDB_API_KEY with your own TMDB API key.
Without that secret, the app cannot load live catalog metadata.

TMDB provides metadata and artwork under its own terms. It does not provide movie files,
episode streams, or streaming rights. No unauthorized video streams are included.
Playback, licensed provider integrations, seasons and episode feeds, persistent favorites,
authentication, and signed release APK are not implemented yet.


Build status: workflow is configured at .github/workflows/android.yml. The generated artifact is a debug APK and is not a signed production release.

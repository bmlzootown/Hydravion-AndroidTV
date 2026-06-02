# SaucedplussyTV

An unofficial Android TV client for Sauce+ (https://www.sauceplus.com).

**⚠️ DISCLAIMER:** This is an unofficial, third-party client. Not affiliated with, endorsed by, or connected to Sauce+, Floatplane Media Inc., or the original Hydravion project.

## About

SaucedplussyTV is a fork of [Hydravion](https://github.com/bmlzootown/Hydravion-AndroidTV) (an unofficial Floatplane client), heavily modified and repointed for the Sauce+ platform. While it shares lineage with Hydravion, it is a distinct project with substantial changes:

- Complete auth rewrite (WebView cookie-session login replacing Keycloak OIDC)
- Repointed for Sauce+ backend (`sauceplus.com`)
- Major structural and architectural changes
- Independent development path

## Features

- Browse subscriptions and videos
- HLS playback with ExoPlayer
- Livestream support
- D-pad optimized Android TV interface
- Cloudflare Turnstile authentication via WebView

## Tech Stack

- Android TV (Leanback), minSdk 26, compileSdk 34
- Kotlin + Java
- Gradle 9.4.1 + Android Gradle Plugin 9.2.1
- ExoPlayer 2.19.1
- Volley + OkHttp + socket.io

## Build

```bash
./gradlew :app:assembleDebug
```

## TODO / Roadmap

See [TODO.md](TODO.md) for planned improvements including Media3 migration, dependency updates, and feature ideas.

## License

MIT License (see LICENSE file)

Original Hydravion project by bmlzootown, NickM-27, Jman012.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**SaucedplussyTV** — an unofficial Android TV client for Sauce+ (`https://www.sauceplus.com`). Fork of [Hydravion Android TV](https://github.com/bmlzootown/Hydravion-AndroidTV), repointed from Floatplane to Sauce+. Sauce+ runs the same backend software as Floatplane, so the `/api/v3/...` API contract and socket.io sync mechanism are unchanged — the work is a host swap plus auth rewrite.

**⚠️ Unofficial / unaffiliated with Sauce+ or Floatplane Media Inc.**

## Build & run

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug   # build / compile check
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:lint            # Android lint
```

**Critical:** The system JDK (26) breaks AGP 9.2.1. Always use `JAVA_HOME=/opt/android-studio/jbr`. If `gradlew` is not executable: `chmod +x gradlew`.

There is no unit-test suite. "Verify it works" means it compiles (`assembleDebug`) and behaves correctly on a TV device/emulator with a D-pad.

## Auth model

Sauce+ uses legacy cookie-session auth (not Keycloak/OIDC). Full recon: `reference/RECON.md`.

**Login flow** (all under `https://www.sauceplus.com`):
1. `GET /api/v3/auth/captcha/info` → Cloudflare Turnstile siteKey (delivered at runtime, not hardcoded).
2. `POST /api/v3/auth/login` with `{ username, password, captchaToken }` → sets `sails.sid` cookie; may return `needs2FA`.
3. If 2FA: `POST /api/v3/auth/checkFor2faLogin` with `{ token }`.

**Cloudflare:** the entire site is behind a bot challenge. API calls must carry a valid `cf_clearance` cookie with a **consistent User-Agent** matching the one that solved the challenge.

**TV login:** `WebLoginActivity` loads `https://www.sauceplus.com/login` in a WebView. The user solves Turnstile + CF naturally. On success the activity harvests `sails.sid` + `cf_clearance` from `CookieManager` plus the WebView's `userAgentString`, stores them via `AuthManager.saveSession()`, and returns `RESULT_OK`. Success is detected by navigating off `/login` to an app route while `sails.sid` is present — no separate HTTP verification (CF TLS fingerprinting blocks it).

## Architecture

`app/src/main/java/com/saucedplussytv/androidtv/`

- `browse/` — `MainActivity`, `MainFragment` (Leanback `BrowseSupportFragment`). `MainFragment` is the central coordinator: it holds both `SaucedplussyTVClient` and `SocketClient`, fetches subscriptions, populates Leanback `ArrayObjectAdapter` rows, and owns all click/selection listeners and the socket event loop.
- `client/` — `SaucedplussyTVClient` (API facade, singleton), `RequestTask` (Volley HTTP, adds Cookie + UA headers to every request), `SocketClient` (socket.io live/sync, singleton), `SyncEvent`/`UserSync` (socket event DTOs).
- `authenticate/` — `AuthManager` (stores `sails.sid`+UA in SharedPreferences, singleton), `WebLoginActivity` (WebView cookie harvest).
- `playback/PlaybackActivity` — ExoPlayer 2.x screen. Injects `Cookie` + `User-Agent` into `DefaultHttpDataSource.Factory` so HLS segment requests also carry the session credential.
- `detail/` — `DetailsActivity` + `VideoDetailsFragment` (Leanback details screen).
- `models/`, `creator/`, `post/`, `subscription/` — Gson DTOs.
- `Constants.kt` — SharedPreferences key names (`PREF_SESSION_COOKIE`, `PREF_USER_AGENT`).

### Non-obvious cross-cutting patterns

**Singleton access:** All three primary objects share the same `getInstance(context, mainPrefs)` factory:
```kotlin
SaucedplussyTVClient.getInstance(context, mainPrefs)
AuthManager.getInstance(context, mainPrefs)
SocketClient.getInstance(context, mainPrefs)
```

**All authed API calls gate on `AuthManager.withValidAccessToken { token -> }`**, which is synchronous (no network, no refresh). An empty cookie calls `onFailure`. Expired/invalid cookies cause downstream 401/403 → "Session Expired" dialog → re-login.

**`RequestTask.VolleyCallback`** has four methods. Only the `sendRequest(uri, token, creatorGUID, callback)` overload triggers `onSuccessCreator`; the standard `sendRequest(uri, token, callback)` triggers `onSuccess`. Implement both but only the relevant one does work per call site.

**`RequestTask`** sets `Cookie` and `User-Agent` headers on every request via `authHeaders()`, reading the UA from `AuthManager.peekUserAgent()` (static, safe before login).

**`SocketClient.initialize()`** injects the session Cookie + User-Agent at the socket.io transport level (via `Manager.EVENT_TRANSPORT` / `Transport.EVENT_REQUEST_HEADERS`). Never log the Cookie value.

**Video delivery:** `SaucedplussyTVClient.getVideo()` calls `/api/v3/delivery/info?scenario=onDemand&entityId=...`, parses `Delivery` → takes `groups[0].origins[0].url` as CDN base, then picks the highest-quality *enabled* variant (sorted `2160 > 1080 > 720 > 480 > 360`). Final URL = `cdn + variant.url`.

**Pagination:** `fetchAfter = (page - 1) * 20`. Do not change this formula — it prevents the load-more from jumping back to latest.

**Debug logging:** `MainFragment.dLog(TAG, msg)` — static helper, guarded by `BuildConfig.DEBUG`. Use it for all debug logs. Never log tokens or full auth responses.

**Logged-out state:** Do not update the UI when `!authManager.isLoggedIn()`. Reset the UI-init flag on logout.

## Tech stack

- **Platform:** Android TV, AndroidX Leanback (D-pad only, no touch). `minSdk 26`, `target/compileSdk 34`, Java 17, Gradle 9.4.1 (Groovy DSL), AGP 9.2.1, Kotlin 2.0.21.
- **Languages:** mixed Kotlin + Java — match the surrounding file's language.
- **Playback:** ExoPlayer `2.19.1` (+ mediasession extension). **TODO:** Migrate to Media3.
- **Networking:** Volley + OkHttp 5 (alpha) + `socket.io-client 1.0.1`; Gson for JSON.
- **UI/misc:** Glide (images), PrettyTime, NanoHTTPD (local server), versioncompare.
- **ZXing** dependency is present but currently unused — QR features were removed with the Keycloak auth rewrite.

## Rebrand status

Code migration is complete. Remaining work requires designer/assets:
- `res/drawable/banner.png`, `res/drawable/icon.png`, `res/drawable/ic_saucedplussytv.xml`
- Launcher mipmaps in `res/mipmap-*`
- `res/values/colors.xml` — brand colors

## Sub-agents and the review gate (IMPORTANT)

Sub-agents are defined in `.claude/agents/`:
- **`rebrand-engineer`** — identity + endpoint changes
- **`api-client`** — v3 API plumbing, models, auth/socket layer
- **`android-feature-dev`** — Leanback UI, presenters, playback, lifecycle
- **`senior-reviewer`** — final gate for all code

### Mandatory workflow

1. Route work to the matching specialist agent (or inline for small changes).
2. Build: `JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug`.
3. **Every code change must pass `senior-reviewer` before it is done. No exceptions.**
4. `senior-reviewer` delegates to a **skeptical Kimi K2.6 instance** via the opencode MCP server (`opencode_ask` → `agent: "reviewer"`, model `kimi-for-coding/k2p6`, defined in `.opencode/agent/reviewer.md`).
5. Reviewer returns **LGTM** or **BLOCKER / MAJOR / MINOR** issues. Fix all BLOCKERs and MAJORs and resubmit (same Kimi session for context).

If the opencode MCP server is unavailable, the change is **not approved** — surface the failure rather than skipping review.

Quick check: `opencode run --agent reviewer 'ping'` should respond without "agent not found".

## Conventions

- Match the language (Kotlin vs Java) of the file you touch.
- Prefer reusing `SaucedplussyTVClient`/`RequestTask`/presenter patterns over new mechanisms.
- Make targeted changes — this fork may still track upstream.
- See `TODO.md` for planned upgrades (Media3, dependency updates, architecture improvements).

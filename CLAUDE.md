# Soap4TV — Android TV App

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

No local JDK installed — use Android Studio's bundled JBR.

## Architecture

**Stack:** Kotlin 2.1, Jetpack Compose, Hilt DI, Room, OkHttp, Jsoup, Coil 3, Media3/ExoPlayer

**Pattern:** MVVM with Repository layer. ViewModels use `mutableStateOf` for UI state and `StateFlow` for reactive data.

### Data flow
```
HTML page → Jsoup parser → Repository (cached) → ViewModel (StateFlow) → Compose UI
```

### Key directories
- `data/parser/` — Jsoup HTML parsers (catalog, episodes, movie detail, token)
- `data/repository/` — Business logic, caching, API calls
- `data/network/` — OkHttp client, cookie jar, MD5 util
- `ui/screen/` — Compose screens (home, catalog, detail, episodes, player, search, login)
- `ui/components/` — Reusable composables (PosterCard, EpisodeRow, FilterChipRow, etc.)
- `di/` — Hilt modules (Network, Database, DataStore)

## Site specifics (soap4youand.me)

- **No REST API** — all data scraped from server-rendered HTML via Jsoup
- **Non-standard attributes**: site uses `data:attr` (colon) instead of `data-attr` (hyphen). All parsers use `dataAttr()` extension that tries both.
- **Auth**: PHPSESSID cookie (12-month lifetime), API token from `<div id="token" data:token="...">`
- **Play API** for series: `POST /api/v2/play/episode/{eid}` with `hash=md5(token+eid+sid+hash_attr)`. Response has `"ok":1` (int, not boolean!) and a single `"stream"` URL — **direct progressive MP4** (not HLS, no variants). Has a CDN session lifetime: after some minutes the URL stops returning 206 partial content (returns HTML / wrong bytes), causing parser exceptions and frozen video.
- **Movies**: HLS URL (`.m3u8`) embedded in HTML via Playerjs JS config, parsed with regex. Different code path from series.
- **Continue watching**: server-side data at `/sort/my/` (`.continue-section .continue-item`)
- **Bookmarked movies**: `/movies/bookmarks/`

## Common pitfalls

1. **JVM declaration clash**: Kotlin `var foo by mutableStateOf(...)` generates `setFoo()`. Don't create `fun setFoo()` — name it `fun onFooChange()` instead.
2. **API `ok` field**: Server returns `"ok":1` (int), not `"ok":true`. Check both: `obj.optBoolean("ok", false) || obj.optInt("ok", 0) == 1`
3. **Jsoup attributes**: Always use `dataAttr()` helper, never raw `attr("data:...")` — Jsoup may handle colons differently across versions.
4. **Navigation**: Series use slug ("The_Beast_in_Me"), movies use int ID. Watch history `contentId` must store slug for series, not numeric sid.
5. **ExoPlayer**: Must use `OkHttpDataSource.Factory` for HLS — default HTTP client lacks auth cookies.
6. **Material Icons**: Uses `material-icons-extended` dependency. Basic icons (PlayArrow, Search) are fine, but Pause/SkipNext/etc need extended.
7. **MediaSource branching**: Branch on `.m3u8` substring in URL — `HlsMediaSource.Factory` for movies, `ProgressiveMediaSource.Factory` for series. `DefaultMediaSourceFactory` works but hides the per-format knobs.
8. **Refreshing the stream URL mid-playback**: do NOT rebuild the ExoPlayer instance. Emit a refresh event from ViewModel and call `player.setMediaItem(newItem); seekTo; prepare()` on the EXISTING player. Rebuilding detaches `PlayerView`'s surface; on MediaTek SoCs (TCL P7K) the new player's video frames render to nothing while audio + subtitles continue (subs render via Compose, audio doesn't need surface). The dispose-order between `DisposableEffect.onDispose` (releases old player) and AndroidView's `update` lambda is what bites.
9. **Autoplay watched-mark race**: when STATE_ENDED triggers `playNextEpisode`, the ViewModel's `episodeId` flips to the next episode BEFORE the old player is disposed (URL fetch is async, dispose is keyed on player rebuild). A naive dispose-time `saveProgress` then marks the *next* episode watched at start. Fix: `PlaybackData` carries `episodeId` as a frozen snapshot; dispose passes that snapshot; `markedWatched=true` flag is set only when the snapshot still matches the current episode (so the next one can still be marked at its own 90% threshold).
10. **Player surface init on MediaTek**: the PlayerView must have `player = exoPlayer` set in the AndroidView `factory` (not just in `update`). Without an attached surface during the first decode, the codec doesn't allocate output buffers and never produces frames even after a later attach.

## Security

- **NEVER** hardcode credentials in source code
- Credentials are entered at runtime via LoginScreen
- Session managed via `SoapCookieJar` (DataStore-backed)
- Delete any temp cookie/session files after curl research

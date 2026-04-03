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
- **Play API** for series: `POST /api/v2/play/episode/{eid}` with `hash=md5(token+eid+sid+hash_attr)`. Response has `"ok":1` (int, not boolean!)
- **Movies**: HLS URL embedded in HTML via Playerjs JS config, parsed with regex
- **Continue watching**: server-side data at `/sort/my/` (`.continue-section .continue-item`)
- **Bookmarked movies**: `/movies/bookmarks/`

## Common pitfalls

1. **JVM declaration clash**: Kotlin `var foo by mutableStateOf(...)` generates `setFoo()`. Don't create `fun setFoo()` — name it `fun onFooChange()` instead.
2. **API `ok` field**: Server returns `"ok":1` (int), not `"ok":true`. Check both: `obj.optBoolean("ok", false) || obj.optInt("ok", 0) == 1`
3. **Jsoup attributes**: Always use `dataAttr()` helper, never raw `attr("data:...")` — Jsoup may handle colons differently across versions.
4. **Navigation**: Series use slug ("The_Beast_in_Me"), movies use int ID. Watch history `contentId` must store slug for series, not numeric sid.
5. **ExoPlayer**: Must use `OkHttpDataSource.Factory` for HLS — default HTTP client lacks auth cookies.
6. **Material Icons**: Uses `material-icons-extended` dependency. Basic icons (PlayArrow, Search) are fine, but Pause/SkipNext/etc need extended.

## Security

- **NEVER** hardcode credentials in source code
- Credentials are entered at runtime via LoginScreen
- Session managed via `SoapCookieJar` (DataStore-backed)
- Delete any temp cookie/session files after curl research

# Soap4YouAndMe — Android TV App
## План разработки

---

## 1. Результаты разведки

### Авторизация
- Тип: PHP-сессия (`PHPSESSID` в Cookie)
- Нет JWT, нет Bearer-токенов
- Сессия передаётся во всех последующих запросах через Cookie

### Получение видео
- Токен зашит **в HTML страницы** (`/movies/{id}/`)
- Парсинг через `<script>` с `initHLSMoviePlayer()`
- HLS URL: `https://cdn-r11.soap4youand.me/hls/{TOKEN}/master.m3u8`
- Токен живёт **~5 минут** (Cache-Control: max-age=300)
- Сегменты и плейлисты живут **~100 дней** (статика на CDN)

### Субтитры
- Статические SRT файлы: `/assets/subsm/{id}/ru.srt`, `/assets/subsm/{id}/en.srt`
- Список языков парсится из HTML: `subtitle: '[Русский]/path,[English]/path'`

### Каталог
- Рендерится на сервере (HTML, не JSON API)
- Иконки подгружаются **lazy при скролле**
- Постеры: `/assets/covers/movies/{id}.jpg`
- Личный список: `/sort/my/`
- Каталог фильмов: `/movies/`

### Нет REST API — только HTML-парсинг (Jsoup)

---

## 2. Технический стек

| Слой | Инструмент | Обоснование |
|---|---|---|
| Язык | Kotlin | Стандарт Android |
| UI | Jetpack Compose for TV | Современный декларативный UI, TV-компоненты |
| Плеер | ExoPlayer (Media3) | Нативная поддержка HLS, SRT, WebVTT |
| Сеть | OkHttp | Управление Cookie (CookieJar для PHPSESSID) |
| HTML-парсер | Jsoup | Парсинг страниц каталога и плеера |
| Изображения | Coil | Lazy-загрузка постеров |
| Архитектура | MVVM + Clean Architecture | — |
| DI | Hilt | — |
| Асинхронность | Kotlin Coroutines + Flow | — |
| Навигация | Jetpack Navigation (TV) | D-pad навигация |

---

## 3. Архитектура проекта

```
app/
├── data/
│   ├── network/
│   │   ├── SoapHttpClient.kt        # OkHttp + CookieJar
│   │   └── SoapParser.kt            # Jsoup-парсеры
│   ├── repository/
│   │   ├── AuthRepository.kt
│   │   ├── CatalogRepository.kt
│   │   └── PlayerRepository.kt
│   └── model/
│       ├── Movie.kt
│       ├── PlayerData.kt
│       └── Subtitle.kt
├── domain/
│   └── usecase/
│       ├── LoginUseCase.kt
│       ├── GetCatalogUseCase.kt
│       └── GetPlayerDataUseCase.kt
└── ui/
    ├── screen/
    │   ├── login/       # Экран авторизации
    │   ├── home/        # Главная + каталог
    │   ├── detail/      # Страница фильма
    │   └── player/      # Плеер
    └── theme/
        └── TvTheme.kt
```

---

## 4. Data Layer — ключевые детали

### 4.1 Авторизация
```kotlin
// POST https://soap4youand.me/login (или /auth/)
// Body: login=...&password=...
// Ответ: Set-Cookie: PHPSESSID=...
// Сохраняем через PersistentCookieJar
```

### 4.2 Парсинг каталога (`/movies/`)
```kotlin
// GET /movies/?page=N
// Jsoup: ищем карточки фильмов
// Каждая карточка: id, title, poster URL, год, жанр
// Lazy иконки = просто img[data-src] или img[src] по мере парсинга
```

### 4.3 Парсинг плеера (`/movies/{id}/`)
```kotlin
data class PlayerData(
    val hlsUrl: String,      // https://cdn-r11.../hls/{TOKEN}/master.m3u8
    val subtitles: List<SubtitleTrack>,  // [(Русский, /assets/subsm/...), ...]
    val poster: String,      // /assets/covers/movies/{id}.jpg
    val title: String
)

// Regex из <script>:
// file: "https://cdn-r11...master.m3u8"
// subtitle: '[Русский]/path,[English]/path'
// poster: "/assets/covers/movies/1045.jpg"
// title: "..."
```

### 4.4 Обновление токена
```kotlin
// Токен живёт 5 минут
// При ошибке плеера (403/404) — переходим на /movies/{id}/,
// парсим свежий HTML, достаём новый HLS URL, перезапускаем плеер
```

---

## 5. Player Layer — ExoPlayer

```kotlin
// Инициализация HLS
val mediaItem = MediaItem.Builder()
    .setUri(hlsUrl)
    .setSubtitleConfigurations(subtitleConfigs) // SRT-субтитры
    .build()

val player = ExoPlayer.Builder(context).build()
player.setMediaItem(mediaItem)
player.prepare()
player.play()

// Субтитры из SRT
val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
    .setMimeType(MimeTypes.APPLICATION_SUBRIP)  // .srt
    .setLanguage("ru")
    .build()
```

**Что ExoPlayer умеет из коробки:**
- HLS адаптивное качество ✅
- Несколько аудиодорожек (выбор озвучки) ✅
- SRT / WebVTT субтитры ✅
- Управление с пульта ✅

---

## 6. UI — Android TV специфика

### Компоненты Compose for TV
- `TvLazyRow` / `TvLazyColumn` — каталог с фокусом
- `Card` с focus-анимацией — карточки фильмов
- Кастомный `PlayerOverlay` — поверх ExoPlayer

### D-pad навигация
- Все интерактивные элементы должны иметь `focusable = true`
- Тестировать на эмуляторе Android TV с D-pad
- Back-кнопка: пульт → выход из плеера / навигация назад

### 10-foot UI правила
- Минимальный размер текста: 24sp
- Отступы крупнее чем на телефоне
- Фокус всегда визуально выделен

---

## 7. Экраны и флоу

```
[Авторизация]
     ↓ (PHPSESSID сохранён)
[Главная]
  ├── Продолжить просмотр (из /sort/my/ или локальное хранилище)
  ├── Новинки
  └── Каталог с фильтрами
     ↓
[Страница фильма]
  ├── Постер, описание, год, жанр
  └── Кнопка "Смотреть"
     ↓ (парсим HTML → HLS URL)
[Плеер]
  ├── ExoPlayer с HLS
  ├── Выбор субтитров (ru/en/выкл)
  ├── Выбор озвучки (из HLS аудиодорожек)
  └── Прогресс (сохраняем локально)
```

---

## 8. Фазы разработки

### Фаза 1 — Ядро (MVP)
- [ ] Настройка проекта (Compose TV, Hilt, OkHttp, Jsoup)
- [ ] Авторизация (POST логин, сохранение PHPSESSID)
- [ ] Парсинг страницы фильма → HLS URL
- [ ] ExoPlayer воспроизводит HLS
- [ ] Минимальный UI: логин → список → плеер

### Фаза 2 — Каталог
- [ ] Парсинг каталога `/movies/` с пагинацией
- [ ] Grid-экран с постерами (TvLazyVerticalGrid)
- [ ] Страница фильма (постер, описание, год)
- [ ] Поиск

### Фаза 3 — Плеер
- [ ] Субтитры (SRT, переключение)
- [ ] Выбор аудиодорожки (озвучки)
- [ ] Сохранение прогресса просмотра (локально, Room)
- [ ] Обновление токена при истечении
- [ ] Скип интро (если найдём паттерн)

### Фаза 4 — Полировка
- [ ] Личный список (`/sort/my/`)
- [ ] Экран "Продолжить просмотр"
- [ ] Анимации, переходы, focus-эффекты
- [ ] Обработка ошибок сети
- [ ] Тестирование на реальном Android TV устройстве

---

## 9. Открытые вопросы (требуют доразведки)

| Вопрос | Как проверить |
|---|---|
| URL и body POST-запроса авторизации | DevTools → Network → XHR при логине |
| Структура HTML карточек каталога | Jsoup-парсинг `/movies/` |
| Есть ли сериалы отдельно от фильмов? | Изучить навигацию сайта |
| Как устроена страница сериала (сезоны/серии)? | Открыть любой сериал |
| Время жизни PHPSESSID | Эмпирически |

---

## 10. Риски

| Риск | Вероятность | Митигация |
|---|---|---|
| Сайт изменит структуру HTML | Средняя | Версионировать парсеры, легко обновлять |
| Cloudflare заблокирует скрапинг | Низкая | Сейчас нет признаков защиты |
| Токен будет привязан к IP/UA | Низкая | Передавать корректный User-Agent |
| Юридические риски | Есть | Личное использование |

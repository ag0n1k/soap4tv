# Soap4TV

Android TV приложение для просмотра сериалов и фильмов с soap4youand.me.

## Возможности

- Каталог сериалов и фильмов с сортировкой и фильтрацией
- Просмотр серий с выбором качества (SD/HD/FHD), озвучки и субтитров
- Просмотр фильмов с субтитрами
- Продолжить просмотр (локальная история + серверная синхронизация)
- Мои сериалы и закладки фильмов
- Поиск по каталогу
- Управление D-pad пультом Android TV

## Технологии

| Компонент | Технология |
|-----------|------------|
| Язык | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| Видео | Media3 / ExoPlayer (HLS) |
| DI | Hilt |
| БД | Room |
| Сеть | OkHttp + Jsoup |
| Изображения | Coil 3 |
| Навигация | Navigation Compose |

## Сборка

Требуется Android Studio (или JDK 17+, Android SDK).

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Установка

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Или запустить через Android Studio на TV-эмуляторе / устройстве.

## Структура проекта

```
app/src/main/java/com/soap4tv/app/
├── data/
│   ├── model/          # Модели данных (Series, Movie, Episode, etc.)
│   ├── parser/         # Jsoup-парсеры HTML страниц
│   ├── repository/     # Бизнес-логика, кэширование, API
│   ├── network/        # OkHttp клиент, cookies, MD5
│   └── local/          # Room база (история просмотра)
├── di/                 # Hilt модули
└── ui/
    ├── theme/          # Цветовая палитра, типографика
    ├── components/     # Переиспользуемые компоненты
    ├── navigation/     # Навигация между экранами
    └── screen/         # Экраны приложения
        ├── login/      # Авторизация
        ├── home/       # Главная (табы + "Моё")
        ├── catalog/    # Каталог сериалов/фильмов
        ├── detail/     # Детали сериала/фильма
        ├── episodes/   # Список эпизодов сезона
        ├── player/     # Видеоплеер
        └── search/     # Поиск
```

## Лицензия

MIT

<div align="center">

<img src="art/icon.png" width="120" height="120" alt="RetroROMs App Icon" />

# RetroROMs

![Android](https://img.shields.io/badge/Platform-Android_8.0+_(API_26--35)-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose_M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![Room Database](https://img.shields.io/badge/Database-Room_2.7.0_(KSP)-F57C00?style=for-the-badge&logo=sqlite&logoColor=white)
![Build](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)

**Нативное Android-приложение для каталогизации, поиска и загрузки ретро-игр с портала [Emu-Land.net](https://www.emu-land.net) с автоматической раскладкой по папкам консолей и умной распаковкой ZIP-архивов.**

[Возможности](#-основные-возможности) • [Архитектура](#-архитектура-и-стек) • [Скриншоты и интерфейс](#-пользовательский-интерфейс) • [Сборка проекта](#-сборка-и-запуск) • [Пайплайн загрузки](#-пайплайн-загрузки-и-распаковки) • [Roadmap](#-roadmap)

</div>

---

## ✨ Основные возможности

* **📚 Каталог десятков ретро-платформ:**
  Dendy / NES, Sega Genesis / Mega Drive, Super Nintendo (SNES), Game Boy, Game Boy Color, Game Boy Advance, Sony PlayStation 1 (PS1), Nintendo 64 (N64), Sega Dreamcast, Sega Saturn, Nintendo DS, PSP, Master System, PC Engine, Atari 2600, 3DO и другие.
* **🔍 Поиск и фильтрация:**
  Мгновенный поиск игр по сайту, фильтрация по категориям («Топ игр», «Все игры», «Русские версии», «Хиты») и сортировка.
* **📦 Выбор ревизий и версий РОМов:**
  Просмотр всех доступных версий игры (USA, Europe, Japan, фанатские переводы на русский, хаки, пиратские дампы, GoodSet).
* **🗂️ Автоматическая организация на накопителе:**
  * Сохранение игр в системную папку `Download/RetroROMs/<Консоль>/`.
  * Поддержка **Storage Access Framework (SAF)** для выбора любой кастомной директории на внутреннем накопителе или SD-карте.
* **⚡ Умная распаковка ZIP-архивов:**
  * **Single-ROM архивы:** распаковываются сразу в папку соответствующей консоли, а исходный `.zip` удаляется для экономии памяти.
  * **Multi-ROM архивы (GoodSet / сборники):** вызывают диалог `ZipExtractionDialog`, позволяющий отметить только необходимые версии РОМов для извлечения или сохранить ZIP целиком.
* **🖼️ Интерактивный предпросмотр обложек:**
  Долгое нажатие (Long Press) на карточку в сетке или строке списка открывает полноэкранный просмотрщик обложки/скриншота с поддержкой масштабирования (Pinch-to-Zoom) и панорамирования.
* **🎛️ Бегущая строка (Marquee) при взаимодействии:**
  Названия игр отображаются компактно, а при касании к тайлу плавно запускается бегущая строка, предотвращая превращение экрана в хаотичное табло.
* **🎨 Темы оформления (Retro Arcade Themes):**
  Встроенные пресеты (Arcade Neon, Cyberpunk, Retrowave, Game Boy Classic), ручной выбор акцентных цветов через RGB-палитру и переключатель вида каталога (Сетка / Список).

---

## 🛠 Архитектура и стек

Приложение построено в строгом соответствии с рекомендациями **Android Modern Architecture (MVVM + Repository + Unidirectional Data Flow)**:

```
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│  (MainActivity, HomeScreen, GameCardItem, Dialogs)     │
└───────────────────────────▲────────────────────────────┘
                            │ StateFlow / Events
┌───────────────────────────┴────────────────────────────┐
│                    EmuLandViewModel                    │
│   (State management, Coroutine scopes, Preferences)    │
└───────────────────────────▲────────────────────────────┘
                            │ Suspend functions / Flow
┌───────────────────────────┴────────────────────────────┐
│                   EmuLandRepository                    │
│        (Координация локального кэша и сети)            │
└─────────────┬───────────────────────────┬──────────────┘
              │                           │
              ▼                           ▼
┌───────────────────────────┐ ┌──────────────────────────┐
│       Room Database       │ │      Сетевой слой        │
│  (GameDao, ConsoleDao,    │ │  • EmuLandScraper (Jsoup)│
│       DownloadDao)        │ │  • RomDownloader (OkHttp)│
│         (SQLite)          │ │  • java.util.zip (Zip)   │
└───────────────────────────┘ └──────────────────────────┘
```

### Ключевые библиотеки

| Библиотека | Назначение |
| :--- | :--- |
| **Jetpack Compose BOM 2024.09.00** | Декларативный UI на базе Material Design 3 |
| **Room 2.7.0 (KSP 2.3.5)** | Локальная реляционная база данных SQLite с поддержкой Flow |
| **OkHttp 4.10.0** | Потоковая загрузка бинарных ROM-файлов, управление таймаутами и заголовками |
| **Jsoup 1.18.3** | Парсинг HTML-страниц Emu-Land.net, извлечение метаданных и прямых ссылок `getmfl` |
| **Coil Compose 2.7.0** | Асинхронная загрузка и кэширование обложек и скриншотов |
| **Kotlin Coroutines & Flow 1.10.2** | Асинхронное программирование и реактивное управление состоянием |
| **DocumentFile (SAF)** | Поддержка пользовательских папок для сохранения РОМов |

---

## 📁 Структура проекта

```
app/src/main/java/com/example/
├── MainActivity.kt                  # Корневая Activity, хост навигации и глобальных диалогов
├── model/
│   └── Models.kt                    # Data-классы: GameCard, RomFileVersion, ZipExtractionRequest и др.
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt           # Room Database определение
│   │   ├── Entities.kt              # Таблицы Room: ConsoleEntity, GameEntity, DownloadEntity
│   │   └── Daos.kt                  # DAO интерфейсы с запросами и Flow
│   ├── remote/
│   │   └── EmuLandScraper.kt        # HTML-парсер каталога, AJAX getmfl и резолвер прямых ссылок
│   ├── downloader/
│   │   └── RomDownloader.kt         # Менеджер загрузки, ZipFile анализ, распаковка, SAF/MediaStore
│   └── repository/
│       └── EmuLandRepository.kt     # Единая точка доступа к данным (Repository pattern)
├── ui/
│   ├── components/
│   │   ├── GameCardItem.kt          # Карточка игры для сетки (компактная, 185 dp, Marquee, Long Click)
│   │   ├── GameListItem.kt          # Элемент игры для списка (52 dp, Long Click по всей строке)
│   │   └── FullScreenImageDialog.kt # Полноэкранный просмотрщик обложек с Pinch-to-Zoom
│   ├── screens/
│   │   ├── HomeScreen.kt            # Главный экран каталога с табами, фильтрами и пагинацией
│   │   ├── GameDetailSheet.kt       # Модальная карточка деталей игры
│   │   ├── RomVersionsSheet.kt      # Шторка выбора ревизий/версий РОМа
│   │   ├── ZipExtractionDialog.kt   # Интерактивный выбор РОМов из многофайлового архива
│   │   ├── DownloadsScreen.kt       # Экран менеджера загрузок и настроек хранилища/распаковки
│   │   ├── FavoritesScreen.kt       # Экран избранных игр
│   │   └── SettingsDialog.kt        # Настройки отображаемых платформ и темы
│   ├── theme/
│   │   ├── Color.kt / Theme.kt      # Палитра цветов Arcade Dark и M3 Theming
│   │   └── ThemePreferences.kt      # Управление пресетами и пользовательскими цветами
│   └── viewmodel/
│       └── EmuLandViewModel.kt      # Главный ViewModel приложения
```

---

## 🚀 Пайплайн загрузки и распаковки

1. **Резолвинг ссылки:** Приложение отправляет запрос к AJAX-эндпоинту Emu-Land `act=getmfl` с валидными заголовками `Referer` и десктопным `User-Agent`.
2. **Временное сохранение:** Поток байтов сохраняется во временный файл в `context.cacheDir` с регулярным отчетом о прогрессе в UI и базу Room.
3. **Анализ содержимого:**
   * Если файл не является ZIP-архивом или авто-распаковка выключена в настройках — файл переносится в целевую папку консоли как есть.
   * Если файл является ZIP-архивом:
     * **1 РОМ внутри:** архив распаковывается напрямую в папку консоли, временный ZIP удаляется, статус обновляется на `COMPLETED`.
     * **>1 РОМа внутри:** выводится `ZipExtractionDialog`. Пользователь отмечает нужные файлы, после чего извлекаются только они, а временный ZIP удаляется.

---

## 🔨 Сборка и запуск

### Требования
* Android Studio Ladybug / Koala или новее.
* JDK 17 или JDK 21.
* Android SDK 35 (Build-tools 35.0.0).

### Сборка через терминал
```bash
# Клонирование репозитория
git clone https://github.com/<ваш-аккаунт>/RetroROMs.git
cd RetroROMs

# Запуск юнит-тестов
gradle testDebugUnitTest

# Сборка Debug APK
gradle assembleDebug
```
Собранный файл будет находиться в `app/build/outputs/apk/debug/app-debug.apk`.

---

## 🗺 Roadmap

- [x] Каталог игр Emu-Land с пагинацией и оффлайн-кэшированием в Room.
- [x] Поиск игр и фильтрация по платформам/категориям.
- [x] Выбор конкретной версии/перевода игры.
- [x] Поддержка Storage Access Framework (SAF) для любых директорий.
- [x] Автоматическая распаковка single-ROM ZIP-архивов с удалением архива.
- [x] Интерактивный диалог выбора файлов для multi-ROM архивов.
- [x] Полноэкранный просмотр обложек по долгому нажатию.
- [x] Бегущие строки (Marquee) при взаимодействии с тайлом.
- [ ] Поддержка распаковки `.7z`-архивов.
- [ ] Запуск скачанных РОМов в установленных на устройстве эмуляторах через Android Intent (`ACTION_VIEW`).
- [ ] Очередь параллельных загрузок с паузой/возобновлением.
- [ ] Проверка контрольных сумм РОМов (CRC32/MD5) по базам No-Intro.

---

## 📄 Лицензия и дисклеймер

Данный проект разработан исключительно в образовательных целях и в целях сохранения ретро-игрового наследия (Retro Gaming Preservation). Все метаданные, обложки и файлы игр предоставляются порталом [Emu-Land.net](https://www.emu-land.net). Права на игры принадлежат их законным правообладателям.

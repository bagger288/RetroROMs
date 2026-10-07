# Handover Documentation: RetroROMs (Emu-Land Catalog & ROM Manager)

Данный документ представляет собой техническое руководство по проекту **RetroROMs** для следующего разработчика / LLM-агента. Документ составлен на основе всей истории разработки, архитектурных решений, исправленных дефектов и требований пользователя.

---

## 1. Краткий обзор приложения (Project Overview)

### Назначение и концепция
**RetroROMs** — нативное Android-приложение, представляющее собой клиент-каталог и менеджер загрузок ретро-игр с портала [Emu-Land.net](https://www.emu-land.net).
Приложение решает следующие задачи:
1. Просмотр и фильтрация каталогов игр по десяткам платформ (Dendy/NES, Sega Genesis/MD, SNES, GBA, PS1, N64, Dreamcast, Saturn, PC Engine, Atari 2600 и др.).
2. Поиск игр по сайту и фильтрация по категориям («Топ игр», «Все игры», «Русские версии», «Хиты»).
3. Детальная карточка игры с описанием, характеристиками (разработчик, год, издатель, рейтинг) и галереей скриншотов.
4. Выбор конкретной ревизии/версии РОМа для скачивания (регионы USA/EUR/JAP, русские переводы, хаки, пиратские дампы, GoodSet).
5. Скачивание файлов напрямую в локальное хранилище устройства:
   * Поддержка Storage Access Framework (SAF) для сохранения в любую выбранную пользователем папку (включая SD-карты).
   * Автоматическая раскладка по подпапкам консолей (`Download/RetroROMs/<Консоль>/`).
6. Автоматическая потоковая распаковка `.zip`-архивов сразу после завершения загрузки с удалением исходного архива.
7. Интерактивный диалог выбора файлов (`ZipExtractionDialog`), если архив содержит несколько версий или файлов РОМа.

### Технологический стек
* **Язык программирования:** Kotlin (v2.2.10).
* **UI-фреймворк:** Jetpack Compose (BOM `2024.09.00`, Material 3). Декларативный UI без XML-разметки (за исключением системных векторных drawable).
* **Архитектурный паттерн:** MVVM (Model-View-ViewModel) + Repository Pattern + Unidirectional Data Flow (UDF) на базе Kotlin Coroutines и `StateFlow` / `SharedFlow`.
* **Локальная база данных:** Room Database (v2.7.0) через Google KSP (v2.3.5).
* **Сетевой стек:**
  * OkHttpClient (v4.10.0) — потоковая загрузка бинарных данных, управление заголовками, таймаутами и редиректами.
  * Jsoup (v1.18.3) — HTML-парсинг страниц Emu-Land.net, извлечение AJAX-эндпоинтов `getmfl`, версий и ссылок.
* **Загрузка изображений:** Coil Compose (v2.7.0) с кэшированием обложек и скриншотов.
* **SDK:** `minSdk = 26` (Android 8.0 Oreo), `targetSdk = 35`, `compileSdk = 35`.
* **Сборка:** Gradle 9.1.1 (Kotlin DSL — `build.gradle.kts`).

---

## 2. Реализованный функционал (Implemented Features)

### Архитектурная диаграмма потоков данных (Data Flow)
```
[Emu-Land.net] 
      │ (HTML / HTTP Streams)
      ▼
[EmuLandScraper] ───► [RomDownloader] (Потоковая загрузка, ZipFile, SAF DocumentFile / MediaStore)
      │                       │
      ▼                       ▼
[EmuLandRepository] ◄───► [Room Database: GameDao, ConsoleDao, DownloadDao]
      │
      ▼
[EmuLandViewModel] (StateFlow: displayedGames, activeDownloads, pendingZipExtraction...)
      │
      ▼
[Jetpack Compose UI: MainActivity, HomeScreen, GameCardItem, ZipExtractionDialog, etc.]
```

### Детализация модулей и классов

#### 1. Слой данных и сеть (`com.example.data`):
* `remote/EmuLandScraper.kt`:
  * Парсинг списка консолей (`getDefaultConsoles()`, `fetchConsoles()`).
  * Парсинг страниц каталога с пагинацией (`fetchGamesPageForConsole()`).
  * Извлечение метаданных игры, скриншотов и связки `mfileId` со страниц игр (`fetchGameCardDetails()`).
  * Парсинг версий РОМов из AJAX-эндпоинта `?act=getmfl&id=<mfileId>` (`fetchVersionsFromGetmfl()`). Корректная группировка по блокам `collaps-item` (ОСНОВНЫЕ, ПИРАТКИ, ПЕРЕВЕДЁННЫЕ, GOODNES).
  * Извлечение прямых ссылок на загрузку без браузерного JS-редиректа (`resolveDirectDownloadUrl()`, `resolveDirectDownloadFromUrl()`).
* `local/AppDatabase.kt`, `local/Entities.kt`, `local/Daos.kt`:
  * `ConsoleEntity` / `ConsoleDao`: хранение списка поддерживаемых платформ, их порядка, включения/выключения.
  * `GameEntity` / `GameDao`: оффлайн-кэш загруженных страниц каталога, избранного (`isFavorite`), статуса загрузки (`isDownloaded`).
  * `DownloadEntity` / `DownloadDao`: история загрузок со статусами (`PENDING`, `DOWNLOADING`, `COMPLETED`, `FAILED`, `CANCELLED`), байтами и путями к файлам.
* `downloader/RomDownloader.kt`:
  * Скачивание файла чанками по 8 КБ с вычислением прогресса каждые 250 мс.
  * Первичная запись во временный файл в `context.cacheDir`.
  * **Авто-распаковка single-ROM:** если в ZIP ровно 1 файл РОМа (и опционально `.txt`/`.nfo`), он извлекается напрямую в целевую папку консоли, а ZIP удаляется.
  * **Обнаружение multi-ROM:** если в архиве найдено >1 РОМа, загрузка приостанавливается с возвратом `DownloadExecutionResult.RequiresSelection(ZipExtractionRequest)`.
  * Методы `extractSelectedEntries()`, `keepZipWithoutExtraction()`, `dismissZipExtraction()`.
  * Поддержка записи в кастомную директорию через `DocumentFile.fromTreeUri` (SAF) или в системную `MediaStore.Downloads` (`Download/RetroROMs/<Консоль>/`).
  * Сохранение настроек в `SharedPreferences ("emuland_prefs")`: `auto_unpack_zip`, `delete_zip_after_unpack`, `custom_folder_uri`.
* `repository/EmuLandRepository.kt`:
  * Фасад данных, объединяющий Scraper, Room DAO и RomDownloader.

#### 2. Слой состояния (`com.example.ui.viewmodel`):
* `viewmodel/EmuLandViewModel.kt`:
  * Хранение и трансляция UI-состояний через `StateFlow`:
    * `selectedConsole`, `displayedGames`, `favoriteGames`, `downloadRecords`, `activeDownloads`.
    * `catalogViewMode` (`GRID` / `LIST`).
    * `pendingZipExtraction` (`ZipExtractionRequest?` для триггера диалога распаковки).
    * `isAutoUnpackEnabled`, `isDeleteZipAfterUnpack`.
    * `userMessage` (`SharedFlow<String>` для Snackbar-уведомлений).
  * Управление темой оформления (`ThemeMode`, кастомные палитры через `ThemePreferences`).

#### 3. Компоненты UI (`com.example.ui`):
* `components/GameCardItem.kt` (Режим сетки):
  * Компактная высота (~185 dp вместо исходных 300+ dp).
  * Обложка 16:10 с рейтингом и кнопкой избранного.
  * **Бегущая строка (Marquee):** `maxLines = 1`, по умолчанию статический текст (`TextOverflow.Ellipsis`). При касании/нажатии на тайл включается `Modifier.basicMarquee(iterations = 4, initialDelayMillis = 300, repeatDelayMillis = 800)`.
  * **Полноэкранная обложка по Long Press:** `combinedClickable` на внешнем контейнере карточки открывает `FullScreenImageDialog` с тактильным виброоткликом.
* `components/GameListItem.kt` (Режим списка):
  * Фиксированная высота 52 dp.
  * Долгое нажатие на **всей площади строки** открывает полноэкранный просмотр обложки.
  * Бегущая строка названия игры при тапе/взаимодействии.
* `components/FullScreenImageDialog.kt`:
  * Полноэкранный просмотр обложки/скриншота с поддержкой масштабирования двумя пальцами (Pinch-to-Zoom), панорамирования (Pan) и закрытия по свайпу/тапу/кнопке Back.
* `screens/RomVersionsSheet.kt`:
  * Модальная шторка со списком версий/ревизий РОМа для выбранной игры.
  * Чистые категории (ОСНОВНЫЕ, ПИРАТКИ, ПЕРЕВЕДЁННЫЕ, GOODNES), подсветка русских версий, размер файлов.
* `screens/ZipExtractionDialog.kt`:
  * Модальный диалог при скачивании многофайлового архива.
  * Отображение всех РОМов из архива с их размерами.
  * Чекбоксы, быстрые кнопки «Выбрать все» / «Снять все».
  * Кнопки «Извлечь (N)» и «Оставить ZIP».
* `screens/DownloadsScreen.kt`:
  * Список активных и завершенных загрузок.
  * Карточка хранилища: выбор кастомной папки через системный проводник SAF (`OpenDocumentTree`), сброс к дефолту.
  * Тумблеры «Распаковывать ZIP-архивы» и «Удалять ZIP после распаковки».
* `screens/SettingsDialog.kt`:
  * Выбор отображаемых платформ (пресеты: Все, Nintendo, Sega, Sony, Ретро 8/16-бит, Портативные).
  * Редактор темы (Arcade Neon, Cyberpunk, Retrowave, Game Boy Classic, кастомная палитра).
* `MainActivity.kt`:
  * Корневой хост для навигации (`NavigationBar` с вкладками Каталог, Загрузки, Избранное).
  * Централизованный хостинг диалогов (`GameDetailSheet`, `RomVersionsSheet`, `SettingsDialog`, `ZipExtractionDialog`).
  * Полная обработка Predictive Back / `BackHandler`.

---

## 3. История проблем и багфиксов (Troubleshooting & Bug Fixes)

Ниже перечислены критические технические проблемы, с которыми проект столкнулся в процессе разработки, их первопричины и решения:

| Проблема / Ошибка | Первопричина | Как решено (Fix) | Gotcha / Что нельзя ломать |
| :--- | :--- | :--- | :--- |
| **Дублирование текста в категориях версий («Основные Основные», «Пиратки Пиратки»)** | В `EmuLandScraper.kt` селектор Jsoup был записан как `.select(".title span, .title")`. Поскольку в HTML тег `span` находится внутри `.title`, Jsoup находил оба тега, а `.text()` конкатенировал их содержимое через пробел. | Заменено на точечный селектор `selectFirst(".title span")` с фолбэком на `ownText()` и дедупликацией повторяющихся слов. В `RomVersionsSheet.kt` добавлена санитарная очистка строк. | Не объединять родительские и дочерние селекторы в Jsoup через запятую, если вызывается `.text()`. |
| **Слишком вытянутые карточки в сетке (Grid Mode) и пустое место** | Фиксированная `heightIn(min = 176.dp)` для блока текста под обложкой, принудительные `minLines = 2` для заголовка и отдельные строки под жанр и бейджи растягивали карточку до >300 dp. | Высота текстового блока уменьшена до ~78 dp, заголовок переведён в 1 строку, подзаголовок сжат в «Жанр • Год», размер кнопки скачивания оптимизирован до 32 dp. Общая высота карточки стала ~185 dp. | Не задавать статическую минимальную высоту текстовым контейнерам карточек сетки. |
| **Рябящий экран при использовании Marquee («табло с бегущими строками»)** | Постоянно анимированный `basicMarquee` на всех карточках одновременно создавал хаотичное движение по всему экрану. | Введён триггер состояния `isInteracted` (по тапу на карточку, удержанию или клику по названию). По умолчанию текст статичен (`TextOverflow.Ellipsis`), а бегущая строка запускается только для активированного тайла. | Не включать `basicMarquee` безусловно на всех элементах Lazy-списков/сеток. |
| **Ошибка компиляции `No parameter with name 'delayMillis' found`** | В Compose BOM 2024.09.00 (Compose Foundation 1.7) сигнатура `Modifier.basicMarquee` принимает `initialDelayMillis` и `repeatDelayMillis`, а не `delayMillis`. | Заменено на `initialDelayMillis = 300, repeatDelayMillis = 800`. | Учитывать точные имена аргументов API Compose Foundation для версии 1.7+. |
| **Долгое нажатие работало только по миниатюре обложки** | `combinedClickable` с вызовом `onLongClick` был повешен исключительно на маленький контейнер обложки (36 dp в списке и картинку в сетке). | `combinedClickable` вынесен на внешний `Box` всего элемента как в `GameCardItem`, так и в `GameListItem`. Добавлен тактильный виброотклик `LocalHapticFeedback`. | Пользователь ожидает Long Press по всей площади карточки/строки. |
| **Ошибки типов Room при создании записей загрузки** | `downloadDao.insertDownload(...)` принимает `@Entity DownloadEntity`, а в `RomDownloader` по ошибке передавался доменный класс `DownloadRecord`. | В `RomDownloader` добавлен маппинг и передача `DownloadEntity(status = DownloadStatus.DOWNLOADING.name)`. | Чётко разделять Domain Models (`DownloadRecord`) и Room Entities (`DownloadEntity`). |
| **Сбой вызова suspend-функции вне корутины** | Метод `dismissZipExtraction` в `RomDownloader` обращался к `suspend fun downloadDao.updateProgress`, не являясь `suspend`. | Метод `dismissZipExtraction` объявлен как `suspend fun ... = withContext(Dispatchers.IO)`, а в `EmuLandViewModel` обёрнут в `viewModelScope.launch`. | Все операции с Room DAO должны выполняться в корутинах на `Dispatchers.IO`. |
| **Сервер Emu-Land возвращал 403 Forbidden или HTML-страницу ошибки вместо РОМа** | Запросы на скачивание блокировались антибот-системой из-за отсутствия правильных заголовков `Referer` и `User-Agent`. | Добавлены обязательные заголовки `Referer: https://www.emu-land.net/...`, валидный десктопный `User-Agent` и резолвинг прямых ссылок через промежуточные запросы Emu-Land `getmfl`. | Скачивание с Emu-Land строго требует корректный заголовок `Referer` с URL страницы соответствующей консоли/игры. |

---

## 4. Текущие компромиссы и техдолг (Technical Debt & Limitations)

1. **Зависимость от HTML-верстки Emu-Land.net (Web Scraping):**
   * У Emu-Land нет публичного REST API. Все данные извлекаются через Jsoup. Если администрация сайта изменит CSS-классы (`.collaps-item`, `.mfile_list`, `.game_card` и т.п.), парсер потребуется обновить.
2. **Поддержка архивов `.7z`:**
   * В текущей реализации распаковка работает для стандартного формата `.zip` через системную библиотеку `java.util.zip`. Некоторые РОМы (особенно сеты GoodNES или образы PS1/Dreamcast) на Emu-Land упакованы в `.7z`. Сейчас такие файлы сохраняются как сырые `.7z` архивы без распаковки. Для распаковки `.7z` потребуется подключение библиотеки Apache Commons Compress или XZ for Java.
3. **Хранение кэша игр:**
   * Страницы каталога кэшируются в Room, однако при полном отсутствии интернета и пустой базе данных первичное наполнение каталога невозможно без сети.
4. **Удаление загрузок:**
   * При удалении записи из вкладки «Загрузки» удаляется только запись из БД `downloadDao`. Сам физический файл на диске пользователя намеренно не стирается без дополнительного подтверждения, чтобы предотвратить случайную потерю данных.

---

## 5. Пожелания пользователя и бэклог (User Requirements & Roadmap)

Следующий бэк-лог сформирован на базе диалога и пожеланий пользователя:

### Приоритет 1 (Высокий / Quick Wins):
* **Поддержка распаковки `.7z`-архивов:**
  * Интегрировать легковесную библиотеку для работы с 7z (например, `org.tukaani:xz` или `SevenZipJBinding` / `Apache Commons Compress`), чтобы логика `ZipExtractionDialog` и авто-распаковки одинаково поддерживала и `.zip`, и `.7z`.
* **Запуск РОМа в установленном эмуляторе:**
  * Добавить на карточку скачанной игры кнопку «Играть» / «Открыть в эмуляторе», отправляющую системный `Intent(Intent.ACTION_VIEW)` с `content://` URI и mime-типом `application/octet-stream` для перенаправления в RetroArch, DuckStation, NES.emu, MD.emu и др.

### Приоритет 2 (Средний):
* **Очередь и параллельная загрузка:**
  * Отображение общего прогресс-бара и возможности ставить несколько игр в очередь на скачивание.
* **Фильтрация в диалоге выбора РОМов:**
  * В `ZipExtractionDialog` добавить быстрое поле поиска или фильтр-чипы («Только RUS», «Только USA») для больших архивов GoodSet, где содержится по 50+ версий одного рома.

### Приоритет 3 (Низкий / Улучшения):
* **Физическое удаление файла с диска:**
  * В диалоге удаления записи загрузки добавить чекбокс: *«Также удалить файл с накопителя»*.
* **Экспорт/импорт настроек и избранного:**
  * Возможность экспортировать список избранного в JSON-файл для переноса на другое устройство.

---

## 6. Памятка для нового ИИ-разработчика (Context Prompt for next LLM)

```markdown
You are continuing development of the "RetroROMs" Android project (an Emu-Land.net game catalog and ROM downloader).
Key architectural rules and constraints:
1. Framework: 100% Jetpack Compose with Material 3. Do NOT introduce XML layouts or fragments.
2. Architecture: MVVM with Repository pattern. State is managed via StateFlow in EmuLandViewModel and collected in UI via collectAsStateWithLifecycle().
3. Styling: Adhere strictly to the Arcade Dark aesthetic. Use AppThemeColors from LocalAppColors.current and centralized theme palette in Theme.kt / ThemePreferences.kt.
4. ROM Extraction:
   - Single-ROM ZIPs unpack automatically into the console folder and delete the zip file.
   - Multi-ROM ZIPs MUST prompt the user via ZipExtractionDialog to select files without automated heuristic pre-selection.
5. Network / Scraping:
   - When communicating with emu-land.net, ALWAYS provide a valid User-Agent and a Referer header matching the respective console/game section URL to bypass 403 Forbidden and bot protections.
   - Be mindful of Jsoup selectors: avoid combining parent and child tags in comma-separated selectors (e.g. use selectFirst(".title span") instead of select(".title span, .title")) to prevent duplicate text concatenation.
6. Build verification: Always verify changes using compile_applet and run gradle testDebugUnitTest before concluding.
```

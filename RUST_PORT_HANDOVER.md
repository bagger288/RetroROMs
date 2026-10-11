# 🦀 RetroROMs — Desktop PC Port Handover Specification (Rust)

> **Документ передачи проекта (Project Handover Document)**  
> **Цель:** Полная спецификация для разработчика или автономного ИИ-агента для создания нативного кроссплатформенного десктопного приложения (Windows / Linux / macOS) на языке **Rust**, повторяющего весь функционал Android-версии **RetroROMs**, но глубоко оптимизированного под настольную ОС, горизонтальные мониторы, файловую систему ПК и мышь/клавиатуру.

---

## 📑 Содержание

1. [Введение и цели проекта](#1-введение-и-цели-проекта)
2. [Рекомендуемый стек технологий на Rust](#2-рекомендуемый-стек-технологий-на-rust)
3. [Архитектура настольного приложения](#3-архитектура-настольного-приложения)
4. [Модели данных (Rust Structs)](#4-модели-данных-rust-structs)
5. [Сетевой слой и протокол Emu-Land.net (Web Scraper)](#5-сетевой-слой-и-протокол-emu-landnet-web-scraper)
6. [Пайплайн загрузки и умной распаковки архивов](#6-пайплайн-загрузки-и-умной-распаковки-архивов)
7. [База данных и локальное хранилище (SQLite)](#7-база-данных-и-локальное-хранилище-sqlite)
8. [Адаптация UI/UX под ПК (Desktop Guidelines)](#8-адаптация-uiux-под-пк-desktop-guidelines)
9. [Интеграция с эмуляторами и файловой системой ПК](#9-интеграция-с-эмуляторами-и-файловой-системой-пк)
10. [Пошаговый план реализации (Actionable Roadmap)](#10-пошаговый-план-реализации-actionable-roadmap)
11. [Приложения: селекторы, заголовки и список консолей](#11-приложения-селекторы-заголовки-и-список-консолей)

---

## 1. Введение и цели проекта

### 1.1 Что такое RetroROMs
**RetroROMs** — это специализированный менеджер каталога и загрузчик ретро-игр с портала [Emu-Land.net](https://www.emu-land.net). Приложение позволяет:
- Просматривать каталог **33 игровых платформ** (только системы с доступными для загрузки файлами).
- Использовать динамические категории, алфавитный рубрикатор и мгновенный поиск.
- Просматривать детальные карточки игр (скриншоты, описание, разработчик, год, рейтинг).
- Выбирать конкретную ревизию ROM-файла (RUS, USA, EUR, Hacks, Translations, GoodSet).
- Скачивать игры с автоматической сортировкой по папкам консолей.
- Автоматически распаковывать однофайловые ZIP-архивы и предоставлять интерактивный диалог выбора файлов для многофайловых архивов (GoodSets).
- Управлять списком избранного и историей загрузок.

### 1.2 Задачи PC-порта
- **Нативная производительность:** Мгновенный запуск, потребление памяти менее 80–120 МБ, плавный рендеринг 60+ FPS.
- **Горизонтальный layout (Widescreen):** Эффективное использование экранов 16:9, 16:10 и Ultrawide 21:9 вместо мобильного вертикального списка.
- **Интеграция с ОС ПК:**
  - Нативный выбор папки для сохранения (`rfd`).
  - Прямой запуск игр в установленных на ПК эмуляторах (RetroArch, Mesen, DuckStation, PCSX-Reloaded, Snes9x, Kega Fusion и др.).
  - Открытие папки с файлом в Проводнике Windows / Finder macOS / файловом менеджере Linux.
- **Удобство ввода:** Горячие клавиши (`Ctrl+F`, `Esc`, `Space` для превью, стрелки), правый клик (контекстное меню), колесо мыши для горизонтального скролла.

---

## 2. Рекомендуемый стек технологий на Rust

| Компонент | Рекомендуемый крейт | Обоснование и альтернативы |
| :--- | :--- | :--- |
| **GUI Framework** | **`egui` + `eframe`** *(Основная рекомендация)* | Чистый Rust, Immediate Mode, идеален для ретро/киберпанк эстетики, мгновенно компилируется, не зависит от системных библиотек C++. <br>*Альтернатива 1:* **`slint`** (великолепный декларативный DSL, реактивность, нативный вид). <br>*Альтернатива 2:* **`tauri`** (Rust бэкенд + Svelte/React фронтенд — если нужен пиксель-перфект веб-дизайн). |
| **Async Runtime** | **`tokio`** (multi-threaded) | Стандарт индустрии для асинхронного ввода-вывода, каналов `tokio::sync::mpsc` и параллельных загрузок. |
| **HTTP Client** | **`reqwest`** (`features = ["json", "stream", "cookies", "gzip", "brotli"]`) | Пул соединений, кастомные заголовки, потоковая передача тела ответа для индикации прогресса, перехват редиректов. |
| **HTML Parser** | **`scraper`** (или **`tl`**) | Парсинг HTML через CSS-селекторы, аналог `Jsoup` из Android/Java. |
| **Local Database** | **`rusqlite`** (`features = ["bundled"]`) или **`sqlx`** | Встроенный SQLite, полный аналог Room. `rusqlite` работает синхронно в отдельных worker-потоках либо через `tokio-rusqlite`. |
| **ZIP Archives** | **`zip`** (`features = ["deflate", "bzip2"]`) | Потоковое чтение заголовков ZIP, извлечение отдельных файлов без распаковки всего архива в память. |
| **File Dialogs** | **`rfd`** (Rusty File Dialogs) | Нативные диалоги выбора папок и файлов на Windows, Linux, macOS. |
| **OS Commands** | **`open`** | Кроссплатформенное открытие папок и файлов в дефолтных приложениях ОС. |
| **Image Loading** | **`egui_extras`** (`features = ["image"]`) + **`image`** | Декодирование и асинхронная загрузка PNG/JPEG/WebP скриншотов и обложек. |
| **Serialization** | **`serde`**, **`serde_json`** | Конфигурация приложения, кэш и настройки. |
| **Config Dirs** | **`dirs`** | Кроссплатформенные стандартные пути (`%APPDATA%`, `~/.config`, `~/Downloads`). |

---

## 3. Архитектура настольного приложения

Рекомендуется архитектура **Actor / Message-Driven** с разделением на UI-поток и фоновые worker-задачи:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        GUI Thread (egui / eframe)                      │
│   • Sidebar (Платформы, Навигация, Фильтр порядка)                     │
│   • Toolbar (Поиск, Категории #pagelist_top, Вид Сетка/Таблица, Сорт)  │
│   • Main Pane (Game Cards / Data Grid, Infinite Scroll / Пагинация)    │
│   • Right Pane / Modal (Детали игры, Выбор версий РОМа, Распаковка)    │
└───────────────────────────▲────────────────────────────┬───────────────┘
                            │ UI Events /                │ Action Commands
                            │ State Updates              │ (Fetch, Download, Extract)
                            │ (mpsc::channel)            │ (mpsc::channel)
┌───────────────────────────┴────────────────────────────▼───────────────┐
│                      Async App Core (Tokio Runtime)                    │
│                                                                        │
│  ┌────────────────────────┐  ┌────────────────┐  ┌──────────────────┐  │
│  │   EmuLandScraper       │  │ DownloadManager│  │  ArchiveService  │  │
│  │   • reqwest client     │  │ • Multi-stream │  │  • zip scan      │  │
│  │   • scraper (Jsoup eq) │  │ • Progress bus │  │  • smart extract │  │
│  │   • act=getmfl parser  │  │ • Speed calc   │  │  • single/multi  │  │
│  └───────────┬────────────┘  └───────┬────────┘  └─────────┬────────┘  │
│              │                       │                     │           │
│  ┌───────────▼───────────────────────▼─────────────────────▼────────┐  │
│  │              SQLite Database & Config Manager (rusqlite)         │  │
│  │  • Consoles (order, enabled)    • Games cache                   │  │
│  │  • Downloads history             • Favorites                     │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Модели данных (Rust Structs)

Аналоги моделей из `app/src/main/java/com/example/model/Models.kt`:

```rust
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum DownloadStatus {
    Pending,
    Downloading,
    Completed,
    Failed,
    Cancelled,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ConsoleInfo {
    pub slug: String,
    pub name: String,
    pub short_name: String,
    pub category: String,          // "home", "portable", "classic"
    pub folder_name: String,       // Название подпапки (например, "NES", "MegaDrive")
    pub section: String,           // "consoles" или "portable"
    pub is_enabled: bool,
    pub order: i32,                // Пользовательский порядок сортировки
    pub release_year: String,
    pub icon_key: String,
    pub roms_count_estimate: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CatalogCategory {
    pub key: String,               // "top", "best", "rating", "a", "b", "0-9"
    pub label: String,             // "🔥 Популярные", "★ Лучшие", "A", "B"
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct GameCard {
    pub id: String,                // Slug или хэш URL
    pub console_slug: String,
    pub console_name: String,
    pub section: String,
    pub title: String,
    pub original_title: Option<String>,
    pub genre: String,
    pub year: String,
    pub publisher: String,
    pub developer: String,
    pub rating: f32,
    pub file_size: String,
    pub cover_url: Option<String>,
    pub screenshot_urls: Vec<String>,
    pub description: String,
    pub download_url: String,      // Исходный URL скачивания или страница getmfl
    pub mfile_id: Option<String>,  // ID для act=getmfl
    pub game_page_slug: Option<String>,
    pub regions: Vec<String>,
    pub is_favorite: bool,
    pub is_downloaded: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RomFileVersion {
    pub fid: String,               // Идентификатор файла внутри mfile
    pub name: String,              // Например: "Super Mario Bros. (W) [!].nes"
    pub size: String,              // Например: "40.5 KiB"
    pub category: String,          // "Основные", "Переводы", "Хаки"
    pub download_url: String,
    pub region_or_type: String,    // "RUS", "USA", "EUR", "JAP", "HACK", "GOODSET"
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ZipRomEntry {
    pub entry_name: String,        // Внутренний путь в архиве
    pub display_name: String,      // Имя файла без пути
    pub size_bytes: u64,
    pub formatted_size: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownloadRecord {
    pub id: i64,
    pub game_id: String,
    pub game_title: String,
    pub console_slug: String,
    pub console_name: String,
    pub file_name: String,
    pub target_directory: String,
    pub total_bytes: u64,
    pub downloaded_bytes: u64,
    pub status: DownloadStatus,
    pub download_url: String,
    pub timestamp: i64,
    pub error_message: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppSettings {
    pub download_directory: String,     // Путь на диске (например, "C:\Emulators\ROMs")
    pub auto_unpack_zip: bool,          // Распаковывать ли single-ROM архивы
    pub delete_zip_after_unpack: bool,  // Удалять ли исходный .zip при успешной распаковке
    pub active_theme: String,           // "ArcadeNeon", "Cyberpunk", "Retrowave", "GameBoy"
    pub view_mode: String,              // "Grid" или "Table"
    pub emulator_paths: std::collections::HashMap<String, String>, // slug -> path to exe
}
```

---

## 5. Сетевой слой и протокол Emu-Land.net (Web Scraper)

### 5.1 HTTP-клиент и обязательные заголовки
Сайт `emu-land.net` блокирует или отдаёт пустые ответы, если запросы идут без стандартных браузерных заголовков:
```rust
const BASE_URL: &str = "https://www.emu-land.net";
const USER_AGENT: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";

let client = reqwest::Client::builder()
    .user_agent(USER_AGENT)
    .timeout(std::time::Duration::from_secs(30))
    .build()?;
```

### 5.2 Определение подпутей игр (Subpaths)
У разных систем на Emu-Land разные корневые подпути:
```rust
pub fn get_game_subpath(slug: &str) -> &'static str {
    match slug.to_lowercase().as_str() {
        "psx" => "iso",
        "3do" | "segacd" | "pcecd" | "neogeocd" | "famicom_disk_system" | "sg-1000" | "gb" | "gbc" => "games",
        _ => "roms",
    }
}
```

### 5.3 Формирование URL каталога и пагинации
- **Первая страница категории:** `https://www.emu-land.net/{section}/{slug}/{subpath}/{category}`
- **Страница N категории:** `https://www.emu-land.net/{section}/{slug}/{subpath}/{category}/{page}`
- **Все игры (если категории отсутствуют):** `https://www.emu-land.net/{section}/{slug}/{subpath}/{page}`

### 5.4 Динамический парсинг категорий (#pagelist_top)
Для каждой консоли на странице каталога присутствует блок `#pagelist_top` (или `.pagelist`):
1. **Специальные фильтры (`.add a`):**
   - Ссылка `.../top` -> "🔥 Популярные"
   - Ссылка `.../best` -> "★ Лучшие"
   - Ссылка `.../rating` -> "★ Рейтинг"
   - Ссылка `.../homebrew` -> "🛠 Homebrew"
   - Ссылка `.../misc` -> "📁 Прочее"
2. **Алфавитный указатель (`.abc a`):**
   - Парсятся только те буквы и цифры, которые реально оформлены тегом `<a href="...">` (т.е. по которым есть игры в базе).
   - Это предотвращает пустые страницы.

### 5.5 Парсинг карточек игр в каталоге
HTML-структура списка игр на Emu-Land:
- Контейнеры игр: `.glist-item`, `.game-item`, либо строки таблицы `.glist tr`.
- Название игры: `.title a` или `.name a`.
- Ссылка на страницу игры: `a[href]` (извлекается `game_page_slug`).
- Обложка/скриншот: `.ss-area img`, `.picture img` или `.preview img`.
- Размер файла: `.size`, `.filesize`.
- Рейтинг: `.rating`, подсчёт звёзд или числовой балл.
- Кнопка/ссылка скачивания: элементы с `[onclick*='getmfl']`, `.btn-sdl`, `a[href*='act=getmfl']`.
  - Извлекается `mfileId` с помощью регулярного выражения: `Regex::new(r"act=(?:getmfl|getfile)&(?:amp;)?id=([0-9]+)")`.

### 5.6 Парсинг версий РОМов (AJAX эндпоинт `act=getmfl`)
Когда пользователь нажимает "Выбрать версию" или пытается скачать игру:
1. Запрос отправляется на:
   ```
   GET https://www.emu-land.net/{section}/{slug}/{subpath}?act=getmfl&id={mfileId}
   ```
2. **Обязательные заголовки:**
   ```http
   User-Agent: Mozilla/5.0 ...
   Referer: https://www.emu-land.net/{section}/{slug}/{subpath}
   X-Requested-With: XMLHttpRequest
   ```
3. **Парсинг HTML ответа:**
   - Блоки групп версий: `.collaps-item`.
   - Название группы: `.title span` или `.title` ("Основные", "Переводы", "Хаки", "GoodSet").
   - Элементы версий: `.item` внутри блока.
   - Ссылка: `a[href*='fid=']` -> извлекается `fid` (ID файла).
   - Название файла: `link.text()` (например: `Battletoads (U) [!].nes`).
   - Размер: `.size` (например: `256.0 KiB`).
   - Определение региона:
     - Если содержит `(R)`, `(Rus)`, `(Russian)` -> `RUS`.
     - Если содержит `(U)`, `(USA)` -> `USA`.
     - Если содержит `(E)`, `(Europe)` -> `EUR`.
     - Если содержит `(J)`, `(Japan)` -> `JAP`.
     - Если содержит `[h`, `(Hack)` -> `HACK`.

### 5.7 Получение прямой ссылки на скачивание (HTTP 302 Interception)
Эндпоинт скачивания на Emu-Land:
```
https://www.emu-land.net/{section}/{slug}/{subpath}?act=getmfl&id={mfileId}&fid={fid}
```
**Критическая особенность:** Данный эндпоинт не возвращает бинарные данные напрямую, а отвечает статусом **HTTP 302 Found** с заголовком `Location`, указывающим на реальный файл на файловом сервере (например, `https://files.emu-land.net/.../rom.zip`).
- В Rust необходимо настроить клиент с отключенным автоматическим редиректом (`redirect::Policy::none()`) либо вручную перехватить заголовок `Location`:
```rust
let no_redirect_client = reqwest::Client::builder()
    .redirect(reqwest::redirect::Policy::none())
    .user_agent(USER_AGENT)
    .build()?;

let resp = no_redirect_client.get(&getmfl_url)
    .header("Referer", &referer)
    .send().await?;

if let Some(location) = resp.headers().get(reqwest::header::LOCATION) {
    let direct_url = location.to_str()?;
    // Это прямая ссылка на ZIP/7Z!
}
```

---

## 6. Пайплайн загрузки и умной распаковки архивов

### 6.1 Потоковое скачивание с индикацией прогресса
Загрузка должна выполняться асинхронно в потоке `tokio`:
1. Создаётся временный файл во временной директории: `std::env::temp_dir().join(format!("rom_{}.tmp", timestamp))`.
2. Запрос отправляется с заголовками:
   ```http
   User-Agent: Mozilla/5.0 ...
   Referer: https://www.emu-land.net/{section}/{slug}/{subpath}
   Accept: */*
   ```
3. **Проверка Content-Type:** Если сервер возвращает `text/html`, загрузка прерывается с ошибкой (значит, сессия истекла или сработал anti-hotlink).
4. Чтение чанков потока (`response.bytes_stream()`):
   - Запись в файл.
   - Подсчёт скачанных байтов.
   - Отправка сообщений в UI поток через канал `tokio::sync::mpsc::channel` с частотой не чаще 10 раз в секунду (для экономии CPU):
     ```rust
     pub struct DownloadProgressEvent {
         pub record_id: i64,
         pub downloaded_bytes: u64,
         pub total_bytes: u64,
         pub speed_bytes_sec: u64,
         pub percent: f32,
     }
     ```

### 6.2 Сканирование ZIP-архива
После завершения скачивания временный файл проверяется на то, является ли он ZIP-архивом (сигнатура `PK\x03\x04`).
Используется крейт `zip::ZipArchive`:
- Игнорируются системные файлы: `__MACOSX`, `.DS_Store`.
- Игнорируются не-ROM файлы по расширению: `.txt`, `.nfo`, `.url`, `.diz`, `.doc`, `.pdf`, `.html`, `.png`, `.jpg`, `.exe`, `.bat`.
- Собирается список найденных ROM-файлов (`Vec<ZipRomEntry>`).

### 6.3 Логика распаковки:
1. **Если в архиве ровно 1 ROM-файл:**
   - Если опция `auto_unpack_zip == true`:
     - ROM извлекается напрямую в `<download_directory>/<ConsoleFolderName>/<RomName>`.
     - Если опция `delete_zip_after_unpack == true`, временный ZIP удаляется.
     - Статус загрузки переводится в `Completed` ("Распаковано: {RomName}").
   - Если `auto_unpack_zip == false`:
     - Исходный ZIP перемещается в `<download_directory>/<ConsoleFolderName>/<GameTitle>.zip`.
2. **Если в архиве > 1 ROM-файлов (GoodSet / сборники):**
   - Отправляется событие в UI для открытия модального окна `ZipExtractionDialog`.
   - В окне выводится список всех файлов с их размерами и чекбоксами.
   - Предусмотрены кнопки "Выбрать все", "Снять все", "Только RUS/USA".
   - Пользователь нажимает "Извлечь выбранное":
     - Выбранные файлы извлекаются в папку консоли.
     - Исходный ZIP удаляется (или сохраняется по желанию пользователя).

---

## 7. База данных и локальное хранилище (SQLite)

Файл базы данных: `<config_dir>/RetroROMs/retroms.db`.

### 7.1 Схема таблиц SQLite
```sql
-- Таблица платформ
CREATE TABLE IF NOT EXISTS consoles (
    slug TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    short_name TEXT NOT NULL,
    category TEXT NOT NULL,
    folder_name TEXT NOT NULL,
    section TEXT NOT NULL DEFAULT 'consoles',
    is_enabled INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER NOT NULL DEFAULT 0,
    release_year TEXT NOT NULL DEFAULT '',
    icon_key TEXT NOT NULL DEFAULT 'gamepad',
    roms_count_estimate TEXT NOT NULL DEFAULT ''
);

-- Кэш списка игр
CREATE TABLE IF NOT EXISTS games (
    id TEXT PRIMARY KEY,
    console_slug TEXT NOT NULL,
    console_name TEXT NOT NULL,
    section TEXT NOT NULL DEFAULT 'consoles',
    title TEXT NOT NULL,
    original_title TEXT,
    genre TEXT NOT NULL DEFAULT 'Action',
    year TEXT NOT NULL DEFAULT 'N/A',
    publisher TEXT NOT NULL DEFAULT 'Unknown',
    developer TEXT NOT NULL DEFAULT 'Unknown',
    rating REAL NOT NULL DEFAULT 4.8,
    file_size TEXT NOT NULL DEFAULT 'ROM',
    cover_url TEXT,
    screenshot_urls_json TEXT NOT NULL DEFAULT '[]',
    description TEXT NOT NULL DEFAULT '',
    download_url TEXT NOT NULL DEFAULT '',
    mfile_id TEXT,
    game_page_slug TEXT,
    category TEXT NOT NULL DEFAULT 'top',
    page INTEGER NOT NULL DEFAULT 1,
    is_favorite INTEGER NOT NULL DEFAULT 0,
    is_downloaded INTEGER NOT NULL DEFAULT 0,
    cached_timestamp INTEGER NOT NULL DEFAULT 0
);

-- Индексы для мгновенного поиска и пагинации
CREATE INDEX IF NOT EXISTS idx_games_console_cat ON games (console_slug, category, page);
CREATE INDEX IF NOT EXISTS idx_games_favorite ON games (is_favorite);
CREATE INDEX IF NOT EXISTS idx_games_title ON games (title COLLATE NOCASE);

-- История загрузок
CREATE TABLE IF NOT EXISTS downloads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    game_id TEXT NOT NULL,
    game_title TEXT NOT NULL,
    console_slug TEXT NOT NULL,
    console_name TEXT NOT NULL,
    file_name TEXT NOT NULL,
    target_directory TEXT NOT NULL,
    total_bytes INTEGER NOT NULL DEFAULT 0,
    downloaded_bytes INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PENDING',
    download_url TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    error_message TEXT
);
```

### 7.2 Инициализация и сохранение порядка консолей
- При первом запуске заполняются дефолтные 33 платформы со значениями `sort_order` от 0 до 32.
- Пользователь в окне "Настройки платформ" может перемещать консоли (Вверх/Вниз/В самый верх) и выключать неиспользуемые.
- Изменение `sort_order` мгновенно обновляется транзакцией `UPDATE consoles SET sort_order = ? WHERE slug = ?`.

---

## 8. Адаптация UI/UX под ПК (Desktop Guidelines)

В отличие от мобильного экрана, где всё выстроено в вертикальную колонку со шторками (BottomSheet), на ПК необходимо реализовать **эргономичный многопанельный интерфейс**:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│ [≡] RetroROMs Desktop           [🔍 Поиск по каталогу...            ]  [Вид: ⊞ ☰]  [Сортировка: Имя ▼]  [⟳ Обновить]│
├────────────────────┬──────────────────────────────────────────────────────────────────────┬──────────────────────┤
│ 🎮 ПЛАТФОРМЫ       │  [🔥 Топ] [★ Лучшие] [★ Рейтинг] [0-9] [A] [B] [C] [D] ... [X] [Y] [Z]│ ℹ️ ИНФОРМАЦИЯ        │
│                    ├──────────────────────────────────────────────────────────────────────┤                      │
│ [●] Dendy (NES)    │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌───────────┐  │ ┌──────────────────┐ │
│ [●] Sega Genesis   │  │  [ОБЛОЖКА]   │  │  [ОБЛОЖКА]   │  │  [ОБЛОЖКА]   │  │ [ОБЛОЖКА] │  │ │   БОЛЬШАЯ        │ │
│ [●] Super Nintendo │  │ Super Mario  │  │ Battletoads  │  │ Contra       │  │ Duck Hunt │  │ │   ОБЛОЖКА        │ │
│ [●] PlayStation 1  │  │ NES • 1985   │  │ NES • 1991   │  │ NES • 1988   │  │ NES • 1984│  │ └──────────────────┘ │
│ [●] Nintendo 64    │  │ ★ 4.9 • 40KB │  │ ★ 4.8 • 256KB│  │ ★ 4.9 • 128KB│  │ ★ 4.7    │  │ Super Mario Bros.   │
│ [●] Game Boy Adv.  │  │ [⬇ Скачать]  │  │ [⬇ Скачать]  │  │ [⬇ Скачать]  │  │ [⬇ Скачать│  │ Жанр: Platformer    │
│ [●] Game Boy Color │  └──────────────┘  └──────────────┘  └──────────────┘  └───────────┘  │ Год: 1985            │
│ [●] Sega CD        │                                                                      │ Разработчик: Nintendo│
│                    │  [Страница 1 из 14]   [< Назад]  [1] [2] [3] ... [14]  [Вперёд >]     │                      │
├────────────────────┤                                                                      │ Доступные версии:    │
│ 📁 НАВИГАЦИЯ       │                                                                      │ • [RUS] Перевод Шедевр│
│ 📚 Каталог         │                                                                      │ • [USA] Rev A [!]    │
│ ⬇ Загрузки (2)     │                                                                      │ • [EUR] Original     │
│ ⭐ Избранное (14)  │                                                                      │                      │
│ ⚙ Настройки        │                                                                      │ [▶ Запуск в эмуляторе│
└────────────────────┴──────────────────────────────────────────────────────────────────────┴──────────────────────┘
```

### 8.1 Левая боковая панель (Sidebar):
- **Секция навигации:** 4 основные вкладки:
  1. `Каталог` (основной экран)
  2. `Загрузки` (активные загрузки с прогресс-барами + история)
  3. `Избранное` (сохранённые игры)
  4. `Настройки` (папка сохранения, распаковка архивов, темы, управление платформами, пути к эмуляторам)
- **Секция платформ:**
  - Вертикальный список включенных платформ с иконками и бейджами количества игр.
  - Drag-and-drop или стрелочки `▲` / `▼` для изменения порядка платформ прямо в сайдбаре.
  - Кнопка-шестерёнка для быстрого перехода в управление видимостью платформ.

### 8.2 Верхняя панель (Toolbar):
- **Строка поиска:** Поле ввода с моментальной фильтрацией по текущей платформе и переключателем "Искать по всем платформам".
- **Горизонтальная полоса категорий:** Кнопки категорий (`🔥 Популярные`, `★ Лучшие`, `0-9`, `A`, `B`, `C`...). Поддержка скроллинга колесом мыши.
- **Переключатель отображения:** Кнопка переключения между Сеткой (Grid) и Таблицей (Table/List).
- **Сортировка:** Выпадающий список (По популярности, По рейтингу, По имени А-Я, По имени Я-А, По размеру).
- **Кнопка обновления:** ⟳ Заново запросить данные с сайта.

### 8.3 Центральная область (Контент):
- **Режим Сетки (Grid View):** Карточки с соотношением сторон ~3:4.
  - При наведении курсора (Hover): карточка слегка масштабируется (zoom-in), появляются быстрые кнопки: `⭐ В избранное`, `⬇ Скачать RUS/USA`, `🔍 Просмотр`.
  - Клик левой кнопкой мыши: открытие правой панели деталей или модального окна.
  - Клик правой кнопкой мыши: контекстное меню ("Скачать версию...", "Копировать ссылку", "Открыть на Emu-Land").
- **Режим Таблицы (Table View):** Полноценный плотный список для ПК с колонками:
  - Обложка (миниатюра 40x40), Название, Платформа, Жанр, Год, Рейтинг, Размер, Действия.
  - Сортировка по клику на заголовок колонки.

### 8.4 Правая панель сведений (Inspector Pane) или Модальное окно:
- Крупная обложка с кнопкой полноэкранного зума.
- Полный текст описания игры.
- Таблица всех найденных ревизий РОМа (GoodSet, RUS, USA, JAP, Hacks) со статусом и кнопкой мгновенного скачивания каждой конкретной ревизии.
- Если игра уже скачана: большая зеленая кнопка **"▶ Играть в эмуляторе"** и кнопка **"📁 Показать в папке"**.

---

## 9. Интеграция с эмуляторами и файловой системой ПК

Одно из ключевых преимуществ версии для ПК — бесшовная работа с десктопными эмуляторами и файловой системой без ограничений мобильного Scoped Storage.

### 9.1 Нативный выбор папки загрузок
С помощью крейта `rfd`:
```rust
use rfd::FileDialog;

pub fn pick_download_folder() -> Option<std::path::PathBuf> {
    FileDialog::new()
        .set_title("Выберите папку для сохранения игр (ROMs)")
        .pick_folder()
}
```

### 9.2 Открытие файла/папки в системном Проводнике
С помощью стандартной библиотеки и ОС:
```rust
pub fn reveal_in_file_explorer(path: &std::path::Path) -> std::io::Result<()> {
    #[cfg(target_os = "windows")]
    {
        std::process::Command::new("explorer")
            .arg("/select,")
            .arg(path)
            .spawn()?;
    }
    #[cfg(target_os = "macos")]
    {
        std::process::Command::new("open")
            .arg("-R")
            .arg(path)
            .spawn()?;
    }
    #[cfg(target_os = "linux")]
    {
        if let Some(parent) = path.parent() {
            std::process::Command::new("xdg-open")
                .arg(parent)
                .spawn()?;
        }
    }
    Ok(())
}
```

### 9.3 Запуск игры в локальном эмуляторе
В настройках приложения пользователь может указать пути к исполняемым файлам эмуляторов для каждой платформы:
- Dendy / Famicom -> `C:\Emulators\Mesen\Mesen.exe`
- Sega Genesis -> `C:\Emulators\Kega\Fusion.exe`
- SNES -> `C:\Emulators\Snes9x\snes9x-x64.exe`
- PS1 -> `C:\Emulators\DuckStation\duckstation-qt-x64-ReleaseLTCG.exe`
- Универсальный -> `C:\RetroArch-Win64\retroarch.exe -L cores\<core>.dll`

Запуск:
```rust
pub fn launch_emulator(emulator_exe: &str, rom_path: &std::path::Path) -> std::io::Result<()> {
    std::process::Command::new(emulator_exe)
        .arg(rom_path)
        .spawn()?;
    Ok(())
}
```

---

## 10. Пошаговый план реализации (Actionable Roadmap)

### Фаза 1: Сетевой модуль и парсер (CLI / Headless)
- [ ] Инициализировать Rust проект (`cargo new retroms-desktop --bin`).
- [ ] Добавить зависимости: `tokio`, `reqwest`, `scraper`, `regex`, `serde`, `serde_json`, `thiserror`.
- [ ] Реализовать `EmuLandClient`:
  - `fetch_consoles()` с фильтром по 33 поддерживаемым системам.
  - `fetch_categories(console_slug)` (парсинг `#pagelist_top`).
  - `fetch_games_page(console_slug, category, page)`.
  - `fetch_rom_versions(game)`.
  - `resolve_direct_download_url(url, referer)`.
- [ ] Написать юнит-тесты на парсер реальных страниц Emu-Land.

### Фаза 2: Локальная база данных и менеджер настроек
- [ ] Подключить `rusqlite` / `sqlx`.
- [ ] Создать миграции таблиц `consoles`, `games`, `downloads`.
- [ ] Реализовать CRUD-операции:
  - Сохранение порядка консолей и статуса включения.
  - Кэширование страниц каталога.
  - Управление статусом `is_favorite`.
- [ ] Реализовать загрузку/сохранение `AppSettings` в `config_dir/RetroROMs/config.json`.

### Фаза 3: Движок загрузок и распаковки архивов
- [ ] Реализовать `Downloader`:
  - Потоковая запись с вычислением байтов, скорости (КБ/с) и процента.
  - Отправка прогресса через `tokio::sync::mpsc`.
  - Поддержка отмены (cancellation token).
- [ ] Реализовать `ArchiveInspector`:
  - Чтение ZIP через `zip::ZipArchive`.
  - Фильтрация мусорных файлов.
  - Распаковка single-ROM архивов с удалением ZIP.
  - Метод выборочной распаковки нескольких файлов.

### Фаза 4: GUI Интерфейс (egui / eframe)
- [ ] Разработать каркас окна:
  - Sidebar (платформы + навигация).
  - Topbar (поиск, категории, сорт, вид).
  - Central View (сетка карточек с асинхронной загрузкой обложек).
  - Пагинация (кнопки переключения страниц).
- [ ] Разработать экран `Загрузки` с живыми прогресс-барами.
- [ ] Разработать экран `Избранное`.
- [ ] Разработать диалоги:
  - `GameDetailsModal` (описание, скриншоты, таблица версий с кнопками скачивания).
  - `ZipExtractionModal` (чекбоксы файлов GoodSet архива).
  - `SettingsModal` (путь к ROMs, темы, сортировка платформ, пути к эмуляторам).

### Фаза 5: Полировка, горячие клавиши и релиз
- [ ] Горячие клавиши (`Ctrl+F`, `F5`, `Esc`).
- [ ] Контекстное меню по правому клику мыши на карточках игр.
- [ ] Интеграция запуска эмуляторов.
- [ ] Сборка бинарников под Windows (`.exe` + Inno Setup installer), Linux (`AppImage`) и macOS (`.app` / `.dmg`).

---

## 11. Приложения: селекторы, заголовки и список консолей

### 11.1 Полный список 33 поддерживаемых платформ (Консоли со скачиваемыми РОМами)

```rust
pub fn get_supported_consoles() -> Vec<(&'static str, &'static str, &'static str, &'static str, &'static str)> {
    // (slug, name, short_name, category, folder_name)
    vec![
        // Домашние консоли
        ("dendy", "NES / Dendy / Famicom", "NES", "home", "NES"),
        ("genesis", "Sega Mega Drive / Genesis", "Genesis", "home", "MegaDrive"),
        ("snes", "Super Nintendo (SNES)", "SNES", "home", "SNES"),
        ("psx", "Sony PlayStation 1", "PS1", "home", "PS1"),
        ("n64", "Nintendo 64", "N64", "home", "N64"),
        ("32x", "Sega 32X", "32X", "home", "Sega32X"),
        ("segacd", "Sega CD / Mega CD", "Sega CD", "home", "SegaCD"),
        ("sms", "Sega Master System", "SMS", "home", "SMS"),
        ("sg-1000", "Sega SG-1000", "SG-1000", "home", "SG1000"),
        ("pce", "PC Engine / TurboGrafx-16", "PCE", "home", "PCEngine"),
        ("pcecd", "PC Engine CD", "PCE CD", "home", "PCEngineCD"),
        ("3do", "3DO Interactive Multiplayer", "3DO", "home", "3DO"),
        ("famicom_disk_system", "Famicom Disk System", "FDS", "home", "FDS"),
        ("neogeocd", "Neo Geo CD", "NeoGeo CD", "home", "NeoGeoCD"),
        ("jaguar", "Atari Jaguar", "Jaguar", "home", "Jaguar"),

        // Портативные системы
        ("gba", "Game Boy Advance", "GBA", "portable", "GBA"),
        ("gb", "Game Boy", "GB", "portable", "GameBoy"),
        ("gbc", "Game Boy Color", "GBC", "portable", "GBC"),
        ("gg", "Sega Game Gear", "Game Gear", "portable", "GameGear"),
        ("lynx", "Atari Lynx", "Lynx", "portable", "Lynx"),
        ("ngp", "Neo Geo Pocket", "NGP", "portable", "NGP"),
        ("ws", "Bandai WonderSwan", "WonderSwan", "portable", "WonderSwan"),
        ("vboy", "Nintendo Virtual Boy", "Virtual Boy", "portable", "VirtualBoy"),
        ("pmini", "Pokémon Mini", "PokeMini", "portable", "PokeMini"),
        ("sv", "Watara Supervision", "Supervision", "portable", "Supervision"),

        // Классические системы
        ("2600", "Atari 2600", "Atari 2600", "classic", "Atari2600"),
        ("5200", "Atari 5200", "Atari 5200", "classic", "Atari5200"),
        ("7800", "Atari 7800", "Atari 7800", "classic", "Atari7800"),
        ("coleco", "ColecoVision", "Coleco", "classic", "ColecoVision"),
        ("vectrex", "Vectrex", "Vectrex", "classic", "Vectrex"),
        ("intellivision", "Intellivision", "Intellivision", "classic", "Intellivision"),
        ("arcadia", "Emerson Arcadia 2001", "Arcadia", "classic", "Arcadia"),
        ("chaf", "Fairchild Channel F", "Channel F", "classic", "ChannelF"),
    ]
}
```

### 11.2 Исключенные платформы (на страницах Emu-Land нет файлов игр)
`Nintendo DS`, `Nintendo 3DS`, `Nintendo Wii`, `Nintendo Wii U`, `Sony PSP`, `Sony PS2`, `Sony PS3`, `Sega Dreamcast`, `Sega Saturn`, `Xbox`, `Xbox 360`.  
*Их парсить и отображать в каталоге не нужно.*

### 11.3 Ключевые CSS-селекторы Emu-Land.net

| Элемент страницы | CSS Селектор / XPath |
| :--- | :--- |
| **Блок категорий и алфавита** | `#pagelist_top`, `.pagelist` |
| **Специальные фильтры (Топ, Лучшие)** | `#pagelist_top .add a`, `.pagelist .add a` |
| **Буквы алфавита** | `#pagelist_top .abc a`, `.pagelist .abc a` |
| **Карточки игр в списке** | `.glist-item`, `.game-item`, `.glist tr` |
| **Название игры** | `.title a`, `.name a` |
| **Обложка / Скриншот карточки** | `.ss-area img`, `.picture img`, `.preview img` |
| **Размер файла в карточке** | `.size`, `.filesize` |
| **Рейтинг игры** | `.rating`, `.stars` |
| **Кнопка вызова версий (mfile)** | `[onclick*='getmfl']`, `.btn-sdl`, `a[href*='act=getmfl']` |
| **Модальный блок версий (getmfl)** | `.collaps-item` |
| **Заголовок группы версий** | `.collaps-item .title` ("Основные", "Переводы") |
| **Элемент конкретной версии** | `.collaps-item .item` |
| **Ссылка на конкретный ROM (fid)** | `.item .file a`, `a[href*='act=getmfl'][href*='fid=']` |
| **Размер конкретного ROM** | `.item .size`, `.item small.size` |
| **Галерея скриншотов на странице игры** | `.ss-area a.ss`, `.picture img` |
| **Текст описания игры** | `.ftext p`, `.description p`, `#description` |

---

*Спецификация подготовлена на основе производственного кода Android-версии RetroROMs. Все эндпоинты, форматы запросов, алгоритмы распаковки и селекторы верифицированы.*

# Emu-Land ROM Downloader & Console Organizer

An Android application designed to browse, search, and download retro console games from Emu-Land.net, complete with detailed game cards, descriptions, screenshots, and automatic file organization into dedicated console folders in Downloads.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were clarified in Phase 1 and guide the architecture of this solution:

- **Confirmed Decision 1 (Storage Organization)**: ROM files are saved into dedicated subdirectories within the device's public `Downloads` directory (`Downloads/RetroROMs/<console_name>/...` e.g., `Downloads/RetroROMs/Sega Mega Drive/`, `Downloads/RetroROMs/Game Boy Advance/`). This makes ROMs immediately accessible to any installed emulator (RetroArch, PPSSPP, Pizza Boy, Nostalgia.NES, etc.).
- **Confirmed Decision 2 (Emulator Scope)**: Focused purely on high-speed browsing, metadata scraping, downloading, and library organization for now. Game launching is intentionally omitted for Phase 1 as requested, with a clean architecture prepared for future emulator launch intents.
- **Confirmed Decision 3 (Console Catalog Management)**: Scrapes all available consoles from Emu-Land (`https://www.emu-land.net/consoles`) with built-in presets and a "Manage Platforms" customization screen allowing users to toggle which consoles appear on their main catalog tabs or home screen.

---

## 1. Overview & Core Concept

- **What It Does**: Allows retro gaming enthusiasts to browse the entire Emu-Land console catalog (Dendy/NES, Sega Genesis/Mega Drive, SNES, GBA, PSX, N64, Dreamcast, etc.), search games, read rich game cards (descriptions, developer/publisher, release year, genre, multi-region releases), view full-res gameplay screenshots, and download ROM archives with real-time download progress and automatic folder sorting.
- **Target Audience / Persona**: Retro gamers, emulation fans, and preservationists who want an elegant native mobile interface for browsing and downloading titles without dealing with slow mobile browser popups or messy download folders.
- **Key Value**: One-tap downloads that are automatically sorted by platform into structured folders ready for any emulator, accompanied by rich offline game metadata caching.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Console Exploration & Main Hub**:
   - Top banner with featured retro platforms and stats (total games cached, active downloads).
   - Chip/tab selector for active consoles (Dendy, Mega Drive, SNES, GBA, PSX, etc.) with a "Customize Consoles" action.
   - Quick search bar with instant title filtering, genre tags, and sorting (Top Rated, Most Popular, Alphabetical).
2. **Game Catalog & Grid/List View**:
   - Visual cards showing game cover/thumbnail, title, region badges (EU, US, JP, RU), and rating.
   - Pagination / smooth infinite scrolling through games scraped directly from `https://www.emu-land.net/consoles/{platform}/roms`.
3. **Rich Game Card / Detail Sheet**:
   - Immersive hero section with swipeable screenshot gallery.
   - Metadata badges: Platform, Year, Publisher, Genre, Size, Rating.
   - Full Russian/English game synopsis and description.
   - Primary action: "Download ROM" button showing file size and target folder indicator (`Downloads/RetroROMs/{Platform}/`).
4. **Download Manager & File Sorting**:
   - Active downloads tab with progress bar, download speed, and pause/cancel controls.
   - Completed tab displaying downloaded titles with file path indicators, file size, and quick actions to open folder or share.
5. **Console Visibility Manager**:
   - Reorder and toggle switch for all 25+ consoles supported by Emu-Land to keep the home screen focused on the systems the user actually plays.

### Visual Identity & Theme

- **Aesthetic Direction**: *Neo-Arcade Noir* — a premium dark theme combining nostalgic arcade vibes (subtle phosphor/neon accents, crisp retro badges) with modern Material 3 fluid surfaces, cards, and micro-interactions.
- **Color Palette**:
  - `Background`: Deep Midnight Slate (`#0B0E14`)
  - `Surface`: Layered Carbon Surface (`#141923`)
  - `Surface Variant`: Elevation Card (`#1E2535`)
  - `Primary / Accent`: Neon Cyan / Cyan Glow (`#00E5FF`)
  - `Secondary Accent`: Warm Amber / Golden Cartridge (`#FFB300`)
  - `Tertiary / Retro Pink`: Synthwave Magenta (`#FF3366`)
  - `Text High-Contrast`: Pure Crisp White (`#F0F4FC`) & Muted Slate (`#94A3B8`)
- **Typography**:
  - Display & Headers: Bold, confident geometric sans-serif with subtle letter-spacing for retro arcade authority.
  - Body & Metadata: Clean, legible sans-serif with clear tabular numbers for file sizes, years, and download speeds.
- **Component Styling & Layout**:
  - Material 3 cards with soft border strokes (`#2A344A`), subtle rounded corners (16.dp), and glowing focus states.
  - Platform pill badges with platform-specific iconic accent colors (e.g. Nintendo Red, Sega Blue, PlayStation Teal, GameBoy Green).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Scraper Architecture (Jsoup + OkHttp Resilient Parser)**:
  - *Chosen Approach*: Lightweight HTML parsing using Jsoup with custom User-Agent headers, session cookie handling, and regex fallback for Emu-Land's download redirect endpoints.
  - *Why*: Emu-Land uses standard server-rendered HTML for games and consoles. A clean OkHttp + Jsoup parser is fast, parses descriptions and image URLs reliably, and avoids heavy WebViews.
  - *Fallback*: Pre-seeded catalog metadata for primary consoles and top games so the app is instantly usable offline or during network latency.
- **Decision 2: Local Persistence (Room Database)**:
  - *Chosen Approach*: Room DB with tables for `ConsoleEntity`, `GameEntity`, and `DownloadEntity`.
  - *Why*: Scraped games, full descriptions, and screenshot URLs are saved locally so subsequent visits are instant and offline-friendly. Users can search through their cached library without hitting the network repeatedly.
- **Decision 3: Android Download & Storage Strategy**:
  - *Chosen Approach*: Android DownloadManager / App-directed Downloads storage with scoped `MediaStore.Downloads` or `Environment.DIRECTORY_DOWNLOADS/RetroROMs/<ConsoleName>`.
  - *Why*: Does not require legacy intrusive storage permissions on Android 10+ (API 29–36). Works natively with system notifications and allows immediate file discovery by external emulator apps.

---

## 4. Technical Architecture & Data Strategy

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI Layer                        │
├──────────────────┬──────────────────┬─────────────────┬────────────────┤
│  HomeScreen      │  GameDetailSheet │ DownloadManager │ ConsoleManager │
│  - PlatformTabs  │  - Screenshots   │ - Active Tasks  │ - Toggle List  │
│  - Search/Filter │  - Info/Badges   │ - Completed     │ - Reorder      │
│  - GameGrid      │  - Download CTA  │ - Folder Path   │ - Presets      │
└─────────┬────────┴─────────┬────────┴────────┬────────┴────────┬───────┘
          │                  │                 │                 │
┌─────────▼──────────────────▼─────────────────▼─────────────────▼───────┐
│                    EmuLandViewModel (UI State)                         │
└────────────────────────────┬───────────────────────────────────────────┘
                             │
┌────────────────────────────▼───────────────────────────────────────────┐
│                       EmuLandRepository                                │
├────────────────────────────┬───────────────────────────────────────────┤
│    Network / Scraping      │          Local Persistence (Room)         │
│  - EmuLandScraperService   │  - AppDatabase                            │
│  - OkHttpClient + Jsoup    │  - ConsoleDao, GameDao, DownloadDao       │
│  - DownloadWorker/Manager  │  - Offline Cache & Favorites              │
└────────────────────────────┴───────────────────────────────────────────┘
```

### Data Model & State

1. **`ConsoleEntity`**:
   - `id: String` (e.g. `"dendy"`, `"genesis"`, `"snes"`, `"gba"`, `"psx"`)
   - `name: String` (e.g. `"Sega Mega Drive / Genesis"`)
   - `category: String` (e.g. `"16-bit"`, `"Handheld"`, `"32-bit"`)
   - `isEnabled: Boolean` (user visibility toggle)
   - `sortOrder: Int`
2. **`GameEntity`**:
   - `id: String` (e.g. `"12345"`)
   - `consoleId: String`
   - `title: String`
   - `originalTitle: String?`
   - `genre: String`
   - `year: String`
   - `publisher: String`
   - `developer: String`
   - `rating: Float`
   - `fileSize: String`
   - `screenshotUrls: List<String>`
   - `description: String`
   - `downloadPageUrl: String`
   - `directDownloadUrl: String?`
   - `isFavorite: Boolean`
3. **`DownloadEntity`**:
   - `id: Long` (DownloadManager ID or unique local ID)
   - `gameId: String`
   - `gameTitle: String`
   - `consoleName: String`
   - `targetDirectory: String` (e.g. `Downloads/RetroROMs/Sega Mega Drive/`)
   - `fileName: String`
   - `totalBytes: Long`
   - `downloadedBytes: Long`
   - `status: DownloadStatus` (PENDING, DOWNLOADING, COMPLETED, FAILED)
   - `timestamp: Long`

### Interactive Component & State Mapping

- **Console Selector**: Tapping a platform updates active console filter, triggers lazy fetching from Emu-Land or Room cache, and resets scroll state.
- **Search & Filter**: Debounced (300ms) query matching against local cache and dynamic scraping endpoint.
- **Game Card Click**: Opens an animated Material 3 modal bottom sheet / detail screen showing full metadata, carousel of screenshots, and download button.
- **Download Action**: Initiates download via Android `DownloadManager` or OkHttp streaming to `Downloads/RetroROMs/<Console>/<game_name>.zip`, tracking byte progress and recording into `DownloadDao`.
- **Console Manager**: Interactive switch list with "Select All", "Deselect All", and quick presets (e.g., "16-bit Classics", "Handhelds Only", "All Consoles").

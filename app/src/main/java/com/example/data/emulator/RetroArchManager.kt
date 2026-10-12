package com.example.data.emulator

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.model.GameCard
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CoreOption(
    val id: String,
    val fileName: String,
    val displayName: String,
    val description: String = ""
)

enum class CoreSelectionSource {
    HISTORY,        // Automatically detected from content_history.lpl (confirmed working & installed)
    USER_SELECTED,  // User explicitly picked this core in settings or launch dialog
    DEFAULT         // Recommended default core
}

data class ResolvedCoreResult(
    val core: CoreOption,
    val source: CoreSelectionSource,
    val availableCores: List<CoreOption>
)

object RetroArchManager {

    private const val PREFS_NAME = "retroarch_prefs"
    private const val KEY_PREF_PREFIX = "core_pref_"
    private const val KEY_SHOW_LAUNCH_TIP = "show_launch_tip"

    val KNOWN_PACKAGES = listOf(
        "com.retroarch.aarch64",
        "com.retroarch",
        "com.retroarch.ra32"
    )

    // Comprehensive console slug to Libretro cores mapping (Updated for 2026)
    val CONSOLE_CORES: Map<String, List<CoreOption>> = mapOf(
        "dendy" to listOf(
            CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm (Рекомендуемое)", "Высокая совместимость, поддержка дампов Dendy и Famicom"),
            CoreOption("nestopia", "nestopia_libretro_android.so", "Nestopia UE", "Высокая точность эмуляции NES"),
            CoreOption("mesen", "mesen_libretro_android.so", "Mesen", "Современный потактовый эмулятор"),
            CoreOption("quicknes", "quicknes_libretro_android.so", "QuickNES", "Максимальная скорость, легкое ядро")
        ),
        "famicom_disk_system" to listOf(
            CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm (Рекомендуемое)", "Поддержка FDS образов"),
            CoreOption("nestopia", "nestopia_libretro_android.so", "Nestopia UE", "Точная эмуляция Famicom Disk System"),
            CoreOption("mesen", "mesen_libretro_android.so", "Mesen", "Высокая точность")
        ),
        "genesis" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Золотой стандарт эмуляции Sega Mega Drive"),
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive", "Высокая производительность на слабых устройствах"),
            CoreOption("genesis_plus_gx_wide", "genesis_plus_gx_wide_libretro_android.so", "Genesis Plus GX Wide", "Поддержка широкоформатного экрана 16:9")
        ),
        "snes" to listOf(
            CoreOption("snes9x", "snes9x_libretro_android.so", "Snes9x (Рекомендуемое)", "Лучший баланс скорости и точности"),
            CoreOption("snes9x2010", "snes9x2010_libretro_android.so", "Snes9x 2010", "Облегченная версия для слабых процессоров"),
            CoreOption("snes9x2005", "snes9x2005_libretro_android.so", "Snes9x 2005", "Максимальная скорость на старых чипах"),
            CoreOption("bsnes", "bsnes_libretro_android.so", "bsnes", "Максимальная потактовая точность"),
            CoreOption("bsnes_hd_beta", "bsnes_hd_beta_libretro_android.so", "bsnes-hd beta", "Широкоформатный рендеринг Mode 7")
        ),
        "gb" to listOf(
            CoreOption("gambatte", "gambatte_libretro_android.so", "Gambatte (Рекомендуемое)", "Лучшая цветовая палитра и точность Game Boy"),
            CoreOption("sameboy", "sameboy_libretro_android.so", "SameBoy", "Высокая точность и встроенные палитры"),
            CoreOption("gearboy", "gearboy_libretro_android.so", "Gearboy", "Быстрое и стабильное ядро"),
            CoreOption("mgba", "mgba_libretro_android.so", "mGBA", "Универсальный эмулятор"),
            CoreOption("tgbdual", "tgbdual_libretro_android.so", "TGB Dual", "Поддержка мультиплеера на разделенном экране")
        ),
        "gbc" to listOf(
            CoreOption("gambatte", "gambatte_libretro_android.so", "Gambatte (Рекомендуемое)", "Идеальная совместимость с GBC"),
            CoreOption("sameboy", "sameboy_libretro_android.so", "SameBoy", "Высокая точность GBC"),
            CoreOption("gearboy", "gearboy_libretro_android.so", "Gearboy", "Стабильное легкое ядро"),
            CoreOption("mgba", "mgba_libretro_android.so", "mGBA", "Универсальный эмулятор")
        ),
        "gba" to listOf(
            CoreOption("mgba", "mgba_libretro_android.so", "mGBA (Рекомендуемое)", "Золотой стандарт для Game Boy Advance"),
            CoreOption("vba_next", "vba_next_libretro_android.so", "VBA Next", "Оптимизированная версия VBA для мобильных чипов"),
            CoreOption("gpsp", "gpsp_libretro_android.so", "gpSP", "Максимальная скорость с динамической рекомпиляцией"),
            CoreOption("vbam", "vbam_libretro_android.so", "VBA-M", "Классический VisualBoyAdvance-M")
        ),
        "n64" to listOf(
            CoreOption("mupen64plus_next", "mupen64plus_next_libretro_android.so", "Mupen64Plus-Next (Рекомендуемое)", "Отличная 3D графика GLideN64 и совместимость"),
            CoreOption("mupen64plus_next_gles3", "mupen64plus_next_gles3_libretro_android.so", "Mupen64Plus-Next GLES3", "Оптимизация под OpenGL ES 3.0"),
            CoreOption("parallel_n64", "parallel_n64_libretro_android.so", "ParaLLEl N64", "Точная низкоуровневая эмуляция")
        ),
        "psx" to listOf(
            CoreOption("pcsx_rearmed", "pcsx_rearmed_libretro_android.so", "PCSX ReARMed (Рекомендуемое)", "Оптимизирован для ARM/Android с NEON"),
            CoreOption("duckstation", "duckstation_libretro_android.so", "DuckStation", "Высокое разрешение, апскейлинг и PGXP"),
            CoreOption("swanstation", "swanstation_libretro_android.so", "SwanStation", "Актуальный порт DuckStation для Libretro"),
            CoreOption("beetle_psx_hw", "beetle_psx_hw_libretro_android.so", "Beetle PSX HW", "Аппаратный рендеринг высокого качества"),
            CoreOption("beetle_psx", "beetle_psx_libretro_android.so", "Beetle PSX", "Точный программный рендеринг")
        ),
        "segacd" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Полная поддержка Mega CD / Sega CD"),
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive", "Поддержка CD-образов (ISO, CUE, CHD)")
        ),
        "32x" to listOf(
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive (Рекомендуемое)", "Единственное стабильное ядро для Sega 32X")
        ),
        "sms" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Master System и Game Gear"),
            CoreOption("gearsystem", "gearsystem_libretro_android.so", "Gearsystem", "Специализированное ядро"),
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive", "Высокая скорость")
        ),
        "sg-1000" to listOf(
            CoreOption("gearsystem", "gearsystem_libretro_android.so", "Gearsystem (Рекомендуемое)", "SG-1000"),
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX", "Sega SG-1000")
        ),
        "gg" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Game Gear"),
            CoreOption("gearsystem", "gearsystem_libretro_android.so", "Gearsystem", "Точная эмуляция экрана GG"),
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive", "Альтернативное быстрое ядро")
        ),
        "3do" to listOf(
            CoreOption("opera", "opera_libretro_android.so", "Opera / 4DO (Рекомендуемое)", "Panasonic 3DO")
        ),
        "pce" to listOf(
            CoreOption("mednafen_pce_fast", "mednafen_pce_fast_libretro_android.so", "Beetle PCE Fast (Рекомендуемое)", "PC Engine / TurboGrafx-16"),
            CoreOption("mednafen_pce", "mednafen_pce_libretro_android.so", "Beetle PCE", "Максимальная точность"),
            CoreOption("mednafen_supergrafx", "mednafen_supergrafx_libretro_android.so", "Beetle SuperGrafx", "PC Engine SuperGrafx")
        ),
        "pcecd" to listOf(
            CoreOption("mednafen_pce_fast", "mednafen_pce_fast_libretro_android.so", "Beetle PCE Fast (Рекомендуемое)", "PC Engine CD"),
            CoreOption("mednafen_pce", "mednafen_pce_libretro_android.so", "Beetle PCE", "Точная эмуляция CD")
        ),
        "neogeocd" to listOf(
            CoreOption("neocd", "neocd_libretro_android.so", "NeoCD (Рекомендуемое)", "Neo Geo CD"),
            CoreOption("fbneo", "fbneo_libretro_android.so", "FinalBurn Neo", "Универсальное аркадное ядро"),
            CoreOption("fbalpha2012_neogeo", "fbalpha2012_neogeo_libretro_android.so", "FB Alpha 2012 Neo Geo", "Облегченное ядро")
        ),
        "2600" to listOf(
            CoreOption("stella", "stella_libretro_android.so", "Stella (Рекомендуемое)", "Современный эмулятор Atari 2600"),
            CoreOption("stella2014", "stella2014_libretro_android.so", "Stella 2014", "Легковесное ядро")
        ),
        "5200" to listOf(
            CoreOption("a5200", "a5200_libretro_android.so", "a5200 (Рекомендуемое)", "Atari 5200"),
            CoreOption("atari800", "atari800_libretro_android.so", "Atari800", "Atari 8-bit")
        ),
        "7800" to listOf(
            CoreOption("prosystem", "prosystem_libretro_android.so", "ProSystem (Рекомендуемое)", "Atari 7800")
        ),
        "jaguar" to listOf(
            CoreOption("virtualjaguar", "virtualjaguar_libretro_android.so", "Virtual Jaguar (Рекомендуемое)", "Atari Jaguar")
        ),
        "lynx" to listOf(
            CoreOption("handy", "handy_libretro_android.so", "Handy (Рекомендуемое)", "Atari Lynx"),
            CoreOption("beetle_lynx", "beetle_lynx_libretro_android.so", "Beetle Lynx", "Beetle Lynx")
        ),
        "coleco" to listOf(
            CoreOption("gearcoleco", "gearcoleco_libretro_android.so", "Gearcoleco (Рекомендуемое)", "ColecoVision"),
            CoreOption("bluemsx", "bluemsx_libretro_android.so", "blueMSX", "MSX & Coleco")
        ),
        "vectrex" to listOf(
            CoreOption("vecx", "vecx_libretro_android.so", "vecx (Рекомендуемое)", "Vectrex")
        ),
        "intellivision" to listOf(
            CoreOption("freeintv", "freeintv_libretro_android.so", "FreeIntv (Рекомендуемое)", "Intellivision")
        ),
        "ws" to listOf(
            CoreOption("mednafen_wswan", "mednafen_wswan_libretro_android.so", "Beetle WonderSwan (Рекомендуемое)", "WonderSwan / Color")
        ),
        "ngp" to listOf(
            CoreOption("mednafen_ngp", "mednafen_ngp_libretro_android.so", "Beetle NeoPop (Рекомендуемое)", "Neo Geo Pocket / Color"),
            CoreOption("race", "race_libretro_android.so", "RACE", "Быстрое ядро")
        ),
        "vboy" to listOf(
            CoreOption("mednafen_vb", "mednafen_vb_libretro_android.so", "Beetle VB (Рекомендуемое)", "Virtual Boy")
        ),
        "pmini" to listOf(
            CoreOption("pokemini", "pokemini_libretro_android.so", "PokeMini (Рекомендуемое)", "Pokemon Mini")
        ),
        "arcadia" to listOf(
            CoreOption("mame2003_plus", "mame2003_plus_libretro_android.so", "MAME 2003-Plus (Рекомендуемое)", "Arcadia")
        ),
        "chaf" to listOf(
            CoreOption("freechaf", "freechaf_libretro_android.so", "FreeChaF (Рекомендуемое)", "Fairchild Channel F")
        ),
        "sv" to listOf(
            CoreOption("potator", "potator_libretro_android.so", "Potator (Рекомендуемое)", "Watara Supervision")
        ),
        "arcade" to listOf(
            CoreOption("fbneo", "fbneo_libretro_android.so", "FinalBurn Neo (Рекомендуемое)", "Актуальное аркадное ядро"),
            CoreOption("mame2003_plus", "mame2003_plus_libretro_android.so", "MAME 2003-Plus", "Классический MAME 0.78+"),
            CoreOption("mame2000", "mame2000_libretro_android.so", "MAME 2000", "Облегченный аркадный эмулятор")
        )
    )

    const val KEY_CUSTOM_CONFIG_PATH = "retroarch_custom_config_path"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isShowLaunchTipEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_LAUNCH_TIP, true)
    }

    fun setShowLaunchTipEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_LAUNCH_TIP, enabled).apply()
    }

    fun getCustomConfigPath(context: Context): String? {
        return getPrefs(context).getString(KEY_CUSTOM_CONFIG_PATH, null)?.trim()?.ifBlank { null }
    }

    fun setCustomConfigPath(context: Context, path: String?) {
        val editor = getPrefs(context).edit()
        if (path.isNullOrBlank()) {
            editor.remove(KEY_CUSTOM_CONFIG_PATH)
        } else {
            editor.putString(KEY_CUSTOM_CONFIG_PATH, path.trim())
        }
        editor.apply()
    }

    fun getDefaultPackageConfigPath(targetPackage: String): String {
        return "/storage/emulated/0/Android/data/$targetPackage/files/retroarch.cfg"
    }

    /**
     * Looks for existing RetroArch config files saved on shared storage
     * (e.g. when user uses "Save Current Configuration" or "Save New Configuration" in RetroArch).
     */
    fun findExistingSharedConfigs(): List<File> {
        val candidates = listOf(
            File("/storage/emulated/0/RetroArch/retroarch.cfg"),
            File("/storage/emulated/0/RetroArch/config/retroarch.cfg"),
            File(Environment.getExternalStorageDirectory(), "RetroArch/retroarch.cfg"),
            File(Environment.getExternalStorageDirectory(), "RetroArch/config/retroarch.cfg"),
            File(Environment.getExternalStorageDirectory(), "Download/RetroArch/retroarch.cfg"),
            File(Environment.getExternalStorageDirectory(), "Download/retroarch.cfg")
        )
        return candidates.filter { file ->
            try {
                file.exists() && file.isFile && file.length() > 0
            } catch (_: Exception) {
                false
            }
        }.distinctBy { it.absolutePath }
    }

    /**
     * Resolves the actual config file path passed to RetroArch via CONFIGFILE extra.
     * Passing CONFIGFILE is required so RetroArch sets its config directory,
     * loads assets, enables virtual buttons overlay, and allows saving configuration.
     */
    fun getEffectiveConfigPath(context: Context, targetPackage: String): String {
        // 1. Explicit user custom path in settings
        val custom = getCustomConfigPath(context)
        if (!custom.isNullOrBlank()) {
            return custom
        }

        // 2. Detected existing configuration in shared storage (e.g. /sdcard/RetroArch/retroarch.cfg)
        val existing = findExistingSharedConfigs()
        if (existing.isNotEmpty()) {
            return existing.first().absolutePath
        }

        // 3. Default standard package config path
        return getDefaultPackageConfigPath(targetPackage)
    }

    /**
     * Looks for RetroArch history playlist file (content_history.lpl) across all standard paths.
     */
    fun findHistoryFiles(context: Context, targetPackage: String? = null): List<File> {
        val candidates = mutableListOf<File>()
        val pkg = targetPackage ?: detectInstalledPackage(context)
        if (!pkg.isNullOrBlank()) {
            candidates.add(File("/storage/emulated/0/Android/data/$pkg/files/playlists/content_history.lpl"))
            candidates.add(File("/storage/emulated/0/Android/data/$pkg/files/content_history.lpl"))
        }
        candidates.add(File("/storage/emulated/0/RetroArch/playlists/content_history.lpl"))
        candidates.add(File("/storage/emulated/0/RetroArch/content_history.lpl"))
        candidates.add(File(Environment.getExternalStorageDirectory(), "RetroArch/playlists/content_history.lpl"))
        candidates.add(File(Environment.getExternalStorageDirectory(), "RetroArch/content_history.lpl"))
        candidates.add(File(Environment.getExternalStorageDirectory(), "Download/RetroArch/playlists/content_history.lpl"))

        // Also check if any known shared config has a playlists/ folder next to it
        for (cfg in findExistingSharedConfigs()) {
            val plDir = File(cfg.parentFile, "playlists/content_history.lpl")
            candidates.add(plDir)
        }

        return candidates.filter {
            try { it.exists() && it.isFile && it.length() > 0 } catch (_: Exception) { false }
        }.distinctBy { it.absolutePath }
    }

    /**
     * Extracts list of libretro core .so filenames from content_history.lpl files,
     * ordered by most recently used first.
     */
    fun parseHistoryCoreFiles(files: List<File>): List<String> {
        val result = mutableListOf<String>()
        for (file in files) {
            try {
                val text = file.readText().trim()
                if (text.startsWith("{")) {
                    val root = JSONObject(text)
                    val items = root.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val corePath = item.optString("core_path", "")
                            if (corePath.isNotBlank()) {
                                val name = File(corePath).name
                                if (name.endsWith(".so", ignoreCase = true) && !result.contains(name)) {
                                    result.add(name)
                                }
                            }
                        }
                    }
                } else {
                    // Legacy 6-line playlist format
                    val lines = file.readLines()
                    var i = 0
                    while (i + 3 < lines.size) {
                        val corePath = lines[i + 2].trim()
                        if (corePath.endsWith(".so", ignoreCase = true)) {
                            val name = File(corePath).name
                            if (!result.contains(name)) {
                                result.add(name)
                            }
                        }
                        i += 6
                    }
                }
            } catch (e: Exception) {
                Log.w("RetroArchManager", "Error parsing playlist file ${file.name}: ${e.message}")
            }
        }
        return result
    }

    /**
     * Resolves the best core for a given console:
     * 1. Explicit user selection (saved in preferences) -> USER_SELECTED
     * 2. Detected from content_history.lpl -> HISTORY
     * 3. Recommended default -> DEFAULT
     */
    fun resolveCore(context: Context, consoleSlug: String, romFile: File? = null): ResolvedCoreResult {
        val s = consoleSlug.lowercase()
        val available = getAvailableCores(s).ifEmpty { getFallbackCoreForFile(romFile) }

        // 1. Check user preference
        val savedId = getPrefs(context).getString("$KEY_PREF_PREFIX$s", null)
        if (!savedId.isNullOrBlank()) {
            val userCore = available.firstOrNull { it.id == savedId }
            if (userCore != null) {
                return ResolvedCoreResult(userCore, CoreSelectionSource.USER_SELECTED, available)
            }
        }

        // 2. Check history playlist content_history.lpl
        try {
            val historyFiles = findHistoryFiles(context, null)
            if (historyFiles.isNotEmpty()) {
                val historyCores = parseHistoryCoreFiles(historyFiles)
                for (hCoreFile in historyCores) {
                    val matched = available.firstOrNull { it.fileName.equals(hCoreFile, ignoreCase = true) }
                    if (matched != null) {
                        return ResolvedCoreResult(matched, CoreSelectionSource.HISTORY, available)
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback to default
        val defaultCore = available.firstOrNull() ?: CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm")
        return ResolvedCoreResult(defaultCore, CoreSelectionSource.DEFAULT, available)
    }

    fun getPreferredCore(context: Context, consoleSlug: String, romFile: File? = null): CoreOption {
        return resolveCore(context, consoleSlug, romFile).core
    }

    fun setPreferredCore(context: Context, consoleSlug: String, coreId: String) {
        getPrefs(context).edit().putString("$KEY_PREF_PREFIX${consoleSlug.lowercase()}", coreId).apply()
    }

    fun clearPreferredCore(context: Context, consoleSlug: String) {
        getPrefs(context).edit().remove("$KEY_PREF_PREFIX${consoleSlug.lowercase()}").apply()
    }

    fun getAvailableCores(consoleSlug: String): List<CoreOption> {
        val s = consoleSlug.lowercase()
        val direct = CONSOLE_CORES[s]
        if (direct != null) return direct

        // Aliases
        val aliasSlug = when (s) {
            "nes" -> "dendy"
            "megadrive", "md", "sega" -> "genesis"
            "sfc" -> "snes"
            "fds" -> "famicom_disk_system"
            "neogeo" -> "neogeocd"
            "a26" -> "2600"
            "a52" -> "5200"
            "a78" -> "7800"
            "vb" -> "vboy"
            "col" -> "coleco"
            "mame" -> "arcade"
            else -> null
        }
        if (aliasSlug != null) {
            val aliasCores = CONSOLE_CORES[aliasSlug]
            if (aliasCores != null) return aliasCores
        }

        return listOf(
            CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm (Универсальное)")
        )
    }

    private fun getFallbackCoreForFile(romFile: File?): List<CoreOption> {
        val ext = romFile?.extension?.lowercase() ?: ""
        return when (ext) {
            "nes", "fds" -> listOf(CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm"))
            "sfc", "smc" -> listOf(CoreOption("snes9x", "snes9x_libretro_android.so", "Snes9x"))
            "md", "gen", "smd", "bin" -> listOf(CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX"))
            "gb", "gbc" -> listOf(CoreOption("gambatte", "gambatte_libretro_android.so", "Gambatte"))
            "gba" -> listOf(CoreOption("mgba", "mgba_libretro_android.so", "mGBA"))
            "z64", "n64", "v64" -> listOf(CoreOption("mupen64plus_next", "mupen64plus_next_libretro_android.so", "Mupen64Plus-Next"))
            "iso", "cue", "chd" -> listOf(CoreOption("pcsx_rearmed", "pcsx_rearmed_libretro_android.so", "PCSX ReARMed"))
            "pce" -> listOf(CoreOption("mednafen_pce_fast", "mednafen_pce_fast_libretro_android.so", "Beetle PCE Fast"))
            "a26" -> listOf(CoreOption("stella", "stella_libretro_android.so", "Stella"))
            "a52" -> listOf(CoreOption("a5200", "a5200_libretro_android.so", "a5200"))
            "a78" -> listOf(CoreOption("prosystem", "prosystem_libretro_android.so", "ProSystem"))
            "col" -> listOf(CoreOption("gearcoleco", "gearcoleco_libretro_android.so", "Gearcoleco"))
            "ws", "wsc" -> listOf(CoreOption("mednafen_wswan", "mednafen_wswan_libretro_android.so", "Beetle WonderSwan"))
            "ngp", "ngc" -> listOf(CoreOption("mednafen_ngp", "mednafen_ngp_libretro_android.so", "Beetle NeoPop"))
            "vb" -> listOf(CoreOption("mednafen_vb", "mednafen_vb_libretro_android.so", "Beetle VB"))
            else -> listOf(CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm"))
        }
    }

    /**
     * Detects installed RetroArch package on device.
     * Prefers 64-bit com.retroarch.aarch64, then standard com.retroarch, then ra32.
     */
    fun detectInstalledPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in KNOWN_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: Exception) {
                try {
                    if (pm.getLaunchIntentForPackage(pkg) != null) return pkg
                } catch (_: Exception) {}
            }
        }

        return try {
            pm.getInstalledApplications(0)
                .firstOrNull { it.packageName.contains("retroarch", ignoreCase = true) }
                ?.packageName
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds the exact RetroArch launch Intent using RetroActivityFuture, LIBRETRO extra,
     * and CONFIGFILE extra (essential for on-screen controls overlay and asset loading).
     */
    fun createLaunchIntent(
        context: Context,
        targetPackage: String,
        romFile: File,
        core: CoreOption,
        contentUri: Uri
    ): Intent {
        val canonicalFile = try { romFile.canonicalFile } catch (_: Exception) { romFile }
        val romPath = canonicalFile.absolutePath
        val corePath = "/data/data/$targetPackage/cores/${core.fileName}"
        val configPath = getEffectiveConfigPath(context, targetPackage)

        return Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(targetPackage, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            addCategory(Intent.CATEGORY_LAUNCHER)
            
            // Essential RetroArch extras:
            putExtra("ROM", romPath)
            putExtra("PATH", romPath)
            putExtra("LIBRETRO", corePath)
            putExtra("CONFIGFILE", configPath)

            // Also attach contentUri and permissions
            data = contentUri
            clipData = ClipData.newRawUri("ROM", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
    }

    /**
     * Fallback Intent for older RetroArch versions or packages that might not expose RetroActivityFuture.
     */
    fun createFallbackLaunchIntent(
        context: Context,
        targetPackage: String,
        romFile: File,
        core: CoreOption,
        contentUri: Uri
    ): Intent? {
        val pm = context.packageManager
        val canonicalFile = try { romFile.canonicalFile } catch (_: Exception) { romFile }
        val romPath = canonicalFile.absolutePath
        val corePath = "/data/data/$targetPackage/cores/${core.fileName}"
        val configPath = getEffectiveConfigPath(context, targetPackage)

        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)?.apply {
            putExtra("ROM", romPath)
            putExtra("PATH", romPath)
            putExtra("LIBRETRO", corePath)
            putExtra("CONFIGFILE", configPath)
            data = contentUri
            clipData = ClipData.newRawUri("ROM", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return launchIntent
    }

    /**
     * Opens the RetroArch main menu directly so user can navigate to Online Updater -> Core Downloader.
     */
    fun openRetroArchMainMenu(context: Context): Boolean {
        val pkg = detectInstalledPackage(context) ?: return false
        val pm = context.packageManager
        val menuComponent = ComponentName(pkg, "com.retroarch.browser.mainmenu.MainMenuActivity")
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                component = menuComponent
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            val launchIntent = pm.getLaunchIntentForPackage(pkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        }
        return false
    }

    /**
     * System chooser "Open with..." for alternative emulators (NES.emu, Snes9x EX+, MyBoy, etc.)
     */
    fun createChooserIntent(
        context: Context,
        romFile: File,
        contentUri: Uri,
        title: String = "Открыть игру с помощью…"
    ): Intent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "*/*")
            clipData = ClipData.newRawUri("ROM", contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

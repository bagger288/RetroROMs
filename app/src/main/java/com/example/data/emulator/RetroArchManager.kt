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
import java.io.File

data class CoreOption(
    val id: String,
    val fileName: String,
    val displayName: String,
    val description: String = ""
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

    // Comprehensive console slug to Libretro cores mapping
    val CONSOLE_CORES: Map<String, List<CoreOption>> = mapOf(
        "dendy" to listOf(
            CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm (Рекомендуемое)", "Высокая совместимость с NES/Dendy"),
            CoreOption("nestopia", "nestopia_libretro_android.so", "Nestopia UE", "Высокая точность эмуляции"),
            CoreOption("mesen", "mesen_libretro_android.so", "Mesen", "Современный точный эмулятор")
        ),
        "famicom_disk_system" to listOf(
            CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm (Рекомендуемое)", "Поддержка FDS"),
            CoreOption("nestopia", "nestopia_libretro_android.so", "Nestopia UE", "Точная эмуляция FDS")
        ),
        "genesis" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Идеальная эмуляция Sega Mega Drive"),
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive", "Быстрый эмулятор, отлично для старых чипов")
        ),
        "snes" to listOf(
            CoreOption("snes9x", "snes9x_libretro_android.so", "Snes9x (Рекомендуемое)", "Лучший баланс скорости и точности"),
            CoreOption("snes9x2010", "snes9x2010_libretro_android.so", "Snes9x 2010", "Облегченная версия"),
            CoreOption("bsnes", "bsnes_libretro_android.so", "bsnes", "Максимальная точность")
        ),
        "gb" to listOf(
            CoreOption("gambatte", "gambatte_libretro_android.so", "Gambatte (Рекомендуемое)", "Лучшая цветовая палитра для Game Boy"),
            CoreOption("gearboy", "gearboy_libretro_android.so", "Gearboy", "Быстрый и стабильный"),
            CoreOption("mgba", "mgba_libretro_android.so", "mGBA", "Универсальный эмулятор")
        ),
        "gbc" to listOf(
            CoreOption("gambatte", "gambatte_libretro_android.so", "Gambatte (Рекомендуемое)", "Отличная совместимость с GBC"),
            CoreOption("gearboy", "gearboy_libretro_android.so", "Gearboy", "Стабильный"),
            CoreOption("mgba", "mgba_libretro_android.so", "mGBA", "Универсальный эмулятор")
        ),
        "gba" to listOf(
            CoreOption("mgba", "mgba_libretro_android.so", "mGBA (Рекомендуемое)", "Золотой стандарт для Game Boy Advance"),
            CoreOption("vba_next", "vba_next_libretro_android.so", "VBA Next", "Оптимизированная версия VBA"),
            CoreOption("gpsp", "gpsp_libretro_android.so", "gpSP", "Максимальная производительность")
        ),
        "n64" to listOf(
            CoreOption("mupen64plus_next", "mupen64plus_next_libretro_android.so", "Mupen64Plus-Next (Рекомендуемое)", "Отличная 3D графика и совместимость"),
            CoreOption("parallel_n64", "parallel_n64_libretro_android.so", "ParaLLEl N64", "Точная низкоуровневая эмуляция")
        ),
        "psx" to listOf(
            CoreOption("pcsx_rearmed", "pcsx_rearmed_libretro_android.so", "PCSX ReARMed (Рекомендуемое)", "Оптимизирован для ARM/Android"),
            CoreOption("duckstation", "duckstation_libretro_android.so", "DuckStation", "Высокое разрешение и апскейлинг"),
            CoreOption("beetle_psx_hw", "beetle_psx_hw_libretro_android.so", "Beetle PSX HW", "Точный рендеринг")
        ),
        "segacd" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Полная поддержка Mega CD"),
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive", "Поддержка CD-образов")
        ),
        "32x" to listOf(
            CoreOption("picodrive", "picodrive_libretro_android.so", "PicoDrive (Рекомендуемое)", "Единственное стабильное ядро для Sega 32X")
        ),
        "sms" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Master System и Game Gear"),
            CoreOption("gearsystem", "gearsystem_libretro_android.so", "Gearsystem", "Специализированное ядро")
        ),
        "sg-1000" to listOf(
            CoreOption("gearsystem", "gearsystem_libretro_android.so", "Gearsystem (Рекомендуемое)", "SG-1000"),
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX", "Sega SG-1000")
        ),
        "gg" to listOf(
            CoreOption("genesis_plus_gx", "genesis_plus_gx_libretro_android.so", "Genesis Plus GX (Рекомендуемое)", "Game Gear"),
            CoreOption("gearsystem", "gearsystem_libretro_android.so", "Gearsystem", "Gearsystem")
        ),
        "3do" to listOf(
            CoreOption("opera", "opera_libretro_android.so", "Opera / 4DO (Рекомендуемое)", "Panasonic 3DO")
        ),
        "pce" to listOf(
            CoreOption("mednafen_pce_fast", "mednafen_pce_fast_libretro_android.so", "Beetle PCE Fast (Рекомендуемое)", "PC Engine / TurboGrafx-16")
        ),
        "pcecd" to listOf(
            CoreOption("mednafen_pce_fast", "mednafen_pce_fast_libretro_android.so", "Beetle PCE Fast (Рекомендуемое)", "PC Engine CD")
        ),
        "neogeocd" to listOf(
            CoreOption("neocd", "neocd_libretro_android.so", "NeoCD (Рекомендуемое)", "Neo Geo CD"),
            CoreOption("fbneo", "fbneo_libretro_android.so", "FinalBurn Neo", "Универсальное аркадное ядро")
        ),
        "2600" to listOf(
            CoreOption("stella", "stella_libretro_android.so", "Stella (Рекомендуемое)", "Atari 2600"),
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
            CoreOption("mednafen_ngp", "mednafen_ngp_libretro_android.so", "Beetle NeoPop (Рекомендуемое)", "Neo Geo Pocket / Color")
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

    fun getPreferredCore(context: Context, consoleSlug: String, romFile: File? = null): CoreOption {
        val s = consoleSlug.lowercase()
        val list = CONSOLE_CORES[s] ?: getFallbackCoreForFile(romFile)
        val savedId = getPrefs(context).getString("$KEY_PREF_PREFIX$s", null)
        if (!savedId.isNullOrBlank()) {
            list.firstOrNull { it.id == savedId }?.let { return it }
        }
        return list.firstOrNull() ?: CoreOption("fceumm", "fceumm_libretro_android.so", "FCEUmm")
    }

    fun setPreferredCore(context: Context, consoleSlug: String, coreId: String) {
        getPrefs(context).edit().putString("$KEY_PREF_PREFIX${consoleSlug.lowercase()}", coreId).apply()
    }

    fun getAvailableCores(consoleSlug: String): List<CoreOption> {
        return CONSOLE_CORES[consoleSlug.lowercase()] ?: listOf(
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

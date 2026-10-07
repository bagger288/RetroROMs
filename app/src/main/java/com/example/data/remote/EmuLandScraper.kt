package com.example.data.remote

import android.util.Log
import com.example.model.ConsoleInfo
import com.example.model.GameCard
import com.example.model.GamesPageResult
import com.example.model.RomFileVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class EmuLandScraper {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Dedicated non-redirecting client to intercept HTTP 302 Location header
    private val noRedirectClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val baseUrl = "https://www.emu-land.net"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    /**
     * Normalizes image and media URLs so Coil and Android can load them without crashing.
     */
    fun normalizeImageUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var url = rawUrl.trim()
            .replace("&amp;", "&")
            .replace("\\/", "/")

        if (url.startsWith("//")) {
            url = "https:$url"
        } else if (url.startsWith("/")) {
            url = "$baseUrl$url"
        }

        // Encode unescaped spaces
        return url.replace(" ", "%20")
    }

    /**
     * Scrapes or builds the complete console list from emu-land.net/consoles and /portable
     */
    suspend fun fetchConsoles(): List<ConsoleInfo> = withContext(Dispatchers.IO) {
        val consoles = mutableListOf<ConsoleInfo>()
        val seenSlugs = mutableSetOf<String>()

        try {
            // 1. Fetch home consoles
            scrapeConsoleSection("/consoles", "consoles", consoles, seenSlugs)
            // 2. Fetch portable consoles
            scrapeConsoleSection("/portable", "portable", consoles, seenSlugs)
        } catch (e: Exception) {
            Log.w("EmuLandScraper", "Network error fetching consoles list: ${e.message}")
        }

        if (consoles.isEmpty()) {
            consoles.addAll(getDefaultConsoles())
        } else {
            // Ensure essential consoles are present
            for (def in getDefaultConsoles()) {
                if (def.slug !in seenSlugs) {
                    consoles.add(def.copy(order = consoles.size))
                    seenSlugs.add(def.slug)
                }
            }
        }

        consoles.sortedBy { it.order }
    }

    private fun scrapeConsoleSection(
        path: String,
        section: String,
        outList: MutableList<ConsoleInfo>,
        seenSlugs: MutableSet<String>
    ) {
        try {
            val request = Request.Builder()
                .url("$baseUrl$path")
                .header("User-Agent", userAgent)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html, baseUrl)
                val links = doc.select("a[href*=$path/]")

                for (link in links) {
                    val href = link.attr("href")
                    val match = Regex("$path/([a-zA-Z0-9_-]+)/?.*").find(href)
                    if (match != null) {
                        val slug = match.groupValues[1].lowercase()
                        if (slug !in seenSlugs && slug != "consoles" && slug != "portable" && slug != "roms") {
                            val title = link.text().trim()
                            if (title.isNotEmpty()) {
                                seenSlugs.add(slug)
                                outList.add(mapSlugToConsoleInfo(slug, title, section, outList.size))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("EmuLandScraper", "Error in scrapeConsoleSection $path: ${e.message}")
        }
    }

    /**
     * Scrapes games for a console from emu-land.net/{section}/{slug}/roms/{category}
     * Supports category: "top" (popular), "best" (best rated), or alphabetical letters ("0-9", "a", "b", etc.)
     */
    suspend fun fetchGamesPageForConsole(
        consoleSlug: String,
        consoleName: String,
        section: String = "consoles",
        category: String = "top",
        page: Int = 1
    ): GamesPageResult = withContext(Dispatchers.IO) {
        val games = mutableListOf<GameCard>()
        val catSlug = when {
            category.equals("top", ignoreCase = true) -> "top"
            category.equals("best", ignoreCase = true) -> "best"
            category.isNotBlank() && category != "all" -> category
            else -> "top"
        }
        val targetUrl = if (page > 1) {
            "$baseUrl/$section/$consoleSlug/roms/$catSlug/$page"
        } else {
            "$baseUrl/$section/$consoleSlug/roms/$catSlug"
        }

        var maxPage = page
        var hasNext = false

        try {
            val request = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html, baseUrl)

                // Parse pagination numbers from Emu-Land: <div class="num"><a ...>1</a>...
                val pageLinks = doc.select(".num a, div.num a, .pagelist .num a, .pagination a, a.pnum")
                for (link in pageLinks) {
                    val pNum = link.text().trim().toIntOrNull()
                    if (pNum != null && pNum > maxPage) {
                        maxPage = pNum
                    }
                }
                // Also check "Последняя" link href: /consoles/dendy/roms/c/5 or /top/20
                val lastPageLink = doc.selectFirst(".num a[title=Последняя], a[title=Последняя], .num a[title*=последн], a[title*=последн]")
                if (lastPageLink != null) {
                    val lastHref = lastPageLink.attr("href")
                    val pFromHref = Regex(".*/([0-9]+)").find(lastHref)?.groupValues?.get(1)?.toIntOrNull()
                    if (pFromHref != null && pFromHref > maxPage) {
                        maxPage = pFromHref
                    }
                }
                hasNext = doc.select(".num a:contains(Далее), a:contains(Далее), .num a:contains(Next), a:contains(Next)").isNotEmpty() || (maxPage > page)

                // Each game on emu-land is inside an .fcontainer
                val containers = doc.select(".fcontainer")

                for (container in containers) {
                    val headerLink = container.selectFirst(".rheader a")
                        ?: container.selectFirst("a[href*=/roms/]")
                    val title = headerLink?.text()?.trim() ?: container.select("h4").text().trim()
                    if (title.isBlank()) continue

                    val gamePageHref = headerLink?.attr("href") ?: ""
                    val gamePageSlug = gamePageHref.substringAfterLast("/roms/").substringBefore("?").trim('/')

                    // Extract alternative title
                    val altTitle = container.select(".atitles").text().trim().takeIf { it.isNotEmpty() }

                    // Extract cover and screenshots from .picture img and data-gallery JSON
                    val picImg = container.selectFirst(".picture img")
                    val rawCover = picImg?.attr("src")
                    val coverUrl = normalizeImageUrl(rawCover)

                    val screenshotUrls = mutableListOf<String>()
                    val galleryAttr = picImg?.attr("data-gallery")
                    if (!galleryAttr.isNullOrBlank()) {
                        try {
                            val galleryJson = JSONObject(galleryAttr)
                            val listArray = galleryJson.optJSONArray("list")
                            if (listArray != null) {
                                for (i in 0 until listArray.length()) {
                                    val itemObj = listArray.optJSONObject(i)
                                    val rawSrc = itemObj?.optString("src")
                                    normalizeImageUrl(rawSrc)?.let { normUrl ->
                                        if (normUrl !in screenshotUrls) {
                                            screenshotUrls.add(normUrl)
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("EmuLandScraper", "Gallery JSON parse failed: ${e.message}")
                        }
                    }
                    if (screenshotUrls.isEmpty() && coverUrl != null) {
                        screenshotUrls.add(coverUrl)
                    }

                    // Extract metadata from .finfo
                    var genre = "Action"
                    var developer = "Unknown"
                    var year = "N/A"
                    var publisher = "Unknown"

                    container.select(".finfo li").forEach { li ->
                        val text = li.text()
                        when {
                            text.contains("Жанр:", ignoreCase = true) -> genre = text.substringAfter("Жанр:").trim()
                            text.contains("Разработчик:", ignoreCase = true) -> developer = text.substringAfter("Разработчик:").trim()
                            text.contains("Издатель:", ignoreCase = true) -> publisher = text.substringAfter("Издатель:").trim()
                            text.contains("Год выпуска:", ignoreCase = true) -> {
                                val match = Regex("([12][0-9]{3})").find(text)
                                if (match != null) year = match.value
                            }
                        }
                    }

                    // Extract description from .ftext
                    val desc = container.select(".ftext p").text().trim().ifBlank {
                        container.select(".description p").text().trim()
                    }

                    // Extract mfileId from download button onclick: mgame('/consoles/dendy/roms?act=getmfl&id=24572', ...)
                    var mfileId: String? = null
                    val dlBtn = container.selectFirst(".btn-sdl, .btn-dl, [onclick*='getmfl']")
                    val onclick = dlBtn?.attr("onclick") ?: ""
                    val idMatch = Regex("id=([0-9]+)").find(onclick)
                    if (idMatch != null) {
                        mfileId = idMatch.groupValues[1]
                    } else {
                        // Fallback: look for id in text_1234 or pict_1234
                        val elemWithId = container.selectFirst("[id^=text_], [id^=pict_], [id^=mfile_]")
                        val idAttr = elemWithId?.id() ?: ""
                        val fallbackMatch = Regex("([0-9]+)").find(idAttr)
                        if (fallbackMatch != null) {
                            mfileId = fallbackMatch.groupValues[1]
                        }
                    }

                    // Extract regions from flags
                    val regions = mutableListOf<String>()
                    container.select("img[src*=/flags/]").forEach { flag ->
                        val src = flag.attr("src")
                        when {
                            src.contains("/ru.") -> regions.add("RU")
                            src.contains("/us.") -> regions.add("US")
                            src.contains("/eu.") -> regions.add("EU")
                            src.contains("/jp.") -> regions.add("JP")
                        }
                    }
                    if (regions.isEmpty()) regions.add("US")

                    val uniqueId = mfileId ?: "${consoleSlug}_${gamePageSlug.ifBlank { title.hashCode().toString() }}"
                    val dlUrl = "$baseUrl/$section/$consoleSlug/roms?act=getmfl&id=${mfileId ?: ""}"

                    games.add(
                        GameCard(
                            id = uniqueId,
                            consoleSlug = consoleSlug,
                            consoleName = consoleName,
                            section = section,
                            title = title,
                            originalTitle = altTitle,
                            genre = genre,
                            year = year,
                            publisher = if (publisher != "Unknown") publisher else developer,
                            developer = developer,
                            rating = 4.8f,
                            fileSize = "ROM",
                            coverUrl = coverUrl,
                            screenshotUrls = screenshotUrls,
                            description = desc,
                            downloadUrl = dlUrl,
                            mfileId = mfileId,
                            gamePageSlug = gamePageSlug,
                            regions = regions.distinct()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("EmuLandScraper", "Error fetching games for $consoleSlug ($category): ${e.message}", e)
        }

        if (games.isEmpty() && category.equals("top", ignoreCase = true) && page == 1) {
            val seeds = getSeedGamesForConsole(consoleSlug, consoleName, section)
            return@withContext GamesPageResult(
                games = seeds,
                currentPage = 1,
                totalPages = 1,
                hasNextPage = false
            )
        }

        GamesPageResult(
            games = games,
            currentPage = page,
            totalPages = maxPage.coerceAtLeast(page),
            hasNextPage = hasNext
        )
    }

    suspend fun fetchGamesForConsole(
        consoleSlug: String,
        consoleName: String,
        section: String = "consoles",
        category: String = "top",
        page: Int = 1
    ): List<GameCard> {
        return fetchGamesPageForConsole(consoleSlug, consoleName, section, category, page).games
    }

    /**
     * Extracts mfileId from HTML using high-precision patterns to avoid false matches like movie IDs.
     */
    fun extractMfileIdFromHtml(html: String): String? {
        val patterns = listOf(
            Regex("act=getmfl&(?:amp;)?id=([0-9]+)"),
            Regex("mgame\\(['\"][^'\"]*id=([0-9]+)"),
            Regex("id=[\"']mfile_([0-9]+)[\"']"),
            Regex("id=[\"']text_([0-9]+)[\"']"),
            Regex("id=[\"']pict_([0-9]+)[\"']")
        )
        for (pat in patterns) {
            val match = pat.find(html)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Classifies ROM version by region or distribution type based on filename and category name.
     */
    private fun classifyRegionOrType(fileName: String, categoryName: String): String {
        return when {
            fileName.contains("[T+Rus", ignoreCase = true) ||
            fileName.contains("Rus", ignoreCase = true) ||
            fileName.contains("(Ru)", ignoreCase = true) ||
            categoryName.contains("Перевед", ignoreCase = true) -> "RUS"

            fileName.contains("(USA)", ignoreCase = true) ||
            fileName.contains("(U)", ignoreCase = true) -> "USA"

            fileName.contains("(Europe)", ignoreCase = true) ||
            fileName.contains("(E)", ignoreCase = true) -> "EUR"

            fileName.contains("(Japan)", ignoreCase = true) ||
            fileName.contains("(J)", ignoreCase = true) -> "JAP"

            fileName.contains("(World)", ignoreCase = true) ||
            fileName.contains("(W)", ignoreCase = true) -> "WLD"

            fileName.contains("Beta", ignoreCase = true) ||
            fileName.contains("Proto", ignoreCase = true) -> "BETA"

            fileName.contains("Hack", ignoreCase = true) ||
            fileName.contains("(H)", ignoreCase = true) -> "HACK"

            categoryName.contains("Пират", ignoreCase = true) ||
            fileName.contains("Unl", ignoreCase = true) ||
            fileName.contains("Pirate", ignoreCase = true) -> "PIRATE"

            categoryName.contains("Good", ignoreCase = true) -> "GoodNES"

            else -> "ROM"
        }
    }

    /**
     * Fetches and parses all ROM file versions for a given mfile ID from Emu-Land's act=getmfl endpoint.
     */
    private fun fetchVersionsFromGetmfl(game: GameCard, mfileId: String): List<RomFileVersion> {
        val versions = mutableListOf<RomFileVersion>()
        val getmflUrl = "$baseUrl/${game.section}/${game.consoleSlug}/roms?act=getmfl&id=$mfileId"
        try {
            val req = Request.Builder()
                .url(getmflUrl)
                .header("User-Agent", userAgent)
                .header("Referer", "$baseUrl/${game.section}/${game.consoleSlug}/roms")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val doc = Jsoup.parse(html, baseUrl)

                val collapsItems = doc.select(".collaps-item")
                if (collapsItems.isNotEmpty()) {
                    for (block in collapsItems) {
                        val rawTitle = block.selectFirst(".title span")?.text()?.trim()
                            ?: block.selectFirst(".title")?.ownText()?.trim()
                            ?: block.selectFirst(".title")?.text()?.trim()
                            ?: "Основные"
                        val categoryName = rawTitle
                            .replace(Regex("\\s*\\(\\?\\)\\s*"), "")
                            .trim()
                            .let { raw ->
                                val parts = raw.split(" ").filter { it.isNotBlank() }
                                if (parts.size >= 2 && parts[0].equals(parts[1], ignoreCase = true)) {
                                    parts.drop(1).joinToString(" ")
                                } else {
                                    raw
                                }
                            }
                            .ifBlank { "Основные" }
                        val items = block.select(".item")
                        for (item in items) {
                            val link = item.selectFirst(".file a, a[href*='act=getmfl'], a[href*='fid=']") ?: continue
                            val fileName = link.text().trim()
                            if (fileName.isBlank()) continue

                            val rawHref = link.attr("href").replace("&amp;", "&")
                            val fid = Regex("fid=([0-9]+)").find(rawHref)?.groupValues?.get(1) ?: fileName.hashCode().toString()
                            val size = item.select(".size, small.size").text().trim().removePrefix("(").removeSuffix(")")

                            val cleanDlUrl = "$baseUrl/${game.section}/${game.consoleSlug}/roms?act=getmfl&id=$mfileId&fid=$fid"
                            val regionOrType = classifyRegionOrType(fileName, categoryName)

                            versions.add(
                                RomFileVersion(
                                    fid = fid,
                                    name = fileName,
                                    size = size.ifBlank { "ROM" },
                                    category = categoryName,
                                    downloadUrl = cleanDlUrl,
                                    regionOrType = regionOrType
                                )
                            )
                        }
                    }
                } else {
                    val links = doc.select("a[href*='act=getmfl'][href*='fid=']")
                    for (link in links) {
                        val fileName = link.text().trim()
                        if (fileName.isBlank()) continue
                        val rawHref = link.attr("href").replace("&amp;", "&")
                        val fid = Regex("fid=([0-9]+)").find(rawHref)?.groupValues?.get(1) ?: fileName.hashCode().toString()
                        val cleanDlUrl = "$baseUrl/${game.section}/${game.consoleSlug}/roms?act=getmfl&id=$mfileId&fid=$fid"
                        val regionOrType = classifyRegionOrType(fileName, "Основные")
                        versions.add(
                            RomFileVersion(
                                fid = fid,
                                name = fileName,
                                size = "ROM",
                                category = "Основные",
                                downloadUrl = cleanDlUrl,
                                regionOrType = regionOrType
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EmuLandScraper", "Error in fetchVersionsFromGetmfl for ${game.title} (mfileId=$mfileId): ${e.message}", e)
        }
        return versions
    }

    /**
     * Fetches all available ROM versions/files (Regions, Translations, Hacks, Prototypes) for a game.
     */
    suspend fun fetchRomVersions(game: GameCard): List<RomFileVersion> = withContext(Dispatchers.IO) {
        val versions = mutableListOf<RomFileVersion>()
        var mfileId = game.mfileId

        // 1. If mfileId is missing or non-numeric, resolve from downloadUrl or game page
        if (mfileId.isNullOrBlank() || !mfileId.all { it.isDigit() }) {
            val idFromDl = Regex("act=getmfl&(?:amp;)?id=([0-9]+)").find(game.downloadUrl)?.groupValues?.get(1)
            if (!idFromDl.isNullOrBlank()) {
                mfileId = idFromDl
            } else {
                val pageSlug = game.gamePageSlug?.takeIf { it.isNotBlank() }
                    ?: game.downloadUrl.substringAfterLast("/roms/").substringBefore("?").trim('/').takeIf { it.isNotBlank() }
                if (pageSlug != null) {
                    mfileId = fetchMfileIdFromGamePage(game.section, game.consoleSlug, pageSlug)
                }
            }
        }

        // 2. Try fetching versions using mfileId
        if (!mfileId.isNullOrBlank()) {
            val fetched = fetchVersionsFromGetmfl(game, mfileId)
            if (fetched.isNotEmpty()) {
                versions.addAll(fetched)
            }
        }

        // 3. If mfileId failed (e.g. was obsolete or 404), fetch fresh game page and re-extract
        if (versions.isEmpty()) {
            val pageSlug = game.gamePageSlug?.takeIf { it.isNotBlank() }
                ?: game.downloadUrl.substringAfterLast("/roms/").substringBefore("?").trim('/').takeIf { it.isNotBlank() }

            if (pageSlug != null) {
                val freshId = fetchMfileIdFromGamePage(game.section, game.consoleSlug, pageSlug)
                if (!freshId.isNullOrBlank() && freshId != mfileId) {
                    mfileId = freshId
                    val fetched = fetchVersionsFromGetmfl(game, freshId)
                    if (fetched.isNotEmpty()) {
                        versions.addAll(fetched)
                    }
                }
            }
        }

        // 4. Fallback only if network truly returned nothing
        if (versions.isEmpty()) {
            val fallbackDlUrl = if (!mfileId.isNullOrBlank()) {
                "$baseUrl/${game.section}/${game.consoleSlug}/roms?act=getmfl&id=$mfileId"
            } else {
                game.downloadUrl
            }
            versions.add(
                RomFileVersion(
                    fid = "default",
                    name = "${game.title}.zip",
                    size = "Standard",
                    category = "Основные",
                    downloadUrl = fallbackDlUrl,
                    regionOrType = "USA"
                )
            )
        }

        versions
    }

    suspend fun resolveDirectDownloadFromUrl(fileUrl: String, referer: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = fileUrl.replace("&amp;", "&")
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
                .build()

            val resp = noRedirectClient.newCall(req).execute()
            val locationHeader = resp.header("Location")
            if (!locationHeader.isNullOrBlank()) {
                val finalUrl = when {
                    locationHeader.startsWith("//") -> "https:$locationHeader"
                    locationHeader.startsWith("/") -> "$baseUrl$locationHeader"
                    else -> locationHeader
                }
                return@withContext finalUrl.replace(" ", "%20")
            }
        } catch (e: Exception) {
            Log.e("EmuLandScraper", "Error resolving direct download from url $fileUrl: ${e.message}", e)
        }
        null
    }

    /**
     * Resolves the direct downloadable ZIP URL using emu-land's 2-step getmfl redirect endpoint.
     */
    suspend fun resolveDirectDownloadUrl(game: GameCard): String? = withContext(Dispatchers.IO) {
        val versions = fetchRomVersions(game)
        val preferredVersion = versions.firstOrNull { it.regionOrType == "RUS" }
            ?: versions.firstOrNull { it.regionOrType == "USA" }
            ?: versions.firstOrNull()

        if (preferredVersion != null && preferredVersion.downloadUrl.contains("act=getmfl")) {
            val referer = "$baseUrl/${game.section}/${game.consoleSlug}/roms"
            val resolved = resolveDirectDownloadFromUrl(preferredVersion.downloadUrl, referer)
            if (resolved != null) return@withContext resolved
        }

        null
    }

    private fun fetchMfileIdFromGamePage(section: String, consoleSlug: String, gamePageSlug: String): String? {
        try {
            val url = "$baseUrl/$section/$consoleSlug/roms/$gamePageSlug"
            val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                return extractMfileIdFromHtml(html)
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Scrapes full game card (description, extra screenshots, specs) from game page
     */
    suspend fun fetchGameCardDetails(game: GameCard): GameCard = withContext(Dispatchers.IO) {
        if (game.screenshotUrls.size > 2 && game.description.length > 100 && !game.mfileId.isNullOrBlank()) {
            return@withContext game
        }

        val gameSlug = game.gamePageSlug ?: return@withContext game
        val url = "$baseUrl/${game.section}/${game.consoleSlug}/roms/$gameSlug"

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                val doc = Jsoup.parse(html, baseUrl)

                val screens = mutableListOf<String>()
                screens.addAll(game.screenshotUrls)

                doc.select(".ss-area a.ss, .picture img").forEach { elem ->
                    val rawSrc = if (elem.hasAttr("href")) elem.attr("href") else elem.attr("src")
                    normalizeImageUrl(rawSrc)?.let { norm ->
                        if (norm !in screens) screens.add(norm)
                    }
                }

                val descElem = doc.selectFirst(".ftext p, .description p, #description")
                val desc = descElem?.text()?.trim() ?: game.description
                val resolvedMfileId = extractMfileIdFromHtml(html) ?: game.mfileId

                return@withContext game.copy(
                    mfileId = resolvedMfileId,
                    screenshotUrls = if (screens.isNotEmpty()) screens else game.screenshotUrls,
                    description = if (desc.isNotEmpty()) desc else game.description,
                    coverUrl = screens.firstOrNull() ?: game.coverUrl
                )
            }
        } catch (e: Exception) {
            Log.w("EmuLandScraper", "Error fetching game details for ${game.title}: ${e.message}")
        }

        game
    }

    /**
     * Global site-wide search across ALL platforms and consoles from Emu-Land.net
     */
    suspend fun searchGamesSiteWide(query: String): List<GameCard> = withContext(Dispatchers.IO) {
        val results = mutableListOf<GameCard>()
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        try {
            val encoded = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val searchUrl = "$baseUrl/search_games?q=$encoded&id=all"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", userAgent)
                .header("Referer", baseUrl)
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val html = resp.body?.string() ?: ""
                val doc = Jsoup.parse(html, baseUrl)

                val paragraphs = doc.select("p:has(a[href*='/roms/'])")
                for (p in paragraphs) {
                    val link = p.selectFirst("a[href*='/roms/']") ?: continue
                    val href = link.attr("href")
                    val title = link.text().trim()
                    if (title.isBlank()) continue

                    val parts = href.trim('/').split('/')
                    if (parts.size < 4) continue
                    val section = parts[0]
                    val consoleSlug = parts[1]
                    val gamePageSlug = parts[3]

                    val imgTag = p.selectFirst("img")
                    val coverSrc = imgTag?.attr("src")?.let {
                        when {
                            it.startsWith("//") -> "https:$it"
                            it.startsWith("/") -> "$baseUrl$it"
                            else -> it
                        }
                    }

                    val smalls = p.select("small.muted-text")
                    var genre = "Retro Game"
                    for (sm in smalls) {
                        val txt = sm.text().trim().removePrefix("|").trim()
                        if (!txt.contains("Игроки", ignoreCase = true) && !txt.contains("Players", ignoreCase = true)) {
                            genre = txt
                            break
                        }
                    }

                    val consoleName = mapSlugToConsoleInfo(consoleSlug, consoleSlug.uppercase(), section, 0).name

                    val id = "${consoleSlug}_$gamePageSlug"
                    results.add(
                        GameCard(
                            id = id,
                            consoleSlug = consoleSlug,
                            consoleName = consoleName,
                            section = section,
                            title = title,
                            originalTitle = title,
                            genre = genre,
                            year = "N/A",
                            rating = 4.8f,
                            fileSize = "ROM",
                            coverUrl = coverSrc,
                            description = "",
                            downloadUrl = "$baseUrl/$section/$consoleSlug/roms/$gamePageSlug",
                            gamePageSlug = gamePageSlug,
                            regions = listOf("USA")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("EmuLandScraper", "Error searching games site-wide for '$query': ${e.message}", e)
        }

        results
    }

    private fun mapSlugToConsoleInfo(slug: String, rawTitle: String, section: String, index: Int): ConsoleInfo {
        val (name, shortName, cat, folder, year) = when (slug) {
            "dendy" -> ConsoleConfig("NES / Famicom / Dendy", "Dendy / NES", "8-bit", "Dendy (NES)", "1983")
            "genesis" -> ConsoleConfig("Sega Mega Drive / Genesis", "Mega Drive", "16-bit", "Sega Mega Drive", "1988")
            "snes" -> ConsoleConfig("Super Nintendo (SNES)", "SNES", "16-bit", "Super Nintendo", "1990")
            "gba" -> ConsoleConfig("Game Boy Advance", "GBA", "Handheld", "Game Boy Advance", "2001")
            "gb" -> ConsoleConfig("Game Boy", "Game Boy", "Handheld", "Game Boy", "1989")
            "gbc" -> ConsoleConfig("Game Boy Color", "GBC", "Handheld", "Game Boy Color", "1998")
            "psx" -> ConsoleConfig("Sony PlayStation 1", "PS1", "32-bit", "PlayStation 1", "1994")
            "n64" -> ConsoleConfig("Nintendo 64", "N64", "64-bit", "Nintendo 64", "1996")
            "dreamcast" -> ConsoleConfig("Sega Dreamcast", "Dreamcast", "128-bit", "Sega Dreamcast", "1998")
            "saturn" -> ConsoleConfig("Sega Saturn", "Saturn", "32-bit", "Sega Saturn", "1994")
            "nds" -> ConsoleConfig("Nintendo DS", "NDS", "Handheld", "Nintendo DS", "2004")
            "psp" -> ConsoleConfig("Sony PlayStation Portable", "PSP", "Handheld", "PlayStation Portable", "2004")
            "sms" -> ConsoleConfig("Sega Master System", "Master System", "8-bit", "Sega Master System", "1985")
            "2600" -> ConsoleConfig("Atari 2600", "Atari 2600", "Classic", "Atari 2600", "1977")
            "pce" -> ConsoleConfig("PC Engine / TurboGrafx-16", "PC Engine", "16-bit", "PC Engine", "1987")
            "3do" -> ConsoleConfig("3DO Interactive", "3DO", "32-bit", "3DO", "1993")
            "32x" -> ConsoleConfig("Sega 32X", "Sega 32X", "32-bit", "Sega 32X", "1994")
            "segacd" -> ConsoleConfig("Sega CD / Mega CD", "Sega CD", "16-bit CD", "Sega CD", "1991")
            "gg" -> ConsoleConfig("Sega Game Gear", "Game Gear", "Handheld", "Game Gear", "1990")
            "ngp" -> ConsoleConfig("Neo Geo Pocket", "NGP", "Handheld", "Neo Geo Pocket", "1998")
            "ps2" -> ConsoleConfig("Sony PlayStation 2", "PS2", "128-bit", "PlayStation 2", "2000")
            "gamecube" -> ConsoleConfig("Nintendo GameCube", "GameCube", "128-bit", "GameCube", "2001")
            else -> ConsoleConfig(rawTitle, rawTitle, if (section == "portable") "Handheld" else "Console", rawTitle.replace("/", "_"), "")
        }

        return ConsoleInfo(
            slug = slug,
            name = name,
            shortName = shortName,
            category = cat,
            folderName = folder,
            section = section,
            isEnabled = true,
            order = index,
            releaseYear = year
        )
    }

    private data class ConsoleConfig(
        val name: String,
        val shortName: String,
        val category: String,
        val folder: String,
        val year: String
    )

    fun getDefaultConsoles(): List<ConsoleInfo> {
        return listOf(
            ConsoleInfo("dendy", "NES / Famicom / Dendy", "Dendy / NES", "8-bit", "Dendy (NES)", "consoles", true, 0, "1983", "gamepad", "2,400+ ROMs"),
            ConsoleInfo("genesis", "Sega Mega Drive / Genesis", "Mega Drive", "16-bit", "Sega Mega Drive", "consoles", true, 1, "1988", "gamepad", "1,800+ ROMs"),
            ConsoleInfo("snes", "Super Nintendo (SNES)", "SNES", "16-bit", "Super Nintendo", "consoles", true, 2, "1990", "gamepad", "2,100+ ROMs"),
            ConsoleInfo("gba", "Game Boy Advance", "GBA", "Handheld", "Game Boy Advance", "portable", true, 3, "2001", "gamepad", "1,950+ ROMs"),
            ConsoleInfo("psx", "Sony PlayStation 1", "PlayStation 1", "32-bit", "PlayStation 1", "consoles", true, 4, "1994", "gamepad", "3,200+ ROMs"),
            ConsoleInfo("n64", "Nintendo 64", "N64", "64-bit", "Nintendo 64", "consoles", true, 5, "1996", "gamepad", "380+ ROMs"),
            ConsoleInfo("gb", "Game Boy", "Game Boy", "Handheld", "Game Boy", "portable", true, 6, "1989", "gamepad", "1,600+ ROMs"),
            ConsoleInfo("gbc", "Game Boy Color", "Game Boy Color", "Handheld", "Game Boy Color", "portable", true, 7, "1998", "gamepad", "1,100+ ROMs"),
            ConsoleInfo("dreamcast", "Sega Dreamcast", "Dreamcast", "128-bit", "Sega Dreamcast", "consoles", true, 8, "1998", "gamepad", "620+ ROMs"),
            ConsoleInfo("saturn", "Sega Saturn", "Saturn", "32-bit", "Sega Saturn", "consoles", false, 9, "1994", "gamepad", "540+ ROMs"),
            ConsoleInfo("nds", "Nintendo DS", "NDS", "Handheld", "Nintendo DS", "portable", false, 10, "2004", "gamepad", "1,400+ ROMs"),
            ConsoleInfo("psp", "Sony PlayStation Portable", "PSP", "Handheld", "PlayStation Portable", "portable", false, 11, "2004", "gamepad", "980+ ROMs"),
            ConsoleInfo("sms", "Sega Master System", "Master System", "8-bit", "Sega Master System", "consoles", false, 12, "1985", "gamepad", "420+ ROMs"),
            ConsoleInfo("pce", "PC Engine / TurboGrafx-16", "PC Engine", "16-bit", "PC Engine", "consoles", false, 13, "1987", "gamepad", "650+ ROMs"),
            ConsoleInfo("2600", "Atari 2600", "Atari 2600", "Classic", "Atari 2600", "consoles", false, 14, "1977", "gamepad", "550+ ROMs"),
            ConsoleInfo("3do", "3DO Interactive", "3DO", "32-bit", "3DO", "consoles", false, 15, "1993", "gamepad", "280+ ROMs"),
            ConsoleInfo("gg", "Sega Game Gear", "Game Gear", "Handheld", "Game Gear", "portable", false, 16, "1990", "gamepad", "390+ ROMs")
        )
    }

    fun getSeedGamesForConsole(slug: String, consoleName: String, section: String = "consoles"): List<GameCard> {
        return when (slug) {
            "dendy" -> listOf(
                GameCard(
                    id = "24471",
                    consoleSlug = "dendy",
                    consoleName = consoleName,
                    section = section,
                    title = "Battletoads & Double Dragon",
                    originalTitle = "The Ultimate Team",
                    genre = "beat 'em up",
                    year = "1993",
                    publisher = "Tradewest",
                    developer = "Rare Limited",
                    rating = 5.0f,
                    fileSize = "256 KB",
                    coverUrl = "https://ss.emu-land.net/nes_pict/Battletoads%20%26%20Double%20Dragon_03.png",
                    screenshotUrls = listOf(
                        "https://ss.emu-land.net/nes_pict/Battletoads%20%26%20Double%20Dragon_03.png",
                        "https://ss.emu-land.net/nes_pict/Battletoads%20%26%20Double%20Dragon_00.png",
                        "https://ss.emu-land.net/nes_pict/Battletoads%20%26%20Double%20Dragon_05.png"
                    ),
                    description = "Слияние двух великих игр Battletoads и Double Dragon. Пять бойцов на выбор, кооператив на двоих игроков и динамичные уровни.",
                    downloadUrl = "https://www.emu-land.net/consoles/dendy/roms?act=getmfl&id=24471",
                    mfileId = "24471",
                    gamePageSlug = "battletoads-and-double-dragon-the-ultimate-team",
                    regions = listOf("US", "RU", "EU")
                ),
                GameCard(
                    id = "24572",
                    consoleSlug = "dendy",
                    consoleName = consoleName,
                    section = section,
                    title = "Chip 'n Dale: Rescue Rangers",
                    originalTitle = "Chip to Dale no Daisakusen",
                    genre = "platform",
                    year = "1990",
                    publisher = "Capcom Co., Ltd.",
                    developer = "Capcom",
                    rating = 4.9f,
                    fileSize = "128 KB",
                    coverUrl = "https://ss.emu-land.net/nes_pict/Chip%20%27n%20Dale%20Rescue%20Rangers_02.png",
                    screenshotUrls = listOf(
                        "https://ss.emu-land.net/nes_pict/Chip%20%27n%20Dale%20Rescue%20Rangers_02.png",
                        "https://ss.emu-land.net/nes_pict/Chip%20%27n%20Dale%20Rescue%20Rangers_00.png",
                        "https://ss.emu-land.net/nes_pict/Chip%20%27n%20Dale%20Rescue%20Rangers_04.png"
                    ),
                    description = "Чип, Дейл и их друзья снова в деле! Один из лучших диснеевских платформеров на двоих игроков.",
                    downloadUrl = "https://www.emu-land.net/consoles/dendy/roms?act=getmfl&id=24572",
                    mfileId = "24572",
                    gamePageSlug = "chip-n-dale-rescue-rangers",
                    regions = listOf("US", "RU", "JP")
                ),
                GameCard(
                    id = "25812",
                    consoleSlug = "dendy",
                    consoleName = consoleName,
                    section = section,
                    title = "Teenage Mutant Ninja Turtles",
                    originalTitle = "Gekikame Ninja Den",
                    genre = "platform / action",
                    year = "1989",
                    publisher = "Konami / Ultra Games",
                    developer = "Konami",
                    rating = 4.8f,
                    fileSize = "138 KB",
                    coverUrl = "https://ss.emu-land.net/nes_pict/Teenage%20Mutant%20Ninja%20Turtles_02.png",
                    screenshotUrls = listOf(
                        "https://ss.emu-land.net/nes_pict/Teenage%20Mutant%20Ninja%20Turtles_02.png",
                        "https://ss.emu-land.net/nes_pict/Teenage%20Mutant%20Ninja%20Turtles_00.png"
                    ),
                    description = "Черепашки-ниндзя исследуют улицы Нью-Йорка и канализацию, спасая Эйприл О'Нил и Сплинтера от Шреддера.",
                    downloadUrl = "https://www.emu-land.net/consoles/dendy/roms?act=getmfl&id=25812",
                    mfileId = "25812",
                    gamePageSlug = "teenage-mutant-ninja-turtles",
                    regions = listOf("US", "RU", "EU", "JP")
                )
            )
            else -> emptyList()
        }
    }
}

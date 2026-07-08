package com.musicbd

import com.cosmix.app.*
import com.cosmix.app.utils.*

class MusicbdProvider : CsxApi() {
    override var mainUrl = "https://musicbd25.site"
    override var name = "Musicbd25"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )

    private val excludedSrcs = listOf(
        "1000016877",
        "wp-1674077227462",
        "r4.png",
        "rssn.png",
        "new-news",
        "1000073990",
        "1000025339",
        ".gif"
    )

    private fun upgradeBloggerImageSize(url: String): String {
        return url.replace(Regex("/s\\d+/"), "/s1600/")
    }

    private fun isValidPoster(src: String): Boolean {
        if (src.isBlank()) return false
        return excludedSrcs.none { src.contains(it) }
    }

    // Extract numeric ID from /page-download/10388/... → "10388"
    private fun extractId(href: String): String {
        return href.split("/page-download/")
            .getOrNull(1)
            ?.split("/")
            ?.firstOrNull()
            ?: ""
    }

    override val mainPage = mainPageOf(
        "$mainUrl/site-0.html" to "Latest"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val listUrl = if (page == 1) request.data else "${request.data}?to-page=$page"
        val listDoc = app.get(listUrl, headers = ua).document

        // Main page is text-only — collect all download links
        val linkElements = listDoc.select("div.catlistblock a[href*=/page-download/]")
        if (linkElements.isEmpty()) return newHomePageResponse(request.name, emptyList(), false)

        // Build poster map: video ID → poster URL
        // Strategy: fetch the first video page, get its own poster + related files posters
        val posterMap = mutableMapOf<String, String?>()

        val firstHref = linkElements.first()?.attr("href")?.trim() ?: ""
        if (firstHref.isNotBlank()) {
            val firstUrl = if (firstHref.startsWith("/")) "$mainUrl$firstHref" else firstHref
            val firstDoc = runCatching { app.get(firstUrl, headers = ua).document }.getOrNull()

            if (firstDoc != null) {
                // Poster of the first video itself
                val ownPoster = (
                    firstDoc.select("div.thumb img") +
                    firstDoc.select("div.finfo img") +
                    firstDoc.select("img[alt][title][src*=blogger.googleusercontent.com]")
                )
                    .map { it.attr("src").trim() }
                    .firstOrNull { isValidPoster(it) }
                    ?.let { upgradeBloggerImageSize(it) }

                val firstId = extractId(firstHref)
                if (firstId.isNotBlank()) posterMap[firstId] = ownPoster

                // Posters of related files (div.updates contains ~10 recent items with images)
                firstDoc.select("div.updates div.itemlist").forEach { item ->
                    val href = item.selectFirst("a")?.attr("href")?.trim() ?: return@forEach
                    val id = extractId(href)
                    if (id.isBlank()) return@forEach
                    val poster = item.selectFirst("img[src*=blogger]")
                        ?.attr("src")?.trim()
                        ?.takeIf { isValidPoster(it) }
                        ?.let { upgradeBloggerImageSize(it) }
                    posterMap[id] = poster
                }
            }
        }

        val items = linkElements.mapNotNull { el ->
            var href = el.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null
            val id = extractId(href)
            if (href.startsWith("/")) href = "$mainUrl$href"

            val title = el.text().trim().ifBlank {
                href.split("/").last().replace(".html", "").replace("-", " ")
            }

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterMap[id]
            }
        }

        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = if (page == 1) "$mainUrl/site-1.html?to-search=$encoded"
                  else "$mainUrl/site-1.html?to-search=$encoded&to-page=$page"
        val doc = app.get(url, headers = ua).document

        val linkElements = doc.select("div.catlistblock a[href*=/page-download/]")
        val posterMap = mutableMapOf<String, String?>()

        // Same strategy: fetch first result's page for posters
        val firstHref = linkElements.first()?.attr("href")?.trim() ?: ""
        if (firstHref.isNotBlank()) {
            val firstUrl = if (firstHref.startsWith("/")) "$mainUrl$firstHref" else firstHref
            val firstDoc = runCatching { app.get(firstUrl, headers = ua).document }.getOrNull()
            if (firstDoc != null) {
                val ownPoster = (
                    firstDoc.select("div.thumb img") +
                    firstDoc.select("div.finfo img")
                )
                    .map { it.attr("src").trim() }
                    .firstOrNull { isValidPoster(it) }
                    ?.let { upgradeBloggerImageSize(it) }
                val firstId = extractId(firstHref)
                if (firstId.isNotBlank()) posterMap[firstId] = ownPoster

                firstDoc.select("div.updates div.itemlist").forEach { item ->
                    val href = item.selectFirst("a")?.attr("href")?.trim() ?: return@forEach
                    val id = extractId(href)
                    if (id.isBlank()) return@forEach
                    val poster = item.selectFirst("img[src*=blogger]")
                        ?.attr("src")?.trim()
                        ?.takeIf { isValidPoster(it) }
                        ?.let { upgradeBloggerImageSize(it) }
                    posterMap[id] = poster
                }
            }
        }

        val items = linkElements.mapNotNull { el ->
            var href = el.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null
            val id = extractId(href)
            if (href.startsWith("/")) href = "$mainUrl$href"

            val title = el.text().trim().ifBlank {
                href.split("/").last().replace(".html", "").replace("-", " ")
            }

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterMap[id]
            }
        }

        return newSearchResponseList(items, items.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        doc.select("div.updates").remove()

        val title = doc.selectFirst("div.hh h2")?.text()?.trim()
            ?: doc.title().trim()

        val poster = (
            doc.select("div.thumb img") +
            doc.select("div.finfo img") +
            doc.select("img[alt][title][src*=blogger.googleusercontent.com]") +
            doc.select("img[src*=blogger.googleusercontent.com]")
        )
            .map { it.attr("src").trim() }
            .firstOrNull { isValidPoster(it) }
            ?.let { upgradeBloggerImageSize(it) }

        val downloadA = doc.selectFirst("a[href*=filedownload]")
        if (downloadA != null) {
            var downloadUrl = downloadA.attr("href").trim()
            if (downloadUrl.startsWith("//")) downloadUrl = "https:$downloadUrl"
            else if (downloadUrl.startsWith("/")) downloadUrl = "$mainUrl$downloadUrl"

            return newMovieLoadResponse(title, url, TvType.Movie, downloadUrl) {
                this.posterUrl = poster
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, "") {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank() || !data.contains("filedownload")) return false

        callback.invoke(
            newExtractorLink(
                this.name,
                "Direct Stream",
                data,
                ExtractorLinkType.VIDEO
            ) {
                quality = Qualities.Unknown.value
            }
        )
        return true
    }
}

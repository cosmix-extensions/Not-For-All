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
        "1000016877",       // Telegram join button
        "wp-1674077227462", // Download GIF
        "r4.png",           // small icon
        "rssn.png",         // RSS icon
        "new-news",         // Notice GIF
        "1000073990",       // old broken default logo
        "1000025339",       // another GIF ad
        ".gif"              // all GIFs
    )

    // Blogger CDN URL এর size ছোট থেকে বড় করে দেয় (s537 → s1600)
    private fun upgradeBloggerImageSize(url: String): String {
        return url.replace(Regex("/s\\d+/"), "/s1600/")
    }

    private fun isValidPoster(src: String): Boolean {
        if (src.isBlank()) return false
        return excludedSrcs.none { src.contains(it) }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/site-0.html" to "All Categories"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}?to-page=$page"
        val doc = app.get(url, headers = ua).document

        val items = doc.select("div.itemlist").mapNotNull { item ->
            val a = item.selectFirst("a") ?: return@mapNotNull null
            var href = a.attr("href")
            if (href.isBlank() || !href.contains("/page-download/")) return@mapNotNull null
            if (href.startsWith("/")) href = "$mainUrl$href"

            var title = a.text().trim()
            if (title.isBlank()) {
                title = a.selectFirst("img")?.attr("alt")?.trim()
                    ?: href.split("/").last().replace(".html", "").replace("-", " ")
            }

            val poster = a.select("img")
                .map { it.attr("src") }
                .firstOrNull { isValidPoster(it) }
                ?.let { upgradeBloggerImageSize(it) }

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = if (page == 1) "$mainUrl/site-1.html?to-search=$encoded"
                  else "$mainUrl/site-1.html?to-search=$encoded&to-page=$page"
        val doc = app.get(url, headers = ua).document

        val items = doc.select("div.itemlist").mapNotNull { item ->
            val a = item.selectFirst("a") ?: return@mapNotNull null
            var href = a.attr("href")
            if (href.isBlank() || !href.contains("/page-download/")) return@mapNotNull null
            if (href.startsWith("/")) href = "$mainUrl$href"

            var title = a.text().trim()
            if (title.isBlank()) {
                title = a.selectFirst("img")?.attr("alt")?.trim()
                    ?: href.split("/").last().replace(".html", "").replace("-", " ")
            }

            val poster = a.select("img")
                .map { it.attr("src") }
                .firstOrNull { isValidPoster(it) }
                ?.let { upgradeBloggerImageSize(it) }

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }

        return newSearchResponseList(items, items.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        // Related files সরিয়ে দাও
        doc.select("div.updates").remove()

        val title = doc.selectFirst("div.hh h2")?.text()?.trim()
            ?: doc.title().trim()

        // ✅ POSTER - 4টি fallback strategy
        // Strategy 1: div.thumb এর img — সবচেয়ে নির্ভরযোগ্য
        // Strategy 2: div.finfo এর যেকোনো valid img
        // Strategy 3: alt + title দুটোই আছে এমন blogger img (poster-এর signature)
        // Strategy 4: যেকোনো valid blogger img
        val poster = (
            doc.select("div.thumb img") +
            doc.select("div.finfo img") +
            doc.select("img[alt][title][src*=blogger.googleusercontent.com]") +
            doc.select("img[src*=blogger.googleusercontent.com]")
        )
            .map { it.attr("src") }
            .firstOrNull { isValidPoster(it) }
            ?.let { upgradeBloggerImageSize(it) }

        val downloadA = doc.selectFirst("a[href*=filedownload]")
        if (downloadA != null) {
            var downloadUrl = downloadA.attr("href")
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

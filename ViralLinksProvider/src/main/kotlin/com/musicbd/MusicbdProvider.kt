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

    // Upgrade Blogger CDN image from small size to high quality
    private fun upgradeBloggerImageSize(url: String): String {
        return url.replace(Regex("/s\\d+/"), "/s1600/")
    }

    private fun isValidPoster(src: String): Boolean {
        if (src.isBlank()) return false
        return excludedSrcs.none { src.contains(it) }
    }

    // Main page uses site-0.html for consistent pagination
    override val mainPage = mainPageOf(
        "$mainUrl/site-0.html" to "Latest"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        // Correct pagination: site-0.html?to-page=N
        val url = if (page == 1) request.data else "${request.data}?to-page=$page"
        val doc = app.get(url, headers = ua).document

        // Main page uses div.catlistblock — no images here, only text links
        val items = doc.select("div.catlistblock").mapNotNull { block ->
            val a = block.selectFirst("a[href*=/page-download/]") ?: return@mapNotNull null
            var href = a.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null
            if (href.startsWith("/")) href = "$mainUrl$href"

            val title = a.text().trim().ifBlank {
                href.split("/").last().replace(".html", "").replace("-", " ")
            }

            // No poster on listing page — CloudStream shows placeholder
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = null
            }
        }

        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        // Correct search URL from the site's form action
        val url = if (page == 1) "$mainUrl/site-1.html?to-search=$encoded"
                  else "$mainUrl/site-1.html?to-search=$encoded&to-page=$page"
        val doc = app.get(url, headers = ua).document

        // Search page also uses div.catlistblock same as main page
        val items = doc.select("div.catlistblock").mapNotNull { block ->
            val a = block.selectFirst("a[href*=/page-download/]") ?: return@mapNotNull null
            var href = a.attr("href").trim()
            if (href.isBlank()) return@mapNotNull null
            if (href.startsWith("/")) href = "$mainUrl$href"

            val title = a.text().trim().ifBlank {
                href.split("/").last().replace(".html", "").replace("-", " ")
            }

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = null
            }
        }

        return newSearchResponseList(items, items.isNotEmpty())
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        doc.select("div.updates").remove()

        val title = doc.selectFirst("div.hh h2")?.text()?.trim()
            ?: doc.title().trim()

        // Poster lives inside div.finfo > div.thumb > img
        // The img has both alt and title set to the video name
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

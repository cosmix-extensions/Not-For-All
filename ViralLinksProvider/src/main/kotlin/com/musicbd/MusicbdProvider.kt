package com.musicbd

import com.cosmix.app.*
import com.cosmix.app.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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

    // Fetch poster from a single download page
    private suspend fun fetchPoster(url: String): String? {
        return runCatching {
            val doc = app.get(url, headers = ua).document
            (
                doc.select("div.thumb img") +
                doc.select("div.finfo img") +
                doc.select("img[alt][title][src*=blogger.googleusercontent.com]")
            )
                .map { it.attr("src").trim() }
                .firstOrNull { isValidPoster(it) }
                ?.let { upgradeBloggerImageSize(it) }
        }.getOrNull()
    }

    override val mainPage = mainPageOf(
        "$mainUrl/site-0.html" to "Latest"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val listUrl = if (page == 1) request.data else "${request.data}?to-page=$page"
        val listDoc = app.get(listUrl, headers = ua).document

        val linkElements = listDoc.select("div.catlistblock a[href*=/page-download/]")
        if (linkElements.isEmpty()) return newHomePageResponse(request.name, emptyList(), false)

        // Fetch ALL video pages in parallel — no limit
        val items = coroutineScope {
            linkElements.map { el ->
                async {
                    var href = el.attr("href").trim()
                    if (href.isBlank()) return@async null
                    if (href.startsWith("/")) href = "$mainUrl$href"

                    val title = el.text().trim().ifBlank {
                        href.split("/").last().replace(".html", "").replace("-", " ")
                    }

                    // Each video page fetched in parallel
                    val poster = fetchPoster(href)

                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = poster
                    }
                }
            }.awaitAll().filterNotNull()
        }

        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = if (page == 1) "$mainUrl/site-1.html?to-search=$encoded"
                  else "$mainUrl/site-1.html?to-search=$encoded&to-page=$page"
        val doc = app.get(url, headers = ua).document

        val linkElements = doc.select("div.catlistblock a[href*=/page-download/]")
        if (linkElements.isEmpty()) return newSearchResponseList(emptyList(), false)

        // Fetch all search result pages in parallel
        val items = coroutineScope {
            linkElements.map { el ->
                async {
                    var href = el.attr("href").trim()
                    if (href.isBlank()) return@async null
                    if (href.startsWith("/")) href = "$mainUrl$href"

                    val title = el.text().trim().ifBlank {
                        href.split("/").last().replace(".html", "").replace("-", " ")
                    }

                    val poster = fetchPoster(href)

                    newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = poster
                    }
                }
            }.awaitAll().filterNotNull()
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

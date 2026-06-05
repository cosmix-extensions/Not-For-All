package com.musicbd

import com.cosmix.app.*
import com.cosmix.app.utils.*

class MusicbdProvider : CsxApi() {
    override var mainUrl = "https://musicbd25.site"
    override var name = "Musicbd25"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    private val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/site-0.html" to "All Categories" // Example, can add more later
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}?to-page=$page"
        val doc = app.get(url, headers = ua).document
        
        val items = doc.select("div.post").mapNotNull { post ->
            val a = post.selectFirst("a") ?: return@mapNotNull null
            var href = a.attr("href")
            if (href.isBlank() || !href.contains("/page-download/")) return@mapNotNull null
            
            if (href.startsWith("/")) {
                href = "$mainUrl$href"
            }
            
            var title = a.text().trim()
            if (title.isBlank()) {
                val img = a.selectFirst("img")
                if (img != null && img.hasAttr("alt")) {
                    title = img.attr("alt")
                } else {
                    title = href.split("/").last().replace(".html", "").replace("-", " ")
                }
            }
            
            val poster = a.selectFirst("img")?.attr("src") ?: "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhQvNXfZt7ctszD6Fy_FwU7NfcyxIEZ6uW6asTw_5cMPS38hkm65bQdzb2bCD-86XfOUVmp5xjOANaefT4ZdWSCf_picqYtsAN5McX_3gVEfdVa5EA4h9e2noiaNLwUhMK8VaGx1mQGI_7TCnpmEI3LxtgNPeVpKsojjSbqSZh50VbyrTiP7_2KOIusBBsC/s1024/1000073990.png"
            
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
        
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val url = if (page == 1) "$mainUrl/?s=${java.net.URLEncoder.encode(query, "UTF-8")}" else "$mainUrl/page/$page/?s=${java.net.URLEncoder.encode(query, "UTF-8")}"
        val doc = app.get(url, headers = ua).document
        
        val items = doc.select("div.post").mapNotNull { post ->
            val a = post.selectFirst("a") ?: return@mapNotNull null
            var href = a.attr("href")
            if (href.isBlank() || !href.contains("/page-download/")) return@mapNotNull null
            
            if (href.startsWith("/")) {
                href = "$mainUrl$href"
            }
            
            var title = a.text().trim()
            if (title.isBlank()) {
                val img = a.selectFirst("img")
                if (img != null && img.hasAttr("alt")) {
                    title = img.attr("alt")
                } else {
                    title = href.split("/").last().replace(".html", "").replace("-", " ")
                }
            }
            
            val poster = a.selectFirst("img")?.attr("src") ?: "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhQvNXfZt7ctszD6Fy_FwU7NfcyxIEZ6uW6asTw_5cMPS38hkm65bQdzb2bCD-86XfOUVmp5xjOANaefT4ZdWSCf_picqYtsAN5McX_3gVEfdVa5EA4h9e2noiaNLwUhMK8VaGx1mQGI_7TCnpmEI3LxtgNPeVpKsojjSbqSZh50VbyrTiP7_2KOIusBBsC/s1024/1000073990.png"
            
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
        }
        
        val hasNext = items.isNotEmpty()
        return newSearchResponseList(items, hasNext)
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, headers = ua).document
        doc.select("div.updates").remove()
        
        val title = doc.title().trim()
        val imgUrls = doc.select("div.post img").mapNotNull { it.attr("src") }
        val poster = imgUrls.firstOrNull { it.contains(".jpg") } 
            ?: imgUrls.firstOrNull { it.contains(".png") && !it.contains("1000016877") }
            ?: "https://blogger.googleusercontent.com/img/b/R29vZ2xl/AVvXsEhQvNXfZt7ctszD6Fy_FwU7NfcyxIEZ6uW6asTw_5cMPS38hkm65bQdzb2bCD-86XfOUVmp5xjOANaefT4ZdWSCf_picqYtsAN5McX_3gVEfdVa5EA4h9e2noiaNLwUhMK8VaGx1mQGI_7TCnpmEI3LxtgNPeVpKsojjSbqSZh50VbyrTiP7_2KOIusBBsC/s1024/1000073990.png"
        
        val downloadA = doc.selectFirst("a[href*=filedownload]")
        if (downloadA != null) {
            var downloadUrl = downloadA.attr("href")
            if (downloadUrl.startsWith("//")) {
                downloadUrl = "https:$downloadUrl"
            } else if (downloadUrl.startsWith("/")) {
                downloadUrl = "$mainUrl$downloadUrl"
            }
            
            return newMovieLoadResponse(title, url, TvType.Movie, downloadUrl) {
                this.posterUrl = poster
            }
        }
        
        return newMovieLoadResponse(title, url, TvType.Movie, "") {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        if (data.isBlank() || !data.contains("filedownload")) return false
        
        try {
            val doc = app.get(data, headers = ua).document
            val finalA = doc.selectFirst("a[href*=filedownload]")
            if (finalA != null) {
                var finalUrl = finalA.attr("href")
                if (finalUrl.startsWith("//")) {
                    finalUrl = "https:$finalUrl"
                }
                
                callback.invoke(
                    newExtractorLink(
                        this.name,
                        "Direct Stream",
                        finalUrl,
                        ExtractorLinkType.VIDEO
                    ) {
                        quality = Qualities.Unknown.value
                    }
                )
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
}

package com.hamster

import com.cosmix.app.*
import com.cosmix.app.utils.*
import org.jsoup.Jsoup
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

class HamsterProvider : CsxApi() {
    override var mainUrl = "https://xhamster.com"
    override var name = "Hamster"
    override var lang = "en"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)

    private val ua = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    private val mapper = jacksonObjectMapper()

    override val mainPage = mainPageOf(
        "$mainUrl/channels/mylf/videos" to "MYLF",
        "$mainUrl/channels/brazzers/videos" to "Brazzers",
        "$mainUrl/channels/propertysex/videos" to "Property Sex",
        "$mainUrl/categories/hd-videos" to "HD Videos",
        "$mainUrl/categories/stepmom" to "Step Mom",
        "$mainUrl/categories/stepson" to "Step Son",
        "$mainUrl/categories/stepdaughter" to "Step Daughter",
        "$mainUrl/categories/japanese" to "Japanese",
        "$mainUrl/categories/hentai" to "Hentai"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page > 1) "${request.data}/$page" else request.data
        val html = app.get(url, headers = ua).text
        val videos = extractVideosFromInitials(html)
        return newHomePageResponse(request.name, videos)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val url = if (page == 1) "$mainUrl/search?q=$q" else "$mainUrl/search?q=$q&p=$page"
        val html = app.get(url, headers = ua).text
        val items = extractVideosFromInitials(html)
        val hasNext = items.isNotEmpty()
        return newSearchResponseList(items, hasNext)
    }

    private fun extractVideosFromInitials(html: String): List<SearchResponse> {
        val list = mutableListOf<SearchResponse>()
        val regex = Regex("""window\.initials=(\{.*?\});?</script>""")
        val match = regex.find(html) ?: return list

        val jsonStr = match.groupValues[1]
        try {
            val root = mapper.readTree(jsonStr)
            
            // On search or category pages, videos are usually in relatedVideosComponent or similar
            // But let's safely traverse to find videoThumbProps
            fun findVideoProps(node: com.fasterxml.jackson.databind.JsonNode) {
                if (node.isObject) {
                    if (node.has("videoThumbProps") && node.get("videoThumbProps").isArray) {
                        for (videoNode in node.get("videoThumbProps")) {
                            val title = videoNode.get("title")?.asText() ?: continue
                            val url = videoNode.get("pageURL")?.asText() ?: continue
                            val poster = videoNode.get("thumbURL")?.asText() ?: videoNode.get("imageURL")?.asText()
                            
                            list.add(
                                newMovieSearchResponse(title, url, TvType.Movie) {
                                    this.posterUrl = poster
                                }
                            )
                        }
                    } else {
                        node.fields().forEach { findVideoProps(it.value) }
                    }
                } else if (node.isArray) {
                    node.forEach { findVideoProps(it) }
                }
            }

            findVideoProps(root)

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Deduplicate
        return list.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val html = app.get(url, headers = ua).text
        val regex = Regex("""window\.initials=(\{.*?\});?</script>""")
        val match = regex.find(html)

        var title = "Unknown Title"
        var poster: String? = null
        var description: String? = null

        if (match != null) {
            try {
                val root = mapper.readTree(match.groupValues[1])
                val videoModel = root.get("videoModel")
                if (videoModel != null) {
                    title = videoModel.get("title")?.asText() ?: title
                    poster = videoModel.get("thumbURL")?.asText() ?: videoModel.get("imageURL")?.asText()
                    description = videoModel.get("description")?.asText()
                }
            } catch (e: Exception) { }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        try {
            val html = app.get(data, headers = ua).text
            
            // Extract .mp4 and .m3u8 stream sources using regex
            // xHamster places raw links in `<video>` inside `<noscript>` tags which Jsoup ignores.
            val matches = Regex("""https?://[^"'\s]+?\.(?:mp4|m3u8)[^"'\s]*""").findAll(html)
            
            var found = false
            val addedUrls = mutableSetOf<String>()
            
            for (match in matches) {
                var streamUrl = match.value
                if (streamUrl.contains("\\/")) {
                    streamUrl = streamUrl.replace("\\/", "/")
                }
                
                // Skip preview trailers
                if (streamUrl.contains("thumb-") || streamUrl.contains(".t.mp4") || streamUrl.contains(".t.av1")) {
                    continue
                }
                
                if (addedUrls.contains(streamUrl)) continue
                addedUrls.add(streamUrl)
                
                val isM3u8 = streamUrl.contains(".m3u8")
                
                // Determine quality
                val qualityMatch = Regex("(\\d{3,4})p").find(streamUrl)
                var qualityValue = Qualities.Unknown.value
                var qualityName = if (isM3u8) "HLS Stream" else "Direct Stream"
                
                if (qualityMatch != null && !isM3u8) {
                    val q = qualityMatch.groupValues[1].toIntOrNull() ?: 0
                    qualityName = "${q}p"
                    qualityValue = when (q) {
                        1080 -> Qualities.P1080.value
                        720 -> Qualities.P720.value
                        480 -> Qualities.P480.value
                        360 -> Qualities.P360.value
                        240 -> 240
                        144 -> 144
                        else -> Qualities.Unknown.value
                    }
                }

                val type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO

                if (isM3u8) {
                    // Manually fetch and parse the m3u8 master playlist so the user sees explicit 1080p, 720p options
                    try {
                        val m3u8Content = app.get(streamUrl, headers = mapOf("Referer" to "https://xhamster.com")).text
                        var currentQuality = Qualities.Unknown.value
                        var currentQualityName = "HLS Stream"
                        
                        m3u8Content.lines().forEach { line ->
                            val l = line.trim()
                            if (l.startsWith("#EXT-X-STREAM-INF")) {
                                val resMatch = Regex("RESOLUTION=\\d+x(\\d+)").find(l)
                                if (resMatch != null) {
                                    val q = resMatch.groupValues[1].toIntOrNull() ?: 0
                                    currentQualityName = "${q}p"
                                    currentQuality = when (q) {
                                        2160 -> Qualities.P2160.value
                                        1080 -> Qualities.P1080.value
                                        720 -> Qualities.P720.value
                                        480 -> Qualities.P480.value
                                        360 -> Qualities.P360.value
                                        240 -> 240
                                        144 -> 144
                                        else -> Qualities.Unknown.value
                                    }
                                }
                            } else if (l.isNotEmpty() && !l.startsWith("#")) {
                                var subUrl = l
                                if (!subUrl.startsWith("http")) {
                                    val baseUrl = streamUrl.substringBeforeLast("/")
                                    subUrl = "$baseUrl/$subUrl"
                                }
                                callback.invoke(
                                    newExtractorLink(
                                        this.name,
                                        currentQualityName,
                                        subUrl,
                                        ExtractorLinkType.M3U8
                                    ) {
                                        quality = currentQuality
                                        headers = mapOf("Referer" to "https://xhamster.com")
                                    }
                                )
                                found = true
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            qualityName,
                            streamUrl,
                            type
                        ) {
                            quality = qualityValue
                            headers = mapOf("Referer" to "https://xhamster.com")
                        }
                    )
                    found = true
                }
            }
            return found
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return false
    }
}

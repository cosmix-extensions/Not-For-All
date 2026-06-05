version = 1

cosmix {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/cosmix-extensions/Not-For-All")
    description = "WowUncut - Hindi, Bengali, Tamil & More Web Series"
    authors = listOf("cosmix-extensions")
    language = "hi"
    tvTypes = listOf("TvSeries", "Movie", "Others")
    iconUrl = "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://wowuncut.com/&size=128"
    status = 1
    isCrossPlatform = true
}

android {
    namespace = "com.wowuncut"
}

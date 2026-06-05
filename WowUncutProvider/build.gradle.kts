cosmix {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/cosmix-extensions/Not-For-All")
    version = 1
    description = "WowUncut - Hindi, Bengali, Tamil & More Web Series"
    authors = listOf("cosmix-extensions")
    language = "hi"
    tvTypes = listOf("TvSeries", "Movie", "Others")
}

android {
    namespace = "com.wowuncut"
}

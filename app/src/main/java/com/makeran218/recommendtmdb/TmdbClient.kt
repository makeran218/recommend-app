package com.makeran218.recommendtmdb

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.LocalDate

object TmdbClient {

    private const val BASE_URL = "https://api.themoviedb.org/3/"
    private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: TmdbApiService = retrofit.create(TmdbApiService::class.java)

    /**
     * Today's date in YYYY-MM-DD format.
     * Used so Discover does not return movies/shows that
     * have not been released/aired yet.
     */
    fun today(): String = LocalDate.now().toString()

    /**
     * Date N days ago in YYYY-MM-DD format.
     */
    fun daysAgo(days: Long): String = LocalDate.now().minusDays(days).toString()

    /**
     * Get poster URL (vertical image for TV channels).
     * Uses w500 size for good quality on TV screens.
     */
    fun posterUrl(path: String?): String {
        path ?: return "https://placehold.co/500x750/?text=No+Poster"
        return "${IMAGE_BASE_URL}w500$path"
    }

    /**
     * Get backdrop URL (wide landscape image).
     */
    fun backdropUrl(path: String?): String {
        path ?: return posterUrl(null)
        return "${IMAGE_BASE_URL}original$path"
    }
}

interface TmdbApiService {

    @GET("trending/{media_type}/{time_window}")
    suspend fun getTrending(
        @Path("media_type") mediaType: String,
        @Path("time_window") timeWindow: String,
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbListResponse

    /**
     * Latest Movies — newest released movies (last 90 days).
     *
     * primary_release_date.gte + lte gives a 90-day window of recently
     * released movies, sorted newest first.
     * with_release_type=1|2|3|4|5|6 excludes movies without release data.
     */
    @GET("discover/movie")
    suspend fun discoverLatestMovies(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "primary_release_date.desc",
        @Query("page") page: Int = 1,
        @Query("primary_release_date.gte") releaseDateGte: String = TmdbClient.daysAgo(90),
        @Query("primary_release_date.lte") releaseDateLte: String = TmdbClient.today(),
        @Query("with_release_type") releaseType: String = "1|2|3|4|5|6"
    ): TmdbListResponse

    /**
     * Popular Movies — all released movies sorted by popularity.
     *
     * release_date.lte prevents upcoming/planned movies from appearing.
     */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("release_date.lte") releaseDateLte: String = TmdbClient.today()
    ): TmdbListResponse

    /**
     * TV shows that have already started airing.
     *
     * first_air_date.lte prevents planned/upcoming shows.
     */
    @GET("discover/tv")
    suspend fun discoverTvShows(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("first_air_date.lte") firstAirDateLte: String = TmdbClient.today()
    ): TmdbListResponse

    // ========================================
    // Netflix Rows (US region)
    // ========================================

    /**
     * Netflix Popular Movies
     * Available on Netflix US via subscription, sorted by popularity.
     * No date filter — Netflix catalog includes older titles.
     */
    @GET("discover/movie")
    suspend fun discoverNetflixPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("watch_region") watchRegion: String = "US",
        @Query("with_watch_providers") provider: String = "8",
        @Query("with_watch_monetization_types") monetization: String = "flatrate"
    ): TmdbListResponse

    /**
     * Netflix New Movies
     * Highest rated movies available on Netflix US via subscription.
     *
     * Note: "New" here means highly rated, not recently added to Netflix.
     * TMDB doesn't track when Netflix added a title, so we can't filter
     * by "added to Netflix date". Using vote_average.desc to show the
     * best Netflix movies, and release_date.lte to exclude unreleased.
     */
    @GET("discover/movie")
    suspend fun discoverNetflixNewMovies(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "vote_average.desc",
        @Query("page") page: Int = 1,
        @Query("watch_region") watchRegion: String = "US",
        @Query("with_watch_providers") provider: String = "8",
        @Query("with_watch_monetization_types") monetization: String = "flatrate",
        @Query("release_date.lte") releaseDateLte: String = TmdbClient.today()
    ): TmdbListResponse

    /**
     * Netflix Popular TV Shows
     * Available on Netflix US via subscription, sorted by popularity.
     * No date filter — Netflix catalog includes older shows.
     */
    @GET("discover/tv")
    suspend fun discoverNetflixPopularTv(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("watch_region") watchRegion: String = "US",
        @Query("with_watch_providers") provider: String = "8",
        @Query("with_watch_monetization_types") monetization: String = "flatrate"
    ): TmdbListResponse

    /**
     * Netflix New TV Shows
     * Highest rated TV shows available on Netflix US via subscription.
     *
     * Note: "New" here means highly rated, not recently added to Netflix.
     * TMDB doesn't track when Netflix added a title, so we can't filter
     * by "added to Netflix date". Using vote_average.desc to show the
     * best Netflix shows, and first_air_date.lte to exclude unreleased.
     */
    @GET("discover/tv")
    suspend fun discoverNetflixNewTv(
        @Query("api_key") apiKey: String,
        @Query("sort_by") sortBy: String = "vote_average.desc",
        @Query("page") page: Int = 1,
        @Query("watch_region") watchRegion: String = "US",
        @Query("with_watch_providers") provider: String = "8",
        @Query("with_watch_monetization_types") monetization: String = "flatrate",
        @Query("first_air_date.lte") firstAirDateLte: String = TmdbClient.today()
    ): TmdbListResponse
}

data class TmdbListResponse(
    val page: Int,
    val total_pages: Int,
    val total_results: Int,
    val results: List<TmdbItem>
)

data class TmdbItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val overview: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
    val media_type: String? = null,
    val genre_ids: List<Int>? = null
) {
    val displayName: String get() = title ?: name ?: "Unknown"

    val displayYear: String?
        get() {
            val date = release_date ?: first_air_date ?: return null
            return if (date.length >= 4) date.substring(0, 4) else null
        }

    val posterUrl: String get() = TmdbClient.posterUrl(poster_path)
    val backdropUrl: String get() = TmdbClient.backdropUrl(backdrop_path)
    val type: String get() = media_type ?: if (title != null) "movie" else "tv"
    val voteDisplay: String get() = vote_average?.let { "${String.format("%.1f", it)} / 10" } ?: "N/A"

    /**
     * Returns true if this item has already been released/aired.
     */
    val isReleased: Boolean
        get() {
            val date = release_date ?: first_air_date ?: return false
            return date <= TmdbClient.today()
        }
}

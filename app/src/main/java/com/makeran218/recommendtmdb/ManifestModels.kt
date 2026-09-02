package com.makeran218.recommendtmdb

import com.google.gson.annotations.SerializedName

// ==========================================
// Manifest Models (Stremio-compatible)
// ==========================================

data class Manifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String?,
    val logo: String?,
    val types: List<String>,
    val resources: List<String>,
    val catalogs: List<Catalog>,
    val idPrefixes: List<String>,
    val behaviorHints: BehaviorHints?
)

data class Catalog(
    val id: String,
    val type: String,
    val name: String,
    val showInHome: Boolean?,
    val extra: List<ExtraField>,
    val extraSupported: List<String>,
    val extraRequired: List<String>?
)

data class ExtraField(
    val name: String,
    val isRequired: Boolean,
    val options: List<String>?
)

data class BehaviorHints(
    val configurable: Boolean?,
    val configurationRequired: Boolean?
)

// ==========================================
// Catalog Meta Item Models
// ==========================================

data class CatalogResponse(
    val metas: List<MetaItem>
)

data class TrailerStream(
    val ytId: String?
)

data class MetaItem(
    val id: String,
    val type: String,
    val name: String,
    val poster: String?,
    val background: String?,
    val landscapePoster: String?,
    val description: String?,
    val releaseInfo: String?,
    val released: String?,
    val imdb_id: String?,
    val status: String?,
    val runtime: String?,
    val trailers: List<Trailer>?,
    val trailerStreams: List<TrailerStream>?,
    val links: List<Link>?,
    val genres: List<String>?,
    val posterFallback: String?,
    val logo: String?,
    val imdbRating: String?  // Can be "N/A" or a numeric string like "8.5"
)

data class Trailer(
    val source: String?,
    val type: String?,
    val name: String?,
    val ytId: String?
)

data class Link(
    val name: String?,
    val category: String?,
    val url: String?
)

// ==========================================
// Internal App Models (used by UI & LauncherChannels)
// ==========================================

data class CatalogEntry(
    val catalogId: String,
    val catalogName: String,
    val catalogType: String, // "movie" or "series"
    val enabled: Boolean = false
) {
    // Unique internal ID combining catalogId + type to avoid collisions
    // e.g., "tmdb.trending.movie" or "tmdb.trending.series"
    val uniqueId: String = "${catalogId}.${catalogType}"

    val channelName: String = catalogName
    val channelDescription: String = when (catalogType) {
        "movie" -> "Movie catalog"
        "series" -> "Series catalog"
        else -> "Catalog"
    }
}

data class ChannelItem(
    val id: String,
    val type: String,       // "movie" or "series"
    val name: String,
    val poster: String?,
    val background: String?,
    val landscapePoster: String?,
    val description: String?,
    val releaseInfo: String?,
    val released: String?,
    val imdb_id: String?,
    val status: String?,
    val runtime: String?,
    val imdbRating: Double?,
    val genres: List<String>?,
    val posterFallback: String?,
    val logo: String?,
    val trailerYtId: String?  // YouTube ID from trailerStreams — URL extracted on-demand by LocalVideoProxy
) {
    val displayName: String get() = name

    val displayYear: String?
        get() {
            val date = released ?: releaseInfo ?: return null
            return if (date.length >= 4) date.substring(0, 4) else null
        }

    val posterUrl: String
        get() = poster ?: posterFallback ?: "https://placehold.co/500x750/?text=No+Poster"

    val backdropUrl: String
        get() = landscapePoster ?: background ?: posterUrl

    val typeLabel: String get() = type
}

package com.makeran218.recommendtmdb

import android.net.Uri

interface PlaybackProvider {
    fun buildUri(item: ChannelItem): Uri
    val scheme: String
    val targetPackage: String
    val displayName: String
}

class NuvioProvider : PlaybackProvider {
    override fun buildUri(item: ChannelItem): Uri {
        val type = if (item.type == "series") "tv" else "movie"
        // Try to build a meaningful URI based on available IDs
        val id = item.imdb_id ?: item.id
        return Uri.parse("nuvio://$type/$id")
    }

    override val scheme: String = "nuvio"
    override val targetPackage: String = "com.nuvio.tv"
    override val displayName: String = "Nuvio"
}

class StremioProvider : PlaybackProvider {
    override fun buildUri(item: ChannelItem): Uri {
        val type = if (item.type == "series") "series" else "movie"

        // Try to build Stremio URI with the best available ID
        val id = item.imdb_id ?: item.id

        // Detect ID prefix to build correct Stremio URI
        val stremioId = when {
            id.startsWith("tt") -> "imdb:$id"
            id.startsWith("tmdb:") -> id
            id.startsWith("tvdb:") -> id
            id.startsWith("tmdbc:") -> "themoviedb:${id.substring(7)}"
            id.startsWith("tvdbc:") -> "thetvdb:${id.substring(7)}"
            id.startsWith("kitsu:") -> "kitsu:${id.substring(6)}"
            id.startsWith("mal:") -> "myanimelist:${id.substring(4)}"
            id.startsWith("anilist:") -> "anilist:${id.substring(8)}"
            id.startsWith("anidb:") -> "anidb:${id.substring(6)}"
            else -> "imdb:$id" // fallback to IMDb
        }

        return Uri.parse("stremio:///detail/$type/$stremioId")
    }

    override val scheme: String = "stremio"
    override val targetPackage: String = "com.stremio.one"
    override val displayName: String = "Stremio"
}

object DeepLinks {
    private var currentProvider: PlaybackProvider = NuvioProvider()

    fun setProvider(provider: PlaybackProvider) {
        currentProvider = provider
    }

    fun getCurrentProvider(): PlaybackProvider = currentProvider

    fun buildChannelUri(item: ChannelItem): Uri {
        return currentProvider.buildUri(item)
    }

    fun getTargetPackage(uri: Uri): String {
        return when (uri.scheme) {
            "nuvio" -> "com.nuvio.tv"
            "stremio" -> "com.stremio.one"
            else -> "com.nuvio.tv"
        }
    }

    fun getProvider(scheme: String): PlaybackProvider {
        return when (scheme) {
            "stremio" -> StremioProvider()
            else -> NuvioProvider()
        }
    }
}

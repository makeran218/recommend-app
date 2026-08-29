package com.makeran218.recommendtmdb

import android.net.Uri

interface PlaybackProvider {
    fun buildUri(item: TmdbItem): Uri
    val scheme: String
    val targetPackage: String
    val displayName: String
}

class NuvioProvider : PlaybackProvider {
    override fun buildUri(item: TmdbItem): Uri {
        val type = if (item.type == "tv") "tv" else "movie"
        return Uri.parse("nuvio://tmdb/$type/${item.id}")
    }

    override val scheme: String = "nuvio"
    override val targetPackage: String = "com.nuvio.tv"
    override val displayName: String = "Nuvio"
}

class StremioProvider : PlaybackProvider {
    override fun buildUri(item: TmdbItem): Uri {
        val type = if (item.type == "tv") "series" else "movie"
        // stremio:///detail/series/tmdb:1396  (triple slash = scheme-relative, colon NOT encoded)
        return Uri.parse("stremio:///detail/$type/tmdb:${item.id}")
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

    fun buildChannelUri(item: TmdbItem): Uri {
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

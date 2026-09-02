package com.makeran218.recommendtmdb

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object AppPreferences {

    private val PLAYBACK_PROVIDER_KEY = stringPreferencesKey("playback_provider")
    private val TRAILER_SOURCE_KEY = stringPreferencesKey("trailer_source")
    private val TRAILER_SERVER_URL_KEY = stringPreferencesKey("trailer_server_url")

    val DEFAULT_PLAYBACK_PROVIDER = "nuvio"
    val DEFAULT_TRAILER_SOURCE = "local"
    val DEFAULT_TRAILER_SERVER_URL = "http://192.168.2.50"

    data class Settings(
        val playbackProvider: String,
        val trailerSource: String = "local",
        val trailerServerUrl: String = "http://192.168.2.50"
    )

    fun readPreferences(context: Context): Flow<Settings> {
        return context.dataStore.data.map { prefs ->
            val provider = prefs[PLAYBACK_PROVIDER_KEY] ?: DEFAULT_PLAYBACK_PROVIDER
            val trailerSource = prefs[TRAILER_SOURCE_KEY] ?: DEFAULT_TRAILER_SOURCE
            val serverUrl = prefs[TRAILER_SERVER_URL_KEY] ?: DEFAULT_TRAILER_SERVER_URL
            Settings(provider, trailerSource, serverUrl)
        }
    }

    suspend fun setPlaybackProvider(context: Context, provider: String) {
        context.dataStore.edit { prefs -> prefs[PLAYBACK_PROVIDER_KEY] = provider }
        DeepLinks.setProvider(DeepLinks.getProvider(provider))
    }

    suspend fun setTrailerSource(context: Context, source: String) {
        context.dataStore.edit { prefs -> prefs[TRAILER_SOURCE_KEY] = source }
    }

    suspend fun setTrailerServerUrl(context: Context, url: String) {
        val cleaned = url.trim().removeSuffix("/")
        context.dataStore.edit { prefs -> prefs[TRAILER_SERVER_URL_KEY] = cleaned }
    }
}

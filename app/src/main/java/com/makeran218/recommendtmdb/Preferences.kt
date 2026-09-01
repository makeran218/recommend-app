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

    val DEFAULT_PLAYBACK_PROVIDER = "nuvio"

    data class Settings(
        val playbackProvider: String
    )

    fun readPreferences(context: Context): Flow<Settings> {
        return context.dataStore.data.map { prefs ->
            val provider = prefs[PLAYBACK_PROVIDER_KEY] ?: DEFAULT_PLAYBACK_PROVIDER
            Settings(provider)
        }
    }

    suspend fun setPlaybackProvider(context: Context, provider: String) {
        context.dataStore.edit { prefs -> prefs[PLAYBACK_PROVIDER_KEY] = provider }
        DeepLinks.setProvider(DeepLinks.getProvider(provider))
    }
}

package com.makeran218.recommendtmdb

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for fetching manifest and catalog data from xperience-app.com.
 * Uses OkHttp with Gson for JSON parsing.
 */
object XperienceClient {

    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch and parse the manifest.json from the given URL.
     */
    suspend fun fetchManifest(manifestUrl: String): Manifest {
        return withContext(Dispatchers.IO) {
            val json = fetchUrl(manifestUrl)
            gson.fromJson(json, Manifest::class.java)
                ?: throw IOException("Failed to parse manifest from $manifestUrl")
        }
    }

    /**
     * Fetch and parse a catalog JSON from the given URL.
     * Returns a CatalogResponse containing the list of meta items.
     */
    suspend fun fetchCatalog(catalogUrl: String): CatalogResponse {
        return withContext(Dispatchers.IO) {
            val json = fetchUrl(catalogUrl)
            gson.fromJson(json, CatalogResponse::class.java)
                ?: throw IOException("Failed to parse catalog from $catalogUrl")
        }
    }

    /**
     * Extract the base URL from a manifest URL.
     * Example: "https://xperience-app.com/manifest/abc/def/manifest.json"
     * Returns: "https://xperience-app.com/manifest/abc/def"
     */
    fun extractBaseUrl(manifestUrl: String): String {
        val base = manifestUrl.removeSuffix("/manifest.json").removeSuffix("/manifest.json/")
        return if (base.endsWith("/")) base.removeSuffix("/") else base
    }

    /**
     * Build a catalog URL from the manifest base URL and catalog info.
     * Example: "https://xperience-app.com/manifest/abc/def" + catalog "anime_trending_series" (series)
     * Returns: "https://xperience-app.com/manifest/abc/def/catalog/series/anime_trending_series"
     */
    fun buildCatalogUrl(baseUrl: String, catalogId: String): String {
        val catalogName = catalogId.removePrefix("catalog_")
        return "$baseUrl/catalog/$catalogName.json"
    }

    /**
     * Build a catalog URL from the manifest base URL, type, and catalog ID.
     * For catalogs that have "catalog_" prefix in their ID, extract the name.
     */
    fun buildCatalogUrl(baseUrl: String, catalogType: String, catalogId: String): String {
        // The catalog ID in the manifest maps to a filename like "anime_trending_series"
        // The URL pattern is: /catalog/{type}/{catalogId}.json
        return "$baseUrl/catalog/$catalogType/$catalogId.json"
    }

    private suspend fun fetchUrl(url: String): String {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                response.body?.string()
                    ?: throw IOException("Empty response from $url")
            }
        }
    }
}

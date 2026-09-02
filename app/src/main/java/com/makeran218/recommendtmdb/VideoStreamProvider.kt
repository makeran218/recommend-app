package com.makeran218.recommendtmdb

import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeStreamLinkHandlerFactory
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * YouTube stream URL extractor using NewPipe Extractor.
 * Called by LocalVideoProxy to extract fresh YouTube URLs on-demand.
 */
object VideoStreamProvider {

    private const val TAG = "VideoStreamProvider"

    private var initialized = false
    private lateinit var youtubeService: YoutubeService

    /**
     * Initialize NewPipe with a custom OkHttp-based downloader.
     * Called once on first extraction.
     */
    private fun initIfNeeded() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                Log.d(TAG, "═══════════════════════════════════════════")
                Log.d(TAG, "INIT: Initializing NewPipe Extractor...")
                val downloader = OkHttpDownloader()
                NewPipe.init(downloader)

                // YouTube is service ID 0
                youtubeService = ServiceList.all()[0] as YoutubeService
                initialized = true
                Log.d(TAG, "✓ NewPipe initialized with YouTube service")
                Log.d(TAG, "═══════════════════════════════════════════")
            } catch (e: Exception) {
                Log.e(TAG, "✗ FAILED: Failed to initialize NewPipe", e)
                Log.e(TAG, "  Error: ${e.message}")
            }
        }
    }

    /**
     * Extract the best video stream URL for a YouTube video ID.
     * Returns the direct YouTube stream URL (fresh, no expiration).
     */
    fun extractStreamUrl(context: Context, videoId: String): String? {
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "EXTRACT: Starting extraction for videoId=$videoId")
        Log.d(TAG, "  Context: ${context.packageName}")

        initIfNeeded()

        if (!::youtubeService.isInitialized) {
            Log.e(TAG, "✗ FAILED: YouTube service not initialized")
            Log.d(TAG, "═══════════════════════════════════════════")
            return null
        }

        return try {
            Log.d(TAG, "  → Creating link handler from ID...")
            // Create link handler from video ID
            val linkHandler = YoutubeStreamLinkHandlerFactory.getInstance().fromId(videoId)
            Log.d(TAG, "  ✓ Link handler created: ${linkHandler.url}")

            Log.d(TAG, "  → Getting stream extractor...")
            val extractor = youtubeService.getStreamExtractor(linkHandler)
            Log.d(TAG, "  ✓ Extractor created")

            // Fetch the YouTube page (required before accessing streams)
            Log.d(TAG, "  → Fetching YouTube page...")
            extractor.fetchPage()
            Log.d(TAG, "  ✓ Page fetched")

            // Try video+audio combined streams first (like yt-dlp -f 18)
            // These are single-file MP4s with both audio and video — no muxing needed
            Log.d(TAG, "  → Fetching video+audio streams...")
            val videoStreams = extractor.getVideoStreams()
            Log.d(TAG, "    Found ${videoStreams.size} video+audio streams")

            if (videoStreams.isNotEmpty()) {
                val bestStream = videoStreams.maxByOrNull { it.getBitrate() }
                Log.d(
                    TAG,
                    "    Best video+audio: bitrate=${bestStream?.getBitrate()}, isUrl=${bestStream?.isUrl()}"
                )

                if (bestStream != null && bestStream.isUrl()) {
                    val url = bestStream.getContent()
                    Log.d(TAG, "  ✓ SUCCESS (video+audio):")
                    Log.d(TAG, "    URL: ${url.take(80)}...")
                    Log.d(TAG, "    Length: ${url.length} chars")
                    Log.d(TAG, "    Bitrate: ${bestStream.getBitrate()}bps")
                    Log.d(TAG, "    Resolution: ${bestStream.getResolution()}")
                    Log.d(TAG, "═══════════════════════════════════════════")
                    return url
                }
            }

            // Fallback: video-only streams (highest quality, but no audio)
            Log.w(TAG, "  → No video+audio streams, falling back to video-only...")
            val videoOnlyStreams = extractor.getVideoOnlyStreams()
            Log.d(TAG, "    Found ${videoOnlyStreams.size} video-only streams")

            if (videoOnlyStreams.isNotEmpty()) {
                val bestVideoOnly = videoOnlyStreams.maxByOrNull { it.getBitrate() }
                Log.d(
                    TAG,
                    "    Best video-only: bitrate=${bestVideoOnly?.getBitrate()}, isUrl=${bestVideoOnly?.isUrl()}"
                )

                if (bestVideoOnly != null && bestVideoOnly.isUrl()) {
                    val url = bestVideoOnly.getContent()
                    Log.w(TAG, "  ⚠ Using video-only (no audio):")
                    Log.d(TAG, "    URL: ${url.take(80)}...")
                    Log.d(TAG, "    Bitrate: ${bestVideoOnly.getBitrate()}bps")
                    Log.d(TAG, "    Resolution: ${bestVideoOnly.getResolution()}")
                    Log.d(TAG, "═══════════════════════════════════════════")
                    return url
                }
            }

            Log.e(TAG, "✗ FAILED: No suitable stream found")
            Log.d(TAG, "═══════════════════════════════════════════")
            null
        } catch (e: ExtractionException) {
            Log.e(TAG, "✗ FAILED: ExtractionException", e)
            Log.e(TAG, "  Error: ${e.message}")
            Log.d(TAG, "═══════════════════════════════════════════")
            null
        } catch (e: Exception) {
            Log.e(TAG, "✗ FAILED: Unexpected error", e)
            Log.e(TAG, "  Error: ${e.message}")
            Log.d(TAG, "═══════════════════════════════════════════")
            null
        }
    }

    /**
     * OkHttp-based Downloader implementation for NewPipe Extractor.
     */
    private class OkHttpDownloader : Downloader() {

        companion object {
            private const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        private val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        override fun execute(request: Request): Response {
            Log.d("NewPipeDownloader", "REQUEST: ${request.httpMethod()} ${request.url().take(100)}...")

            val builder = okhttp3.Request.Builder()
                .url(request.url())

            // Add headers
            request.headers()?.forEach { (name, values) ->
                values.forEach { value ->
                    builder.addHeader(name, value)
                }
            }
            // Default User-Agent if no headers provided
            if (request.headers()?.isEmpty() != false) {
                builder.addHeader("User-Agent", USER_AGENT)
            }

            // Set body for POST requests
            val body = request.dataToSend()
            if (body != null && body.isNotEmpty()) {
                builder.post(body.toRequestBody())
            } else {
                builder.method(request.httpMethod(), null)
            }

            val okResponse = client.newCall(builder.build()).execute()

            Log.d(
                "NewPipeDownloader",
                "RESPONSE: ${okResponse.code} ${okResponse.message} for ${okResponse.request.url}"
            )

            val responseBodyBytes = okResponse.body?.bytes() ?: byteArrayOf()
            val responseBody = responseBodyBytes.decodeToString()

            return Response(
                okResponse.code,
                okResponse.message,
                okResponse.headers.toMultimap(),
                responseBody,
                okResponse.request.url.toString()
            )
        }
    }
}

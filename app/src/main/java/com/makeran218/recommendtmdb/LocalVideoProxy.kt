package com.makeran218.recommendtmdb

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import java.io.InputStream

/**
 * Local HTTP proxy server using NanoHTTPD that extracts YouTube stream URLs on-demand.
 *
 * When the launcher hovers over a channel, it requests:
 *   http://127.0.0.1:{PORT}/stream?id={VIDEO_ID}
 *
 * This server extracts a fresh YouTube stream URL via NewPipe and proxies the video stream back.
 * No caching, no expiration — always fresh.
 */
object LocalVideoProxy {

    private const val TAG = "LocalVideoProxy"
    private const val DEFAULT_PORT = 18080

    private var server: NanoHTTPD? = null
    private var isRunning = false

    /**
     * Start the local proxy server.
     */
    fun start(context: Context): Boolean {
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "START: Attempting to start proxy server...")
        Log.d(TAG, "  Context: ${context.packageName}")

        if (isRunning) {
            Log.w(TAG, "⚠ Server already running")
            Log.d(TAG, "═══════════════════════════════════════════")
            return true
        }

        return try {
            Log.d(TAG, "  → Creating NanoHTTPD server on 127.0.0.1:$DEFAULT_PORT...")
            server = object : NanoHTTPD("127.0.0.1", DEFAULT_PORT) {
                override fun serve(session: IHTTPSession): NanoHTTPD.Response {
                    return YouTubeStreamHandler(context).handleRequest(session)
                }
            }

            Log.d(TAG, "  → Starting server...")
            server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            isRunning = true
            Log.d(TAG, "✓ Server started successfully on port $DEFAULT_PORT")
            Log.d(TAG, "  Access URL: http://127.0.0.1:$DEFAULT_PORT/stream?id={VIDEO_ID}")
            Log.d(TAG, "═══════════════════════════════════════════")
            true
        } catch (e: Exception) {
            Log.e(TAG, "✗ FAILED: Failed to start server", e)
            Log.e(TAG, "  Error: ${e.message}")
            Log.d(TAG, "═══════════════════════════════════════════")
            false
        }
    }

    /**
     * Stop the local proxy server.
     */
    fun stop() {
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "STOP: Stopping proxy server...")

        if (!isRunning) {
            Log.w(TAG, "⚠ Server not running")
            Log.d(TAG, "═══════════════════════════════════════════")
            return
        }

        try {
            server?.stop()
            server = null
            isRunning = false
            Log.d(TAG, "✓ Server stopped")
            Log.d(TAG, "═══════════════════════════════════════════")
        } catch (e: Exception) {
            Log.e(TAG, "✗ FAILED: Failed to stop server", e)
            Log.e(TAG, "  Error: ${e.message}")
            Log.d(TAG, "═══════════════════════════════════════════")
        }
    }

    /**
     * Get the proxy base URL to use in PreviewProgram.
     */
    fun getProxyUrl(videoId: String): String {
        return "http://127.0.0.1:$DEFAULT_PORT/stream?id=$videoId"
    }

    /**
     * Check if the server is currently running.
     */
    fun isCurrentlyRunning(): Boolean = isRunning

    /**
     * HTTP handler for YouTube stream extraction.
     */
    private class YouTubeStreamHandler(private val context: Context) {

        fun handleRequest(session: IHTTPSession): NanoHTTPD.Response {
            val uri = session.getUri()
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "REQUEST: ${session.method} ${uri ?: "null"}")

            // Only handle GET requests to /stream
            if (session.method != Method.GET) {
                Log.w(TAG, "✗ Wrong method: ${session.method} (expected GET)")
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED,
                    "text/plain",
                    "Method not allowed"
                )
            }

            val videoId = extractVideoId(session)

            if (videoId == null) {
                Log.e(TAG, "✗ FAILED: No video ID in request")
                Log.e(TAG, "  URI: $uri")
                Log.e(TAG, "  Params: ${session.getParms()}")
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.BAD_REQUEST,
                    "text/plain",
                    "Missing video ID"
                )
            }

            Log.d(TAG, "  Video ID: $videoId")
            Log.d(TAG, "  → Extracting stream URL via NewPipe...")

            return try {
                // Extract fresh YouTube stream URL using NewPipe
                val streamUrl = VideoStreamProvider.extractStreamUrl(context, videoId)

                if (streamUrl == null) {
                    Log.e(TAG, "✗ FAILED: Stream extraction returned null")
                    Log.d(TAG, "═══════════════════════════════════════════")
                    NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.INTERNAL_ERROR,
                        "text/plain",
                        "Failed to extract stream"
                    )
                } else {
                    Log.d(TAG, "  ✓ Stream URL extracted successfully")
                    Log.d(TAG, "    URL preview: ${streamUrl.take(80)}...")
                    Log.d(TAG, "    URL length: ${streamUrl.length} chars")
                    Log.d(TAG, "  → Connecting to YouTube...")

                    // Fetch the actual video stream from YouTube and proxy it
                    Log.d(TAG, "  → Opening HTTP connection to YouTube...")
                    val youtubeConnection = java.net.URL(streamUrl).openConnection() as java.net.HttpURLConnection
                    youtubeConnection.instanceFollowRedirects = true
                    youtubeConnection.connectTimeout = 10000
                    youtubeConnection.readTimeout = 30000

                    Log.d(TAG, "  → Sending request to YouTube...")
                    if (youtubeConnection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                        Log.e(TAG, "✗ FAILED: YouTube returned ${youtubeConnection.responseCode}")
                        youtubeConnection.disconnect()
                        Log.d(TAG, "═══════════════════════════════════════════")
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.INTERNAL_ERROR,
                            "text/plain",
                            "YouTube error"
                        )
                    } else {
                        val contentType = youtubeConnection.contentType ?: "video/mp4"
                        val contentLength = youtubeConnection.contentLengthLong
                        Log.d(TAG, "  ✓ YouTube connection successful")
                        Log.d(TAG, "    Content-Type: $contentType")
                        Log.d(TAG, "    Content-Length: $contentLength bytes")

                        val input: InputStream = youtubeConnection.inputStream
                        Log.d(TAG, "  → Starting video stream proxy...")

                        // Wrap input stream to log when NanoHTTPD finishes reading it
                        val loggingInput = object : InputStream() {
                            private val delegate = input
                            private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

                            override fun read(): Int = delegate.read()
                            override fun read(b: ByteArray): Int = delegate.read(b)
                            override fun read(b: ByteArray, off: Int, len: Int): Int =
                                delegate.read(b, off, len)

                            override fun close() {
                                if (closed.compareAndSet(false, true)) {
                                    Log.d(TAG, "  ✓ Video streaming completed (all data sent)")
                                    youtubeConnection.disconnect()
                                    Log.d(TAG, "  → YouTube connection closed")
                                    Log.d(TAG, "═══════════════════════════════════════════")
                                }
                                delegate.close()
                            }
                        }

                        // Stream the video data back to the launcher
                        // NanoHTTPD will read from loggingInput and close it when done
                        val response = NanoHTTPD.newChunkedResponse(
                            NanoHTTPD.Response.Status.OK,
                            contentType,
                            loggingInput
                        )

                        response
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "✗ FAILED: Proxy error", e)
                Log.e(TAG, "  Error: ${e.message}")
                Log.d(TAG, "═══════════════════════════════════════════")
                NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.INTERNAL_ERROR,
                    "text/plain",
                    "Proxy error: ${e.message}"
                )
            }
        }

        /**
         * Extract video ID from query string: /stream?id=VIDEO_ID
         */
        private fun extractVideoId(session: IHTTPSession): String? {
            // Use NanoHTTPD's built-in getParms() which parses query parameters
            val params = session.getParms()
            if (params == null) {
                Log.e(TAG, "✗ No query parameters found")
                return null
            }

            Log.d(TAG, "  Query params: $params")
            val videoId = params["id"]
            if (videoId == null) {
                Log.e(TAG, "✗ No 'id' parameter found in query")
                return null
            }

            Log.d(TAG, "  ✓ Video ID extracted: $videoId")
            return videoId
        }
    }
}

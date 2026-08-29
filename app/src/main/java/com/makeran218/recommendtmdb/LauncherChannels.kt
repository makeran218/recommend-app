package com.makeran218.recommendtmdb

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherChannels(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "LauncherChannels"

        // Official framework constants — do NOT hardcode these integers.
        // They map to internal TvContract database values that the launcher
        // uses to decide the tile container size.
        const val ASPECT_RATIO_2_3 = TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3
        const val ASPECT_RATIO_16_9 = TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9

        /**
         * Program type constants (from android.media.tv.TvContract.Programs).
         */
        const val TYPE_MOVIE = 1
        const val TYPE_TV_SHOW = 2

        fun syncAll(
            context: Context,
            itemsByCategory: Map<String, List<TmdbItem>>,
            displayType: DisplayType,
            scope: CoroutineScope
        ) {
            val launcher = LauncherChannels(context, scope)
            launcher.syncChannels(itemsByCategory, displayType)
        }
    }

    private fun syncChannels(itemsByCategory: Map<String, List<TmdbItem>>, displayType: DisplayType) {
        scope.launch {
            try {
                deleteAllChannels()

                for ((categoryKey, items) in itemsByCategory) {
                    val category = Category.values().find { it.key == categoryKey } ?: continue
                    if (items.isEmpty()) continue

                    syncCategory(category, items, displayType)
                }

                Log.d(TAG, "Channels synced: ${itemsByCategory.size} categories, Display: $displayType")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync channels", e)
            }
        }
    }

    private suspend fun syncCategory(category: Category, items: List<TmdbItem>, displayType: DisplayType) {
        withContext(Dispatchers.IO) {
            val channelId = createChannel(category)

            for (item in items) {
                createProgram(channelId, category, item, displayType)
            }
        }
    }

    private fun createChannel(category: Category): Long {
        // Build a content URI pointing to the drawable resource
        val logoUri = Uri.Builder()
            .scheme("android.resource")
            .authority(context.packageName)
            .path(context.resources.getResourceEntryName(R.drawable.ic_launcher_foreground))
            .build()

        val builder = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(category.channelName(context))
            .setDescription(category.channelDescription())
            .setAppLinkIconUri(logoUri)
            .setAppLinkIntentUri(Uri.parse("channel://tmdb/${category.key}"))

        val channelUri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            builder.build().toContentValues()
        )

        val channelId = ContentUris.parseId(channelUri!!)
        TvContractCompat.requestChannelBrowsable(context, channelId)

        return channelId
    }

    private fun createProgram(channelId: Long, category: Category, item: TmdbItem, displayType: DisplayType) {
        val deepLinkUri = DeepLinks.buildChannelUri(item)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = deepLinkUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val programBuilder = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setTitle(item.displayName)
            .setDescription(item.overview ?: "")
            .setIntent(intent)
            .setType(if (item.type == "tv") TYPE_TV_SHOW else TYPE_MOVIE)

        when (displayType) {
            DisplayType.POSTER -> {
                // Vertical poster image — 2:3 aspect ratio
                val posterUri = Uri.parse(item.posterUrl)
                programBuilder
                    .setPosterArtUri(posterUri)
                    .setPosterArtAspectRatio(ASPECT_RATIO_2_3)
            }

            DisplayType.WIDE -> {
                // Wide landscape image — 16:9 aspect ratio
                // Use both poster art and thumbnail with 16:9 so the launcher
                // recognises this as wide/landscape content.
                val wideUri = Uri.parse(item.backdropUrl)
                programBuilder
                    .setPosterArtUri(wideUri)
                    .setPosterArtAspectRatio(ASPECT_RATIO_16_9)
                    .setThumbnailUri(wideUri)
                    .setThumbnailAspectRatio(ASPECT_RATIO_16_9)
                    .setPreviewVideoUri(wideUri)
            }
        }

        val program = programBuilder.build()

        context.contentResolver.insert(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            program.toContentValues()
        )
    }

    private fun deleteAllChannels() {
        try {
            val uri = TvContractCompat.buildChannelsUriForInput("tmdb")
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete channels", e)
        }
    }
}

enum class DisplayType {
    POSTER,  // Vertical poster (2:3)
    WIDE     // Wide landscape (16:9)
}

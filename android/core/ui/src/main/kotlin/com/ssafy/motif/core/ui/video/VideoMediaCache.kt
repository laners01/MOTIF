package com.ssafy.motif.core.ui.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
internal object VideoMediaCache {

    private const val CACHE_SIZE_BYTES = 200L * 1024 * 1024

    @Volatile
    private var instance: SimpleCache? = null

    fun getInstance(context: Context): SimpleCache {
        return instance ?: synchronized(this) {
            instance ?: createCache(context.applicationContext)
                .also { instance = it }
        }
    }

    private fun createCache(
        context: Context,
    ): SimpleCache {
        return SimpleCache(
            File(context.cacheDir, "video_cache"),
            LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }
}
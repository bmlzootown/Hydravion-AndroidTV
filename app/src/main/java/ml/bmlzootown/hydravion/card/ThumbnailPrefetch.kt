package ml.bmlzootown.hydravion.card

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import ml.bmlzootown.hydravion.models.Video

object ThumbnailPrefetch {

    private const val PREFETCH_LIMIT = 12

    @JvmStatic
    fun prefetch(context: Context, videos: Array<Video>) {
        val version = ml.bmlzootown.hydravion.BuildConfig.VERSION_NAME
        val headers = LazyHeaders.Builder()
            .addHeader("User-Agent", "Hydravion (AndroidTV $version)")
            .build()

        videos.take(PREFETCH_LIMIT).forEach { video ->
            val path = video.thumbnail?.path ?: return@forEach
            Glide.with(context.applicationContext)
                .load(GlideUrl(path, headers))
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .preload()
        }
    }
}

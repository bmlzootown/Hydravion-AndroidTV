package ml.bmlzootown.hydravion.card

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.core.text.parseAsHtml
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.progressindicator.LinearProgressIndicator
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.client.HydravionClient
import ml.bmlzootown.hydravion.detail.DetailsActivity
import ml.bmlzootown.hydravion.ext.getTagColor
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.models.VideoProgress
import ml.bmlzootown.hydravion.models.VideoTypeUtil

class CardPresenter(private val videoProgress: List<VideoProgress>) : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.card_video, parent, false)
        )
        return ViewHolder(cardView.rootView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val holder = CardViewHolder(viewHolder.view)
        when (item) {
            is Video -> holder.setData(item, videoProgress.find { it.id == item.id })
            CardPlaceholder -> holder.setPlaceholder()
            else -> holder.clear()
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        CardViewHolder(viewHolder.view).clear()
    }

    private class CardViewHolder(val rootView: View) {

        val version = ml.bmlzootown.hydravion.BuildConfig.VERSION_NAME

        private val image: ImageView = rootView.findViewById(R.id.image)
        private val progress: LinearProgressIndicator = rootView.findViewById(R.id.watch_progress)
        private val duration: TextView = rootView.findViewById(R.id.duration)
        private val title: TextView = rootView.findViewById(R.id.title)
        private val desc: TextView = rootView.findViewById(R.id.desc)
        private val tagList: LinearLayout = rootView.findViewById(R.id.tags)
        private val client: HydravionClient = HydravionClient.getInstance(rootView.context)

        init {
            rootView.onFocusChangeListener =
                View.OnFocusChangeListener { _, isFocused -> setIsSelected(isFocused) }
            setIsSelected(false)
        }

        private fun setIsSelected(isSelected: Boolean) {
            title.isSelected = isSelected

            if (isSelected) {
                title.ellipsize = TextUtils.TruncateAt.MARQUEE
                title.marqueeRepeatLimit = -1
                title.isFocusable = true
                title.setHorizontallyScrolling(true)
            } else {
                title.ellipsize = TextUtils.TruncateAt.END
            }
        }

        fun setPlaceholder() {
            clearGlideTarget(image)
            image.tag = null
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setImageDrawable(null)
            image.setBackgroundColor(resolveThemeColor(R.attr.hydravionCardBackground))
            image.alpha = 0.55f
            title.text = ""
            desc.text = ""
            desc.isGone = true
            duration.isGone = true
            progress.isGone = true
            tagList.removeAllViews()
            tagList.isGone = true
        }

        fun setData(video: Video, videoProgress: VideoProgress?) {
            ViewCompat.setTransitionName(image, DetailsActivity.SHARED_ELEMENT_NAME)
            image.tag = video.id

            title.text = video.title

            val videoDesc = video.description.parseAsHtml()
            desc.text = videoDesc
            if (videoDesc.isBlank()) {
                desc.isInvisible = true
                title.textSize = 20f
            } else {
                desc.isVisible = true
                title.textSize = 16f
            }

            val textPost = VideoTypeUtil.isTextPost(video)
            val thumbnailUrl = resolveThumbnailUrl(video)
            if (!thumbnailUrl.isNullOrBlank()) {
                image.scaleType = ImageView.ScaleType.CENTER_CROP
                image.alpha = 1f
                if (canUseGlide()) {
                    Glide.with(image)
                        .load(
                            GlideUrl(
                                thumbnailUrl,
                                LazyHeaders.Builder()
                                    .addHeader("User-Agent", "Hydravion (AndroidTV $version)")
                                    .build()
                            )
                        )
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .transform(RoundedCorners(24))
                        .centerCrop()
                        .into(image)
                }
            } else {
                loadCreatorPlaceholder(video)
            }

            when {
                textPost -> {
                    duration.isVisible = true
                    duration.text = rootView.context.getString(R.string.post_type_text)
                }
                video.type.equals("live", ignoreCase = true) -> duration.isGone = true
                else -> {
                    val totalDurationSecs = video.metadata?.videoDurationInSecs ?: 0
                    if (totalDurationSecs > 0) {
                        duration.isVisible = true
                        duration.text = formatDuration(totalDurationSecs)
                    } else {
                        duration.isGone = true
                    }
                }
            }

            if (videoProgress != null && !textPost) {
                progress.isVisible = true
                progress.min = 0
                progress.max = 100
                progress.progress = videoProgress.progress
            } else {
                progress.isGone = true
            }

            if (video.tags.isNotEmpty()) {
                tagList.removeAllViews()
                tagList.visibility = View.VISIBLE
                desc.maxLines = 1

                video.tags.forEach { tag ->
                    (LayoutInflater.from(rootView.context)
                        .inflate(R.layout.view_tag, tagList, false) as TextView).apply {
                        text = "#$tag"
                        backgroundTintList = ColorStateList.valueOf(rootView.context.getTagColor(tag))
                        tagList.addView(this)
                    }
                }
            } else {
                tagList.removeAllViews()
                tagList.visibility = View.GONE
                desc.maxLines = 2
            }
        }

        fun clear() {
            clearGlideTarget(image)
            image.tag = null
            image.setImageDrawable(null)
            image.background = null
            image.alpha = 1f
            title.text = ""
            desc.text = ""
            duration.text = ""
            duration.isGone = true
            progress.isGone = true
            tagList.removeAllViews()
            tagList.visibility = View.GONE
        }

        private fun loadCreatorPlaceholder(video: Video) {
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.alpha = 0.4f
            clearGlideTarget(image)
            image.setImageDrawable(null)

            val creatorId = video.creator?.id
            if (creatorId.isNullOrBlank()) {
                return
            }

            client.getCreatorById(creatorId) { creator ->
                if (image.tag != video.id || !canUseGlide()) {
                    return@getCreatorById
                }
                val iconPath = creator.icon?.path
                if (iconPath.isNullOrBlank()) {
                    return@getCreatorById
                }
                Glide.with(image)
                    .load(
                        GlideUrl(
                            iconPath,
                            LazyHeaders.Builder()
                                .addHeader("User-Agent", "Hydravion (AndroidTV $version)")
                                .build()
                        )
                    )
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .fitCenter()
                    .into(image)
            }
        }

        private fun resolveThumbnailUrl(video: Video): String? {
            val thumb = video.thumbnail ?: return null
            val childImages = thumb.childImages
            if (!childImages.isNullOrEmpty()) {
                return childImages[0].path
            }
            return thumb.path?.takeIf { it.isNotBlank() }
        }

        private fun canUseGlide(): Boolean {
            val context = rootView.context
            if (context is Activity) {
                return !context.isDestroyed && !context.isFinishing
            }
            return true
        }

        private fun clearGlideTarget(target: ImageView) {
            if (!canUseGlide()) {
                return
            }
            try {
                Glide.with(target).clear(target)
            } catch (_: IllegalArgumentException) {
                // Activity is tearing down during theme recreate.
            }
        }

        fun formatDuration(durationSecs: Int): String =
            DateUtils.formatElapsedTime(durationSecs.toLong())

        private fun resolveThemeColor(attr: Int): Int {
            val typedValue = TypedValue()
            rootView.context.theme.resolveAttribute(attr, typedValue, true)
            return ContextCompat.getColor(rootView.context, typedValue.resourceId)
        }
    }
}

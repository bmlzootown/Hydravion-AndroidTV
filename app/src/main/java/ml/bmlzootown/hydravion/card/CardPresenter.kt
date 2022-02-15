package ml.bmlzootown.hydravion.card

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.ext.getTagColor
import ml.bmlzootown.hydravion.models.Video

class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.card_video, parent, false)
        )
        return ViewHolder(cardView.rootView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (item as? Video?)?.let { video ->
            CardViewHolder(viewHolder.view).setData(video)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        CardViewHolder(viewHolder.view).unBind()
    }

    private class CardViewHolder(val rootView: View) {

        /**
         * Views
         */
        private val image: ImageView = rootView.findViewById(R.id.image)
        private val title: TextView = rootView.findViewById(R.id.title)
        private val desc: TextView = rootView.findViewById(R.id.desc)
        private val tagList: LinearLayout = rootView.findViewById(R.id.tags)

        /**
         * Colors and objects that need context
         */
        private var selectedBackgroundColor: Int = -1
        private var defaultBackgroundColor: Int = -1
        private var defaultCardImage: Drawable? = null

        init {
            rootView.context.run {
                defaultBackgroundColor = ContextCompat.getColor(this, R.color.default_background)
                selectedBackgroundColor = ContextCompat.getColor(this, R.color.selected_background)

                /**
                 * This template uses a default image in res/drawable, but the general case for Android TV
                 * will require your resources in xhdpi. For more information, see
                 * https://developer.android.com/training/tv/start/layouts.html#density-resources
                 */
                defaultCardImage = ContextCompat.getDrawable(this, R.drawable.white_plane)
            }
            rootView.onFocusChangeListener =
                View.OnFocusChangeListener { _, isFocused -> setIsSelected(isFocused) }
            setIsSelected(false)
        }

        private fun setIsSelected(isSelected: Boolean) {
            if (isSelected) {
                rootView.setBackgroundColor(selectedBackgroundColor)
                title.ellipsize = TextUtils.TruncateAt.MARQUEE
                title.marqueeRepeatLimit = -1
                title.isSelected = true
                title.isFocusable = true
                title.setHorizontallyScrolling(true)
            } else {
                rootView.setBackgroundColor(defaultBackgroundColor)
                title.ellipsize = TextUtils.TruncateAt.END
                title.isSelected = false
            }
        }

        fun setData(video: Video) {
            if (video.thumbnail != null && video.thumbnail!!.childImages != null) {
                title.text = video.title
                desc.text = video.description

                (if (video.thumbnail?.childImages?.size ?: 0 > 0) {
                    video.thumbnail?.childImages?.get(0)?.path
                } else {
                    video.thumbnail?.path
                })?.let { thumbnail ->
                    Glide.with(rootView.context)
                        .load(thumbnail)
                        .centerCrop()
                        .error(defaultCardImage)
                        .into(image)
                }

                Log.e("ERROR?", "Tag is ${video.tags.contentToString()}")

                if (video.tags.isNotEmpty()) {
                    tagList.removeAllViews()
                    tagList.visibility = View.VISIBLE

                    video.tags.forEach { tag ->
                        Chip(rootView.context).apply {
                            text = "#$tag"
                            chipBackgroundColor =
                                ColorStateList.valueOf(rootView.context.getTagColor(tag))
                            tagList.addView(this)
                        }
                    }
                } else {
                    tagList.removeAllViews()
                    tagList.visibility = View.GONE
                }
            }
        }

        fun unBind() {
            image.setImageDrawable(null)
        }
    }
}
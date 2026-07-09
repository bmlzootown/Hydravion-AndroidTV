package ml.bmlzootown.hydravion.browse

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.client.HydravionClient

/**
 * Sidebar header presenter:
 * - SubscriptionHeaderItem -> collapsible subscription with chevron
 * - IconHeaderItem -> creator row or indented channel row with its icon
 * - Settings -> gear icon
 */
class FilteredHeaderPresenter : RowHeaderPresenter() {

    private var client: HydravionClient? = null
    private val version = ml.bmlzootown.hydravion.BuildConfig.VERSION_NAME
    private val iconRequestTag = R.id.header_icon

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        if (client == null) {
            client = HydravionClient.getInstance(parent.context)
        }

        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_subscription, parent, false))
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val row = item as? Row ?: return
        val headerItem = row.headerItem ?: return
        val view = viewHolder.view
        val icon = view.findViewById<ImageView>(R.id.header_icon) ?: return
        val label = view.findViewById<TextView>(R.id.header_sub) ?: return
        val chevron = view.findViewById<TextView>(R.id.header_chevron)
        val name = headerItem.name ?: ""

        view.isFocusable = true
        view.isFocusableInTouchMode = true
        icon.visibility = View.VISIBLE
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        label.alpha = 1f
        label.textSize = 14f
        label.text = name
        view.setPadding(0, view.paddingTop, view.paddingRight, view.paddingBottom)

        when (headerItem) {
            is SubscriptionHeaderItem -> {
                chevron?.visibility = View.VISIBLE
                chevron?.text = if (headerItem.expanded) "▾" else "›"
                loadCreatorIcon(icon, headerItem.iconUrl, headerItem.creatorGUID)
            }

            is IconHeaderItem -> {
                chevron?.visibility = View.GONE
                val leftPad = if (headerItem.indented) {
                    (16 * view.resources.displayMetrics.density).toInt()
                } else {
                    0
                }
                view.setPadding(leftPad, view.paddingTop, view.paddingRight, view.paddingBottom)

                when {
                    headerItem.creatorGUID != null ->
                        loadCreatorIcon(icon, headerItem.iconUrl, headerItem.creatorGUID)
                    !headerItem.iconUrl.isNullOrEmpty() ->
                        loadIcon(icon, headerItem.iconUrl)
                    else ->
                        setFallbackIcon(icon)
                }
            }

            else -> {
                chevron?.visibility = View.GONE
                if (name == view.context.getString(R.string.settings)) {
                    icon.setImageResource(R.drawable.exo_ic_settings)
                } else {
                    setFallbackIcon(icon)
                    client?.getCreatorByName(name) { creator ->
                        loadIcon(icon, creator.icon?.path)
                    }
                }
            }
        }
    }

    private fun setFallbackIcon(icon: ImageView) {
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        icon.setImageResource(R.drawable.ic_header_fallback)
    }

    private fun loadCreatorIcon(icon: ImageView, iconUrl: String?, creatorGUID: String) {
        if (!iconUrl.isNullOrEmpty()) {
            loadIcon(icon, iconUrl)
            return
        }
        val cacheKey = "creator:$creatorGUID"
        if (icon.getTag(iconRequestTag) == cacheKey && icon.drawable != null) {
            return
        }
        setFallbackIcon(icon)
        icon.setTag(iconRequestTag, cacheKey)
        client?.getCreatorById(creatorGUID) { creator ->
            if (icon.getTag(iconRequestTag) == cacheKey) {
                loadIcon(icon, creator.icon?.path)
            }
        }
    }

    private fun loadIcon(icon: ImageView, iconPath: String?) {
        if (iconPath.isNullOrEmpty()) {
            setFallbackIcon(icon)
            icon.setTag(iconRequestTag, null)
            return
        }
        if (icon.getTag(iconRequestTag) == iconPath && icon.drawable != null) {
            return
        }
        icon.setTag(iconRequestTag, iconPath)
        icon.scaleType = ImageView.ScaleType.CENTER_CROP
        Glide.with(icon)
            .load(
                GlideUrl(
                    iconPath, LazyHeaders.Builder()
                        .addHeader("User-Agent", "Hydravion (AndroidTV $version)")
                        .build()
                )
            )
            .apply(RequestOptions.circleCropTransform())
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .dontAnimate()
            .error(R.drawable.ic_header_fallback)
            .into(icon)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) {
        viewHolder?.view?.findViewById<ImageView>(R.id.header_icon)?.let { icon ->
            Glide.with(icon).clear(icon)
            icon.setTag(iconRequestTag, null)
        }
    }
}

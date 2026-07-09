package ml.bmlzootown.hydravion.browse

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import ml.bmlzootown.hydravion.R

class GridItemPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_setting, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val view = viewHolder.view
        val label = view.findViewById<TextView>(R.id.setting_label)
        val icon = view.findViewById<ImageView>(R.id.setting_icon)
        val title = item?.toString().orEmpty()

        label.text = title
        icon.setImageResource(iconForSetting(title, view.context))

        view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            icon.alpha = if (hasFocus) 1f else 0.75f
            label.alpha = if (hasFocus) 1f else 0.85f
            label.paint.isFakeBoldText = hasFocus
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) = Unit

    private fun iconForSetting(title: String, context: android.content.Context): Int {
        return when (title) {
            context.getString(R.string.refresh) -> R.drawable.ic_setting_refresh
            context.getString(R.string.live_stream) -> R.drawable.ic_setting_live
            context.getString(R.string.format_settings) -> R.drawable.ic_setting_format
            context.getString(R.string.appearance_settings) -> R.drawable.ic_setting_theme
            context.getString(R.string.app_info) -> R.drawable.ic_setting_about
            context.getString(R.string.logout) -> R.drawable.ic_setting_logout
            else -> R.drawable.exo_ic_settings
        }
    }
}

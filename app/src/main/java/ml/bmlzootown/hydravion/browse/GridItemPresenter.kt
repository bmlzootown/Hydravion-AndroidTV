package ml.bmlzootown.hydravion.browse

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import ml.bmlzootown.hydravion.R

class GridItemPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(context, R.color.default_background))
            setTextColor(Color.WHITE)
        })

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (viewHolder.view as TextView).text = item.toString()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) = Unit

    companion object {

        private const val GRID_ITEM_WIDTH = 200
        private const val GRID_ITEM_HEIGHT = 200
    }
}
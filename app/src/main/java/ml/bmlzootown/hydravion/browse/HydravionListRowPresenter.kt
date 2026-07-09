package ml.bmlzootown.hydravion.browse

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter
import ml.bmlzootown.hydravion.R

/**
 * Leanback's default ListRowPresenter dims non-selected rows and overlays a dark
 * scrim on each child. Disable both so every row keeps the app background colour.
 */
class HydravionListRowPresenter : ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE, false) {

    init {
        shadowEnabled = false
        setSelectEffectEnabled(false)
    }

    override fun isUsingDefaultListSelectEffect(): Boolean = false

    override fun isUsingDefaultShadow(): Boolean = false

    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any?) {
        super.onBindRowViewHolder(holder, item)
        val color = resolveThemeColor(holder.view.context, R.attr.hydravionWindowBackground)
        holder.view.setBackgroundColor(color)
        findHorizontalGridView(holder.view as? ViewGroup)?.setBackgroundColor(color)
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attr, typedValue, true)
        return ContextCompat.getColor(context, typedValue.resourceId)
    }

    private fun findHorizontalGridView(parent: ViewGroup?): HorizontalGridView? {
        if (parent == null) return null
        for (i in 0 until parent.childCount) {
            when (val child = parent.getChildAt(i)) {
                is HorizontalGridView -> return child
                is ViewGroup -> findHorizontalGridView(child)?.let { return it }
            }
        }
        return null
    }
}

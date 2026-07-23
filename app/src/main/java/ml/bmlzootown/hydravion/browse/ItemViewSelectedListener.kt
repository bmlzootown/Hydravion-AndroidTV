package ml.bmlzootown.hydravion.browse

import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import ml.bmlzootown.hydravion.card.CardPlaceholder
import ml.bmlzootown.hydravion.models.Video

class ItemViewSelectedListener(
    private val onRowEndReached: (Long) -> Unit,
    private val onRowDisplayed: (Long) -> Unit,
    private val onRowItemSelected: (Long, Int) -> Unit
) : OnItemViewSelectedListener {

    private var lastRowId: Long = -1

    override fun onItemSelected(
        itemViewHolder: Presenter.ViewHolder?,
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder?,
        row: Row
    ) {
        if (row !is ListRow) {
            return
        }

        val rowId = row.headerItem?.id ?: return
        if (rowId != lastRowId) {
            lastRowId = rowId
            onRowDisplayed(rowId)
        }

        val adapter = row.adapter as? ArrayObjectAdapter ?: return
        if (item is CardPlaceholder || adapterOnlyPlaceholders(adapter)) {
            onRowDisplayed(rowId)
        }

        if (item != null) {
            val selected = adapter.indexOf(item)
            if (selected != -1) {
                onRowItemSelected(rowId, selected)
            }
        }

        if (item is Video) {
            val selected = adapter.indexOf(item)
            if (selected != -1 && selected >= adapter.size() - PREFETCH_THRESHOLD) {
                onRowEndReached(rowId)
            }
        }
    }

    private fun adapterOnlyPlaceholders(adapter: ArrayObjectAdapter): Boolean {
        if (adapter.size() == 0) {
            return true
        }
        for (i in 0 until adapter.size()) {
            if (adapter.get(i) != CardPlaceholder) {
                return false
            }
        }
        return true
    }

    companion object {
        private const val PREFETCH_THRESHOLD = 5
    }
}

package ml.bmlzootown.hydravion.browse

import androidx.leanback.widget.*
import ml.bmlzootown.hydravion.models.Video

class ItemViewSelectedListener(private val onCheckIndices: (String, Int) -> Unit, private val onVideoSelected: () -> Unit) : OnItemViewSelectedListener {

    override fun onItemSelected(
        itemViewHolder: Presenter.ViewHolder?,
        item: Any?,
        rowViewHolder: RowPresenter.ViewHolder?,
        row: Row
    ) {
        if (item is Video && row is ListRow) {
            (row.adapter as ArrayObjectAdapter).let { current ->
               current.indexOf(item).let { selected ->
                   onCheckIndices(item.creator?.id ?: "", selected)

                   if (selected != -1 && current.size() - 1 == selected) {
                       onVideoSelected()
                   }
                }
            }
        }
    }
}
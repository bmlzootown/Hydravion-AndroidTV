package ml.bmlzootown.hydravion.browse

import android.content.Context
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.models.Video

private class BrowseViewClickListener(
    private val context: Context,
    private val onVideoSelected: (Presenter.ViewHolder?, Video) -> Unit,
    private val onSettingsSelected: (SettingsAction) -> Unit
) : OnItemViewClickedListener {

    override fun onItemClicked(itemViewHolder: Presenter.ViewHolder?, item: Any?, rowViewHolder: RowPresenter.ViewHolder?, row: Row?) {
        when (item) {
            is Video -> onVideoSelected(itemViewHolder, item)
            context.getString(R.string.refresh) -> onSettingsSelected(SettingsAction.REFRESH)
            context.getString(R.string.logout) -> onSettingsSelected(SettingsAction.LOGOUT)
            context.getString(R.string.app_info) -> onSettingsSelected(SettingsAction.APP_INFO)
            context.getString(R.string.live_stream) -> onSettingsSelected(SettingsAction.LIVESTREAM)
        }
    }
}
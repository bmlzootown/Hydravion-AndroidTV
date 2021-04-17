package ml.bmlzootown.hydravion.subscription

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.RowHeaderPresenter
import com.bumptech.glide.Glide
import ml.bmlzootown.hydravion.R

class SubscriptionHeaderPresenter : RowHeaderPresenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_subscription, parent, false))

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        (item as? ListRow)?.headerItem?.name?.let { data ->
            viewHolder.view?.let { subView ->
                if (data == subView.context.getString(R.string.settings)) {
                    subView.findViewById<ImageView>(R.id.header_icon).setImageResource(R.drawable.exo_ic_settings)
                    subView.findViewById<TextView>(R.id.header_sub).text = data
                } else {
                    Glide.with(subView)
                        .load(data)
                        .into(subView.findViewById(R.id.header_icon))
                    subView.findViewById<TextView>(R.id.header_sub).text = data
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) = Unit
}
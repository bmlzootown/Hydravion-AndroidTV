package ml.bmlzootown.hydravion.subscription

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.RowHeaderPresenter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.request.RequestOptions
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.client.HydravionClient

class SubscriptionHeaderPresenter : RowHeaderPresenter() {

    private var client: HydravionClient? = null
    val version = ml.bmlzootown.hydravion.BuildConfig.VERSION_NAME

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        if (client == null) {
            client = HydravionClient.getInstance(parent.context, (parent.context as Activity).getPreferences(Context.MODE_PRIVATE))
        }

        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.header_subscription, parent, false))
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        (item as? ListRow)?.headerItem?.name?.let { name ->
            viewHolder.view?.let { subView ->
                if (name == subView.context.getString(R.string.settings)) {
                    subView.findViewById<ImageView>(R.id.header_icon).setImageResource(R.drawable.exo_ic_settings)
                    subView.findViewById<TextView>(R.id.header_sub).text = name
                } else {
                    subView.findViewById<TextView>(R.id.header_sub).text = name
                    client?.getCreatorByName(name) { creator ->
                        Glide.with(subView)
                            .load(
                                GlideUrl(
                                    creator.icon?.path, LazyHeaders.Builder()
                                        .addHeader("User-Agent", "Hydravion (AndroidTV $version), CFNetwork")
                                        .build()
                                )
                            )
                            .apply(RequestOptions.circleCropTransform())
                            .into(subView.findViewById(R.id.header_icon))
                    }
                }
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) = Unit
}
package ml.bmlzootown.hydravion.detail

import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import android.annotation.SuppressLint
import androidx.core.text.parseAsHtml
import ml.bmlzootown.hydravion.models.Video
import org.ocpsoft.prettytime.PrettyTime
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class DetailsDescriptionPresenter : AbstractDetailsDescriptionPresenter() {
    override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
        val vid = item as Video
        @SuppressLint("SimpleDateFormat") val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        input.timeZone = TimeZone.getTimeZone("UTC")
        var date: Date? = null
        try {
            date = input.parse(vid.releaseDate)
        } catch (e: ParseException) {
            e.printStackTrace()
        }
        val p = PrettyTime()
        val elapsed = p.format(date)
        viewHolder.title.text = vid.title
        //viewHolder.getSubtitle().setText(vid.getReleaseDate());
        viewHolder.subtitle.text = elapsed
        viewHolder.body.text = vid.description.parseAsHtml()
    }
}
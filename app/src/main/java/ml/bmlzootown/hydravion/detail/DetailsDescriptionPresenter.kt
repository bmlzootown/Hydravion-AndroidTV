package ml.bmlzootown.hydravion.detail

import android.annotation.SuppressLint
import android.text.TextUtils
import androidx.core.text.parseAsHtml
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.models.VideoTypeUtil
import org.ocpsoft.prettytime.PrettyTime
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

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
        val elapsed = PrettyTime().format(date)
        val textPost = VideoTypeUtil.isTextPost(vid)

        viewHolder.title.text = vid.title
        viewHolder.subtitle.text = elapsed
        viewHolder.body.visibility = android.view.View.VISIBLE
        viewHolder.body.text = vid.description.parseAsHtml()

        if (textPost) {
            viewHolder.body.maxLines = Int.MAX_VALUE
            viewHolder.body.ellipsize = null
            viewHolder.body.isFocusable = true
            viewHolder.body.isFocusableInTouchMode = true
        } else {
            viewHolder.body.maxLines = 5
            viewHolder.body.ellipsize = TextUtils.TruncateAt.END
            viewHolder.body.isFocusable = false
            viewHolder.body.isFocusableInTouchMode = false
        }
    }
}

package ml.bmlzootown.hydravion.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
class Channel : Serializable {

    @SerializedName("id")
    @Expose
    var id: String = ""

    @SerializedName("creator")
    @Expose
    var creator: String = ""

    @SerializedName("title")
    @Expose
    var title: String = ""

    @SerializedName("urlname")
    @Expose
    var urlname: String = ""

    @SerializedName("about")
    @Expose
    var about: String = ""

    @SerializedName("order")
    @Expose
    var order: Int = 0

    @SerializedName("cover")
    @Expose
    var cover: Thumbnail? = null

    @SerializedName("card")
    @Expose
    var card: Thumbnail? = null

    @SerializedName("icon")
    @Expose
    var icon: Thumbnail? = null

    @SerializedName("socialLinks")
    @Expose
    var socialLinks: Map<String, String>? = null

    companion object {
        private const val serialVersionUID = -5477687235495303565L
    }
}

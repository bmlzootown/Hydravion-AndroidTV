package ml.bmlzootown.hydravion.creator

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import ml.bmlzootown.hydravion.models.Live

@Keep
class Creator {

    @SerializedName("title")
    var name: String = ""

    @SerializedName("urlname")
    var urlExtension: String = ""

    @SerializedName("description")
    var description: String = ""

    @SerializedName("cover")
    var coverImage: FloatplaneIcon? = null

    @SerializedName("icon")
    var icon: FloatplaneIcon? = null

    @SerializedName("liveStream")
    var lastLiveStream: Live? = null
}

@Keep
class FloatplaneIcon {

    @SerializedName("width")
    var width: Int = 0

    @SerializedName("height")
    var height: Int = 0

    @SerializedName("path")
    var path: String = ""
}

@Keep
class FloatplaneLiveStream {

    @SerializedName("id")
    var id: String = ""

    @SerializedName("title")
    var title: String = ""

    @SerializedName("description")
    var description: String = ""

    @SerializedName("thumbnail")
    var thumbnail: FloatplaneIcon? = null

    @SerializedName("streamPath")
    var streamPath: String = ""

    @SerializedName("offline")
    var offlineStatus: Any? = null
}
package ml.bmlzootown.hydravion.post

import com.google.gson.annotations.SerializedName

class Post {

    val isLiked: Boolean
    get() = userInteractions.contains("like")

    val isDisliked: Boolean
    get() = userInteractions.contains("dislike")

    @SerializedName("id")
    var id: String = ""

    @SerializedName("title")
    var title: String = ""

    @SerializedName("userInteraction")
    var userInteractions: List<String> = emptyList()

    @SerializedName("videoAttachments")
    var videoAttachments: List<VideoAttachments> = emptyList()
}

class VideoAttachments (
    @SerializedName("guid")
    var guid : String = ""
)
package ml.bmlzootown.hydravion.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
class Video : Serializable {

    @SerializedName("id")
    @Expose
    var id: String = ""

    @SerializedName("guid")
    @Expose
    var guid: String = ""

    //---
    //----
    @SerializedName("vidurl")
    @Expose
    var vidUrl: String = ""

    //---
    //---
    @SerializedName("title")
    @Expose
    var title: String = ""

    @SerializedName("type")
    @Expose
    var type: String = ""

    @SerializedName("tags")
    var tags: Array<String> = emptyArray()

    @SerializedName("text")
    @Expose
    var description: String = ""

    @SerializedName("releaseDate")
    @Expose
    var releaseDate: String = ""

    @SerializedName("creator")
    @Expose
    var creator: Creator? = null

    @SerializedName("likes")
    @Expose
    var likes: Int? = null

    @SerializedName("dislikes")
    @Expose
    var dislikes: Int? = null

    @SerializedName("primaryBlogPost")
    @Expose
    var primaryBlogPost: String = ""

    @SerializedName("thumbnail")
    @Expose
    var thumbnail: Thumbnail? = null

    @SerializedName("private")
    @Expose
    var private: Boolean? = null

    @SerializedName("subscriptionPermissions")
    @Expose
    var subscriptionPermissions: List<String>? = null

    @SerializedName("videoinfo")
    @Expose
    var videoInfo: VideoInfo? = null

    override fun toString(): String =
        """
            id: $id
            guid: $guid
            videUrl: $vidUrl
            title: $title
            type: $type
            desc: $description
        """.trimIndent()

    companion object {

        private const val serialVersionUID = -5477687235495303564L
    }
}
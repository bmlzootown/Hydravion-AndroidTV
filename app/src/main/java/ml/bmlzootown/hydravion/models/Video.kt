package ml.bmlzootown.hydravion.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

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

    @SerializedName("description")
    @Expose
    var description: String = ""

    @SerializedName("releaseDate")
    @Expose
    var releaseDate: String = ""

    @SerializedName("duration")
    @Expose
    var duration: Int? = null

    @SerializedName("creator")
    @Expose
    var creator: String = ""

    @SerializedName("likes")
    @Expose
    var likes: Int? = null

    @SerializedName("dislikes")
    @Expose
    var dislikes: Int? = null

    @SerializedName("score")
    @Expose
    var score: Int? = null

    @SerializedName("isProcessing")
    @Expose
    var isProcessing: Boolean? = null

    @SerializedName("primaryBlogPost")
    @Expose
    var primaryBlogPost: String = ""

    @SerializedName("thumbnail")
    @Expose
    var thumbnail: Thumbnail? = null

    @SerializedName("isAccessible")
    @Expose
    var isAccessible: Boolean? = null

    @SerializedName("hasAccess")
    @Expose
    var hasAccess: Boolean? = null

    @SerializedName("private")
    @Expose
    var private: Boolean? = null

    @SerializedName("subscriptionPermissions")
    @Expose
    var subscriptionPermissions: List<String>? = null

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
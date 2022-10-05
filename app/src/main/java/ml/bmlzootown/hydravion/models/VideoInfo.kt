package ml.bmlzootown.hydravion.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

class VideoInfo : Serializable {

    @SerializedName("id")
    @Expose
    var id: String? = null

    @SerializedName("guid")
    @Expose
    var guid: String? = null

    @SerializedName("title")
    @Expose
    var title: String? = null

    @SerializedName("type")
    @Expose
    var type: String? = null

    @SerializedName("tags")
    @Expose
    var tags: Array<String> = emptyArray()

    @SerializedName("description")
    @Expose
    var description: String? = null

    @SerializedName("releaseDate")
    @Expose
    var releaseDate: String? = null

    @SerializedName("duration")
    @Expose
    var duration: Int? = null

    @SerializedName("creator")
    @Expose
    var creator: String? = null

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
    var primaryBlogPost: String? = null

    @SerializedName("thumbnail")
    @Expose
    var thumbnail: Thumbnail? = null

    @SerializedName("isAccessible")
    @Expose
    var isAccessible: Boolean? = null

    @SerializedName("blogPosts")
    @Expose
    var blogPosts: List<String>? = null

    @SerializedName("timelineSprite")
    @Expose
    var timelineSprite: TimelineSprite? = null

    @SerializedName("levels")
    @Expose
    var levels: List<Level>? = null

    @SerializedName("canWatchVideo")
    @Expose
    var canWatchVideo: Boolean? = null

    @SerializedName("progress")
    @Expose
    var progress: Int = 0

    companion object {

        private const val serialVersionUID = 8503392556194772771L
    }
}
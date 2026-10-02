package ml.bmlzootown.hydravion.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import ml.bmlzootown.hydravion.post.Post
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

    /** Floatplane livestream entity id used for chat (`/live/{id}`). Empty for VOD. */
    @SerializedName("liveStreamId")
    @Expose
    var liveStreamId: String = ""

    //---
    //---
    @SerializedName("title")
    @Expose
    var title: String = ""

    @SerializedName("type")
    @Expose
    var type: String = ""

    @SerializedName("tags")
    @Expose
    var tags: Array<String> = emptyArray()

    @SerializedName("attachmentOrder")
    @Expose
    var attachmentIds: Array<String> = emptyArray()

    @SerializedName("text")
    @Expose
    var description: String = ""

    @SerializedName("textMarkdown")
    @Expose
    var textMarkdown: String = ""

    /** False when the viewer cannot play this post. Missing means accessible. */
    @SerializedName("isAccessible")
    @Expose
    var accessible: Boolean? = null

    /** "video" or "audio". Set from the post detail and sent when saving progress. */
    var playbackContentType: String = "video"

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

    @SerializedName("metadata")
    @Expose
    var metadata: VideoMetaData? = null

    /** Playback id. Attachment ids win; [guid] is the blog post id on list items. */
    fun getVideoId(): String = attachmentIds.firstOrNull { it.isNotBlank() } ?: guid

    fun displayText(): String = description.ifBlank { textMarkdown }

    /**
     * Copy the playable attachment from a post-detail response onto this list item.
     * Creator feeds omit attachment arrays; `/api/v3/content/post` returns them as objects.
     */
    fun applyPostDetail(post: Post) {
        val videoId = post.firstVideoAttachmentId()
        val audioId = post.firstAudioAttachmentId()
        when {
            videoId.isNotEmpty() -> {
                attachmentIds = arrayOf(videoId)
                playbackContentType = "video"
            }
            audioId.isNotEmpty() -> {
                attachmentIds = arrayOf(audioId)
                playbackContentType = "audio"
            }
        }
        if (description.isBlank()) {
            val body = post.body()
            if (body.isNotBlank()) {
                description = body
            }
        }
        if (post.accessible == false) {
            accessible = false
        }
    }

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
package ml.bmlzootown.hydravion.post

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

class Post {

    val isLiked: Boolean
        get() = getInteractions().any { it.equals("like", ignoreCase = true) }

    val isDisliked: Boolean
        get() = getInteractions().any { it.equals("dislike", ignoreCase = true) }

    @SerializedName("id")
    var id: String = ""

    @SerializedName("title")
    var title: String = ""

    @SerializedName("text")
    var text: String = ""

    @SerializedName("textMarkdown")
    var textMarkdown: String = ""

    @SerializedName("isAccessible")
    var accessible: Boolean? = null

    @SerializedName("userInteraction")
    var userInteractions: List<String> = emptyList()

    /** Singular interaction used when [userInteractions] is empty. */
    @SerializedName("selfUserInteraction")
    var selfUserInteraction: String? = null

    @SerializedName("videoAttachments")
    @JsonAdapter(MediaAttachmentListDeserializer::class)
    var videoAttachments: List<VideoAttachments> = emptyList()

    @SerializedName("audioAttachments")
    @JsonAdapter(MediaAttachmentListDeserializer::class)
    var audioAttachments: List<VideoAttachments> = emptyList()

    fun body(): String {
        if (text.isNotBlank()) {
            return text
        }
        return textMarkdown
    }

    fun firstVideoAttachmentId(): String = videoAttachments.firstResolvedId()

    fun firstAudioAttachmentId(): String = audioAttachments.firstResolvedId()

    /** Likes from either the interaction list or the singular self field. */
    fun getInteractions(): List<String> {
        val merged = userInteractions.filter { it.isNotBlank() }.toMutableList()
        val self = selfUserInteraction
        if (!self.isNullOrBlank() && merged.none { it.equals(self, ignoreCase = true) }) {
            merged.add(self)
        }
        return merged
    }
}

class VideoAttachments {
    @SerializedName("id")
    var id: String = ""

    @SerializedName("guid")
    var guid: String = ""

    fun resolvedId(): String = id.ifBlank { guid }
}

private fun List<VideoAttachments>.firstResolvedId(): String =
    firstOrNull()?.resolvedId().orEmpty()

/**
 * Detail responses send attachment objects. Older list responses sent string ids.
 */
class MediaAttachmentListDeserializer : JsonDeserializer<List<VideoAttachments>> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<VideoAttachments> {
        if (json == null || json.isJsonNull || !json.isJsonArray) {
            return emptyList()
        }
        return json.asJsonArray.mapNotNull { element ->
            when {
                element == null || element.isJsonNull -> null
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val value = element.asString
                    if (value.isBlank()) {
                        null
                    } else {
                        VideoAttachments().apply {
                            id = value
                            guid = value
                        }
                    }
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val id = obj.stringOrEmpty("id")
                    val guid = obj.stringOrEmpty("guid")
                    if (id.isBlank() && guid.isBlank()) {
                        null
                    } else {
                        VideoAttachments().apply {
                            this.id = id.ifBlank { guid }
                            this.guid = guid.ifBlank { id }
                        }
                    }
                }
                else -> null
            }
        }
    }
}

private fun com.google.gson.JsonObject.stringOrEmpty(name: String): String {
    val value = get(name) ?: return ""
    if (value.isJsonNull || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
        return ""
    }
    return value.asString
}

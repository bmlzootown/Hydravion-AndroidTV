package ml.bmlzootown.hydravion.chat

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class ChatEmote(
    @SerializedName("code") val code: String = "",
    @SerializedName("image") val image: String = ""
)

@Keep
data class RadioChatter(
    @SerializedName("id") val id: String = "",
    @SerializedName("userGUID") val userGuid: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("channel") val channel: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("userType") val userType: String = "",
    @SerializedName("emotes") val emotes: List<ChatEmote> = emptyList(),
    @SerializedName("success") val success: Boolean = true
)

@Keep
data class JoinLivestreamResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("emotes") val emotes: List<ChatEmote> = emptyList()
)

@Keep
data class SailsAck(
    @SerializedName("body") val body: com.google.gson.JsonElement? = null,
    @SerializedName("statusCode") val statusCode: Int = 0
)

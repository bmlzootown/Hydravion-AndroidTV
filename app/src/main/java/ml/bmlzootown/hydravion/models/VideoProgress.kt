package ml.bmlzootown.hydravion.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class VideoProgress(
    @SerializedName("id")
    val id: String,

    @SerializedName("progress")
    val progress: Int
)
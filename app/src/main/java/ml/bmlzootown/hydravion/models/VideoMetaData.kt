package ml.bmlzootown.hydravion.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
class VideoMetaData : Serializable {

    @SerializedName("hasVideo")
    @Expose
    var hasVideo: Boolean? = null

    @SerializedName("hasAudio")
    @Expose
    var hasAudio: Boolean? = null

    @SerializedName("hasPicture")
    @Expose
    var hasPicture: Boolean? = null

    @SerializedName("videoCount")
    @Expose
    var videoCount: Int = 0

    /** Total video seconds on the post. Prefer [durationSeconds] for display and resume. */
    @SerializedName("videoDuration")
    @Expose
    private var videoDurationRaw: Double = 0.0

    @SerializedName("audioDuration")
    @Expose
    private var audioDurationRaw: Double = 0.0

    /** Seconds the website shows for the post. Absent on older payloads. */
    @SerializedName("displayDuration")
    @Expose
    private var displayDurationRaw: Double = 0.0

    val videoDurationInSecs: Int
        get() = videoDurationRaw.toInt()

    /**
     * One duration for the card and for converting watch-progress percent into seconds.
     * `displayDuration` is preferred. Otherwise the average video length, then audio.
     */
    fun durationSeconds(): Int {
        if (displayDurationRaw > 0) {
            return displayDurationRaw.toInt()
        }
        if (videoDurationRaw > 0) {
            return if (videoCount > 0) {
                (videoDurationRaw / videoCount).toInt()
            } else {
                videoDurationRaw.toInt()
            }
        }
        if (audioDurationRaw > 0) {
            return audioDurationRaw.toInt()
        }
        return 0
    }
}

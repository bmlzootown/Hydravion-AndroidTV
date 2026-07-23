package ml.bmlzootown.hydravion.poll

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.time.Instant

@Keep
data class PollRunningTally(
    @SerializedName("tick") val tick: Int = 0,
    @SerializedName("counts") val counts: List<Int> = emptyList()
)

@Keep
data class PollVoteInfo(
    @SerializedName("optionIndex") val optionIndex: Int = 0
)

@Keep
data class PollInfo(
    @SerializedName("id") val id: String = "",
    @SerializedName("type") val type: String = "simple",
    @SerializedName("creator") val creator: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("options") val options: List<String> = emptyList(),
    @SerializedName("startDate") val startDate: String? = null,
    /** Scheduled auto-close time while open; becomes the close instant once ended. */
    @SerializedName("endDate") val endDate: String? = null,
    @SerializedName("createdBy") val createdBy: String? = null,
    @SerializedName("closedBy") val closedBy: String? = null,
    @SerializedName("runningTally") val runningTally: PollRunningTally? = null,
    @SerializedName("finalTallyApproximate") val finalTallyApproximate: List<Int>? = null,
    @SerializedName("finalTallyReal") val finalTallyReal: List<Int>? = null,
    @SerializedName("voted") val voted: Boolean? = null,
    @SerializedName("voteInfo") val voteInfo: PollVoteInfo? = null
) {
    /**
     * Live polls always include [endDate] as start + duration. Only treat as finished
     * once that time has passed, or once [closedBy] is set (manual close).
     */
    val hasEnded: Boolean
        get() {
            if (!closedBy.isNullOrEmpty()) return true
            val end = endDate?.takeIf { it.isNotBlank() } ?: return false
            return try {
                !Instant.parse(end).isAfter(Instant.now())
            } catch (_: Exception) {
                false
            }
        }

    fun tallyCounts(): List<Int> {
        finalTallyReal?.takeIf { it.isNotEmpty() }?.let { return it }
        runningTally?.counts?.takeIf { it.isNotEmpty() }?.let { return it }
        return List(options.size) { 0 }
    }

    fun withTally(tick: Int, counts: List<Int>): PollInfo {
        val currentTick = runningTally?.tick ?: -1
        if (tick < currentTick) return this
        return copy(runningTally = PollRunningTally(tick = tick, counts = counts))
    }

    fun withVoted(optionIndex: Int): PollInfo =
        copy(voted = true, voteInfo = PollVoteInfo(optionIndex = optionIndex))
}

@Keep
data class JoinLiveRoomInfo(
    @SerializedName("activePolls") val activePolls: List<PollInfo> = emptyList()
)

@Keep
data class PollTallyEvent(
    @SerializedName("pollId") val pollId: String = "",
    @SerializedName("tick") val tick: Int = 0,
    @SerializedName("counts") val counts: List<Int> = emptyList()
)

@Keep
data class PollOpenEvent(
    @SerializedName("poll") val poll: PollInfo? = null
)

@Keep
data class PollCreatedEvent(
    @SerializedName("createdPoll") val createdPoll: PollInfo? = null
)

@Keep
data class PollCloseEvent(
    @SerializedName("poll") val poll: PollInfo? = null
)

@Keep
data class SailsAck(
    @SerializedName("body") val body: com.google.gson.JsonElement? = null,
    @SerializedName("statusCode") val statusCode: Int = 0
)

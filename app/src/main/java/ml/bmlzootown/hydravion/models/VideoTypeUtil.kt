package ml.bmlzootown.hydravion.models

object VideoTypeUtil {

    /**
     * Floatplane marks every feed item as type "blogPost". Text-only posts have no video
     * or audio. Picture-only posts are shown the same way, because this client has no gallery.
     */
    @JvmStatic
    fun isTextPost(video: Video): Boolean {
        if (video.type.equals("live", ignoreCase = true)) {
            return false
        }
        val metadata = video.metadata
        if (metadata != null &&
            (metadata.hasVideo != null || metadata.hasAudio != null || metadata.hasPicture != null)
        ) {
            return metadata.hasVideo != true && metadata.hasAudio != true
        }
        return video.attachmentIds.isEmpty() && (metadata?.durationSeconds() ?: 0) <= 0
    }

    /** Live, video, and audio can play. Locked and text posts open the detail screen only. */
    @JvmStatic
    fun isPlayable(video: Video): Boolean {
        if (video.type.equals("live", ignoreCase = true)) {
            return true
        }
        if (video.accessible == false) {
            return false
        }
        return !isTextPost(video)
    }
}

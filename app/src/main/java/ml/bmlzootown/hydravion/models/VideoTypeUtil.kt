package ml.bmlzootown.hydravion.models

object VideoTypeUtil {

    /**
     * Floatplane marks every feed item as type "blogPost". Text-only posts are identified
     * via metadata.hasVideo == false (see BlogPostModelV3 / PostMetadataModel in OpenAPI).
     */
    @JvmStatic
    fun isTextPost(video: Video): Boolean {
        if (video.type.equals("live", ignoreCase = true)) {
            return false
        }
        return when (video.metadata?.hasVideo) {
            false -> true
            true -> false
            null -> video.attachmentIds.isEmpty() &&
                (video.metadata?.videoDurationInSecs ?: 0) <= 0
        }
    }
}

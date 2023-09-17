package ml.bmlzootown.hydravion.client

data class SyncEvent (
    val event: String? = null,
    val data: Data? = null
)

data class Data (
    val id: String? = null,
    val eventType: String? = null,
    val title: String? = null,
    val message: String? = null,
    val creator: String,
    val channel: String? = null,
    val content: String? = null,
    val icon: String? = null,
    val thumbnail: String? = null,
    val target: Target? = null,
    val foregroundVisible: String? = null,
    val video: Video? = null,
    val post: Post? = null,
    val deliveryDelayRange: ArrayList<Int>? = null,

    val guid: String? = null,
    val text: String? = null,
    val type: String? = null,
    val attachmentOrder: List<String>? = null,
    val metadata: Metadata? = null,
    val releaseDate: Any? = null,
    val likes: Long? = null,
    val dislikes: Long? = null,
    val score: Long? = null,
    val comments: Long? = null,
)

data class Target (
    val url: String? = null,
    val matchScheme: String? = null,
    val match: String? = null,
    val foregroundDiscardOnMatch: Boolean? = null,
    val matchPortion: String? = null
)

data class Video (
    val creator: String? = null,
    val guid: String? = null
)

data class Post (
    val creator: String? = null,
    val guid: String? = null,
    val id: String? = null,
    val text: String? = null,
    val title: String? = null
)

data class Metadata (
    val hasVideo: Boolean? = null,
    val videoCount: Long? = null,
    val videoDuration: Long? = null,
    val hasAudio: Boolean? = null,
    val audioCount: Long? = null,
    val audioDuration: Long? = null,
    val hasPicture: Boolean? = null,
    val pictureCount: Long? = null,
    val hasGallery: Boolean? = null,
    val galleryCount: Long? = null,
    val isFeatured: Boolean? = null
)



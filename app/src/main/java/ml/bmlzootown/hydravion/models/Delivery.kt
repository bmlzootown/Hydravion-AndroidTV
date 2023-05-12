package ml.bmlzootown.hydravion.models

data class Delivery (
    val groups: List<Group>
)

data class Group (
    val origins: List<Origin>,
    val variants: List<Variant>
)

data class Origin (
    val url: String,
)

data class Variant (
    val name: String,
    val label: String,
    val url: String,
    val mimeType: String,
    val order: Int?,
    val hidden: Boolean,
    val enabled: Boolean,
)

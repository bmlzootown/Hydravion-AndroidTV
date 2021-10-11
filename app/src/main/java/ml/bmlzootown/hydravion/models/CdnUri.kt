package ml.bmlzootown.hydravion.models

data class CdnUri (
    val cdn: String,
    val strategy: String,
    val resource: Resource
)

data class Resource (
    val uri: String,
    val data: Data
)

data class Data (
    val qualityLevels: List<QualityLevel>,
    val qualityLevelParams: Map<String, QualityLevelParam>,
    val token: String
)

data class QualityLevelParam (
    val token: String
)

data class QualityLevel (
    val name: String,
    val width: Long,
    val height: Long,
    val label: String,
    val order: Long
)
package ml.bmlzootown.hydravion.client

data class UserSync (
    val body : Body? = null,
    val statusCode : Int? = null
)

data class Body (
    val message : String? = null
)

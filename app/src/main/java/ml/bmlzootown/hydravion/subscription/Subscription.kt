package ml.bmlzootown.hydravion.subscription

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import ml.bmlzootown.hydravion.models.Plan

@Keep
class Subscription {

    @SerializedName("startDate")
    @Expose
    var startDate: String? = null

    @SerializedName("endDate")
    @Expose
    var endDate: String? = null

    @SerializedName("paymentID")
    @Expose
    var paymentID: Int? = null

    @SerializedName("interval")
    @Expose
    var interval: String? = null

    @SerializedName("paymentCancelled")
    @Expose
    var paymentCancelled: Boolean? = null

    @SerializedName("plan")
    @Expose
    var plan: Plan? = null

    @SerializedName("creator")
    @Expose
    var creator: String? = null

    @SerializedName("streamUrl")
    @Expose
    var streamUrl: String? = null
}
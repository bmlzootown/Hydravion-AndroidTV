package ml.bmlzootown.hydravion.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

@Keep
class Plan {

    @SerializedName("id")
    @Expose
    var id: String? = null

    @SerializedName("title")
    @Expose
    var title: String? = null

    @SerializedName("description")
    @Expose
    var description: String? = null

    @SerializedName("price")
    @Expose
    var price: String? = null

    @SerializedName("priceYearly")
    @Expose
    var priceYearly: Any? = null

    @SerializedName("currency")
    @Expose
    var currency: String? = null

    @SerializedName("logo")
    @Expose
    var logo: String? = null

    @SerializedName("interval")
    @Expose
    var interval: String? = null

    @SerializedName("featured")
    @Expose
    var featured: Boolean? = null

    @SerializedName("allowGrandfatheredAccess")
    @Expose
    var allowGrandfatheredAccess: Boolean? = null
}
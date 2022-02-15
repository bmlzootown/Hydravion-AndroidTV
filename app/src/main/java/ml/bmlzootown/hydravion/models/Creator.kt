package ml.bmlzootown.hydravion.models

import androidx.annotation.Keep
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
class Creator : Serializable {

    @SerializedName("id")
    @Expose
    var id: String = ""
}
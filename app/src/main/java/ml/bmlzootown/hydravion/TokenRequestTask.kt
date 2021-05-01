package ml.bmlzootown.hydravion

import android.content.Context
import com.android.volley.DefaultRetryPolicy
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley

class TokenRequestTask(context: Context) {

    private val volleyQueue: RequestQueue = Volley.newRequestQueue(context)

    fun doRequest(uri: String, callback: VolleyCallback) {
        object : StringRequest(
            Method.GET,
            uri,
            Response.Listener { response: String -> callback.onSuccess(response) },
            Response.ErrorListener { error: VolleyError ->
                error.printStackTrace()
                callback.onError(error)
            }) {}.apply {
            retryPolicy = DefaultRetryPolicy(0, 0, 0f)
            volleyQueue.add(this)
        }

    }

    interface VolleyCallback {

        fun onSuccess(response: String)
        fun onError(error: VolleyError)
    }

    companion object {

        const val URI_GENERATE = "https://www.bmlzoo.town/hydravion/generate?token="
        const val URI_AUTHENTICATE = "https://www.bmlzoo.town/hydravion/authenticate?token="
        const val URI_DISCONNECT = "https://www.bmlzoo.town/hydravion/disconnect?token="
    }
}
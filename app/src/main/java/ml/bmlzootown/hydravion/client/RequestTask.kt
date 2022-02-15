package ml.bmlzootown.hydravion.client

import android.content.Context
import com.android.volley.*
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import java.util.*

class RequestTask(context: Context) {

    private val volleyQueue: RequestQueue = Volley.newRequestQueue(context)

    fun getReponseStatus(uri: String?, callback: VolleyCallback) {
        var responseCode: Int = 0;
        val stringRequest: StringRequest = object : StringRequest(Method.GET, uri,
            Response.Listener { _: String? ->
                //Log.d("JSON", response);
                callback.onResponseCode(responseCode);
            }, Response.ErrorListener { error: VolleyError ->
                //error.printStackTrace()
                callback.onResponseCode(error.networkResponse.statusCode)
            }) {
            override fun parseNetworkResponse(response: NetworkResponse?): Response<String>? {
                responseCode = response?.statusCode ?: 0;
                return super.parseNetworkResponse(response);
            }
        }
        stringRequest.setShouldCache(false)
        volleyQueue.add(stringRequest)
    }

    fun sendRequest(uri: String?, cookies: String, callback: VolleyCallback) {
        val stringRequest: StringRequest = object : StringRequest(Method.GET, uri,
            Response.Listener { response: String? ->
                //Log.d("JSON", response);
                callback.onSuccess(response ?: "")
            }, Response.ErrorListener { error: VolleyError ->
                error.printStackTrace()
                callback.onError(error)
            }) {
            override fun getHeaders(): Map<String, String> {
                val params: MutableMap<String, String> = HashMap()
                params["Cookie"] = cookies
                params["Accept"] = "application/json"
                return params
            }
        }
        volleyQueue.add(stringRequest)
    }

    fun sendData(uri: String?, cookies: String, params: Map<String, String>?, callback: VolleyCallback) {
        val stringRequest: StringRequest = object : StringRequest(
            Method.POST,
            uri,
            Response.Listener { response: String? ->
                callback.onSuccess(response ?: "")
            }, Response.ErrorListener { error ->
                error.printStackTrace()
                callback.onError(error)
            }) {

            @Throws(AuthFailureError::class)
            override fun getParams(): Map<String, String>? = params

            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> =
                mapOf(
                    "Cookie" to cookies,
                    "Accept" to ACCEPT_JSON
                )
        }
        volleyQueue.add(stringRequest)
    }

    fun sendRequest(uri: String?, cookies: String, creatorGUID: String?, callback: VolleyCallback) {
        val stringRequest: StringRequest = object : StringRequest(
            Method.GET,
            uri,
            Response.Listener { response: String? ->
                callback.onSuccessCreator(response ?: "", creatorGUID ?: "")
            }, Response.ErrorListener { error: VolleyError ->
                error.printStackTrace()
                callback.onError(error)
            }) {

            override fun getHeaders(): Map<String, String> =
                mapOf(
                    "Cookie" to cookies,
                    "Accept" to ACCEPT_JSON
                )
        }
        volleyQueue.add(stringRequest)
    }

    interface VolleyCallback {
        fun onResponseCode(response: Int)
        fun onSuccess(response: String)
        fun onSuccessCreator(response: String, creatorGUID: String)
        fun onError(error: VolleyError)
    }

    companion object {

        private const val ACCEPT_JSON = "application/json"
    }
}
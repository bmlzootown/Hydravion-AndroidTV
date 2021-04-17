package ml.bmlzootown.hydravion.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.VolleyError
import com.google.gson.Gson
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.RequestTask
import ml.bmlzootown.hydravion.models.Video

class HydravionClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    private fun getCookiesString(): String =
        "${Constants.PREF_CFD_UID}=${mainPrefs.getString(Constants.PREF_CFD_UID, "")}; ${Constants.PREF_SAIL_SSID}=${mainPrefs.getString(Constants.PREF_SAIL_SSID, "")}"

    fun getVideos(creatorGUID: String, page: Int, callback: (Array<Video>) -> Unit) {
        RequestTask(context).sendRequest("$URI_VIDEOS?creatorGUID=$creatorGUID&fetchAfter=${(page - 1) * 20}", getCookiesString(), creatorGUID, object : RequestTask.VolleyCallback {

            override fun onSuccess(string: String?) = Unit

            override fun onSuccessCreator(response: String, creatorGUID: String) {
                callback(Gson().fromJson(response, Array<Video>::class.java))
            }

            override fun onError(error: VolleyError?) = Unit
        })
    }

    companion object {

        private const val URI_SUBSCRIPTIONS = "https://www.floatplane.com/api/user/subscriptions"
        private const val URI_VIDEOS = "https://www.floatplane.com/api/creator/videos"
        private var INSTANCE: HydravionClient? = null

        @Synchronized
        fun getInstance(context: Context, mainPrefs: SharedPreferences): HydravionClient {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = HydravionClient(context.applicationContext, mainPrefs)
                }
            }

            return INSTANCE!!
        }
    }
}
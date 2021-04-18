package ml.bmlzootown.hydravion.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.VolleyError
import com.google.gson.Gson
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.RequestTask
import ml.bmlzootown.hydravion.creator.Creator
import ml.bmlzootown.hydravion.models.Live
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.subscription.Subscription
import org.json.JSONArray

class HydravionClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    private val creatorIds: MutableMap<String, String> = hashMapOf()
    private val creatorCache: MutableMap<String, Creator> = hashMapOf()

    /**
     * Convenience fun to get cookies string
     * @return Cookies string
     */
    private fun getCookiesString(): String =
        "${Constants.PREF_CFD_UID}=${mainPrefs.getString(Constants.PREF_CFD_UID, "")}; ${Constants.PREF_SAIL_SSID}=${mainPrefs.getString(Constants.PREF_SAIL_SSID, "")}"

    fun getSubs(callback: (Array<Subscription>?) -> Unit) {
        RequestTask(context).sendRequest(URI_SUBSCRIPTIONS, getCookiesString(), object : RequestTask.VolleyCallback {

            override fun onSuccess(string: String?) {
                if (string == null || string.contains("errors")) {
                    callback(null)
                    return
                }

                Gson().fromJson(string, Array<Subscription>::class.java).let { subs ->
                    subs.forEach { sub ->
                        sub.creator?.let { creatorId ->
                            creatorIds[sub.plan?.title.toString()] = creatorId

                            if (creatorCache[creatorId] == null) {
                                cacheLogo(creatorId, null)
                            }
                        }
                    }
                    callback(subs)
                }
            }

            override fun onSuccessCreator(string: String?, creatorGUID: String?) = Unit

            override fun onError(error: VolleyError?) = callback(null)
        })
    }

    fun getVideos(creatorGUID: String, page: Int, callback: (Array<Video>) -> Unit) {
        RequestTask(context).sendRequest(
            "$URI_VIDEOS?creatorGUID=$creatorGUID&fetchAfter=${(page - 1) * 20}",
            getCookiesString(),
            creatorGUID,
            object : RequestTask.VolleyCallback {

                override fun onSuccess(string: String?) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) {
                    callback(Gson().fromJson(response, Array<Video>::class.java))
                }

                override fun onError(error: VolleyError?) = Unit
            })
    }

    fun getLive(creatorGUID: String, callback: (Live) -> Unit) {
        RequestTask(context).sendRequest("$URI_LIVE?type=live&creator=$creatorGUID", getCookiesString(), object : RequestTask.VolleyCallback {

            override fun onSuccess(string: String?) {
                callback(Gson().fromJson(string, Live::class.java))
            }

            override fun onSuccessCreator(string: String?, creatorGUID: String?) = Unit

            override fun onError(error: VolleyError?) = Unit
        })
    }

    fun getCreator(name: String, callback: (Creator) -> Unit) {
        // Check for existing logo, otherwise fetch it and then run the callback
        creatorCache[creatorIds[name]]?.let { callback(it) } ?: run {
            cacheLogo(creatorIds[name] ?: return@run, callback)
        }
    }

    private fun cacheLogo(creatorGUID: String, callback: ((Creator) -> Unit)?) {
        if (creatorCache[creatorGUID] != null) {
            // If the logo already is cached, no reason to retrieve it again
            return
        }

        RequestTask(context).sendRequest("$URI_CREATOR_INFO?creatorGUID=$creatorGUID", getCookiesString(), object : RequestTask.VolleyCallback {

            override fun onSuccess(response: String?) {
                try {
                    Log.e("ERROR?", "Creator response: $response")
                    JSONArray(response).getString(0)?.let {
                        Gson().fromJson(it, Creator::class.java).let { creator ->
                            creatorCache[creatorGUID] = creator
                            callback?.invoke(creator)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onSuccessCreator(string: String?, creatorGUID: String?) = Unit

            override fun onError(error: VolleyError?) = Unit
        })
    }

    companion object {

        private const val URI_SUBSCRIPTIONS = "https://www.floatplane.com/api/user/subscriptions"
        private const val URI_CREATOR_INFO = "https://www.floatplane.com/api/creator/info"
        private const val URI_VIDEOS = "https://www.floatplane.com/api/creator/videos"
        private const val URI_LIVE = "https://www.floatplane.com/api/cdn/delivery"
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
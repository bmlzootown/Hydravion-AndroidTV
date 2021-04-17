package ml.bmlzootown.hydravion.client

import android.content.Context
import android.content.SharedPreferences
import com.android.volley.VolleyError
import com.google.gson.Gson
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.RequestTask
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.subscription.Subscription
import org.json.JSONArray

class HydravionClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    private val creatorLogos: MutableMap<String, String> = hashMapOf()

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
                            if (sub.plan?.logo == null) {
                                cacheLogo(creatorId, null)
                            } else {
                                creatorLogos[creatorId] = sub.plan?.logo ?: ""
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

    fun getCreatorLogo(creatorGUID: String, callback: (String) -> Unit) {
        // Check for existing logo, otherwise fetch it and then run the callback
        creatorLogos[creatorGUID]?.let { callback(it) } ?: run {
            cacheLogo(creatorGUID, callback)
        }
    }

    private fun cacheLogo(creatorGUID: String, callback: ((String) -> Unit)?) {
        if (creatorLogos[creatorGUID] != null) {
            // If the logo already is cached, no reason to retrieve it again
            return
        }

        RequestTask(context).sendRequest("$URI_CREATOR_INFO?creatorGUID=$creatorGUID", getCookiesString(), object : RequestTask.VolleyCallback {

            override fun onSuccess(response: String?) {
                try {
                    JSONArray(response).getJSONObject(0)?.getJSONObject("icon")?.getString("path")?.let { creatorPath ->
                        creatorLogos[creatorGUID] = creatorPath
                        callback?.invoke(creatorPath)
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
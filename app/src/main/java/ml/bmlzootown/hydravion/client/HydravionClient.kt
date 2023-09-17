package ml.bmlzootown.hydravion.client

import android.content.Context
import android.content.SharedPreferences
import com.android.volley.VolleyError
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import ml.bmlzootown.hydravion.BuildConfig
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.browse.MainFragment
import ml.bmlzootown.hydravion.creator.Creator
import ml.bmlzootown.hydravion.creator.FloatplaneLiveStream
import ml.bmlzootown.hydravion.github.Release
import ml.bmlzootown.hydravion.models.*
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.post.Post
import ml.bmlzootown.hydravion.subscription.Subscription
import org.json.JSONArray
import org.json.JSONObject

class HydravionClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    private val creatorIds: MutableMap<String, String> = hashMapOf()
    private val creatorCache: MutableMap<String, Creator> = hashMapOf()
    private val requestTask: RequestTask = RequestTask(context)

    /**
     * Convenience fun to get cookies string
     * @return Cookies string
     */
    private fun getCookiesString(): String =
        "${Constants.PREF_SAIL_SSID}=${mainPrefs.getString(Constants.PREF_SAIL_SSID, "")}"

    fun getSubs(callback: (Array<Subscription>?) -> Unit) {
        requestTask.sendRequest(URI_SUBSCRIPTIONS, getCookiesString(), object : RequestTask.VolleyCallback {

            override fun onResponseCode(response: Int) {
                //Ignore
            }

            override fun onSuccess(response: String) {
                if (BuildConfig.DEBUG) {
                    MainFragment.dLog(TAG, "getSubs: $response")
                }

                if (response.contains("errors")) {
                    callback(null)
                    return
                }

                Gson().fromJson(response, Array<Subscription>::class.java).let { subs ->
                    subs.forEach { sub ->
                        sub.creator?.let { creatorId ->
                            creatorIds[sub.plan?.title.toString()] = creatorId

                            if (creatorCache[creatorId] == null) {
                                cacheLogo(creatorId, null)
                            }

                            getCreatorInfo(creatorId) {
                                sub.streamInfo = it
                            }
                        }
                    }
                    callback(subs)
                }
            }

            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

            override fun onError(error: VolleyError) = callback(null)
        })
    }

    fun getCreatorInfo(creatorGUID: String, callback: (FloatplaneLiveStream) -> Unit) {
        requestTask.sendRequest(
            "$URI_CREATOR_INFO?creatorGUID=$creatorGUID",
            getCookiesString(),
            object : RequestTask.VolleyCallback {
                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG,"getCreatorInfo: $response")
                    }

                    try {
                        JSONArray(response).getString(0)?.let {
                            Gson().fromJson(it, Creator::class.java).let { creator ->
                                creator.lastLiveStream?.let { it1 -> callback.invoke(it1) }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getVideos(creatorGUID: String, page: Int, callback: (Array<Video>) -> Unit) {
        requestTask.sendRequest(
            "$URI_VIDEOS?id=$creatorGUID&fetchAfter=${(page - 1) * 20}",
            getCookiesString(),
            creatorGUID,
            object : RequestTask.VolleyCallback {

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccess(response: String) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG, "getVideos: $response")
                    }

                    callback(Gson().fromJson(response, Array<Video>::class.java))
                }

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getVideo(video: Video, res: String, callback: (Video) -> Unit) {
        //val y = Util.getCurrentDisplayModeSize(context).y;
        requestTask.sendRequest(
            "$URI_DELIVERY?scenario=onDemand&entityId=${video.getVideoId()}",
            getCookiesString(),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG, "getVideo: $response")
                    }

                    val delivery = Gson().fromJson(response, Delivery::class.java)
                    //val resolution = if (res != "2160") res else "4K"
                    val cdn = delivery.groups.get(0).origins.get(0).url
                    var uri = ""
                    val variants = (delivery.groups.get(0).variants).sortedWith(
                        compareByDescending<Variant> {
                            when (it.name) {
                                "2160-avc1" -> 5
                                "1080-avc1" -> 4
                                "720-avc1" -> 3
                                "480-avc1" -> 2
                                "360-avc1" -> 1
                                else -> 0
                            }
                        }
                    )

                    for(variant in variants) {
                        MainFragment.dLog("VARIANT", variant.toString())
                        if (!variant.enabled) {
                            MainFragment.dLog("VARIANT", variant.label + " DISABLED")
                            continue
                        } else {
                            uri = variant.url
                            break
                        }
                    }
                    video.vidUrl = cdn + uri
                    MainFragment.dLog(TAG, "Video: $video")
                    callback(video)
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }


    fun getVideoObject(id: String, callback: (Video) -> Unit) {
        requestTask.sendRequest(
            "$URI_VIDEO_OBJECT?id=$id",
            getCookiesString(),
            object : RequestTask.VolleyCallback {
                override fun onSuccess(response: String) {
                    try {
                        callback(Gson().fromJson(response, Video::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getVideoInfo(videoID: String, callback: (VideoInfo) -> Unit) {
        requestTask.sendRequest(
            "$URI_VIDEO_INFO?id=$videoID",
            getCookiesString(),
            object : RequestTask.VolleyCallback {


                override fun onSuccess(response: String) {
                    try {
                        callback(Gson().fromJson(response, VideoInfo::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getLive(sub: Subscription, callback: (Delivery) -> Unit) {
        requestTask.sendRequest(
            "$URI_CREATOR?id=${sub.creator}",
            getCookiesString(),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    val c: Creator = Gson().fromJson(response, Creator::class.java)
                    c.lastLiveStream?.let {
                        getLive(it.id) {
                            callback(it)
                        }
                    }
                    //callback(Gson().fromJson(response, Creator::class.java))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getLive(livestreamID: String, callback: (Delivery) -> Unit) {
        requestTask.sendRequest(
            "$URI_DELIVERY?scenario=live&entityId=$livestreamID",
            getCookiesString(),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(Gson().fromJson(response, Delivery::class.java))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    /*fun getLive(creatorGUID: String, callback: (Live) -> Unit) {
        requestTask.sendRequest(
            "$URI_LIVE?type=live&creator=$creatorGUID",
            getCookiesString(),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(Gson().fromJson(response, Live::class.java))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }*/

    fun checkLive(streamUri: String, callback: (Int) -> Unit) {
        requestTask.getReponseStatus(streamUri, object : RequestTask.VolleyCallback {
            override fun onResponseCode(response: Int) {
                callback(response)
            }

            override fun onSuccess(response: String) = Unit

            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

            override fun onError(error: VolleyError) = Unit

        })
    }

    fun getCreatorByName(name: String, callback: (Creator) -> Unit) {
        getCreatorById(creatorIds[name] ?: "", callback)
    }

    fun getCreatorById(id: String, callback: (Creator) -> Unit) {
        if (id.isNotEmpty()) {
            // Check for existing logo, otherwise fetch it and then run the callback
            creatorCache[id]?.let { callback(it) } ?: run {
                cacheLogo(id, callback)
            }
        }
    }

    private fun cacheLogo(creatorGUID: String, callback: ((Creator) -> Unit)?) {
        if (creatorCache[creatorGUID] != null) {
            // If the logo already is cached, no reason to retrieve it again
            return
        }

        requestTask.sendRequest(
            "$URI_CREATOR_INFO?creatorGUID=$creatorGUID",
            getCookiesString(),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
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

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getPost(postId: String, callback: (Post) -> Unit) {
        requestTask.sendRequest(
            "$URI_POST?id=$postId",
            getCookiesString(),
            object : RequestTask.VolleyCallback {


                override fun onSuccess(response: String) {
                    try {
                        callback(Gson().fromJson(response, Post::class.java))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            });
    }

    fun getLatest(callback: (String) -> Unit) {
        requestTask.sendRequest(LATEST, "", object : RequestTask.VolleyCallback {
            override fun onResponseCode(response: Int) = Unit

            override fun onSuccess(response: String) {

                try {
                    callback(Gson().fromJson(response, Release::class.java).tag_name)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

            override fun onError(error: VolleyError) = Unit

        });
    }

    fun toggleLikePost(postId: String, callback: (Boolean) -> Unit) {
        requestTask.sendData(
            URI_LIKE,
            getCookiesString(),
            mapOf("id" to postId, "contentType" to "blogPost"),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(response.contains("like"))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun toggleDislikePost(postId: String, callback: (Boolean) -> Unit) {
        requestTask.sendData(
            URI_DISLIKE,
            getCookiesString(),
            mapOf("id" to postId, "contentType" to "blogPost"),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(response.contains("dislike"))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getVideoProgress(blogPostIds: List<String>, callback: (List<VideoProgress>) -> Unit) {

        val body = JSONObject().let { json ->
            json.put("ids", JSONArray(blogPostIds))
            json.put("contentType", "blogPost")
            json
        }.toString()
        requestTask.sendDataWithBody(
            URI_GET_PROGRESS,
            getCookiesString(),
            body,
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
                        val type = (object : TypeToken<List<VideoProgress>>() {}).getType()
                        callback(Gson().fromJson(response, type))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(ArrayList())
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) {
                    callback(ArrayList())
                }
            }
        )
    }

    fun setVideoProgress(videoId: String, progressInPercent: Int) {
        requestTask.sendData(
            URI_UPDATE_PROGRESS,
            getCookiesString(),
            mapOf("id" to videoId, "contentType" to "video", "progress" to progressInPercent.toString()),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) = Unit

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            }
        )
    }

    companion object {

        private const val TAG = "HydravionClient"
        private const val SITE = "https://www.floatplane.com"

        // TODO Update to v3 API
        private const val URI_SUBSCRIPTIONS = "$SITE/api/user/subscriptions"
        private const val URI_CREATOR_INFO = "$SITE/api/creator/info"

        // Already updated!
        private const val URI_DELIVERY = "$SITE/api/v3/delivery/info"
        private const val URI_CREATOR = "$SITE/api/v3/creator/info"
        private const val URI_VIDEOS = "$SITE/api/v3/content/creator"
        private const val URI_VIDEO_OBJECT = "$SITE/api/v3/content/info"
        private const val URI_VIDEO_INFO = "$SITE/api/v3/content/video"
        private const val URI_POST = "$SITE/api/v3/content/post"
        private const val URI_LIKE = "$SITE/api/v3/content/like"
        private const val URI_DISLIKE = "$SITE/api/v3/content/dislike"
        private const val URI_GET_PROGRESS = "$SITE/api/v3/content/get/progress"
        private const val URI_UPDATE_PROGRESS = "$SITE/api/v3/content/progress"

        private const val LATEST = "https://api.github.com/repos/bmlzootown/Hydravion-AndroidTV/releases/latest"
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
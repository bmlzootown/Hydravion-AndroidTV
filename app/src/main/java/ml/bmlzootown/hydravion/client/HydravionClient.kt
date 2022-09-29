package ml.bmlzootown.hydravion.client

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.volley.VolleyError
import com.google.gson.Gson
import ml.bmlzootown.hydravion.BuildConfig
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.creator.Creator
import ml.bmlzootown.hydravion.creator.FloatplaneLiveStream
import ml.bmlzootown.hydravion.github.Release
import ml.bmlzootown.hydravion.models.*
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.post.Post
import ml.bmlzootown.hydravion.subscription.Subscription
import org.json.JSONArray
import java.util.regex.Pattern

class HydravionClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    private val creatorIds: MutableMap<String, String> = hashMapOf()
    private val creatorCache: MutableMap<String, Creator> = hashMapOf()

    /**
     * Convenience fun to get cookies string
     * @return Cookies string
     */
    private fun getCookiesString(): String =
        //"${Constants.PREF_CFD_UID}=${mainPrefs.getString(Constants.PREF_CFD_UID, "")}; ${Constants.PREF_SAIL_SSID}=${mainPrefs.getString(Constants.PREF_SAIL_SSID, "")}"
        "${Constants.PREF_SAIL_SSID}=${mainPrefs.getString(Constants.PREF_SAIL_SSID, "")}"

    fun getSubs(callback: (Array<Subscription>?) -> Unit) {
        RequestTask(context).sendRequest(URI_SUBSCRIPTIONS, getCookiesString(), object : RequestTask.VolleyCallback {

            override fun onResponseCode(response: Int) {
                //Ignore
            }

            override fun onSuccess(response: String) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "getSubs: $response")
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
        RequestTask(context).sendRequest(
            "$URI_CREATOR_INFO?creatorGUID=$creatorGUID",
            getCookiesString(),
            object : RequestTask.VolleyCallback {
                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "getCreatorInfo: $response")
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
        RequestTask(context).sendRequest(
            "$URI_VIDEOS?id=$creatorGUID&fetchAfter=${(page - 1) * 20}",
            getCookiesString(),
            creatorGUID,
            object : RequestTask.VolleyCallback {

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccess(response: String) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "getVideos: $response")
                    }

                    callback(Gson().fromJson(response, Array<Video>::class.java))
                }

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getVideo(video: Video, res: String, callback: (Video) -> Unit) {
        //val y = Util.getCurrentDisplayModeSize(context).y;
        RequestTask(context).sendRequest(
            "$URI_LIVE?type=vod&guid=${video.getVideoId()}",
            getCookiesString(),
            object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "getVideo: $response")
                    }

                    val cdnUri = Gson().fromJson(response, CdnUri::class.java)
                    val uri = cdnUri.cdn + cdnUri.resource.uri

                    // replace {qualityLevels}
                    val p = Pattern.compile("(?<=\\/)(\\{qualityLevelParams\\.2\\})")
                    val m = p.matcher(uri)
                    val newUrl = m.replaceAll("$res.mp4")

                    // replace {qualityLevelParams.token}
                    val p2 = Pattern.compile("(\\{qualityLevelParams.4\\})")
                    val m2 = p2.matcher(newUrl)
                    val ql = cdnUri.resource.data.qualityLevels.find { it.label.contains(res)}
                    val newUrl2 = m2.replaceAll(cdnUri.resource.data.qualityLevelParams.get(ql?.name)?.token.toString())

                    video.vidUrl = newUrl2
                    Log.d(TAG, "Video: $video")
                    callback(video)
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
    }

    fun getVideoObject(id: String, callback: (Video) -> Unit) {
        RequestTask(context).sendRequest(
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
        RequestTask(context).sendRequest(
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

    fun getLive(creatorGUID: String, callback: (Live) -> Unit) {
        RequestTask(context).sendRequest(
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
    }

    fun checkLive(streamUri: String, callback: (Int) -> Unit) {
        RequestTask(context).getReponseStatus(streamUri, object : RequestTask.VolleyCallback {
            override fun onResponseCode(response: Int) {
                callback(response)
            }

            override fun onSuccess(response: String) = Unit

            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

            override fun onError(error: VolleyError) = Unit

        })
    }

    fun getCdnServers(callback: (Array<String>) -> Unit) {
        RequestTask(context).sendRequest(URI_CDNS, getCookiesString(), object : RequestTask.VolleyCallback {

            override fun onSuccess(response: String) {
                Gson().fromJson(response, Edges::class.java)?.let { edges ->
                    callback(
                        edges.edges.filter { edge ->
                            edge.allowStreaming // filter only those that allow streaming
                        }.map { liveEdge ->
                            liveEdge.hostname // map list of streaming edge to hostname
                        }.toTypedArray() // convert to array and send to callback
                    )
                }
            }

            override fun onResponseCode(response: Int) = Unit

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

        RequestTask(context).sendRequest(
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
        RequestTask(context).sendRequest(
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
        RequestTask(context).sendRequest(LATEST, "", object : RequestTask.VolleyCallback {
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
        RequestTask(context).sendData(
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
        RequestTask(context).sendData(
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

    companion object {

        private const val TAG = "HydravionClient"
        // TODO Update to v3 API
        private const val URI_SUBSCRIPTIONS = "https://www.floatplane.com/api/user/subscriptions"
        private const val URI_CREATOR_INFO = "https://www.floatplane.com/api/creator/info"
        private const val URI_LIVE = "https://www.floatplane.com/api/cdn/delivery"
        private const val URI_CDNS = "https://www.floatplane.com/api/edges"
        // Already updated!
        private const val URI_VIDEOS = "https://www.floatplane.com/api/v3/content/creator"
        private const val URI_VIDEO_OBJECT = "https://www.floatplane.com/api/v3/content/info"
        private const val URI_VIDEO_INFO = "https://www.floatplane.com/api/v3/content/video"
        private const val URI_POST = "https://www.floatplane.com/api/v3/content/post"
        private const val URI_LIKE = "https://www.floatplane.com/api/v3/content/like"
        private const val URI_DISLIKE = "https://www.floatplane.com/api/v3/content/dislike"

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
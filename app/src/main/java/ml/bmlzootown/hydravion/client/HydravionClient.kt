package ml.bmlzootown.hydravion.client

import android.content.Context
import android.content.SharedPreferences
import com.android.volley.VolleyError
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import ml.bmlzootown.hydravion.BuildConfig
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.browse.MainFragment
import ml.bmlzootown.hydravion.authenticate.AuthManager
import ml.bmlzootown.hydravion.creator.Creator
import ml.bmlzootown.hydravion.creator.FloatplaneLiveStream
import ml.bmlzootown.hydravion.github.Release
import ml.bmlzootown.hydravion.models.*
import ml.bmlzootown.hydravion.models.Video
import ml.bmlzootown.hydravion.models.Channel
import ml.bmlzootown.hydravion.post.Post
import ml.bmlzootown.hydravion.playback.DevicePlaybackCompat
import ml.bmlzootown.hydravion.subscription.Subscription
import org.json.JSONArray
import org.json.JSONObject

class HydravionClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    private val creatorIds: MutableMap<String, String> = hashMapOf()
    private val creatorCache: MutableMap<String, Creator> = hashMapOf()
    private val requestTask: RequestTask = RequestTask(context)
    private val authManager: AuthManager = AuthManager.getInstance(context)

    fun getSubs(callback: (Array<Subscription>?) -> Unit) {
        getSubs(callback, null)
    }

    /**
     * @param onAuthFailure invoked only when tokens are gone / permanently invalid
     *                      (caller should prompt re-login). Network and API errors
     *                      still return null via [callback] without clearing credentials.
     */
    fun getSubs(callback: (Array<Subscription>?) -> Unit, onAuthFailure: (() -> Unit)?) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(URI_SUBSCRIPTIONS, token, object : RequestTask.VolleyCallback {

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

            override fun onError(error: VolleyError) {
                val status = error.networkResponse?.statusCode
                if (status == 401 || status == 403) {
                    // Access token rejected by API — drop cache and try one refresh+retry.
                    MainFragment.dLog(TAG, "getSubs got $status; invalidating cache and retrying once")
                    authManager.invalidateCache()
                    authManager.withValidAccessToken({ freshToken ->
                        requestTask.sendRequest(URI_SUBSCRIPTIONS, freshToken, object : RequestTask.VolleyCallback {
                            override fun onResponseCode(response: Int) = Unit
                            override fun onSuccess(response: String) {
                                if (response.contains("errors")) {
                                    callback(null)
                                    return
                                }
                                try {
                                    callback(Gson().fromJson(response, Array<Subscription>::class.java))
                                } catch (e: Exception) {
                                    callback(null)
                                }
                            }
                            override fun onSuccessCreator(response: String, creatorGUID: String) = Unit
                            override fun onError(retryError: VolleyError) {
                                val retryStatus = retryError.networkResponse?.statusCode
                                if (retryStatus == 401 || retryStatus == 403) {
                                    // Still unauthorized after refresh — only prompt re-login if
                                    // credentials were actually cleared.
                                    if (!authManager.hasRefreshToken()) {
                                        onAuthFailure?.invoke() ?: callback(null)
                                    } else {
                                        callback(null)
                                    }
                                } else {
                                    callback(null)
                                }
                            }
                        })
                    }, {
                        if (!authManager.hasRefreshToken() && onAuthFailure != null) {
                            onAuthFailure.invoke()
                        } else {
                            callback(null)
                        }
                    })
                } else {
                    callback(null)
                }
            }
        })
        }, {
            // Refresh failed. Only treat as auth failure if credentials were wiped.
            if (!authManager.hasRefreshToken() && onAuthFailure != null) {
                onAuthFailure.invoke()
            } else {
                callback(null)
            }
        })
    }

    fun getCreatorInfo(creatorGUID: String, callback: (FloatplaneLiveStream) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_CREATOR_INFO?id=$creatorGUID",
                token,
                object : RequestTask.VolleyCallback {
                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG,"getCreatorInfo: $response")
                    }

                    try {
                        // v3 API returns a single object, not an array
                        Gson().fromJson(response, Creator::class.java).let { creator ->
                            creator.lastLiveStream?.let { it1 -> callback.invoke(it1) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op on failure
        })
    }

    /**
     * Fetch a page of a creator's videos, optionally filtered to a single channel.
     * Invokes [callback] with null on failure (so callers can distinguish
     * "request failed, retry later" from "no more videos").
     */
    fun getVideos(creatorGUID: String, channelId: String?, fetchAfter: Int, callback: (Array<Video>?) -> Unit) {
        authManager.withValidAccessToken({ token ->
            val channelParam = if (channelId != null) "&channel=$channelId" else ""
            requestTask.sendRequest(
                "$URI_VIDEOS?id=$creatorGUID$channelParam&fetchAfter=$fetchAfter",
                token,
                creatorGUID,
                object : RequestTask.VolleyCallback {

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccess(response: String) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG, "getVideos: $response")
                    }

                    try {
                        callback(Gson().fromJson(response, Array<Video>::class.java))
                    } catch (e: Exception) {
                        MainFragment.dError(TAG, "Error parsing videos: ${e.message}")
                        callback(null)
                    }
                }

                override fun onError(error: VolleyError) {
                    MainFragment.dError(TAG, "Error fetching videos: ${error.message}")
                    callback(null)
                }
            })
        }, {
            callback(null)
        })
    }

    fun getChannels(creatorGUID: String, callback: (Array<Channel>) -> Unit) {
        getChannelsForCreators(listOf(creatorGUID)) { grouped ->
            callback(grouped[creatorGUID]?.toTypedArray() ?: emptyArray())
        }
    }

    /**
     * Batch channel lookup for all subscribed creators in a single request.
     * See `/api/v3/creator/channels/list` (ids[] supports multiple creators).
     */
    fun getChannelsForCreators(
        creatorIds: Collection<String>,
        callback: (Map<String, List<Channel>>) -> Unit
    ) {
        val uniqueIds = creatorIds.filter { it.isNotEmpty() }.distinct()
        if (uniqueIds.isEmpty()) {
            callback(emptyMap())
            return
        }

        val query = uniqueIds.joinToString("&") { "ids=$it" }
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_CHANNELS?$query",
                token,
                object : RequestTask.VolleyCallback {

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccess(response: String) {
                    if (BuildConfig.DEBUG) {
                        MainFragment.dLog(TAG, "getChannelsForCreators: $response")
                    }

                    try {
                        val channels = Gson().fromJson(response, Array<Channel>::class.java)
                        callback(channels.groupBy { it.creator })
                    } catch (e: Exception) {
                        MainFragment.dError(TAG, "Error parsing channels: ${e.message}")
                        callback(emptyMap())
                    }
                }

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) {
                    MainFragment.dError(TAG, "Error fetching channels: ${error.message}")
                    callback(emptyMap())
                }
            })
        }, {
            callback(emptyMap())
        })
    }

    fun getVideo(video: Video, res: String, callback: (Video) -> Unit) {
        //val y = Util.getCurrentDisplayModeSize(context).y;
        authManager.withValidAccessToken({ token ->
            // Get output format preference
            val outputFormat = mainPrefs.getString(Constants.PREF_OUTPUT_FORMAT, Constants.OUTPUT_FORMAT_DEFAULT)
            val outputKindParam = "&outputKind=$outputFormat"
            val deliveryUrl = "$URI_DELIVERY?scenario=onDemand&entityId=${video.getVideoId()}$outputKindParam"
            
            MainFragment.dLog(TAG, "Requesting delivery with format: $outputFormat, URL: $deliveryUrl")
            
            requestTask.sendRequest(
                deliveryUrl,
                token,
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
        }, {
            // no-op
        })
    }


    fun getVideoObject(id: String, callback: (Video) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_POST?id=$id",
                token,
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
        }, {
            // no-op
        })
    }

    fun getVideoInfo(videoID: String, callback: (VideoInfo) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_VIDEO_INFO?id=$videoID",
                token,
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
        }, {
            // no-op
        })
    }

    fun getLive(sub: Subscription, callback: (Delivery) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_CREATOR?id=${sub.creator}",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    val c: Creator = Gson().fromJson(response, Creator::class.java)
                    c.lastLiveStream?.let { liveStream ->
                        // Ensure stream metadata is available before addLiveCard runs
                        // (getSubs fills this async and can race with live checks).
                        sub.streamInfo = liveStream
                        getLive(liveStream.id) {
                            callback(it)
                        }
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op
        })
    }

    fun getLive(livestreamID: String, callback: (Delivery) -> Unit) {
        requestLiveDelivery(livestreamID, DevicePlaybackCompat.preferredLiveOutputKind(), callback)
    }

    private fun requestLiveDelivery(
        livestreamID: String,
        outputKind: String,
        callback: (Delivery) -> Unit,
    ) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_DELIVERY?scenario=live&entityId=$livestreamID&outputKind=$outputKind",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    MainFragment.dLog(TAG, "getLive delivery (outputKind=$outputKind): $response")
                    val delivery = Gson().fromJson(response, Delivery::class.java)
                    if (delivery.groups.isNullOrEmpty()
                        && outputKind != Constants.OUTPUT_FORMAT_HLS_MPEGTS
                    ) {
                        MainFragment.dLog(
                            TAG,
                            "Live delivery empty for $outputKind; falling back to ${Constants.OUTPUT_FORMAT_HLS_MPEGTS}"
                        )
                        requestLiveDelivery(livestreamID, Constants.OUTPUT_FORMAT_HLS_MPEGTS, callback)
                        return
                    }
                    callback(delivery)
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op
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

        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_CREATOR_INFO?id=$creatorGUID",
                token,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
                        // v3 API returns a single object, not an array
                        Gson().fromJson(response, Creator::class.java).let { creator ->
                            creatorCache[creatorGUID] = creator
                            callback?.invoke(creator)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            // no-op
        })
    }

    fun getPost(postId: String, callback: (Post) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendRequest(
                "$URI_POST?id=$postId",
                token,
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
            })
        }, {
            // no-op
        })
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
        authManager.withValidAccessToken({ token ->
            requestTask.sendData(
                URI_LIKE,
                token,
                mapOf("id" to postId, "contentType" to "blogPost"),
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(response.contains("like"))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            callback(false)
        })
    }

    fun toggleDislikePost(postId: String, callback: (Boolean) -> Unit) {
        authManager.withValidAccessToken({ token ->
            requestTask.sendData(
                URI_DISLIKE,
                token,
                mapOf("id" to postId, "contentType" to "blogPost"),
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    callback(response.contains("dislike"))
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) = Unit
            })
        }, {
            callback(false)
        })
    }

    fun getVideoProgress(blogPostIds: List<String>, callback: (List<VideoProgress>) -> Unit) {
        val ids = blogPostIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (ids.isEmpty()) {
            callback(emptyList())
            return
        }

        val results = mutableListOf<VideoProgress>()
        fetchProgressBatch(ids, 0, PROGRESS_BATCH_SIZE, results, callback)
    }

    private fun fetchProgressBatch(
        allIds: List<String>,
        offset: Int,
        batchSize: Int,
        accumulated: MutableList<VideoProgress>,
        callback: (List<VideoProgress>) -> Unit
    ) {
        if (offset >= allIds.size) {
            callback(accumulated)
            return
        }

        val chunk = allIds.subList(offset, minOf(offset + batchSize, allIds.size))
        val body = JSONObject().apply {
            put("ids", JSONArray(chunk))
            put("contentType", "blogPost")
        }.toString()
        authManager.withValidAccessToken({ token ->
            requestTask.sendDataWithBody(
                URI_GET_PROGRESS,
                token,
                body,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) {
                    try {
                        val type = (object : TypeToken<List<VideoProgress>>() {}).type
                        val batch = Gson().fromJson<List<VideoProgress>>(response, type) ?: emptyList()
                        accumulated.addAll(batch)
                    } catch (e: Exception) {
                        MainFragment.dError(TAG, "Error parsing progress batch: ${e.message}")
                    }
                    fetchProgressBatch(allIds, offset + batchSize, batchSize, accumulated, callback)
                }

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) {
                    MainFragment.dError(TAG, "Progress batch failed (${chunk.size} ids): ${error.message}")
                    fetchProgressBatch(allIds, offset + batchSize, batchSize, accumulated, callback)
                }
            })
        }, {
            callback(accumulated)
        })
    }

    fun setVideoProgress(videoId: String, progressSeconds: Int) {
        if (videoId.isBlank() || progressSeconds < 0) {
            return
        }
        val body = JSONObject().apply {
            put("id", videoId)
            put("contentType", "video")
            put("progress", progressSeconds)
        }.toString()
        authManager.withValidAccessToken({ token ->
            requestTask.sendDataWithBody(
                URI_UPDATE_PROGRESS,
                token,
                body,
                object : RequestTask.VolleyCallback {

                override fun onSuccess(response: String) = Unit

                override fun onResponseCode(response: Int) = Unit

                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit

                override fun onError(error: VolleyError) {
                    MainFragment.dError(TAG, "Failed to save progress: ${error.message}")
                }
            })
        }, {
            // ignore
        })
    }

    companion object {

        private const val TAG = "HydravionClient"
        private const val SITE = "https://www.floatplane.com"

        // Updated to v3 API
        private const val URI_SUBSCRIPTIONS = "$SITE/api/v3/user/subscriptions"
        private const val URI_CREATOR_INFO = "$SITE/api/v3/creator/info"

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
        private const val URI_CHANNELS = "$SITE/api/v3/creator/channels/list"
        private const val PROGRESS_BATCH_SIZE = 25

        private const val LATEST = "https://api.github.com/repos/bmlzootown/Hydravion-AndroidTV/releases/latest"
        private var INSTANCE: HydravionClient? = null

        // Always resolve the shared prefs file internally so the singleton can never be
        // bound to a per-activity prefs file by its first caller (caused login loops
        // when the task was restored into DetailsActivity after process death).
        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): HydravionClient {
            if (INSTANCE == null) {
                val appContext = context.applicationContext
                INSTANCE = HydravionClient(
                    appContext,
                    appContext.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)
                )
            }

            return INSTANCE!!
        }

    }
}
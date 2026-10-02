package ml.bmlzootown.hydravion.poll

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import io.socket.engineio.client.transports.WebSocket
import ml.bmlzootown.hydravion.BuildConfig
import ml.bmlzootown.hydravion.authenticate.AuthManager
import ml.bmlzootown.hydravion.browse.MainFragment
import ml.bmlzootown.hydravion.chat.SailsSessionHelper
import ml.bmlzootown.hydravion.client.RequestTask
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Floatplane live-poll client.
 *
 * Polls ride the www.floatplane.com Sails socket (not chat.floatplane.com):
 * join via `/api/v3/poll/tk/live/joinroom`, then listen for
 * `pollOpen` / `pollClose` / `pollUpdateTally`. Voting is a normal REST POST.
 */
class LivePollClient private constructor(private val context: Context) {

    interface Listener {
        fun onPollsChanged(polls: List<PollInfo>)
        fun onError(message: String)
    }

    private val authManager = AuthManager.getInstance(context)
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val pollsById = ConcurrentHashMap<String, PollInfo>()

    private var socket: Socket? = null
    private var creatorId: String? = null
    private var listener: Listener? = null

    fun connect(creatorId: String, listener: Listener) {
        disconnect()
        this.listener = listener
        this.creatorId = creatorId

        authManager.withValidAccessToken({ token ->
            ioExecutor.execute {
                val userAgent = "Hydravion (AndroidTV ${BuildConfig.VERSION_NAME})"
                val sid = SailsSessionHelper.resolveSailsSid(context, token, userAgent)
                mainHandler.post {
                    openSocket(token, sid, userAgent, creatorId, listener)
                }
            }
        }, {
            post { listener.onError("Session expired — polls unavailable") }
        })
    }

    private fun openSocket(
        token: String,
        sid: String?,
        userAgent: String,
        creatorId: String,
        listener: Listener
    ) {
        try {
            val okHttpClient = OkHttpClient.Builder().build()
            IO.setDefaultOkHttpWebSocketFactory(okHttpClient)
            IO.setDefaultOkHttpCallFactory(okHttpClient)

            val opts = IO.Options().apply {
                query = SAILS_QUERY
                transports = arrayOf(WebSocket.NAME)
                forceNew = true
                reconnection = true
                callFactory = okHttpClient
                webSocketFactory = okHttpClient
            }

            val sock = IO.socket(URI.create(WWW_URI), opts)
            socket = sock

            sock.io().on(Manager.EVENT_TRANSPORT) { args ->
                val transport = args[0] as Transport
                transport.on(Transport.EVENT_REQUEST_HEADERS) { headerArgs ->
                    @Suppress("UNCHECKED_CAST")
                    val headers = headerArgs[0] as MutableMap<String, List<String>>
                    val currentToken = authManager.getAccessToken()
                    headers["Origin"] = listOf("https://www.floatplane.com")
                    headers["Authorization"] = listOf("Bearer $currentToken")
                    headers["User-Agent"] = listOf(userAgent)
                    if (!sid.isNullOrEmpty()) {
                        headers["Cookie"] = listOf("sails.sid=$sid")
                    }
                }
            }

            sock.on(Socket.EVENT_CONNECT) {
                MainFragment.dLog(TAG, "Connected to www poll socket")
                joinRoom(sock, token, creatorId)
            }
            sock.on(Socket.EVENT_DISCONNECT) {
                MainFragment.dLog(TAG, "Disconnected from www poll socket")
            }
            sock.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val err = args.firstOrNull()?.toString() ?: "unknown"
                MainFragment.dError(TAG, "Poll socket error: $err")
                post { listener.onError("Poll connection failed") }
            }

            sock.on(EVENT_POLL_OPEN) { args ->
                handlePollPayload(args, creatorId, opened = true)
            }
            sock.on(EVENT_POLL_CLOSE) { args ->
                handlePollPayload(args, creatorId, opened = false)
            }
            sock.on(EVENT_POLL_TALLY) { args ->
                val raw = args.firstOrNull()?.toString() ?: return@on
                MainFragment.dLog(TAG, "pollUpdateTally: ${raw.take(200)}")
                val event = runCatching { gson.fromJson(raw, PollTallyEvent::class.java) }.getOrNull()
                    ?: return@on
                val existing = pollsById[event.pollId] ?: return@on
                pollsById[event.pollId] = existing.withTally(event.tick, event.counts)
                emitPolls()
            }

            sock.connect()
        } catch (e: Exception) {
            MainFragment.dError(TAG, "Failed to open poll socket: ${e.message}")
            post { listener.onError("Poll connection failed") }
        }
    }

    private fun handlePollPayload(args: Array<Any>, creatorId: String, opened: Boolean) {
        val raw = args.firstOrNull()?.toString() ?: return
        MainFragment.dLog(TAG, "${if (opened) "pollOpen" else "pollClose"}: ${raw.take(300)}")
        val poll = parsePollFromEvent(raw) ?: return
        if (poll.id.isEmpty()) return
        if (poll.creator.isNotEmpty() && poll.creator != creatorId) return
        pollsById[poll.id] = poll
        emitPolls()
    }

    private fun parsePollFromEvent(raw: String): PollInfo? {
        // Payloads vary: {poll}, {createdPoll}, or a bare PollInfo.
        runCatching { gson.fromJson(raw, PollOpenEvent::class.java).poll }.getOrNull()
            ?.takeIf { it.id.isNotEmpty() }
            ?.let { return it }
        runCatching { gson.fromJson(raw, PollCreatedEvent::class.java).createdPoll }.getOrNull()
            ?.takeIf { it.id.isNotEmpty() }
            ?.let { return it }
        return runCatching { gson.fromJson(raw, PollInfo::class.java) }.getOrNull()
            ?.takeIf { it.id.isNotEmpty() && it.options.isNotEmpty() }
    }

    private fun joinRoom(sock: Socket, token: String, creatorId: String) {
        // Cookie-session join works with sails.sid. OAuth tk join has been returning 500.
        val data = JSONObject().apply {
            put("creatorId", creatorId)
            put("moderator", false)
        }
        val request = sailsRequest("post", URL_JOIN_COOKIE, data, token)
        sock.emit("post", request, Ack { args ->
            val raw = args.firstOrNull()?.toString().orEmpty()
            MainFragment.dLog(TAG, "joinRoom ack: ${raw.take(400)}")
            val ack = runCatching { gson.fromJson(raw, SailsAck::class.java) }.getOrNull()
            val status = ack?.statusCode ?: 0
            if (status in 200..299) {
                applyJoinBody(ack?.body)
            } else {
                joinRoomToken(sock, token, creatorId)
            }
        })
    }

    private fun joinRoomToken(sock: Socket, token: String, creatorId: String) {
        val data = JSONObject().apply {
            put("creatorId", creatorId)
            put("token", token)
            put("moderator", false)
        }
        val request = sailsRequest("post", URL_JOIN_TOKEN, data, token)
        sock.emit("post", request, Ack { args ->
            val raw = args.firstOrNull()?.toString().orEmpty()
            MainFragment.dLog(TAG, "joinRoom(tk) ack: ${raw.take(400)}")
            val ack = runCatching { gson.fromJson(raw, SailsAck::class.java) }.getOrNull()
            val status = ack?.statusCode ?: 0
            if (status in 200..299) {
                applyJoinBody(ack?.body)
            } else {
                post { listener?.onError("Could not join poll room ($status)") }
            }
        })
    }

    private fun applyJoinBody(bodyElement: com.google.gson.JsonElement?) {
        val body = bodyElement?.let {
            runCatching { gson.fromJson(it, JoinLiveRoomInfo::class.java) }.getOrNull()
        }
        pollsById.clear()
        body?.activePolls?.forEach { pollsById[it.id] = it }
        MainFragment.dLog(TAG, "Joined poll room with ${pollsById.size} active poll(s)")
        emitPolls()
    }

    fun vote(pollId: String, optionIndex: Int, onResult: (Boolean, String?) -> Unit) {
        authManager.withValidAccessToken({ token ->
            val body = JSONObject()
                .put("pollId", pollId)
                .put("optionIndex", optionIndex)
                .toString()
            RequestTask(context).sendDataWithBody(URI_VOTE, token, body, object : ml.bmlzootown.hydravion.client.RequestTask.VolleyCallback {
                override fun onResponseCode(response: Int) = Unit
                override fun onSuccess(response: String) {
                    val existing = pollsById[pollId]
                    if (existing != null) {
                        pollsById[pollId] = existing.withVoted(optionIndex)
                        emitPolls()
                    }
                    post { onResult(true, null) }
                }
                override fun onSuccessCreator(response: String, creatorGUID: String) = Unit
                override fun onError(error: com.android.volley.VolleyError) {
                    val msg = error.networkResponse?.let { String(it.data) }
                        ?.takeIf { it.isNotBlank() }
                        ?: error.message
                        ?: "Vote failed"
                    val alreadyVoted = msg.contains("alreadyVoted", ignoreCase = true)
                    if (alreadyVoted) {
                        val existing = pollsById[pollId]
                        if (existing != null) {
                            pollsById[pollId] = existing.copy(voted = true)
                            emitPolls()
                        }
                    }
                    post { onResult(false, if (alreadyVoted) "Already voted" else "Vote failed") }
                }
            })
        }, {
            post { onResult(false, "Session expired") }
        })
    }

    fun disconnect() {
        val sock = socket
        val id = creatorId
        val token = authManager.getAccessToken()
        if (sock != null && id != null && !token.isNullOrEmpty()) {
            try {
                val data = JSONObject().apply {
                    put("creatorId", id)
                    put("token", token)
                    put("moderator", false)
                }
                sock.emit("post", sailsRequest("post", URL_LEAVE_TOKEN, data, token))
            } catch (_: Exception) {
            }
        }
        sock?.off()
        sock?.disconnect()
        socket = null
        creatorId = null
        listener = null
        pollsById.clear()
    }

    private fun emitPolls() {
        val snapshot = pollsById.values
            .sortedByDescending { it.startDate.orEmpty() }
            .toList()
        post { listener?.onPollsChanged(snapshot) }
    }

    private fun sailsRequest(
        method: String,
        url: String,
        data: JSONObject,
        token: String
    ): JSONObject {
        return JSONObject().apply {
            put("method", method)
            put("headers", JSONObject().apply {
                if (token.isNotEmpty()) {
                    put("authorization", "Bearer $token")
                }
            })
            put("data", data)
            put("url", url)
        }
    }

    private fun post(block: () -> Unit) {
        mainHandler.post(block)
    }

    companion object {
        private const val TAG = "LivePollClient"
        private const val WWW_URI = "https://www.floatplane.com"
        private const val SAILS_QUERY =
            "__sails_io_sdk_version=1.2.1&__sails_io_sdk_platform=browser&__sails_io_sdk_language=javascript"
        private const val URL_JOIN_TOKEN = "/api/v3/poll/tk/live/joinroom"
        private const val URL_LEAVE_TOKEN = "/api/v3/poll/tk/live/leaveroom"
        private const val URL_JOIN_COOKIE = "/api/v3/poll/live/joinroom"
        // Current Floatplane frontend posts to /poll/vote (openapi still lists votePoll).
        private const val URI_VOTE = "https://www.floatplane.com/api/v3/poll/vote"
        private const val EVENT_POLL_OPEN = "pollOpen"
        private const val EVENT_POLL_CLOSE = "pollClose"
        private const val EVENT_POLL_TALLY = "pollUpdateTally"

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: LivePollClient? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): LivePollClient {
            if (INSTANCE == null) {
                INSTANCE = LivePollClient(context.applicationContext)
            }
            return INSTANCE!!
        }
    }
}

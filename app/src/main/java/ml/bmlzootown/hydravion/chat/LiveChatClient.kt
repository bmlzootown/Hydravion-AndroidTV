package ml.bmlzootown.hydravion.chat

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
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.Executors

/**
 * Socket.IO client for Floatplane livestream chat.
 *
 * chat.floatplane.com still requires a Sails session cookie (`sails.sid`). OAuth Bearer
 * alone is rejected (403). We first try to obtain a session cookie from www.floatplane.com
 * using the access token, then connect to chat with that cookie.
 */
class LiveChatClient private constructor(private val context: Context) {

    interface Listener {
        fun onConnected()
        fun onJoined(emotes: List<ChatEmote>)
        fun onMessage(message: RadioChatter)
        fun onError(message: String)
        fun onDisconnected()
    }

    private val authManager = AuthManager.getInstance(context)
    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var socket: Socket? = null
    private var channel: String? = null
    private var listener: Listener? = null
    private var sailsSid: String? = null

    fun connect(livestreamId: String, listener: Listener) {
        disconnect()
        this.listener = listener
        this.channel = "/live/$livestreamId"

        authManager.withValidAccessToken({ token ->
            ioExecutor.execute {
                val userAgent = "Hydravion (AndroidTV ${BuildConfig.VERSION_NAME})"
                val sid = SailsSessionHelper.resolveSailsSid(context, token, userAgent)
                sailsSid = sid
                if (sid == null) {
                    MainFragment.dError(
                        TAG,
                        "No sails.sid from www — chat.floatplane.com will likely return 403"
                    )
                } else {
                    MainFragment.dLog(TAG, "Got sails.sid (${sid.take(8)}…), connecting chat")
                }
                mainHandler.post {
                    openSocket(token, sid, userAgent, listener)
                }
            }
        }, {
            post { listener.onError("Session expired — chat unavailable") }
        })
    }

    private fun openSocket(
        token: String,
        sid: String?,
        userAgent: String,
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

            val sock = IO.socket(URI.create(CHAT_URI), opts)
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
                MainFragment.dLog(TAG, "Connected to chat socket (sid=${sid != null})")
                post { listener.onConnected() }
                // www supports tk/connect; chat does not. Skip tk on chat host.
                joinChannel(sock, token)
            }
            sock.on(Socket.EVENT_DISCONNECT) {
                MainFragment.dLog(TAG, "Disconnected from chat socket")
                post { listener.onDisconnected() }
            }
            sock.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val err = args.firstOrNull()?.toString() ?: "connect_error"
                MainFragment.dError(TAG, "Connect error: $err")
                post { listener.onError("Chat connection failed") }
            }
            sock.on(EVENT_RADIO_CHATTER) { args ->
                val raw = args.firstOrNull() ?: return@on
                try {
                    val json = when (raw) {
                        is JSONObject -> raw.toString()
                        is String -> raw
                        else -> gson.toJson(raw)
                    }
                    val message = gson.fromJson(json, RadioChatter::class.java)
                    if (message != null && message.message.isNotEmpty()) {
                        post { listener.onMessage(message) }
                    }
                } catch (e: Exception) {
                    MainFragment.dError(TAG, "Failed to parse radioChatter: ${e.message}")
                }
            }

            sock.connect()
        } catch (e: Exception) {
            MainFragment.dError(TAG, "Failed to init chat socket: ${e.message}")
            post { listener.onError("Could not start live chat") }
        }
    }

    fun disconnect() {
        val sock = socket ?: return
        val ch = channel
        if (ch != null && sock.connected()) {
            leaveChannel(sock, ch)
        }
        sock.off()
        sock.disconnect()
        sock.close()
        socket = null
        channel = null
        listener = null
        sailsSid = null
    }

    private fun joinChannel(sock: Socket, token: String) {
        val ch = channel ?: return
        val request = sailsRequest(
            method = "get",
            url = URL_JOIN,
            data = JSONObject().apply {
                put("channel", ch)
                put("message", JSONObject.NULL)
            },
            token = token
        )
        MainFragment.dLog(TAG, "Joining $ch")
        sock.emit("get", request, Ack { args ->
            try {
                val ackJson = args.firstOrNull()?.toString() ?: return@Ack
                MainFragment.dLog(TAG, "Join ack: $ackJson")
                val ack = gson.fromJson(ackJson, SailsAck::class.java)
                if (ack.statusCode == 403) {
                    post {
                        listener?.onError(
                            if (sailsSid == null) {
                                "Live chat blocked (403): Floatplane chat still needs a sails.sid session cookie, which OAuth login does not provide yet."
                            } else {
                                "Could not join live chat (403) even with session cookie"
                            }
                        )
                    }
                    return@Ack
                }
                if (ack.statusCode !in 200..299) {
                    post { listener?.onError("Could not join live chat (${ack.statusCode})") }
                    return@Ack
                }
                val body = ack.body?.let { gson.fromJson(it, JoinLivestreamResponse::class.java) }
                if (body?.success == true) {
                    post { listener?.onJoined(body.emotes) }
                } else {
                    post { listener?.onError("Could not join live chat") }
                }
            } catch (e: Exception) {
                MainFragment.dError(TAG, "Join parse error: ${e.message}")
                post { listener?.onError("Could not join live chat") }
            }
        })
    }

    private fun leaveChannel(sock: Socket, ch: String) {
        val token = authManager.getAccessToken()
        val request = sailsRequest(
            method = "post",
            url = URL_LEAVE,
            data = JSONObject().apply {
                put("channel", ch)
                put("message", "bye")
            },
            token = token
        )
        try {
            sock.emit("post", request)
        } catch (_: Exception) {
            // Best-effort leave on teardown
        }
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
        private const val TAG = "LiveChatClient"
        private const val CHAT_URI = "https://chat.floatplane.com"
        private const val SAILS_QUERY =
            "__sails_io_sdk_version=1.2.1&__sails_io_sdk_platform=browser&__sails_io_sdk_language=javascript"
        private const val EVENT_RADIO_CHATTER = "radioChatter"
        private const val URL_JOIN = "/RadioMessage/joinLivestreamRadioFrequency"
        private const val URL_LEAVE = "/RadioMessage/leaveLivestreamRadioFrequency"

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: LiveChatClient? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): LiveChatClient {
            if (INSTANCE == null) {
                INSTANCE = LiveChatClient(context.applicationContext)
            }
            return INSTANCE!!
        }
    }
}

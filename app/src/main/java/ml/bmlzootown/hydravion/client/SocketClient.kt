package ml.bmlzootown.hydravion.client

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Manager
import io.socket.client.Socket
import io.socket.engineio.client.Transport
import io.socket.engineio.client.transports.WebSocket
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.post.Post
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.*


class SocketClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    /**
     * Convenience fun to get cookies string
     * @return Cookies string
     */
    private fun getCookiesString(): String =
        "${Constants.PREF_SAIL_SSID}=${mainPrefs.getString(Constants.PREF_SAIL_SSID, "")}"

    fun initialize(): Socket {
        val heads = mutableMapOf<String, List<String>>()
        heads["Origin"] = listOf("https://www.floatplane.com")
        heads["Cookie"] = listOf(getCookiesString())

        val okHttpClient = OkHttpClient.Builder().build()
        IO.setDefaultOkHttpWebSocketFactory(okHttpClient)
        IO.setDefaultOkHttpCallFactory(okHttpClient)

        val uri = URI.create(SOCKET_URI)

        val opts = IO.Options()
        opts.query = "__sails_io_sdk_version=1.2.1&__sails_io_sdk_platform=browser&__sails_io_sdk_language=javascript"
        opts.transports = arrayOf(WebSocket.NAME)
        opts.forceNew = true
        opts.callFactory = okHttpClient;
        opts.webSocketFactory = okHttpClient;

        val socket = IO.socket(uri, opts)

        // Modify *initial* request headers
        socket.io().on(Manager.EVENT_TRANSPORT) { args ->
            val transport: Transport = args[0] as Transport
            transport.on(Transport.EVENT_REQUEST_HEADERS) {
                // Request Headers
                val headers = it[0] as MutableMap<String, List<String>>
                // Modify Request Headers
                headers["Origin"] = listOf("https://www.floatplane.com")
                headers["Cookie"] = listOf("sails.sid=s%3AcC3HQ8_yhIt_SqTyfDPGFL84O4FPDmFa.0NC1oN0IhW4QckRngBKDLXMprFvnh3ugiGaY9xBk%2Fc0;")
                Log.d("$TAG --> MODIFYING HEADERS", headers.toString())
            }
            transport.on(Transport.EVENT_RESPONSE_HEADERS){
                // Response Headers
                val headers = it[0] as Map<String, List<String>>
                Log.d("$TAG --> RESPONSE HEADERS", headers.toString())
            }
        }

        socket.connect()

        return socket
    }

    // Methods to parse UserSync and SyncEvents
    /*fun parseUserSync(obj: String): UserSync? {
        Log.d("$TAG --> UserSync", obj)
        Gson().fromJson(obj, UserSync::class.java).let { parsed ->
            return parsed
        }
    }

    fun parseSyncEvent(obj: JSONObject): SyncEvent {
        Log.d("$TAG --> SyncEvent", obj)
        Gson().fromJson(obj.toString(), SyncEvent::class.java).let { parsed ->
            return parsed
        }
    }*/

    companion object {

        private const val TAG = "SocketClient"
        private const val SOCKET_URI = "https://www.floatplane.com"
        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: SocketClient? = null

        @Synchronized
        fun getInstance(context: Context, mainPrefs: SharedPreferences): SocketClient {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = SocketClient(context.applicationContext, mainPrefs)
                }
            }

            return INSTANCE!!
        }
    }
}
package com.saucedplussytv.androidtv.client

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
import com.saucedplussytv.androidtv.Constants
import com.saucedplussytv.androidtv.authenticate.AuthManager
import com.saucedplussytv.androidtv.browse.MainFragment
import com.saucedplussytv.androidtv.post.Post
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.*


class SocketClient private constructor(private val context: Context, private val mainPrefs: SharedPreferences) {

    private val authManager: AuthManager = AuthManager.getInstance(context, mainPrefs)

     // Initialize the WebSocket connection, ensuring we use a fresh access token.
    fun initialize(onReady: (Socket?) -> Unit) {
        // Gate on being logged in; the live Cookie/User-Agent are read at request time below.
        authManager.withValidAccessToken({ _ ->
            val okHttpClient = OkHttpClient.Builder().build()
            IO.setDefaultOkHttpWebSocketFactory(okHttpClient)
            IO.setDefaultOkHttpCallFactory(okHttpClient)

            val uri = URI.create(SOCKET_URI)

            val opts = IO.Options()
            opts.query = "__sails_io_sdk_version=1.2.1&__sails_io_sdk_platform=browser&__sails_io_sdk_language=javascript"
            opts.transports = arrayOf(WebSocket.NAME)
            opts.forceNew = true
            opts.callFactory = okHttpClient
            opts.webSocketFactory = okHttpClient

            val socket = IO.socket(uri, opts)

            // Modify *initial* request headers
            socket.io().on(Manager.EVENT_TRANSPORT) { args ->
                val transport: Transport = args[0] as Transport
                transport.on(Transport.EVENT_REQUEST_HEADERS) {
                    // Request Headers
                    val headers = it[0] as MutableMap<String, List<String>>
                    // Sauce+ cookie-session auth: send the session Cookie + matching User-Agent.
                    val currentCookie = authManager.getSessionCookie()
                    headers["Origin"] = listOf(SITE)
                    headers["Cookie"] = listOf(currentCookie)
                    headers["User-Agent"] = listOf(authManager.getUserAgent())
                    // Never log the Cookie value — it is the entire session credential.
                    val redacted = headers.mapValues { (k, v) -> if (k == "Cookie") "[redacted]" else v.toString() }
                    MainFragment.dLog("$TAG --> MODIFYING HEADERS", redacted.toString())
                }
                transport.on(Transport.EVENT_RESPONSE_HEADERS){
                    // Response Headers
                    val headers = it[0] as Map<String, List<String>>
                    MainFragment.dLog("$TAG --> RESPONSE HEADERS", headers.toString())
                }
            }

            socket.connect()
            onReady(socket)
        }, {
            MainFragment.dLog(TAG, "Failed to obtain access token for socket")
            onReady(null)
        })
    }

    // Methods to parse UserSync and SyncEvents
    fun parseUserSync(obj: String): UserSync? {
        MainFragment.dLog("$TAG --> UserSync", obj)
        Gson().fromJson(obj, UserSync::class.java).let { parsed ->
            return parsed
        }
    }

    fun parseSyncEvent(obj: JSONObject): SyncEvent {
        MainFragment.dLog("$TAG --> SyncEvent", obj.toString())
        Gson().fromJson(obj.toString(), SyncEvent::class.java).let { parsed ->
            return parsed
        }
    }

    companion object {

        private const val TAG = "SocketClient"
        private const val SITE = "https://www.sauceplus.com"
        private const val SOCKET_URI = SITE
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
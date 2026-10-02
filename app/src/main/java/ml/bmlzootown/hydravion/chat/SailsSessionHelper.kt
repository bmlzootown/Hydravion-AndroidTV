package ml.bmlzootown.hydravion.chat

import android.content.Context
import ml.bmlzootown.hydravion.Constants
import ml.bmlzootown.hydravion.browse.MainFragment
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Session cookie helpers for livestream chat.
 *
 * chat.floatplane.com authenticates via `sails.sid`. OAuth Bearer alone is rejected.
 * Users paste a browser cookie in Settings → Chat Cookie.
 */
object SailsSessionHelper {

    private const val TAG = "SailsSessionHelper"
    private const val WWW = "https://www.floatplane.com"
    private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

    fun hasChatCookie(context: Context): Boolean =
        getChatCookie(context).isNotEmpty()

    fun getChatCookie(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(Constants.PREF_CHAT_COOKIE, null)
            ?: prefs.getString(Constants.PREF_CHAT_COOKIE_LEGACY, "")
            .orEmpty()
        val normalized = normalizeSailsSid(raw)
        if (normalized.isNotEmpty()
            && prefs.getString(Constants.PREF_CHAT_COOKIE, null).isNullOrEmpty()
        ) {
            prefs.edit()
                .putString(Constants.PREF_CHAT_COOKIE, normalized)
                .remove(Constants.PREF_CHAT_COOKIE_LEGACY)
                .apply()
        }
        return normalized
    }

    fun setChatCookie(context: Context, value: String) {
        val normalized = normalizeSailsSid(value)
        context.applicationContext
            .getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(Constants.PREF_CHAT_COOKIE, normalized)
            .remove(Constants.PREF_CHAT_COOKIE_LEGACY)
            .apply()
        MainFragment.dLog(
            TAG,
            if (normalized.isEmpty()) "Cleared chat cookie"
            else "Saved chat cookie (${normalized.take(8)}…)"
        )
    }

    fun clearChatCookie(context: Context) = setChatCookie(context, "")

    /**
     * Accepts raw cookie values such as `s:…`, `sails.sid=s:…`, or a full Cookie header.
     */
    fun normalizeSailsSid(raw: String): String {
        var value = raw.trim().trim('"')
        if (value.isEmpty()) return ""

        if (value.contains(';') || value.contains("sails.sid=", ignoreCase = true)) {
            val sidPart = value.split(';')
                .map { it.trim() }
                .firstOrNull { it.startsWith("sails.sid=", ignoreCase = true) }
            if (sidPart != null) {
                value = sidPart.substringAfter('=').trim()
            } else if (value.startsWith("sails.sid=", ignoreCase = true)) {
                value = value.substringAfter('=').trim()
            }
        }

        return value.trim()
    }

    /**
     * Prefer the saved chat cookie, otherwise try to obtain one via Bearer (usually fails).
     */
    fun resolveSailsSid(context: Context, accessToken: String, userAgent: String): String? {
        val saved = getChatCookie(context)
        if (saved.isNotEmpty()) {
            MainFragment.dLog(TAG, "Using saved chat cookie (${saved.take(8)}…)")
            return saved
        }
        return fetchSailsSid(accessToken, userAgent)
    }

    fun fetchSailsSid(accessToken: String, userAgent: String): String? {
        val jar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
                cookies.forEach {
                    MainFragment.dLog(TAG, "Set-Cookie ${url.host}: ${it.name}=${it.value.take(12)}…")
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                cookieStore[url.host].orEmpty()
        }

        val client = OkHttpClient.Builder()
            .cookieJar(jar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val paths = listOf(
            "/api/v3/user/self",
            "/api/v3/socket/tk/connect"
        )

        for (path in paths) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$WWW$path")
                    .header("Authorization", "Bearer $accessToken")
                    .header("Origin", "https://www.floatplane.com")
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")

                val request = if (path.endsWith("/connect")) {
                    val body = """{"token":"$accessToken"}"""
                        .toRequestBody("application/json".toMediaTypeOrNull())
                    requestBuilder.post(body).build()
                } else {
                    requestBuilder.get().build()
                }

                client.newCall(request).execute().use { response ->
                    MainFragment.dLog(TAG, "HTTP ${response.code} $path")
                    response.headers("Set-Cookie").forEach { raw ->
                        MainFragment.dLog(TAG, "Raw Set-Cookie: ${raw.take(40)}…")
                        extractSid(raw)?.let { return it }
                    }
                }
            } catch (e: Exception) {
                MainFragment.dError(TAG, "Failed $path: ${e.message}")
            }
        }

        cookieStore.values.flatten().forEach { cookie ->
            if (cookie.name == "sails.sid" && cookie.value.isNotEmpty()) {
                return cookie.value
            }
        }

        MainFragment.dLog(TAG, "No sails.sid cookie issued for Bearer token")
        return null
    }

    private fun extractSid(setCookie: String): String? {
        val part = setCookie.split(";").firstOrNull() ?: return null
        val idx = part.indexOf('=')
        if (idx <= 0) return null
        val name = part.substring(0, idx).trim()
        val value = part.substring(idx + 1).trim()
        return if (name == "sails.sid" && value.isNotEmpty()) value else null
    }
}

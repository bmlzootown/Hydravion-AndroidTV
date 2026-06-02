package com.saucedplussytv.androidtv.authenticate

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.saucedplussytv.androidtv.Constants

/**
 * Manages the SaucedplussyTV session credential.
 *
 * SaucedplussyTV uses the Sauce+ backend (white-label Floatplane) with cookie-session
 * auth (not OAuth/OIDC tokens). The credential is the `Cookie` header value harvested
 * from the WebView login ([WebLoginActivity]) — typically `sails.sid=...; cf_clearance=...`.
 * It is paired with the WebView's User-Agent, which must be reused on every request
 * so Cloudflare's `cf_clearance` cookie remains valid.
 *
 * Unlike OAuth there is no silent refresh: when the server session expires the API
 * returns 401/403 and the user must log in again. The legacy method names
 * ([withValidAccessToken], [getAccessToken], [clearTokens]) are retained so existing
 * call sites are unchanged, but the value they carry is now a Cookie header, not a
 * Bearer token.
 */
class AuthManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val TAG = "AuthManager"

    /** The full Cookie header value for api.sauceplus.com, or "" if not logged in. */
    fun getAccessToken(): String =
        prefs.getString(Constants.PREF_SESSION_COOKIE, "") ?: ""

    /** Alias for [getAccessToken] with the accurate name; both return the session cookie. */
    fun getSessionCookie(): String = getAccessToken()

    /** The User-Agent captured at login, or a sensible browser default for fresh installs. */
    fun getUserAgent(): String =
        prefs.getString(Constants.PREF_USER_AGENT, "")
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_USER_AGENT

    fun isLoggedIn(): Boolean = getAccessToken().isNotEmpty()

    /** Persist the session harvested from the WebView login. */
    fun saveSession(cookieHeader: String, userAgent: String) {
        prefs.edit()
            .putString(Constants.PREF_SESSION_COOKIE, cookieHeader)
            .putString(
                Constants.PREF_USER_AGENT,
                userAgent.takeIf { it.isNotEmpty() } ?: DEFAULT_USER_AGENT
            )
            .apply()
    }

    fun clearTokens() {
        prefs.edit()
            .remove(Constants.PREF_SESSION_COOKIE)
            .remove(Constants.PREF_USER_AGENT)
            .apply()
    }

    /**
     * Runs [onToken] with the stored session cookie when logged in, otherwise [onFailure].
     * Synchronous (no network) — cookie sessions cannot be silently refreshed here.
     */
    fun withValidAccessToken(
        onToken: (String) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        val cookie = getAccessToken()
        if (cookie.isNotEmpty()) {
            onToken(cookie)
        } else {
            Log.d(TAG, "No session cookie stored; login required")
            onFailure?.invoke()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        // Desktop-Chrome UA used as a fallback before a login captures the real WebView UA.
        // Cloudflare binds cf_clearance to the UA, so the stored value should win at runtime.
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Android TV) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun getInstance(context: Context, prefs: SharedPreferences): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(context.applicationContext, prefs).also { INSTANCE = it }
            }
        }

        /** Reads the current User-Agent without needing prefs; safe before login (returns default). */
        fun peekUserAgent(): String = INSTANCE?.getUserAgent() ?: DEFAULT_USER_AGENT
    }
}



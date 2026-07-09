package ml.bmlzootown.hydravion.authenticate

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import ml.bmlzootown.hydravion.Constants
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

 // Centralized helper for managing OAuth tokens and automatic access-token refresh.
class AuthManager private constructor(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val queue: RequestQueue = Volley.newRequestQueue(context.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "AuthManager"
    
    // Cache for validated token to avoid re-validation on every request
    @Volatile
    private var cachedToken: String? = null
    @Volatile
    private var cacheValidUntil: Long = 0
    
    // Queue for concurrent token validation requests
    private val pendingCallbacks = ConcurrentLinkedQueue<Pair<(String) -> Unit, (() -> Unit)?>>()
    @Volatile
    private var isRefreshing = false
    /** Monotonic id so late Volley responses from a prior refresh cannot affect a new one. */
    @Volatile
    private var inFlightRefreshId = 0
    private var refreshTimeoutRunnable: Runnable? = null

    private val tokenEndpoint =
        "https://auth.floatplane.com/realms/floatplane/protocol/openid-connect/token"
    private val clientId = "hydravion"

    fun getAccessToken(): String =
        prefs.getString(Constants.PREF_ACCESS_TOKEN, "") ?: ""

    fun getRefreshToken(): String =
        prefs.getString(Constants.PREF_REFRESH_TOKEN, "") ?: ""

    fun hasRefreshToken(): Boolean = getRefreshToken().isNotEmpty()

    private fun getExpiresAt(): Long =
        prefs.getLong(Constants.PREF_TOKEN_EXPIRES_AT, 0L)

    /**
     * Persist tokens from a successful login and reset any in-flight refresh state.
     * Prefer this over writing SharedPreferences directly so AuthManager's cache stays in sync.
     */
    fun storeTokens(accessToken: String, refreshToken: String, expiresInSeconds: Long) {
        val expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L
        prefs.edit()
            .putString(Constants.PREF_ACCESS_TOKEN, accessToken)
            .putString(Constants.PREF_REFRESH_TOKEN, refreshToken)
            .putLong(Constants.PREF_TOKEN_EXPIRES_AT, expiresAt)
            .commit()
        cachedToken = accessToken
        cacheValidUntil = System.currentTimeMillis() + CACHE_TTL_MS
        // Unblock any stuck refresh and deliver the fresh token to waiters.
        cancelRefreshTimeout()
        val callbacks = drainPendingCallbacks()
        isRefreshing = false
        callbacks.forEach { (tokenCallback, _) ->
            tokenCallback(accessToken)
        }
        Log.d(TAG, "Tokens stored. Access token expires in ${expiresInSeconds}s")
    }

    fun clearTokens() {
        prefs.edit()
            .remove(Constants.PREF_ACCESS_TOKEN)
            .remove(Constants.PREF_REFRESH_TOKEN)
            .remove(Constants.PREF_TOKEN_EXPIRES_AT)
            .commit()
        cachedToken = null
        cacheValidUntil = 0
        // Always clear stuck refresh state when wiping credentials.
        cancelRefreshTimeout()
        drainPendingCallbacks()
        isRefreshing = false
    }

    /**
     * Drop the short-lived in-memory cache so the next call re-checks expiry / refreshes.
     * Does not touch tokens on disk.
     */
    fun invalidateCache() {
        cachedToken = null
        cacheValidUntil = 0
    }

    private fun drainPendingCallbacks(): List<Pair<(String) -> Unit, (() -> Unit)?>> {
        val callbacks = mutableListOf<Pair<(String) -> Unit, (() -> Unit)?>>()
        while (pendingCallbacks.isNotEmpty()) {
            pendingCallbacks.poll()?.let { callbacks.add(it) }
        }
        return callbacks
    }

    private fun cancelRefreshTimeout() {
        refreshTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshTimeoutRunnable = null
    }

    private fun scheduleRefreshTimeout() {
        cancelRefreshTimeout()
        val runnable = Runnable {
            if (isRefreshing) {
                Log.e(TAG, "Token refresh timed out after ${REFRESH_TIMEOUT_MS}ms; keeping tokens")
                // Do not clear tokens on timeout — network may simply be unavailable after TV wake.
                completeRefreshFailure(clearCredentials = false)
            }
        }
        refreshTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, REFRESH_TIMEOUT_MS)
    }

    private fun isAccessTokenValid(): Boolean {
        val token = getAccessToken()
        if (token.isEmpty()) {
            Log.d(TAG, "Access token is empty")
            return false
        }

        // Add 60s of leeway to avoid race with server-side expiry.
        val now = System.currentTimeMillis()
        val expiresAt = getExpiresAt()
        val isValid = now + 60_000L < expiresAt
        if (!isValid) {
            val timeUntilExpiry = expiresAt - now
            Log.d(TAG, "Access token expired or expiring soon. Time until expiry: ${timeUntilExpiry / 1000}s")
        }
        return isValid
    }

    /**
     * True when the refresh failure indicates the refresh token itself is invalid
     * (user must re-login). Transient network / proxy errors must NOT wipe credentials.
     */
    private fun isPermanentRefreshFailure(error: VolleyError): Boolean {
        val data = error.networkResponse?.data ?: return false
        return try {
            val json = JSONObject(String(data, Charsets.UTF_8))
            val oauthError = json.optString("error", "")
            oauthError == "invalid_grant" ||
                oauthError == "invalid_token" ||
                oauthError == "unauthorized_client"
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse refresh error body; treating as transient", e)
            false
        }
    }

    private fun completeRefreshSuccess(newAccessToken: String) {
        cancelRefreshTimeout()
        val callbacks = drainPendingCallbacks()
        isRefreshing = false
        callbacks.forEach { (tokenCallback, _) ->
            tokenCallback(newAccessToken)
        }
    }

    private fun completeRefreshFailure(clearCredentials: Boolean) {
        cancelRefreshTimeout()
        val callbacks = drainPendingCallbacks()
        if (clearCredentials) {
            prefs.edit()
                .remove(Constants.PREF_ACCESS_TOKEN)
                .remove(Constants.PREF_REFRESH_TOKEN)
                .remove(Constants.PREF_TOKEN_EXPIRES_AT)
                .commit()
        }
        // Always drop the short-lived cache; keep disk tokens unless permanently invalid.
        cachedToken = null
        cacheValidUntil = 0
        isRefreshing = false
        callbacks.forEach { (_, failureCallback) ->
            failureCallback?.invoke()
        }
    }

     // Ensures a valid access token, refreshing with the refresh token when necessary.
     // - On success: invokes [onToken] with a non-empty access token.
     // - On permanent auth failure: clears stored tokens and invokes [onFailure].
     // - On transient failure: keeps tokens, invokes [onFailure] so callers can retry.
     // - Batches concurrent requests to avoid multiple simultaneous refresh attempts.
    fun withValidAccessToken(
        onToken: (String) -> Unit,
        onFailure: (() -> Unit)? = null
    ) {
        // Check cache first — but only if the underlying token is still within its expiry window.
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < cacheValidUntil && isAccessTokenValid()) {
            Log.d(TAG, "Using cached valid token")
            onToken(cachedToken!!)
            return
        }
        
        // Check if token is valid in storage
        if (isAccessTokenValid()) {
            val token = getAccessToken()
            // Cache the token for 30 seconds
            cachedToken = token
            cacheValidUntil = now + CACHE_TTL_MS
            Log.d(TAG, "Access token is valid, using existing token (cached for ${CACHE_TTL_MS / 1000}s)")
            onToken(token)
            return
        }

        // If already refreshing, queue this callback to be notified when refresh completes
        if (isRefreshing) {
            Log.d(TAG, "Token refresh in progress, queuing callback")
            pendingCallbacks.add(Pair(onToken, onFailure))
            return
        }

        // Start refresh process. Queue the initiating callback with any concurrent waiters
        // so timeout / success / failure all notify every caller uniformly.
        isRefreshing = true
        pendingCallbacks.add(Pair(onToken, onFailure))
        val refreshId = ++inFlightRefreshId
        Log.d(TAG, "Access token expired or invalid, attempting refresh (id=$refreshId)")
        val refreshToken = getRefreshToken()
        if (refreshToken.isEmpty()) {
            Log.w(TAG, "Refresh token is empty, cannot refresh. Clearing tokens.")
            completeRefreshFailure(clearCredentials = true)
            return
        }

        Log.d(TAG, "Refreshing access token using refresh token")
        scheduleRefreshTimeout()

        val request = object : StringRequest(
            Method.POST,
            tokenEndpoint,
            { response ->
                if (refreshId != inFlightRefreshId) {
                    Log.w(TAG, "Ignoring stale token refresh success (id=$refreshId)")
                } else try {
                    val json = JSONObject(response)
                    if (json.has("access_token")) {
                        val newAccessToken = json.getString("access_token")
                        val newRefreshToken = json.optString("refresh_token", refreshToken)
                        val expiresIn = json.optLong("expires_in", 1800L)
                        val expiresAt = System.currentTimeMillis() + expiresIn * 1000L

                        Log.d(TAG, "Token refresh successful. New token expires in ${expiresIn}s")
                        if (newRefreshToken != refreshToken) {
                            Log.d(TAG, "Received new refresh token")
                        }

                        prefs.edit()
                            .putString(Constants.PREF_ACCESS_TOKEN, newAccessToken)
                            .putString(Constants.PREF_REFRESH_TOKEN, newRefreshToken)
                            .putLong(Constants.PREF_TOKEN_EXPIRES_AT, expiresAt)
                            .commit()

                        // Cache the new token
                        cachedToken = newAccessToken
                        cacheValidUntil = System.currentTimeMillis() + CACHE_TTL_MS

                        // Always persist a successful refresh, even if we already timed out —
                        // the next withValidAccessToken call can then use the fresh token.
                        if (isRefreshing) {
                            completeRefreshSuccess(newAccessToken)
                        } else {
                            Log.w(TAG, "Late token refresh success after timeout; tokens updated")
                        }
                    } else if (json.has("error")) {
                        if (isRefreshing) {
                            // OAuth error in a 200 body (unusual but possible)
                            val oauthError = json.optString("error", "unknown")
                            Log.e(TAG, "Token refresh returned OAuth error: $oauthError")
                            val permanent = oauthError == "invalid_grant" ||
                                oauthError == "invalid_token" ||
                                oauthError == "unauthorized_client"
                            completeRefreshFailure(clearCredentials = permanent)
                        } else {
                            Log.w(TAG, "Ignoring late token refresh OAuth error")
                        }
                    } else if (isRefreshing) {
                        Log.e(TAG, "Token refresh response missing access_token")
                        // Ambiguous response — keep credentials and let the caller retry.
                        completeRefreshFailure(clearCredentials = false)
                    } else {
                        Log.w(TAG, "Ignoring late token refresh response")
                    }
                } catch (e: Exception) {
                    if (isRefreshing) {
                        Log.e(TAG, "Exception parsing token refresh response", e)
                        // Parse errors are not proof the refresh token is bad.
                        completeRefreshFailure(clearCredentials = false)
                    } else {
                        Log.w(TAG, "Ignoring late token refresh parse error")
                    }
                }
            },
            { error ->
                if (refreshId != inFlightRefreshId) {
                    Log.w(TAG, "Ignoring stale token refresh error (id=$refreshId)")
                } else if (isRefreshing) {
                    Log.e(TAG, "Token refresh request failed: ${error.message}, status=${error.networkResponse?.statusCode}")
                    val permanent = isPermanentRefreshFailure(error)
                    if (permanent) {
                        Log.w(TAG, "Permanent refresh failure — clearing tokens")
                    } else {
                        Log.w(TAG, "Transient refresh failure — keeping tokens for retry")
                    }
                    completeRefreshFailure(clearCredentials = permanent)
                } else {
                    Log.w(TAG, "Ignoring late token refresh error")
                }
            }
        ) {
            override fun getParams(): MutableMap<String, String> =
                mutableMapOf(
                    "grant_type" to "refresh_token",
                    "client_id" to clientId,
                    "refresh_token" to refreshToken
                )
        }

        // Prefer fresher network state after TV wake / long background.
        request.setShouldCache(false)
        queue.add(request)
    }

    companion object {
        private const val CACHE_TTL_MS = 30_000L
        private const val REFRESH_TIMEOUT_MS = 30_000L

        @Volatile
        private var INSTANCE: AuthManager? = null

        // Always resolve the shared prefs file internally so the singleton can never be
        // bound to a per-activity prefs file (which has no tokens) by its first caller.
        @JvmStatic
        fun getInstance(context: Context): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager(
                    context.applicationContext,
                    context.applicationContext.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)
                ).also { INSTANCE = it }
            }
        }

    }
}

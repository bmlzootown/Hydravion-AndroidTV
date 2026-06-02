package com.saucedplussytv.androidtv.authenticate

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import com.saucedplussytv.androidtv.R
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cookie-session login for SaucedplussyTV (Sauce+ / white-label Floatplane).
 *
 * SaucedplussyTV uses the Sauce+ backend which has no device-code/OIDC flow;
 * the site (incl. /api) sits behind Cloudflare with a Turnstile-protected
 * username/password + Discord login. Rather than reimplement Turnstile and the CF
 * challenge natively, we host the real login page in a [WebView]: the user signs in
 * there (Turnstile + CF solved by the WebView), and we harvest the resulting
 * `sails.sid` (+ `cf_clearance`) cookies and the WebView's User-Agent, which the rest
 * of the app replays on its API calls.
 *
 * Returns, on success: extras [EXTRA_SESSION_COOKIE] and [EXTRA_USER_AGENT], RESULT_OK.
 *
 * NOTE: success is detected by leaving the `/login` route for an app route while a
 * `sails.sid` cookie exists. The exact post-login landing route should be confirmed on
 * a real device; adjust [isAppRoute] if the backend redirects somewhere unexpected.
 */
class WebLoginActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    @Volatile private var completed = false
    private val verifying = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_login)
        webView = findViewById(R.id.web_view)
        progress = findViewById(R.id.progress)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progress.visibility = View.GONE
                maybeFinish(url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // Catches client-side (SPA) route changes that don't trigger onPageFinished.
                maybeFinish(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only react to main-frame failures (ignore favicon/subresource errors).
                if (completed || request?.isForMainFrame != true) return
                Log.w(TAG, "Login page failed to load: ${error?.description}")
                progress.visibility = View.GONE
                Toast.makeText(
                    this@WebLoginActivity,
                    "Couldn't reach the SaucedplussyTV login page. Check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }

        webView.loadUrl(LOGIN_URL)
    }

    /**
     * When [url] is a logged-in app route with a session cookie present, verify the cookie is
     * actually authenticated (Sails issues `sails.sid` to anonymous visitors too) before
     * returning it.
     */
    private fun maybeFinish(url: String?) {
        if (completed || url == null) return
        val uri = Uri.parse(url)
        val host = uri.host ?: return
        // Exact host match only — endsWith() would accept lookalikes ("notsauceplus.com")
        // and arbitrary subdomains ("evil.sauceplus.com").
        if (host != HOST && host != "www.$HOST") return
        if (!isAppRoute(uri.path)) return

        val cookieHeader = CookieManager.getInstance().getCookie(SITE)
        if (cookieHeader.isNullOrEmpty() || !cookieHeader.contains(SESSION_COOKIE_NAME)) return

        verifyAndFinish(cookieHeader, webView.settings.userAgentString)
    }

    /**
     * Confirms the harvested cookie authenticates against an authed endpoint (200) before
     * returning it, so anonymous Sails sessions / Cloudflare interstitials don't false-positive.
     * Runs off the UI thread; on failure it simply lets the next navigation retry.
     */
    private fun verifyAndFinish(cookieHeader: String, userAgent: String) {
        if (completed || !verifying.compareAndSet(false, true)) return
        // Hold only a WeakReference + a Looper-bound Handler so an in-flight verification
        // (up to 15s) never pins this Activity in memory if the user backs out / it's destroyed.
        val activityRef = WeakReference(this)
        val handler = Handler(Looper.getMainLooper())
        Thread {
            var authed = false
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(VERIFY_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Cookie", cookieHeader)
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", "application/json")
                }
                authed = conn.responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                Log.w(TAG, "Session verification failed: ${e.message}")
            } finally {
                conn?.disconnect()
            }

            val ok = authed
            handler.post {
                val activity = activityRef.get()
                if (activity == null || activity.isFinishing || activity.isDestroyed) return@post
                if (ok && !activity.completed) {
                    activity.completed = true
                    CookieManager.getInstance().flush()
                    Log.d(TAG, "Login verified; harvested session cookie")
                    activity.setResult(
                        RESULT_OK,
                        Intent().apply {
                            putExtra(EXTRA_SESSION_COOKIE, cookieHeader)
                            putExtra(EXTRA_USER_AGENT, userAgent)
                        }
                    )
                    activity.finish()
                } else {
                    // Not authenticated yet (anonymous session / CF page) — allow a later retry.
                    activity.verifying.set(false)
                }
            }
        }.start()
    }

    /** True for post-login app routes; false for the auth/login pages themselves. */
    private fun isAppRoute(path: String?): Boolean {
        val p = (path ?: "/").lowercase()
        return NON_APP_PREFIXES.none { p == it || p.startsWith("$it/") }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(RESULT_CANCELED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WebLoginActivity"
        private const val HOST = "sauceplus.com"
        private const val SITE = "https://www.sauceplus.com"
        private const val LOGIN_URL = "$SITE/login"
        // Authed endpoint used to confirm the harvested cookie is logged-in (200 vs 401/403).
        private const val VERIFY_URL = "$SITE/api/v3/user/subscriptions"
        private const val SESSION_COOKIE_NAME = "sails.sid"

        // Routes that are NOT a logged-in destination: the auth journey, Cloudflare
        // interstitials (/cdn-cgi), and API/XHR paths.
        private val NON_APP_PREFIXES = listOf(
            "/login", "/signup", "/auth", "/connect", "/forgot", "/reset", "/oauth",
            "/cdn-cgi", "/api"
        )

        const val EXTRA_SESSION_COOKIE = "session_cookie"
        const val EXTRA_USER_AGENT = "user_agent"
    }
}

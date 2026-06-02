package com.saucedplussytv.androidtv

object Constants {

    // SaucedplussyTV session auth preferences.
    // SaucedplussyTV uses the Sauce+ backend (white-label Floatplane) with cookie-session
    // auth, not OAuth tokens:
    // PREF_SESSION_COOKIE holds the full Cookie header harvested from the WebView
    // login (e.g. "sails.sid=...; cf_clearance=..."), and PREF_USER_AGENT holds the
    // WebView's User-Agent, which must be reused so Cloudflare's cf_clearance stays valid.
    const val PREF_SESSION_COOKIE = "session_cookie"
    const val PREF_USER_AGENT = "session_user_agent"

    const val REQ_CODE_DETAIL = 1
    const val REQ_CODE_LOGIN = 42
}
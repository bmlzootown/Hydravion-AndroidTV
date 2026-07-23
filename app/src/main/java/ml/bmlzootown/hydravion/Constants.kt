package ml.bmlzootown.hydravion

object Constants {

    // Single shared preferences file for the whole app. Never use Activity.getPreferences()
    // for auth/token state — per-activity files caused login loops after process death.
    const val PREF_FILE_NAME = "hydravion_prefs"

    // OAuth2 / OpenID Connect token preferences
    const val PREF_ACCESS_TOKEN = "access_token"
    const val PREF_REFRESH_TOKEN = "refresh_token"
    const val PREF_TOKEN_EXPIRES_AT = "token_expires_at"

    /** Browser sails.sid cookie value for livestream chat auth. */
    const val PREF_CHAT_COOKIE = "chat_cookie"
    /** Legacy key; migrated to [PREF_CHAT_COOKIE] on read. */
    const val PREF_CHAT_COOKIE_LEGACY = "test_sails_sid"

    const val REQ_CODE_DETAIL = 1
    
    // Output format preference
    // Allowed values: hls.mpegts, hls.fmp4, dash.mpegts, dash.m4s, flat
    const val PREF_OUTPUT_FORMAT = "output_format"
    const val OUTPUT_FORMAT_HLS_MPEGTS = "hls.mpegts"
    const val OUTPUT_FORMAT_HLS_FMP4 = "hls.fmp4"
    const val OUTPUT_FORMAT_DASH_MPEGTS = "dash.mpegts"
    const val OUTPUT_FORMAT_DASH_M4S = "dash.m4s"
    const val OUTPUT_FORMAT_FLAT = "flat"
    const val OUTPUT_FORMAT_DEFAULT = OUTPUT_FORMAT_HLS_MPEGTS // Default to HLS MPEGTS
}
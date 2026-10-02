package ml.bmlzootown.hydravion

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import ml.bmlzootown.hydravion.authenticate.QrLoginActivity
import ml.bmlzootown.hydravion.browse.MainActivity
import ml.bmlzootown.hydravion.detail.DetailsActivity
import ml.bmlzootown.hydravion.playback.PlaybackActivity

object ThemeManager {

    const val PREF_THEME = "app_theme"
    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    @JvmStatic
    fun isLight(context: Context): Boolean =
        context.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)
            .getString(PREF_THEME, THEME_DARK) == THEME_LIGHT

    @JvmStatic
    fun applyTheme(activity: Activity) {
        activity.setTheme(themeStyleFor(activity))
    }

    @JvmStatic
    fun setThemePreference(activity: AppCompatActivity, theme: String) {
        activity.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_THEME, theme)
            .apply()
        activity.recreate()
    }

    private fun themeStyleFor(activity: Activity): Int {
        val light = isLight(activity)
        return when (activity) {
            is MainActivity -> if (light) R.style.AppTheme_Browse_Light else R.style.AppTheme_Browse
            is DetailsActivity -> if (light) R.style.AppTheme_Video_Light else R.style.AppTheme_Video
            is PlaybackActivity -> if (light) R.style.AppTheme_Video_Light else R.style.AppTheme_Video
            is QrLoginActivity -> if (light) R.style.AppTheme_Login_Light else R.style.AppTheme_Login
            else -> if (light) R.style.AppTheme_Browse_Light else R.style.AppTheme_Browse
        }
    }

    fun toggleTheme(activity: AppCompatActivity) {
        val prefs = activity.getSharedPreferences(Constants.PREF_FILE_NAME, Context.MODE_PRIVATE)
        val next = if (isLight(activity)) THEME_DARK else THEME_LIGHT
        prefs.edit().putString(PREF_THEME, next).apply()
        activity.recreate()
    }
}

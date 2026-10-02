package ml.bmlzootown.hydravion.models

/**
 * `GET /api/v3/content/get/progress` returns 0–100 for a blog post id.
 * Resume and the player still work in seconds.
 */
object ContentShape {

    @JvmStatic
    fun progressToSeconds(percent: Int, durationSeconds: Int): Int {
        if (durationSeconds <= 0 || percent <= 0) {
            return 0
        }
        if (percent >= 100) {
            return durationSeconds
        }
        return ((percent / 100.0) * durationSeconds).toInt()
    }
}

package ml.bmlzootown.hydravion.chat

import android.graphics.Color
import java.math.BigInteger

/**
 * Floatplane live-chat username colors: hash the username the same way the web client
 * does (`fastHashStringToInt` → index 1..10) and map to the dark-theme palette.
 */
object ChatUsernameColors {

    private const val NAME_COLOR_COUNT = 10
    private val MAX_SAFE_INTEGER = BigInteger.valueOf((1L shl 53) - 1)
    private val COLOR_COUNT = BigInteger.valueOf(NAME_COLOR_COUNT.toLong())

    // theme-dark .color-N from Floatplane's live chat CSS
    private val DARK_COLORS = intArrayOf(
        Color.parseColor("#AAAAAA"), // color-1
        Color.parseColor("#006699"), // color-2
        Color.parseColor("#CC6600"), // color-3
        Color.parseColor("#D400D4"), // color-4
        Color.parseColor("#009933"), // color-5
        Color.parseColor("#FF6600"), // color-6
        Color.parseColor("#006666"), // color-7
        Color.parseColor("#B63D3D"), // color-8
        Color.parseColor("#9763CB"), // color-9
        Color.parseColor("#0099CC"), // color-10
    )

    fun colorForUsername(username: String): Int {
        val index = getUsernameColorIndex(username)
        return DARK_COLORS[index - 1]
    }

    /**
     * 1-based index matching Floatplane's `getUsernameColorIndex`.
     *
     * JS does `Number(hash % MAX_SAFE_INTEGER) % 10 + 1`. The safe-integer result can
     * exceed [Int.MAX_VALUE], so we must not narrow to Int before the color modulo.
     */
    fun getUsernameColorIndex(username: String): Int {
        val safeHash = fastHashString(username).mod(MAX_SAFE_INTEGER)
        return safeHash.mod(COLOR_COUNT).toInt() + 1
    }

    /**
     * Port of Floatplane's `fastHashStringToInt` BigInt loop:
     * `n = charCode + ((n << 5) - n)`.
     */
    private fun fastHashString(value: String): BigInteger {
        var n = BigInteger.ZERO
        for (ch in value) {
            n = BigInteger.valueOf(ch.code.toLong()) + (n.shiftLeft(5) - n)
        }
        return n
    }

    fun isStaffBadge(userType: String): Boolean =
        when (userType.lowercase()) {
            "moderator", "mod", "admin", "administrator" -> true
            else -> false
        }
}

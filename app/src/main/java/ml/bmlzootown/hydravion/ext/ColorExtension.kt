package ml.bmlzootown.hydravion.ext

import android.content.Context
import android.graphics.Color
import ml.bmlzootown.hydravion.R

/**
 * Gets color of a tag
 *
 * @param name of tag to get unique color for
 * @return Color [Int] of tag
 */
fun Context.getTagColor(name: String): Int =
    resources.let { resources ->
        when (name.lowercase()[0]) {
            'a' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[0])
            'b' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[1])
            'c' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[2])
            'd' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[3])
            'e' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[4])
            'f' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[5])
            'g' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[6])
            'h' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[7])
            'i' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[8])
            'j' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[9])
            'k' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[10])
            'l' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[11])
            'm' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[12])
            'n' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[13])
            'o' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[14])
            'p' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[15])
            'q' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[16])
            'r' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[17])
            's' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[18])
            't' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[19])
            'u' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[20])
            'v' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[21])
            'w' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[22])
            'x' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[23])
            'y' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[24])
            'z' -> Color.parseColor(resources.getStringArray(R.array.default_colors)[25])
            else -> Color.parseColor(resources.getStringArray(R.array.default_colors)[26])
        }
    }
package ml.bmlzootown.hydravion.chat

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.widget.TextView
import androidx.core.text.HtmlCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import ml.bmlzootown.hydravion.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders Floatplane chat messages with `:emote:` images and `@username` mentions
 * colored via the same hash palette as display names.
 *
 * Text segments are HTML-entity decoded to match the web client's SafeMarkdown
 * `entity` handling (`&quot;` → `"`, etc.).
 */
object ChatEmoteRenderer {

    private val TOKEN_REGEX =
        Regex("""(@[a-z0-9_-]{4,20})(?=$|[^@a-z0-9_-])|(:[a-z0-9_-]{1,32}:)""", RegexOption.IGNORE_CASE)

    private val EMOTE_REGEX = Regex("""^:[a-z0-9_-]{1,32}:$""", RegexOption.IGNORE_CASE)

    private val MENTION_REGEX = Regex("""^@[a-z0-9_-]{4,20}$""", RegexOption.IGNORE_CASE)

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    fun render(textView: TextView, rawMessage: String, emotesByCode: Map<String, String>) {
        if (rawMessage.isEmpty()) {
            textView.text = ""
            return
        }

        val needsTokens = rawMessage.contains('@') || rawMessage.contains(':')
        if (!needsTokens) {
            textView.text = decodeHtmlEntities(rawMessage)
            return
        }

        val emoteSize = textView.resources.getDimensionPixelSize(R.dimen.live_chat_emote_size)
        val builder = SpannableStringBuilder()
        var last = 0
        val pendingLoads = LinkedHashMap<String, MutableList<IntRange>>()

        for (match in TOKEN_REGEX.findAll(rawMessage)) {
            if (match.range.first > last) {
                builder.append(decodeHtmlEntities(rawMessage.substring(last, match.range.first)))
            }
            val token = match.value
            when {
                EMOTE_REGEX.matches(token) -> {
                    appendEmote(builder, token, emotesByCode, textView, emoteSize, pendingLoads)
                }
                MENTION_REGEX.matches(token) -> {
                    appendMention(builder, token)
                }
                else -> builder.append(decodeHtmlEntities(token))
            }
            last = match.range.last + 1
        }
        if (last < rawMessage.length) {
            builder.append(decodeHtmlEntities(rawMessage.substring(last)))
        }

        textView.setText(builder, TextView.BufferType.SPANNABLE)

        if (pendingLoads.isEmpty()) {
            return
        }

        val bindToken = rawMessage
        textView.setTag(R.id.chat_message, bindToken)
        for ((url, ranges) in pendingLoads) {
            Glide.with(textView)
                .asBitmap()
                .load(url)
                .into(object : CustomTarget<Bitmap>(emoteSize, emoteSize) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        bitmapCache[url] = resource
                        if (textView.getTag(R.id.chat_message) != bindToken) {
                            return
                        }
                        val current = textView.text
                        if (current !is Spannable) {
                            return
                        }
                        for (range in ranges) {
                            if (range.last < current.length) {
                                applyImageSpan(current, textView, resource, range.first, range.last + 1, emoteSize)
                            }
                        }
                        textView.invalidate()
                    }

                    override fun onLoadCleared(placeholder: Drawable?) = Unit
                })
        }
    }

    /**
     * Decode HTML entities the same way FP's markdown `entity` rule does before display.
     * Uses FROM_HTML_MODE_LEGACY so named + numeric entities are handled; result is plain text.
     */
    private fun decodeHtmlEntities(text: String): String {
        if (text.isEmpty() || text.indexOf('&') < 0) {
            return text
        }
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun appendMention(builder: SpannableStringBuilder, token: String) {
        val username = token.substring(1)
        val start = builder.length
        builder.append(token)
        val end = builder.length
        val color = ChatUsernameColors.colorForUsername(username)
        builder.setSpan(
            ForegroundColorSpan(color),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            StyleSpan(Typeface.BOLD),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun appendEmote(
        builder: SpannableStringBuilder,
        token: String,
        emotesByCode: Map<String, String>,
        textView: TextView,
        emoteSize: Int,
        pendingLoads: LinkedHashMap<String, MutableList<IntRange>>
    ) {
        val code = token.substring(1, token.length - 1)
        val imageUrl = resolveImageUrl(
            emotesByCode[code] ?: emotesByCode[code.lowercase()].orEmpty()
        )
        if (imageUrl.isEmpty()) {
            builder.append(token)
            return
        }
        val start = builder.length
        builder.append("\uFFFC")
        val end = builder.length
        val cached = bitmapCache[imageUrl]
        if (cached != null && !cached.isRecycled) {
            applyImageSpan(builder, textView, cached, start, end, emoteSize)
        } else {
            pendingLoads.getOrPut(imageUrl) { mutableListOf() }
                .add(start until end)
        }
    }

    private fun applyImageSpan(
        spannable: Spannable,
        textView: TextView,
        bitmap: Bitmap,
        start: Int,
        end: Int,
        size: Int
    ) {
        val drawable = BitmapDrawable(textView.resources, bitmap)
        drawable.setBounds(0, 0, size, size)
        spannable.setSpan(
            ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    fun clearCache() {
        bitmapCache.clear()
    }

    private fun resolveImageUrl(image: String): String {
        if (image.isEmpty()) return ""
        return when {
            image.startsWith("https://", ignoreCase = true) -> image
            image.startsWith("http://", ignoreCase = true) -> image
            image.startsWith("//") -> "https:$image"
            image.startsWith("/") -> "https://www.floatplane.com$image"
            else -> image
        }
    }
}

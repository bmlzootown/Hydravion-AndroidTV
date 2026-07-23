package ml.bmlzootown.hydravion.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ml.bmlzootown.hydravion.R

class LiveChatAdapter : RecyclerView.Adapter<LiveChatAdapter.MessageViewHolder>() {

    private val messages = ArrayList<RadioChatter>(MAX_MESSAGES)
    private val emotesByCode = HashMap<String, String>()

    fun setEmotes(emotes: List<ChatEmote>) {
        emotesByCode.clear()
        for (emote in emotes) {
            if (emote.code.isEmpty() || emote.image.isEmpty()) continue
            emotesByCode[emote.code] = emote.image
            emotesByCode[emote.code.lowercase()] = emote.image
        }
        notifyDataSetChanged()
    }

    fun addMessage(message: RadioChatter) {
        if (messages.any { it.id == message.id && message.id.isNotEmpty() }) {
            return
        }
        // Per-message emote payloads (if present) merge into the lookup map.
        for (emote in message.emotes) {
            if (emote.code.isEmpty() || emote.image.isEmpty()) continue
            emotesByCode[emote.code] = emote.image
            emotesByCode[emote.code.lowercase()] = emote.image
        }
        if (messages.size >= MAX_MESSAGES) {
            messages.removeAt(0)
            notifyItemRemoved(0)
        }
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        val count = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, count)
        ChatEmoteRenderer.clearCache()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position], emotesByCode)
    }

    override fun getItemCount(): Int = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val modBadge: ImageView = itemView.findViewById(R.id.chat_mod_badge)
        private val username: TextView = itemView.findViewById(R.id.chat_username)
        private val message: TextView = itemView.findViewById(R.id.chat_message)

        fun bind(chatter: RadioChatter, emotesByCode: Map<String, String>) {
            username.text = chatter.username
            username.setTextColor(ChatUsernameColors.colorForUsername(chatter.username))
            modBadge.visibility =
                if (ChatUsernameColors.isStaffBadge(chatter.userType)) View.VISIBLE else View.GONE
            ChatEmoteRenderer.render(message, chatter.message, emotesByCode)
        }
    }

    companion object {
        private const val MAX_MESSAGES = 80
    }
}

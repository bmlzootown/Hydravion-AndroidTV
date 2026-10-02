package ml.bmlzootown.hydravion.chat

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ml.bmlzootown.hydravion.R
import ml.bmlzootown.hydravion.ThemeManager
import ml.bmlzootown.hydravion.browse.MainFragment
import ml.bmlzootown.hydravion.poll.LivePollPanelController

/**
 * Chat-only screen for testing livestream chat without needing an active stream.
 * Also joins the creator poll room so live polls appear above the message list.
 */
class LiveChatActivity : FragmentActivity() {

    private var chatClient: LiveChatClient? = null
    private var pollPanel: LivePollPanelController? = null
    private var adapter: LiveChatAdapter? = null
    private var statusView: TextView? = null
    private var listView: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_chat)

        val livestreamId = intent.getStringExtra(EXTRA_LIVESTREAM_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val creatorId = intent.getStringExtra(EXTRA_CREATOR_ID).orEmpty()

        if (livestreamId.isEmpty()) {
            Toast.makeText(this, R.string.live_chat_no_id, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        statusView = findViewById(R.id.live_chat_standalone_status)
        listView = findViewById(R.id.live_chat_standalone_list)
        findViewById<TextView>(R.id.live_chat_standalone_title).text =
            if (title.isNotEmpty()) getString(R.string.live_chat_for, title) else getString(R.string.live_chat)

        adapter = LiveChatAdapter()
        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        listView?.layoutManager = layoutManager
        listView?.adapter = adapter
        listView?.itemAnimator = null

        setupPolls(creatorId)

        chatClient = LiveChatClient.getInstance(this)
        chatClient?.connect(livestreamId, object : LiveChatClient.Listener {
            override fun onConnected() {
                statusView?.setText(R.string.live_chat_connecting)
            }

            override fun onJoined(emotes: List<ChatEmote>) {
                statusView?.setText(R.string.live_chat_connected)
                adapter?.setEmotes(emotes)
                MainFragment.dLog(TAG, "Joined $livestreamId with ${emotes.size} emotes")
            }

            override fun onMessage(message: RadioChatter) {
                adapter?.addMessage(message)
                val count = adapter?.itemCount ?: 0
                if (count > 0) {
                    listView?.scrollToPosition(count - 1)
                }
            }

            override fun onError(message: String) {
                statusView?.setText(R.string.live_chat_disconnected)
                MainFragment.dError(TAG, message)
                Toast.makeText(this@LiveChatActivity, message, Toast.LENGTH_SHORT).show()
            }

            override fun onDisconnected() {
                statusView?.setText(R.string.live_chat_disconnected)
            }
        })
    }

    private fun setupPolls(creatorId: String) {
        if (creatorId.isEmpty()) {
            MainFragment.dLog(TAG, "No creator id — polls unavailable in standalone chat")
            findViewById<View>(R.id.live_poll_panel)?.visibility = View.GONE
            return
        }
        val root = findViewById<View>(android.R.id.content)
        pollPanel = LivePollPanelController(root) {
            // Panel manages its own visibility; nothing else to resize here.
        }
        pollPanel?.connect(creatorId)
    }

    override fun onDestroy() {
        pollPanel?.disconnect()
        pollPanel = null
        chatClient?.disconnect()
        chatClient = null
        adapter?.clear()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_LIVESTREAM_ID = "live_stream_id"
        const val EXTRA_TITLE = "live_chat_title"
        const val EXTRA_CREATOR_ID = "live_chat_creator_id"
        private const val TAG = "LiveChatActivity"
    }
}

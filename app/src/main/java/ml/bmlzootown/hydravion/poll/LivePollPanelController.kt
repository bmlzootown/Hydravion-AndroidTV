package ml.bmlzootown.hydravion.poll

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import ml.bmlzootown.hydravion.R
import java.time.Instant

/**
 * Binds active Floatplane polls into the player / standalone chat UI.
 * Supports collapse/expand and auto-hides closed polls after a short delay,
 * with a draining timer bar like the website.
 */
class LivePollPanelController(
    private val root: View,
    private val onVisibilityChanged: Runnable
) {
    private val panel: View = root.findViewById(R.id.live_poll_panel)
    private val headerRow: View = root.findViewById(R.id.live_poll_header_row)
    private val headerLabel: TextView = root.findViewById(R.id.live_poll_header)
    private val collapseToggle: TextView = root.findViewById(R.id.live_poll_collapse_toggle)
    private val body: View = root.findViewById(R.id.live_poll_body)
    private val titleView: TextView = root.findViewById(R.id.live_poll_title)
    private val optionsScroll: android.widget.ScrollView = root.findViewById(R.id.live_poll_options_scroll)
    private val optionsContainer: LinearLayout = root.findViewById(R.id.live_poll_options)
    private val statusView: TextView = root.findViewById(R.id.live_poll_status)
    private val dismissTimer: ProgressBar = root.findViewById(R.id.live_poll_dismiss_timer)
    private val inflater = LayoutInflater.from(root.context)
    private val handler = Handler(Looper.getMainLooper())

    private var pollClient: LivePollClient? = null
    private var activePoll: PollInfo? = null
    private var voting = false
    private var collapsed = false
    private var dismissScheduledForId: String? = null
    private val dismissedPollIds = mutableSetOf<String>()

    private var dismissRunnable: Runnable? = null
    private var endCheckRunnable: Runnable? = null
    private var dismissAnimator: ObjectAnimator? = null

    val isVisible: Boolean
        get() = panel.visibility == View.VISIBLE

    init {
        headerRow.setOnClickListener { toggleCollapsed() }
        applyCollapsedState()
        resetDismissTimerBar()
    }

    fun connect(creatorId: String) {
        if (creatorId.isEmpty()) return
        if (pollClient != null) return
        pollClient = LivePollClient.getInstance(root.context)
        pollClient?.connect(creatorId, object : LivePollClient.Listener {
            override fun onPollsChanged(polls: List<PollInfo>) {
                val open = polls.firstOrNull { !it.hasEnded && it.id !in dismissedPollIds }
                val closed = polls.firstOrNull {
                    it.hasEnded && it.id !in dismissedPollIds
                }
                bindPoll(open ?: closed)
            }

            override fun onError(message: String) {
                if (isVisible) {
                    statusView.text = message
                }
            }
        })
    }

    fun disconnect() {
        cancelTimers()
        pollClient?.disconnect()
        pollClient = null
        activePoll = null
        dismissedPollIds.clear()
        dismissScheduledForId = null
        collapsed = false
        hidePanel()
    }

    private fun toggleCollapsed() {
        if (!isVisible) return
        collapsed = !collapsed
        applyCollapsedState()
        onVisibilityChanged.run()
    }

    private fun applyCollapsedState() {
        body.visibility = if (collapsed) View.GONE else View.VISIBLE
        val poll = activePoll
        if (collapsed && poll != null) {
            headerLabel.text = root.context.getString(R.string.live_poll_header_collapsed, poll.title)
            collapseToggle.setText(R.string.live_poll_expand_symbol)
            headerRow.contentDescription = root.context.getString(R.string.live_poll_expand)
        } else {
            headerLabel.setText(R.string.live_poll)
            collapseToggle.setText(R.string.live_poll_collapse_symbol)
            headerRow.contentDescription = root.context.getString(R.string.live_poll_collapse)
        }
    }

    private fun bindPoll(poll: PollInfo?) {
        if (poll == null || poll.options.isEmpty() || poll.id in dismissedPollIds) {
            hidePanel()
            return
        }

        val previousId = activePoll?.id
        val wasHidden = panel.visibility != View.VISIBLE
        val newlyOpened = previousId != poll.id || wasHidden
        activePoll = poll

        if (newlyOpened && !poll.hasEnded) {
            // Fresh open poll: expand and clear any prior dismiss timer.
            collapsed = false
            cancelDismiss()
            dismissScheduledForId = null
        } else if (poll.hasEnded && previousId == poll.id) {
            // Show final results before the auto-dismiss timer fires.
            collapsed = false
        }

        panel.visibility = View.VISIBLE
        titleView.text = poll.title
        applyCollapsedState()

        val counts = poll.tallyCounts()
        val total = counts.sum().coerceAtLeast(0)
        val ended = poll.hasEnded
        val voted = poll.voted == true
        val votedIndex = poll.voteInfo?.optionIndex ?: -1
        val showResults = ended || voted

        optionsContainer.removeAllViews()
        val canVote = !ended && !voted && !voting
        var firstOption: View? = null
        poll.options.forEachIndexed { index, label ->
            val optionView = inflater.inflate(R.layout.item_poll_option, optionsContainer, false) as TextView
            val count = counts.getOrElse(index) { 0 }
            val percent = if (total > 0) (count * 100) / total else 0
            optionView.text = if (showResults) {
                root.context.getString(R.string.live_poll_option_votes, label, percent, count)
            } else {
                label
            }
            optionView.isSelected = votedIndex == index
            optionView.isFocusable = canVote
            optionView.isClickable = canVote
            if (canVote) {
                optionView.setOnClickListener { submitVote(poll.id, index) }
                if (firstOption == null) firstOption = optionView
            } else {
                optionView.setOnClickListener(null)
            }
            optionView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    optionsScroll.post { scrollOptionIntoView(v) }
                }
            }
            optionsContainer.addView(optionView)
        }

        statusView.text = when {
            ended -> root.context.getString(R.string.live_poll_closed)
            voted -> root.context.getString(R.string.live_poll_voted)
            else -> root.context.getString(R.string.live_poll_vote_hint)
        }

        if (!ended) {
            resetDismissTimerBar()
        }

        scheduleEndHandling(poll)
        onVisibilityChanged.run()

        if (wasHidden || newlyOpened) {
            if (!poll.hasEnded) {
                Toast.makeText(
                    root.context,
                    root.context.getString(R.string.live_poll_started, poll.title),
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (canVote && !collapsed) {
                firstOption?.post { firstOption?.requestFocus() }
            } else {
                headerRow.post { headerRow.requestFocus() }
            }
        }
    }

    private fun scrollOptionIntoView(option: View) {
        val parent = optionsContainer
        val top = option.top
        val bottom = option.bottom
        val scrollY = optionsScroll.scrollY
        val visibleBottom = scrollY + optionsScroll.height
        when {
            top < scrollY -> optionsScroll.smoothScrollTo(0, top)
            bottom > visibleBottom -> optionsScroll.smoothScrollTo(0, bottom - optionsScroll.height)
        }
        // Keep ScrollView's own focus scrolling in sync for nested layouts.
        optionsScroll.requestChildFocus(parent, option)
    }

    private fun scheduleEndHandling(poll: PollInfo) {
        cancelEndCheck()
        if (poll.hasEnded) {
            scheduleDismiss(poll.id)
            return
        }
        val end = poll.endDate?.takeIf { it.isNotBlank() } ?: return
        val delayMs = try {
            Instant.parse(end).toEpochMilli() - Instant.now().toEpochMilli()
        } catch (_: Exception) {
            return
        }
        if (delayMs <= 0L) {
            // End time already passed but state may not have refreshed yet.
            scheduleDismiss(poll.id)
            return
        }
        val pollId = poll.id
        endCheckRunnable = Runnable {
            val current = activePoll
            if (current != null && current.id == pollId) {
                // Re-bind so hasEnded flips and results show, then dismiss timer starts.
                bindPoll(current)
            }
        }
        handler.postDelayed(endCheckRunnable!!, delayMs + 250L)
    }

    private fun scheduleDismiss(pollId: String) {
        if (dismissScheduledForId == pollId) return
        cancelDismiss()
        dismissScheduledForId = pollId
        startDismissTimerBar()
        dismissRunnable = Runnable {
            dismissedPollIds.add(pollId)
            dismissScheduledForId = null
            if (activePoll?.id == pollId) {
                hidePanel()
            }
        }
        handler.postDelayed(dismissRunnable!!, DISMISS_AFTER_CLOSE_MS)
    }

    private fun startDismissTimerBar() {
        dismissAnimator?.cancel()
        dismissTimer.visibility = View.VISIBLE
        dismissTimer.max = TIMER_MAX
        dismissTimer.progress = TIMER_MAX
        dismissAnimator = ObjectAnimator.ofInt(dismissTimer, "progress", TIMER_MAX, 0).apply {
            duration = DISMISS_AFTER_CLOSE_MS
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (dismissTimer.progress <= 0) {
                        dismissTimer.visibility = View.GONE
                    }
                }
            })
            start()
        }
    }

    private fun resetDismissTimerBar() {
        dismissAnimator?.cancel()
        dismissAnimator = null
        dismissTimer.animate().cancel()
        dismissTimer.progress = TIMER_MAX
        dismissTimer.visibility = View.GONE
    }

    private fun hidePanel() {
        cancelTimers()
        activePoll = null
        collapsed = false
        panel.visibility = View.GONE
        optionsContainer.removeAllViews()
        resetDismissTimerBar()
        applyCollapsedState()
        onVisibilityChanged.run()
    }

    private fun cancelDismiss() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        resetDismissTimerBar()
    }

    private fun cancelEndCheck() {
        endCheckRunnable?.let { handler.removeCallbacks(it) }
        endCheckRunnable = null
    }

    private fun cancelTimers() {
        cancelDismiss()
        cancelEndCheck()
        dismissScheduledForId = null
    }

    private fun submitVote(pollId: String, optionIndex: Int) {
        if (voting) return
        voting = true
        statusView.setText(R.string.live_poll_vote_hint)
        pollClient?.vote(pollId, optionIndex) { ok, error ->
            voting = false
            if (!ok) {
                Toast.makeText(
                    root.context,
                    error ?: root.context.getString(R.string.live_poll_vote_failed),
                    Toast.LENGTH_SHORT
                ).show()
                activePoll?.let { bindPoll(it) }
            }
        }
    }

    companion object {
        /** Matches the short post-close linger on floatplane.com before the poll UI goes away. */
        private const val DISMISS_AFTER_CLOSE_MS = 20_000L
        private const val TIMER_MAX = 1000
    }
}

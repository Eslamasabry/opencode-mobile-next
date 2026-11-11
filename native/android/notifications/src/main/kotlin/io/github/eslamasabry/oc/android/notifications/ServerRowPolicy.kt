package io.github.eslamasabry.oc.android.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What to do with the phone server's own row (4747) when the conversations' state changes. */
public enum class ServerRowStep {
    /** Show the conversations' words there (a run works or waits for the person). */
    Lend,

    /** Give the server its own words back ("Ready"), because nothing runs and nothing waits any more. */
    Restore,

    /** Leave the row to whoever holds it now. */
    Leave,
}

/**
 * The phone server's own row says "Ready · nothing running". It must not say so while a conversation on the phone works
 * or waits for the person (a request alert shows, the reply watch has ended after its 10 s of no progress). This rule is
 * the whole decision; it holds no timer and starts no service: the app's process-lifetime state drives it.
 */
public object ServerRowPolicy {
    public fun step(
        conversationsShown: Boolean,
        lent: Boolean,
        replyWatchActive: Boolean,
        liveModeActive: Boolean,
        appVisible: Boolean,
    ): ServerRowStep =
        when {
            // The watch and live mode write the row themselves and hand it back through [ReplyRunSource.returnRowToServer].
            replyWatchActive || liveModeActive -> ServerRowStep.Leave
            // In the app the person sees the conversations themselves; the row only needs to stop claiming otherwise.
            appVisible -> if (lent) ServerRowStep.Restore else ServerRowStep.Leave
            conversationsShown -> ServerRowStep.Lend
            lent -> ServerRowStep.Restore
            else -> ServerRowStep.Leave
        }

    /**
     * The conversations' words as the server's row shows them. A wait for the person can last hours and is the one
     * state whose row the person cannot stop by stopping a reply, so the waiting row keeps the server's own
     * "Stop OpenCode" (the third button; live mode's Pause is never on a lent row). A working row ends by itself and
     * keeps Open and Stop the reply only, as in Flutter's working row.
     */
    public fun lentWords(row: StatusContent): StatusContent =
        if (row.kind != StatusKind.NeedsYou || StatusAction.StopServer in row.actions) row
        else row.copy(actions = (row.actions + StatusAction.StopServer).take(StatusContent.MAX_ACTIONS))
}

/** What [ServerRowLender] reads each time it decides; read fresh on every decision, also for a delayed post. */
public data class ServerRowFacts(
    /** The conversations' row (working, needs you), from the dock; null when nothing runs and nothing waits. */
    val conversations: StatusContent?,
    /** Reply watch is on (joined to this row or on its own service): it writes the row itself while it runs. */
    val replyWatchActive: Boolean,
    /** "Stay connected" holds the row: live mode writes it. */
    val liveModeActive: Boolean,
    val appVisible: Boolean,
)

/**
 * The one decision of what the phone server's row (4747) says while "Stay connected" is off. Every write of the app's
 * words into that row outside reply watch goes through [settle], and reply watch hands the row back through it too
 * ([ReplyRunSource.returnRowToServer]), so the server's "Ready" and a chat's "needs you" never alternate.
 *
 * Who writes, at any moment: live mode while it is active; reply watch while it runs (a delayed post of this lender is
 * dropped then, and the watch's end re-posts the current words once); otherwise this lender: the conversations' words
 * while the app is hidden and any run works or waits, the server's own words once when that stops or the app shows.
 *
 * Stale words: words are lent only while the server's own `specialUse` foreground service holds the row
 * ([serverHoldsRow], checked again before every post, delayed ones included). That service runs in the app's process,
 * so while it holds the row the process is a foreground-service process, never cached or frozen, and the next change
 * is seen. When the service stops it takes the row (and the lent words) away with it. So lent words can only stand
 * while something is there to correct them.
 *
 * No service, no timer of its own beyond the one-second trailing post, nothing unbounded.
 */
public class ServerRowLender(
    private val scope: CoroutineScope,
    /** Monotonic milliseconds. */
    private val elapsedMs: () -> Long,
    private val facts: () -> ServerRowFacts,
    /** The phone server's foreground service holds row 4747 now. */
    private val serverHoldsRow: () -> Boolean,
    /** Posts lent words into row 4747. */
    private val post: (StatusContent) -> Unit,
    /** Re-posts the server's own row; false when no service holds the row any more. */
    private val restore: () -> Boolean,
) {
    private var lent = false
    private val throttle = StatusThrottle(StatusThrottle.DOCK_GAP_MS)
    private var trailing: Job? = null

    /** The conversations' words stand in the server's row now. */
    public val lending: Boolean get() = lent

    /**
     * Puts the right words in the row now. [watchEnding] is reply watch handing the row back at its end: then the
     * server's own words or the conversations' words are always posted once. False when no service holds the row.
     */
    public fun settle(watchEnding: Boolean = false): Boolean {
        val now = facts()
        val watching = now.replyWatchActive && !watchEnding
        val step =
            ServerRowPolicy.step(
                conversationsShown = now.conversations != null,
                lent = lent,
                replyWatchActive = watching,
                liveModeActive = now.liveModeActive,
                appVisible = now.appVisible,
            )
        return when {
            step == ServerRowStep.Lend && now.conversations != null -> lend(now.conversations)
            step == ServerRowStep.Leave && !watchEnding -> {
                // Another writer holds the row: drop a pending post, and post in full when the row comes back.
                if (watching || now.liveModeActive) forget()
                true
            }
            else -> {
                lent = false
                forget()
                restore()
            }
        }
    }

    private fun lend(row: StatusContent): Boolean {
        if (!serverHoldsRow()) {
            lent = false
            forget()
            return false
        }
        lent = true
        when (val step = throttle.offer(ServerRowPolicy.lentWords(row), elapsedMs())) {
            is StatusThrottle.Step.Post -> post(step.content)
            is StatusThrottle.Step.Wait ->
                if (trailing == null)
                    trailing =
                        scope.launch {
                            delay(step.delayMs)
                            trailing = null
                            // The newest facts decide again: the watch may run now, the server may have left.
                            settle()
                        }
            StatusThrottle.Step.Skip -> Unit
        }
        return true
    }

    private fun forget() {
        trailing?.cancel()
        trailing = null
        throttle.reset()
    }
}

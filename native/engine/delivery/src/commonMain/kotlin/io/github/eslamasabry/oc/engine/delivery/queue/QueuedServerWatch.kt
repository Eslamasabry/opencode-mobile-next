package io.github.eslamasabry.oc.engine.delivery.queue

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What [QueuedServerWatch] needs from the app (all reads are cheap and local). */
public interface QueuedServerPorts {
    /** The shared `oc.offlineQueue`, as stored. */
    public fun queue(): QueueRead

    /** True when this saved server already has a live connection. */
    public fun live(profile: ProfileId): Boolean

    /**
     * Makes sure that live server's delivery exists and asks it to send what waits once its stream is connected
     * (B-146: a connection nobody reads the delivery of never sends its queue). Local and cheap; never a second
     * send, because the queue's dispatch marker decides.
     */
    public fun nudge(profile: ProfileId)

    /** True while watching may cost a request: the app is in use, there is a network, no battery saver. */
    public fun mayWatch(): Boolean

    /**
     * Opens that saved server's connection. On success its delivery sends the queued prompts once
     * the stream connects, under the queue's own never-twice rules. A refusal means "not now".
     */
    public suspend fun open(profile: ProfileId): Outcome<Unit>

    /** Lets go of a connection this watch opened, once nothing waits for it any more. */
    public suspend fun release(profile: ProfileId)
}

/**
 * B-146: prompts queued for a saved server that is not the one in use are sent once that server
 * can be reached, without the person connecting to it first (2.1.4 kept watching its saved
 * servers). Bounded and battery-aware:
 *
 * - only servers with an unsent, not-yet-dispatched queued prompt, at most [MAX_SERVERS] per round;
 * - only while [QueuedServerPorts.mayWatch] (in use, online, no battery saver);
 * - per server a backoff from [FIRST_WAIT] doubling to [LONGEST_WAIT] after each refused open;
 * - no new send path: opening the connection hands the prompt to that server's own offline queue,
 *   whose dispatch marker makes the send happen exactly once;
 * - every round, each live server with a waiting prompt is nudged ([QueuedServerPorts.nudge]): a connection
 *   opened elsewhere, or one whose last send pass stopped early, still sends once it can.
 *
 * A connection this watch opened is released when that server has nothing left waiting.
 */
public class QueuedServerWatch(
    private val ports: QueuedServerPorts,
    private val scope: CoroutineScope,
    private val now: () -> Long,
) {
    public companion object {
        public const val MAX_SERVERS: Int = 3
        public val ROUND: Duration = 15.seconds
        public val FIRST_WAIT: Duration = 15.seconds
        public val LONGEST_WAIT: Duration = 60.seconds

        /** Saved servers with a queued prompt that has not left the device. An unreadable queue: none. */
        public fun waiting(read: QueueRead): Set<ProfileId> =
            (read as? QueueRead.Readable)
                ?.entries
                ?.filter { !it.dispatched && it.profileId.raw.isNotEmpty() }
                ?.mapTo(LinkedHashSet()) { it.profileId }
                .orEmpty()
    }

    private class Backoff(val nextAt: Long, val wait: Duration)

    private val backoff = HashMap<ProfileId, Backoff>()
    private val opened = LinkedHashSet<ProfileId>()
    private var job: Job? = null

    /** Starts the rounds (idempotent). */
    public fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                round()
                delay(ROUND)
            }
        }
    }

    public fun stop() {
        job?.cancel()
        job = null
    }

    /** One round. Runs only in the watch's own loop (and in tests), never concurrently. */
    internal suspend fun round() {
        val waiting = waiting(ports.queue())
        // Nothing waits any more: let go of what this watch opened, forget old backoff.
        for (profile in opened.toList()) {
            if (profile !in waiting) {
                opened -= profile
                ports.release(profile)
            }
        }
        backoff.keys.retainAll(waiting)
        if (!ports.mayWatch()) return
        for (profile in waiting) if (ports.live(profile)) ports.nudge(profile)
        val at = now()
        val due =
            waiting
                .filter { !ports.live(it) && (backoff[it]?.nextAt ?: 0L) <= at }
                .take(MAX_SERVERS)
        for (profile in due) {
            when (ports.open(profile)) {
                is Outcome.Ok -> {
                    opened += profile
                    backoff -= profile
                    ports.nudge(profile)
                }
                is Outcome.Failed -> {
                    val wait =
                        backoff[profile]?.wait?.let { minOf(it * 2, LONGEST_WAIT) } ?: FIRST_WAIT
                    backoff[profile] = Backoff(now() + wait.inWholeMilliseconds, wait)
                }
            }
        }
    }
}

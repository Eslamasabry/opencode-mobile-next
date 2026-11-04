package io.github.eslamasabry.oc.android.notifications

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The one ongoing status row as a pure function of app state: port of `lib/background/notification_status.dart`
 * (`buildNotificationStatus`, B-ANDR-042..047). [StatusController] gathers a [StatusInput]; [StatusModel.build] answers
 * with the [StatusContent] the renderer draws.
 *
 * Highest wins, in this order: needs you, Android's background limit, reconnecting, chats working, the phone is hot,
 * waiting on a provider, paused while idle, setting up, ready. A request is never repeated here: the row counts it,
 * the alert asks it.
 */
public enum class PauseReason {
    None,

    /** Android's six-hour-a-day background limit ended the connection. */
    AndroidLimit,

    /** The heat guard paused the work. */
    PhoneHot,

    /** The phone server stopped itself because nothing was running. */
    Idle,
}

/** One chat that is actually working (not waiting on a provider, not waiting on the person). */
public data class StatusChat(
    val id: String,
    /** The chat title the person sees; empty when untitled. */
    val title: String = "",
    val project: String = "",
    /** What it does now: the task in progress, or a generic tool sentence. */
    val liveLine: String? = null,
    val tasksDone: Int? = null,
    val tasksTotal: Int? = null,
    val elapsed: Duration? = null,
) {
    val hasTasks: Boolean get() = (tasksTotal ?: 0) > 0 && tasksDone != null
}

/** A chat waiting out a provider limit or retry backoff. Never carries the server's own message. */
public data class StatusRetry(
    val chatId: String,
    val chatTitle: String = "",
    /** The provider's display name, when known. */
    val provider: String? = null,
    /** When the next attempt runs, when the server said. */
    val nextTry: Instant? = null,
)

/** First-time phone setup, shown while it runs. */
public data class StatusSetup(val step: Int, val steps: Int, val name: String = "", val percent: Int? = null)

public data class StatusInput(
    /** Chats doing work now, most recently active first. */
    val working: List<StatusChat> = emptyList(),
    /** Requests waiting on the person: permissions, questions, forms. */
    val pendingCount: Int = 0,
    val firstPendingTitle: String = "",
    val firstPendingProject: String = "",
    val retries: List<StatusRetry> = emptyList(),
    val pause: PauseReason = PauseReason.None,
    val reconnecting: Boolean = false,
    /** What it was doing before the connection dropped, when known. */
    val reconnectingLastActivity: String? = null,
    /** The in-app server runs on this phone. */
    val phoneServerRunning: Boolean = false,
    val setup: StatusSetup? = null,
    /** How many servers are live. The server's name appears only above one. */
    val liveServerCount: Int = 1,
    val serverName: String = "",
    val lastReplyAt: Instant? = null,
    val lastReplyTitle: String = "",
    /** The profile the named chat belongs to, so **Stop the reply** targets exactly it. */
    val profileId: String = "",
)

/** The words of the status row, in the person's language. [AndroidStatusWords] reads `strings_notify_status.xml`. */
public interface StatusWords {
    public fun working(): String
    public fun needsYou(count: Int): String
    public fun stillWorking(count: Int): String
    public fun chatsWorking(count: Int): String
    public fun more(count: Int): String
    public fun waitingCount(count: Int): String
    public fun tasksProgress(done: Int, total: Int): String
    public fun minutes(count: Long): String
    public fun hours(count: Long): String
    public fun waitingProvider(provider: String): String
    public fun waitingGeneric(): String
    public fun triesAgainAt(time: String): String
    public fun triesAgainSoon(): String
    public fun pausedAndroidTitle(): String
    public fun pausedAndroidBody(): String
    public fun pausedHotTitle(): String
    public fun pausedHotBody(): String
    public fun pausedIdleTitle(): String
    public fun pausedIdleBody(): String
    public fun reconnectingTitle(server: String): String
    public fun reconnectingGeneric(): String
    public fun reconnectingBody(): String
    public fun readyTitle(): String
    public fun readyBody(): String
    public fun lastReply(ago: String): String
    public fun lastReplyNow(): String
    public fun setupTitle(step: Int, steps: Int): String
    public fun onServer(server: String): String
    public fun onThisPhone(): String
    public fun lockWorking(count: Int): String
    public fun lockWaiting(): String
    public fun lockPaused(): String
    public fun lockReconnecting(): String
    public fun lockReady(): String
    public fun lockSetup(): String
}

/** [StatusWords] from this module's resources, through [NotifyWords] (stored app language, phone digits). */
public class AndroidStatusWords(private val words: NotifyWords) : StatusWords {
    override fun working(): String = words.text(R.string.notify_status_working)
    override fun needsYou(count: Int): String = words.plural(R.plurals.notify_status_needs_you, count, count)
    override fun stillWorking(count: Int): String = words.text(R.string.notify_status_still_working, count)
    override fun chatsWorking(count: Int): String = words.text(R.string.notify_status_chats_working, count)
    override fun more(count: Int): String = words.text(R.string.notify_status_more, count)
    override fun waitingCount(count: Int): String = words.text(R.string.notify_status_waiting_count, count)
    override fun tasksProgress(done: Int, total: Int): String = words.text(R.string.notify_status_tasks_progress, done, total)
    override fun minutes(count: Long): String = words.text(R.string.notify_status_minutes, count)
    override fun hours(count: Long): String = words.text(R.string.notify_status_hours, count)
    override fun waitingProvider(provider: String): String = words.text(R.string.notify_status_waiting_provider, provider)
    override fun waitingGeneric(): String = words.text(R.string.notify_status_waiting_generic)
    override fun triesAgainAt(time: String): String = words.text(R.string.notify_status_tries_again_at, time)
    override fun triesAgainSoon(): String = words.text(R.string.notify_status_tries_again_soon)
    override fun pausedAndroidTitle(): String = words.text(R.string.notify_status_paused_android_title)
    override fun pausedAndroidBody(): String = words.text(R.string.notify_status_paused_android_body)
    override fun pausedHotTitle(): String = words.text(R.string.notify_status_paused_hot_title)
    override fun pausedHotBody(): String = words.text(R.string.notify_status_paused_hot_body)
    override fun pausedIdleTitle(): String = words.text(R.string.notify_status_paused_idle_title)
    override fun pausedIdleBody(): String = words.text(R.string.notify_status_paused_idle_body)
    override fun reconnectingTitle(server: String): String = words.text(R.string.notify_status_reconnecting_title, server)
    override fun reconnectingGeneric(): String = words.text(R.string.notify_status_reconnecting_generic)
    override fun reconnectingBody(): String = words.text(R.string.notify_status_reconnecting_body)
    override fun readyTitle(): String = words.text(R.string.notify_status_ready_title)
    override fun readyBody(): String = words.text(R.string.notify_status_ready_body)
    override fun lastReply(ago: String): String = words.text(R.string.notify_status_last_reply, ago)
    override fun lastReplyNow(): String = words.text(R.string.notify_status_last_reply_now)
    override fun setupTitle(step: Int, steps: Int): String = words.text(R.string.notify_status_setup_title, step, steps)
    override fun onServer(server: String): String = words.text(R.string.notify_status_on_server, server)
    override fun onThisPhone(): String = words.text(R.string.notify_status_on_this_phone)
    override fun lockWorking(count: Int): String = words.text(R.string.notify_status_lock_working, count)
    override fun lockWaiting(): String = words.text(R.string.notify_status_lock_waiting)
    override fun lockPaused(): String = words.text(R.string.notify_status_lock_paused)
    override fun lockReconnecting(): String = words.text(R.string.notify_status_lock_reconnecting)
    override fun lockReady(): String = words.text(R.string.notify_status_lock_ready)
    override fun lockSetup(): String = words.text(R.string.notify_status_lock_setup)
}

public object StatusModel {
    /** How many inbox lines are listed before "+N more". */
    public const val MAX_LINES: Int = 2

    private fun join(parts: List<String?>): String = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")

    /** Builds the row for [input] in [words]' language. [formatTime] writes a clock time the way the locale does. */
    public fun build(input: StatusInput, words: StatusWords, now: Instant, formatTime: (Instant) -> String): StatusContent {
        val several = input.liveServerCount > 1
        val server = input.serverName.trim()
        val serverLabel = if (several && server.isNotEmpty()) words.onServer(server) else ""

        fun span(value: Duration?): String {
            if (value == null || value.inWholeMinutes < 1) return ""
            return if (value.inWholeMinutes >= 60) words.hours(value.inWholeHours) else words.minutes(value.inWholeMinutes)
        }

        fun chatLines(chats: List<StatusChat>): List<String> {
            val shown = chats.take(MAX_LINES).map { chat ->
                val line = chat.liveLine?.trim().orEmpty()
                join(listOf(chat.title, line.ifEmpty { words.working() }))
            }
            val more = chats.size - shown.size
            return if (more > 0) shown + words.more(more) else shown
        }

        fun ref(session: String): ConversationRef? =
            if (session.isBlank()) null else ConversationRef(ProfileId(input.profileId), SessionId(session))

        fun content(
            kind: StatusKind,
            title: String,
            text: String = "",
            lines: List<String> = emptyList(),
            subText: String = "",
            progress: Int? = null,
            actions: List<StatusAction>,
            session: String = "",
            publicTitle: String,
            count: Int = 1,
        ): StatusContent =
            StatusContent.of(kind, title, text, lines, subText, progress, actions, ref(session), publicTitle = publicTitle, count = count)
                // A title is always worded here; the fallback only guards an empty translation.
                ?: StatusContent(kind, "OpenCode", actions = actions, publicTitle = publicTitle)

        val working = input.working

        // 1. Needs you: the title only counts; the text names whose request and how many others still work.
        if (input.pendingCount > 0) {
            val needs = words.needsYou(input.pendingCount)
            return content(
                StatusKind.NeedsYou,
                title = needs,
                text = join(listOf(input.firstPendingTitle, input.firstPendingProject, if (working.isNotEmpty()) words.stillWorking(working.size) else null)),
                lines = if (working.isEmpty()) emptyList() else chatLines(working),
                subText = serverLabel,
                actions = listOf(StatusAction.Open),
                publicTitle = join(listOf("OpenCode", needs)),
                count = input.pendingCount,
            )
        }

        // 2. Android ended the background connection.
        if (input.pause == PauseReason.AndroidLimit) {
            return content(
                StatusKind.PausedAndroid, words.pausedAndroidTitle(), words.pausedAndroidBody(),
                actions = listOf(StatusAction.Open), publicTitle = words.lockPaused(),
            )
        }

        // 3. The connection dropped: whatever was working may not still be.
        if (input.reconnecting) {
            val last = input.reconnectingLastActivity?.trim().orEmpty()
            return content(
                StatusKind.Reconnecting,
                title = if (several && server.isNotEmpty()) words.reconnectingTitle(server) else words.reconnectingGeneric(),
                text = last.ifEmpty { words.reconnectingBody() },
                actions = listOf(StatusAction.Open),
                publicTitle = words.lockReconnecting(),
            )
        }

        // 4. Chats working.
        if (working.isNotEmpty()) {
            val waiting = input.retries.size
            val waitingSuffix = if (waiting > 0) words.waitingCount(waiting) else ""
            val publicTitle = words.lockWorking(working.size)
            if (working.size == 1) {
                val chat = working.single()
                val line = chat.liveLine?.trim().orEmpty()
                val tasks = if (chat.hasTasks) words.tasksProgress(chat.tasksDone!!, chat.tasksTotal!!) else ""
                return content(
                    StatusKind.Working,
                    title = line.ifEmpty { words.working() },
                    text = join(listOf(chat.title, tasks, waitingSuffix)),
                    subText = join(listOf(chat.project, span(chat.elapsed), serverLabel)),
                    progress = if (chat.hasTasks) (chat.tasksDone!! * 100 / chat.tasksTotal!!).coerceIn(0, 100) else null,
                    actions = listOf(StatusAction.Open, StatusAction.StopReply),
                    session = chat.id,
                    publicTitle = publicTitle,
                )
            }
            val lines = chatLines(working)
            return content(
                StatusKind.SeveralWorking,
                title = words.chatsWorking(working.size),
                text = join(listOf(lines.first(), waitingSuffix)),
                lines = lines,
                subText = serverLabel,
                actions = listOf(StatusAction.Open),
                publicTitle = publicTitle,
                count = working.size,
            )
        }

        // 5. The phone is hot.
        if (input.pause == PauseReason.PhoneHot) {
            return content(
                StatusKind.PausedHot, words.pausedHotTitle(), words.pausedHotBody(),
                actions = listOf(StatusAction.Open), publicTitle = words.lockPaused(),
            )
        }

        // 6. Waiting on a provider: the retry that comes first is shown.
        if (input.retries.isNotEmpty()) {
            val retry = input.retries.sortedWith(compareBy(nullsLast()) { it.nextTry }).first()
            val provider = retry.provider?.trim().orEmpty()
            val next = retry.nextTry
            val tries = if (next == null || next < now) words.triesAgainSoon() else words.triesAgainAt(formatTime(next))
            return content(
                StatusKind.Waiting,
                title = if (provider.isEmpty()) words.waitingGeneric() else words.waitingProvider(provider),
                text = join(listOf(tries, retry.chatTitle)),
                subText = serverLabel,
                actions = listOf(StatusAction.Open, StatusAction.StopReply),
                session = retry.chatId,
                publicTitle = words.lockWaiting(),
            )
        }

        // 7. The phone server stopped itself while idle.
        if (input.pause == PauseReason.Idle) {
            return content(
                StatusKind.PausedIdle, words.pausedIdleTitle(), words.pausedIdleBody(),
                actions = listOf(StatusAction.Start), publicTitle = words.lockPaused(),
            )
        }

        // 8. First setup.
        input.setup?.let { setup ->
            return content(
                StatusKind.Setup,
                title = words.setupTitle(setup.step, setup.steps),
                text = setup.name,
                progress = setup.percent?.coerceIn(0, 100),
                actions = listOf(StatusAction.Open),
                publicTitle = words.lockSetup(),
            )
        }

        // 9. Ready.
        val at = input.lastReplyAt
        val text =
            if (at == null) {
                words.readyBody()
            } else {
                val ago = span(now - at)
                join(listOf(if (ago.isEmpty()) words.lastReplyNow() else words.lastReply(ago), input.lastReplyTitle))
            }
        return content(
            StatusKind.Idle,
            title = words.readyTitle(),
            text = text,
            subText = if (input.phoneServerRunning) words.onThisPhone() else serverLabel,
            actions = if (input.phoneServerRunning) listOf(StatusAction.NewChat, StatusAction.StopServer) else listOf(StatusAction.NewChat),
            publicTitle = words.lockReady(),
        )
    }
}

package io.github.eslamasabry.oc.android.notifications

import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Turns chat state into the one status row (B-ANDR-041). Port of the Flutter `ConnectionController`'s
 * `notificationStatusInput` / `_noteTasks` / `_noteRunStarted` / `_noteRunEnded` (lib/state/connection/surfaces.dart),
 * protocol-neutral: the app graph feeds it what the connection and request engines already know (sessions, the polled
 * busy set, session status events, plans, pending requests, stream state, pauses) and posts [status] through the
 * [StatusThrottle]. Words come from [words] on every call, so the row follows the app language.
 *
 * Single-threaded by contract: call it from one lane (the attention engine's), like every engine here.
 */
public class StatusController(
    private val words: StatusWords,
    private val clock: Clock,
    /** A clock time the way the person's locale writes it (the retry time). */
    private val formatTime: (Instant) -> String,
) {
    /** A conversation as the status needs it. [title] is already the display title (markers cut by the caller). */
    public data class Session(
        val id: String,
        val title: String = "",
        val directory: String? = null,
        /** Non-null for a sub-agent session: never a chat on the status. */
        val parentId: String? = null,
        val updatedAt: Long = 0,
    )

    /** One item of the agent's plan (`todowrite` input or metadata, `todo.updated`). */
    public data class Todo(val content: String, val status: String)

    /** A session status event (`session.status`, `session.idle`). */
    public sealed interface Run {
        public data object Busy : Run

        public data object Idle : Run

        /** Waiting out a provider limit; [provider] is a display name, never the server's message. */
        public data class Retry(val nextTry: Instant?, val provider: String? = null) : Run
    }

    private data class Plan(val done: Int, val total: Int, val current: String?)

    private var sessions: Map<String, Session> = emptyMap()
    private val busy = linkedSetOf<String>()
    private val retries = mutableMapOf<String, Run.Retry>()
    private val plans = mutableMapOf<String, Plan>()
    private val toolLines = mutableMapOf<String, String>()
    private val busySince = mutableMapOf<String, Instant>()
    private var permissionSessions: List<String> = emptyList()
    private var questionSessions: List<String> = emptyList()
    private var formSessions: List<String> = emptyList()
    private var lastActivity: String? = null
    private var lastReply: Pair<String, Instant>? = null

    public var profileId: String = ""
    public var serverName: String = ""
    public var liveServerCount: Int = 1
    public var reconnecting: Boolean = false
    public var phoneServerRunning: Boolean = false

    /** The heat guard paused the work (folds into the one row, no second notification). */
    public var thermalPaused: Boolean = false

    /** Android's background limit ended the connection (`onTimeout(dataSync)`). */
    public var androidLimit: Boolean = false
    public var setup: StatusSetup? = null

    public fun setSessions(list: List<Session>) {
        sessions = list.associateBy { it.id }
    }

    /** The polled busy set. Plans and start times survive it: a plan can arrive before the poll says busy. */
    public fun setBusy(ids: Set<String>) {
        val now = clock.now()
        busy.retainAll(ids)
        for (id in ids) if (busy.add(id)) busySince.getOrPut(id) { now }
    }

    public fun onRun(sessionId: String, run: Run) {
        if (sessionId.isBlank()) return
        when (run) {
            Run.Busy -> {
                busy += sessionId
                retries -= sessionId
                busySince.getOrPut(sessionId) { clock.now() }
            }
            is Run.Retry -> {
                busy += sessionId
                retries[sessionId] = run
                busySince.getOrPut(sessionId) { clock.now() }
            }
            Run.Idle -> {
                val wasRunning = busy.remove(sessionId)
                retries -= sessionId
                ended(sessionId, replied = wasRunning)
            }
        }
    }

    /** The run failed or was stopped: it ends without becoming "Last reply". */
    public fun onError(sessionId: String) {
        busy -= sessionId
        retries -= sessionId
        ended(sessionId, replied = false)
    }

    /** The plan the agent keeps ("5 of 6 tasks"); null or an invalid list drops it. */
    public fun onPlan(sessionId: String, todos: List<Todo>?) {
        val plan = planOf(todos)
        if (plan == null) plans -= sessionId else plans[sessionId] = plan
    }

    /** A generic sentence for the tool a session runs now ("Editing files"); null when it ends. */
    public fun onToolLine(sessionId: String, sentence: String?) {
        if (sentence.isNullOrBlank()) toolLines -= sessionId else toolLines[sessionId] = sentence.trim()
    }

    /** Pending requests' sessions, each in arrival order. Forms of the global scope carry no session. */
    public fun setPending(permissions: List<String>, questions: List<String>, forms: List<String> = emptyList()) {
        permissionSessions = permissions
        questionSessions = questions
        formSessions = forms
    }

    /** Everything the row says, gathered from session truth. */
    public fun input(): StatusInput {
        val now = clock.now()
        val pending = (permissionSessions + questionSessions + formSessions).toSet()
        val working = mutableListOf<StatusChat>()
        val waiting = mutableListOf<StatusRetry>()
        val ordered = busy.filter { sessions[it]?.parentId == null }.sortedByDescending { sessions[it]?.updatedAt ?: 0 }
        for (id in ordered) {
            val session = sessions[id]
            val title = session?.title?.trim().orEmpty()
            val retry = retries[id]
            if (retry != null) {
                waiting += StatusRetry(id, title, retry.provider?.trim()?.takeIf { it.isNotEmpty() }, retry.nextTry)
                continue
            }
            // A chat waiting on the person counts as needing them, not as working: the row says each thing once.
            if (id in pending) continue
            val plan = plans[id]
            val since = busySince.getOrPut(id) { now }
            working +=
                StatusChat(
                    id = id,
                    title = title,
                    project = projectOf(session?.directory),
                    liveLine = plan?.current ?: toolLines[id],
                    tasksDone = plan?.done,
                    tasksTotal = plan?.total,
                    elapsed = now - since,
                )
        }
        val first = permissionSessions.firstOrNull() ?: questionSessions.firstOrNull() ?: formSessions.firstOrNull()
        val firstSession = first?.let { sessions[it] }
        working.firstOrNull()?.let { lastActivity = it.liveLine ?: it.title }
        return StatusInput(
            working = working,
            pendingCount = permissionSessions.size + questionSessions.size + formSessions.size,
            firstPendingTitle = firstSession?.title?.trim().orEmpty(),
            firstPendingProject = projectOf(firstSession?.directory),
            retries = waiting,
            pause =
                when {
                    androidLimit -> PauseReason.AndroidLimit
                    thermalPaused -> PauseReason.PhoneHot
                    else -> PauseReason.None
                },
            reconnecting = reconnecting,
            reconnectingLastActivity = lastActivity,
            phoneServerRunning = phoneServerRunning,
            setup = setup,
            liveServerCount = liveServerCount,
            serverName = serverName,
            lastReplyAt = lastReply?.second,
            lastReplyTitle = lastReply?.first.orEmpty(),
            profileId = profileId,
        )
    }

    /** The row's content now, in the current app language. */
    public fun status(): StatusContent = StatusModel.build(input(), words, clock.now(), formatTime)

    private fun ended(sessionId: String, replied: Boolean) {
        busySince -= sessionId
        plans -= sessionId
        toolLines -= sessionId
        val session = sessions[sessionId]
        if (replied && session?.parentId == null) lastReply = session?.title?.trim().orEmpty() to clock.now()
    }

    public companion object {
        /** A project's name: the last folder of its directory. */
        public fun projectOf(directory: String?): String =
            directory?.trim().orEmpty().split(Regex("[\\\\/]+")).lastOrNull { it.isNotEmpty() }.orEmpty()

        private val STATUSES = setOf("pending", "in_progress", "completed", "cancelled")

        /** Port of `MobileTaskView.fromTodos` counting: cancelled tasks count neither way; any bad item drops it. */
        private fun planOf(todos: List<Todo>?): Plan? {
            if (todos.isNullOrEmpty() || todos.size > 64) return null
            if (todos.any { it.content.isBlank() || it.content.length > 1024 || it.status !in STATUSES }) return null
            val tracked = todos.count { it.status != "cancelled" }
            if (tracked == 0) return null
            val current = todos.firstOrNull { it.status == "in_progress" }?.content?.trim()?.takeIf { it.isNotEmpty() }
            return Plan(done = todos.count { it.status == "completed" }, total = tracked, current = current)
        }
    }
}

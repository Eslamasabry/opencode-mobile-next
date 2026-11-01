package io.github.eslamasabry.opencode_mobile

/**
 * The pure half of the one status notification (the other half is
 * [StatusNotification], which draws it). Nothing here imports Android, so the
 * rules that matter (one owner set, one update every two seconds, only when
 * the content changed, what Dart is allowed to send) are unit tested.
 *
 * Dart owns the wording (lib/background/notification_status.dart); this file
 * only validates and carries it. The wire names below must match that file.
 */

/** Must match `NotificationStatusKind.wireValue` in notification_status.dart. */
internal enum class StatusKind(val wire: String) {
    NEEDS_YOU("needs_you"),
    WORKING("working"),
    SEVERAL_WORKING("several_working"),
    WAITING("waiting"),
    PAUSED_ANDROID("paused_android"),
    PAUSED_HOT("paused_hot"),
    PAUSED_IDLE("paused_idle"),
    RECONNECTING("reconnecting"),
    IDLE("idle"),
    SETUP("setup");

    companion object {
        fun fromWire(value: Any?): StatusKind? = values().firstOrNull { it.wire == value }
    }
}

/** Must match `NotificationStatusAction.wireValue` in notification_status.dart. */
internal enum class StatusAction(val wire: String) {
    OPEN("open"),
    STOP_REPLY("stop_reply"),
    START("start"),
    NEW_CHAT("new_chat"),
    STOP_SERVER("stop_server");

    companion object {
        fun fromWire(value: Any?): StatusAction? = values().firstOrNull { it.wire == value }
    }
}

/** What the status notification shows, already worded. */
internal data class StatusContent(
    val kind: StatusKind,
    val title: String,
    val text: String = "",
    val lines: List<String> = emptyList(),
    val subText: String = "",
    /** 0-100 when a real fraction is known; null means no bar, never a spinner. */
    val progress: Int? = null,
    val actions: List<StatusAction> = listOf(StatusAction.OPEN),
    /** The chat "Stop the reply" stops. */
    val sessionId: String = "",
    /** The lock screen's version: counts only. */
    val publicTitle: String = "OpenCode",
) {
    companion object {
        const val MAX_LINES = 4
        const val MAX_TEXT = 240
        const val MAX_ACTIONS = 3
        const val MAX_PROGRESS = 100

        /**
         * Reads Dart's `status` map. Anything malformed is refused (null) so a
         * bad push never replaces a good notification; sizes are capped.
         */
        fun fromMap(map: Map<*, *>?): StatusContent? {
            val kind = StatusKind.fromWire(map?.get("kind"))
            val title = clip(map?.get("title"))
            if (map == null || kind == null || title.isNullOrEmpty()) return null
            val actions = (map["actions"] as? List<*>)
                ?.mapNotNull { StatusAction.fromWire(it) }
                ?.distinct()
                ?.take(MAX_ACTIONS)
                ?: emptyList()
            val lines = (map["lines"] as? List<*>)
                ?.mapNotNull { clip(it)?.takeIf { line -> line.isNotEmpty() } }
                ?.take(MAX_LINES)
                ?: emptyList()
            return StatusContent(
                kind = kind,
                title = title,
                text = clip(map["text"]) ?: "",
                lines = lines,
                subText = clip(map["subText"]) ?: "",
                progress = (map["progress"] as? Number)?.toInt()?.coerceIn(0, MAX_PROGRESS),
                actions = actions.ifEmpty { listOf(StatusAction.OPEN) },
                sessionId = clip(map["sessionId"]) ?: "",
                publicTitle = clip(map["publicTitle"])?.takeIf { it.isNotEmpty() } ?: "OpenCode",
            )
        }

        private fun clip(value: Any?): String? =
            (value as? String)?.trim()?.take(MAX_TEXT)
    }
}

/**
 * Which foreground services currently own the one status notification.
 * Both [BackgroundConnectionService] and [BuiltinServerService] call
 * `startForeground` with the same ID; stopping one must not remove the row
 * while the other still runs.
 */
internal class StatusOwners {
    private val owners = linkedSetOf<String>()

    @Synchronized fun start(owner: String): Boolean {
        val first = owners.isEmpty()
        owners.add(owner)
        return first
    }

    /** True when [owner] was the last one: only then may the row be removed. */
    @Synchronized fun stop(owner: String): Boolean {
        val removed = owners.remove(owner)
        return removed && owners.isEmpty()
    }

    @Synchronized fun has(owner: String): Boolean = owner in owners
    @Synchronized fun any(): Boolean = owners.isNotEmpty()
    @Synchronized fun snapshot(): Set<String> = owners.toSet()
    @Synchronized fun clear() = owners.clear()
}

/**
 * At most one notification update per [minGapMs], and only when the content
 * changed. The first change goes at once; changes inside the gap coalesce
 * into one trailing post of the newest content, due [Step.Wait] ms later.
 */
internal class StatusThrottle(private val minGapMs: Long = 2_000L) {
    sealed class Step {
        object Skip : Step()
        data class Post(val content: StatusContent) : Step()
        data class Wait(val delayMs: Long) : Step()
    }

    private var posted: StatusContent? = null
    private var postedAt = Long.MIN_VALUE
    private var pending: StatusContent? = null

    @Synchronized fun offer(content: StatusContent, now: Long): Step {
        if (content == posted) {
            // Back to what is already shown: the trailing post is moot.
            pending = null
            return Step.Skip
        }
        val gap = if (postedAt == Long.MIN_VALUE) minGapMs else now - postedAt
        return if (gap >= minGapMs) {
            record(content, now)
            Step.Post(content)
        } else {
            val firstWait = pending == null
            pending = content
            if (firstWait) Step.Wait(minGapMs - gap) else Step.Skip
        }
    }

    /** The trailing content, when its time has come; null when nothing is due. */
    @Synchronized fun due(now: Long): StatusContent? {
        val next = pending ?: return null
        return if (postedAt != Long.MIN_VALUE && now - postedAt < minGapMs) {
            null
        } else {
            pending = null
            if (next == posted) null else {
                record(next, now)
                next
            }
        }
    }

    /** A post that must happen now (a service starting): bypasses the gap. */
    @Synchronized fun force(content: StatusContent, now: Long) {
        pending = null
        record(content, now)
    }

    @Synchronized fun reset() {
        posted = null
        postedAt = Long.MIN_VALUE
        pending = null
    }

    private fun record(content: StatusContent, now: Long) {
        posted = content
        postedAt = now
    }
}

/** One button on a "needs you" alert. The label comes from native copy. */
internal data class AlertButton(
    val decision: String,
    val label: Label,
    /** Android asks for the unlocked phone before it runs. */
    val authRequired: Boolean = false,
) {
    enum class Label { ALLOW_ONCE, REJECT, OPEN }
}

/**
 * Which buttons an alert carries: at most two. A permission the shade can
 * show (its command or file is in the body) offers exactly Allow once (phone
 * unlocked) and Reject; "Always allow" is never offered from a notification.
 * Every other ask opens the chat: a question, a form, or a permission whose
 * exact command the shade cannot show (never allow what you cannot read).
 */
internal object AlertButtons {
    fun plan(kind: String, answerable: Boolean): List<AlertButton> = when (kind) {
        "permission" -> if (answerable) listOf(
            AlertButton("allow", AlertButton.Label.ALLOW_ONCE, authRequired = true),
            AlertButton("deny", AlertButton.Label.REJECT),
        ) else listOf(AlertButton("open", AlertButton.Label.OPEN))
        "question" -> listOf(AlertButton("open", AlertButton.Label.OPEN))
        else -> emptyList()
    }
}

/**
 * The words of a request or a finished conversation, before localization:
 * the title reads as the question ("Run npm install?") or the plain result
 * ("Finished: Add combo counter"), the body carries the exact thing. Text from
 * Dart is already masked; here it is only trimmed and capped.
 */
internal object AlertCopy {
    const val MAX_TARGET = 40
    const val MAX_QUESTION = 80
    const val MAX_LINE = 140
    const val MAX_OPTIONS = 3
    private const val MAX_OPTION_LENGTH = 24

    enum class Title {
        /** The ask's own words ([Copy.titleArg] is the whole title). */
        LITERAL,
        RUN, EDIT, READ, FOLDER,
        /** A permission without a readable target: "Allow OpenCode to continue?". */
        ASK,
        /** A question without words (a form): "OpenCode has a question for you". */
        QUESTION,
        FINISHED,
        FINISHED_ANY,
        STOPPED,
        STOPPED_ANY,
    }

    data class Copy(val title: Title, val titleArg: String = "", val body: String = "")

    /** Whether Allow once / Reject may show: the body carries what is allowed. */
    fun answerable(kind: String, allowActions: Boolean, detail: String): Boolean =
        kind == "permission" && allowActions && detail.isNotBlank()

    /** The body line for a question: its few short choices, else nothing. */
    fun choices(raw: List<String>): String {
        val options = raw.map { it.trim() }
        val valid = options.isNotEmpty() && options.size <= MAX_OPTIONS &&
            options.none { it.isEmpty() || it.length > MAX_OPTION_LENGTH } &&
            options.toSet().size == options.size
        return if (valid) options.joinToString(" · ") else ""
    }

    fun of(request: CodingAlertRequest): Copy? = with(request) {
        val line = cap(detail, MAX_LINE)
        val chat = cap(chatTitle, MAX_QUESTION)
        when (kind) {
            "permission" -> permission(askAction, cap(askTarget, MAX_TARGET), line)
            "question" -> {
                val question = cap(detail, MAX_QUESTION)
                if (question.isEmpty()) Copy(Title.QUESTION)
                else Copy(Title.LITERAL, question, choices(options))
            }
            "complete" -> if (chat.isEmpty()) Copy(Title.FINISHED_ANY, body = line)
                else Copy(Title.FINISHED, chat, line)
            "error" -> if (chat.isEmpty()) Copy(Title.STOPPED_ANY) else Copy(Title.STOPPED, chat)
            else -> null
        }
    }

    private fun permission(action: String, target: String, line: String): Copy {
        val title = if (target.isEmpty() || line.isEmpty()) Title.ASK else when (action) {
            "run" -> Title.RUN
            "edit" -> Title.EDIT
            "read" -> Title.READ
            "folder" -> Title.FOLDER
            else -> Title.ASK
        }
        return Copy(title, if (title == Title.ASK) "" else target, line)
    }

    /** The line a group summary lists for one alert: the conversation, or the question. */
    fun summaryLine(request: CodingAlertRequest, title: String): String =
        if (request.kind == "complete") cap(request.chatTitle, MAX_QUESTION).ifEmpty { title } else title

    private fun cap(value: String, max: Int): String {
        val text = value.replace(Regex("\\s+"), " ").trim()
        return if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
    }
}

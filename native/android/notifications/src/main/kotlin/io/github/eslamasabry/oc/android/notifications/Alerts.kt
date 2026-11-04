package io.github.eslamasabry.oc.android.notifications

/** Alert kinds at the switch (plan 11 §10.4); `team_*` kinds come with D6. Wire names unchanged. */
public enum class AlertKind(public val wire: String) {
    Permission("permission"),
    Question("question"),
    Complete("complete"),
    Error("error"),
    Quota("quota"),
    QuotaReset("quota_reset"),
    CheckIn("checkin"),
    Test("test");

    public val isRequest: Boolean
        get() = this == Permission || this == Question

    public val isQuota: Boolean
        get() = this == Quota || this == QuotaReset

    public companion object {
        public fun fromWire(value: String?): AlertKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One alert ("moment"), already in the person's words and already masked by [NotificationText]
 * (port of the Flutter `CodingAlertRequest`). Ids are the routing identifiers the intents carry;
 * nothing else ever enters an intent.
 */
public data class CodingAlert(
    val kind: AlertKind,
    val sessionId: String,
    /**
     * The dedupe key (`input:<profile>:<session>`, `status:<profile>:<session>`, …); also the
     * notification id seed.
     */
    val key: String,
    /** The exact pending request this alert represents; empty for non-requests. */
    val requestId: String = "",
    val profileId: String = "",
    val monitorToken: String = "",
    /** "server · project" (see [NotificationText.subtitle]). */
    val subtext: String = "",
    val agentName: String = "",
    val chatTitle: String = "",
    /**
     * The exact thing: the command (permission), the question, or the first reply line (complete).
     * Masked.
     */
    val detail: String = "",
    val reason: StopReason = StopReason.Other,
    val options: List<String> = emptyList(),
    /** What a permission asks; null when it names neither a command nor a file. */
    val ask: PermissionAsk? = null,
    /**
     * False when the caller already knows the shade must not answer it (e.g. a monitored alert,
     * B-ANDR-061).
     */
    val allowActions: Boolean = true,
    /** Opaque identity of this one posted request; never request content or a credential. */
    val actionToken: String = "",
) {
    /** Validity (plan 11 §10.4, ported from `validCodingAlert`). */
    public val isValid: Boolean
        get() {
            if (sessionId.isBlank() || key.isBlank()) return false
            if (!kind.isQuota) return true
            return sessionId == "quota" &&
                profileId.isNotBlank() &&
                MONITOR_TOKEN.matches(monitorToken)
        }

    /** Only a validated, short agent name enters a template; anything else reads "OpenCode". */
    public val safeAgentName: String
        get() = agentName.takeIf { AGENT_NAME.matches(it) } ?: "OpenCode"

    override fun toString(): String =
        "CodingAlert(kind=$kind, key=<${key.length}>, request=${requestId.isNotEmpty()})"

    internal companion object {
        val MONITOR_TOKEN = Regex("^[a-f0-9]{64}$")
        val AGENT_NAME = Regex("^[A-Za-z0-9 .-]{1,32}$")
    }
}

/** One button on an alert; the label comes from native copy. */
public data class AlertButton(
    val decision: ShadeDecision,
    val label: Label,
    val authRequired: Boolean = false,
) {
    public enum class Label {
        AllowOnce,
        Reject,
        Open,
    }
}

/**
 * Which buttons an alert carries: at most two (plan 11 §10.4, port of `AlertButtons.plan`, commit
 * b8c07f3e0). A permission whose exact command or file is readable in the body offers exactly
 * **Allow once** (phone unlocked) and **Reject**; **Always allow** is never offered. A question
 * opens the app; its ≤ 3 short options are one text line.
 */
public object AlertButtons {
    public fun plan(alert: CodingAlert): List<AlertButton> =
        when (alert.kind) {
            AlertKind.Permission ->
                if (AlertCopy.answerable(alert)) {
                    listOf(
                        AlertButton(
                            ShadeDecision.Allow,
                            AlertButton.Label.AllowOnce,
                            authRequired = true,
                        ),
                        AlertButton(ShadeDecision.Deny, AlertButton.Label.Reject),
                    )
                } else {
                    listOf(AlertButton(ShadeDecision.Open, AlertButton.Label.Open))
                }
            AlertKind.Question -> listOf(AlertButton(ShadeDecision.Open, AlertButton.Label.Open))
            else -> emptyList()
        }
}

/**
 * The words of an alert before localization (port of `AlertCopy`): the title reads as the question
 * ("Run npm install?") or the plain result ("Finished: Add combo counter"), the body carries the
 * exact thing. Text arrives masked; here it is only trimmed and capped.
 */
public object AlertCopy {
    public const val MAX_TARGET: Int = 40
    public const val MAX_QUESTION: Int = 80
    public const val MAX_LINE: Int = 140
    public const val MAX_OPTIONS: Int = 3
    private const val MAX_OPTION_LENGTH = 24

    public enum class Title {
        Literal,
        Run,
        Edit,
        Read,
        Folder,
        Ask,
        Question,
        Finished,
        FinishedAny,
        Stopped,
        StoppedAny,
    }

    public data class Copy(val title: Title, val titleArg: String = "", val body: String = "")

    /**
     * Allow once / Reject may show only when the body carries what is allowed (never allow what you
     * cannot read).
     */
    public fun answerable(alert: CodingAlert): Boolean =
        alert.kind == AlertKind.Permission &&
            alert.allowActions &&
            alert.detail.isNotBlank() &&
            alert.requestId.isNotBlank() &&
            alert.ask != null &&
            alert.ask.target.isNotBlank()

    /** The body line for a question: its few short choices joined by " · ", else nothing. */
    public fun choices(raw: List<String>): String {
        val options = raw.map { it.trim() }
        val valid =
            options.isNotEmpty() &&
                options.size <= MAX_OPTIONS &&
                options.none { it.isEmpty() || it.length > MAX_OPTION_LENGTH } &&
                options.toSet().size == options.size
        return if (valid) options.joinToString(" · ") else ""
    }

    /** Null for kinds whose words are fixed copy (quota, check-in, test). */
    public fun of(alert: CodingAlert): Copy? {
        val line = cap(alert.detail, MAX_LINE)
        val chat = cap(alert.chatTitle, MAX_QUESTION)
        return when (alert.kind) {
            AlertKind.Permission -> permission(alert.ask, line)
            AlertKind.Question -> {
                val question = cap(alert.detail, MAX_QUESTION)
                if (question.isEmpty()) Copy(Title.Question)
                else Copy(Title.Literal, question, choices(alert.options))
            }
            AlertKind.Complete ->
                if (chat.isEmpty()) Copy(Title.FinishedAny, body = line)
                else Copy(Title.Finished, chat, line)
            AlertKind.Error ->
                if (chat.isEmpty()) Copy(Title.StoppedAny) else Copy(Title.Stopped, chat)
            else -> null
        }
    }

    private fun permission(ask: PermissionAsk?, line: String): Copy {
        val target = cap(ask?.target.orEmpty(), MAX_TARGET)
        if (ask == null || target.isEmpty() || line.isEmpty()) return Copy(Title.Ask)
        val title =
            when (ask.action) {
                AskAction.Run -> Title.Run
                AskAction.Edit -> Title.Edit
                AskAction.Read -> Title.Read
                AskAction.Folder -> Title.Folder
            }
        return Copy(title, target, line)
    }

    /** The line a group summary lists for one alert: the conversation, or the question. */
    public fun summaryLine(alert: CodingAlert, title: String): String =
        if (alert.kind == AlertKind.Complete) cap(alert.chatTitle, MAX_QUESTION).ifEmpty { title }
        else title

    internal fun cap(value: String, max: Int): String {
        val text = value.replace(Regex("\\s+"), " ").trim()
        return if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
    }
}

/** Where an alert goes, or why it is not posted. */
public sealed interface AlertRoute {
    public data class Post(val channel: NotifyChannel, val group: String?) : AlertRoute

    public data class Skip(val why: SkipWhy) : AlertRoute
}

public enum class SkipWhy {
    Invalid,
    NoWatch,
    AppVisible,
    NoPermission,
    ServerAlertsOff,
    QuietHours,
    NotOnWifi,
    SubAgent,
}

/** Facts the posting rule reads (plan 11 §10.4 "When posted"). */
public data class AlertFacts(
    val liveModeActive: Boolean,
    val replyWatchActive: Boolean,
    val appVisible: Boolean,
    val permissionGranted: Boolean,
    val serverAlertsOn: Boolean,
    val quietHours: Boolean,
    /** `DeviceFacts.network.onWifi`; null = unknown, which never counts as Wi-Fi. */
    val onWifi: Boolean?,
    val wifiOnly: Boolean,
    val subAgentSession: Boolean = false,
)

/**
 * When an alert is posted (plan 11 §10.4; B-ANDR-013, -039, -059; ported unchanged). Lives here
 * until `:engine:attention` exists (S4-ATTNE), which then owns it as `NotificationPolicy`.
 */
public object AlertPolicy {
    public fun route(alert: CodingAlert, facts: AlertFacts): AlertRoute {
        if (!alert.isValid) return AlertRoute.Skip(SkipWhy.Invalid)
        if (!facts.permissionGranted) return AlertRoute.Skip(SkipWhy.NoPermission)
        // "Send a test notification" ignores the live-mode gate but not the platform permission
        // (B-ANDR-013).
        if (alert.kind == AlertKind.Test) return AlertRoute.Post(NotifyChannel.CodingAction, null)
        if (!facts.liveModeActive && !facts.replyWatchActive)
            return AlertRoute.Skip(SkipWhy.NoWatch)
        if (facts.appVisible) return AlertRoute.Skip(SkipWhy.AppVisible)
        if (!facts.serverAlertsOn) return AlertRoute.Skip(SkipWhy.ServerAlertsOff)
        if (alert.kind == AlertKind.Complete && facts.subAgentSession)
            return AlertRoute.Skip(SkipWhy.SubAgent)
        if (alert.kind.isQuota && facts.wifiOnly && facts.onWifi != true)
            return AlertRoute.Skip(SkipWhy.NotOnWifi)
        val loud = alert.kind.isRequest || alert.kind == AlertKind.Error
        if (facts.quietHours) {
            // Requests and failures post silently; a finished reply is never posted nor queued
            // (`alerts.dart:292`);
            // no check-in reminder during quiet hours (B-ANDR-059); quota alerts are held by the
            // monitor (B-ANDR-097).
            if (!loud) return AlertRoute.Skip(SkipWhy.QuietHours)
            return AlertRoute.Post(NotifyChannel.CodingActionQuiet, groupOf(alert.kind))
        }
        return AlertRoute.Post(
            if (loud) NotifyChannel.CodingAction else NotifyChannel.CodingStatus,
            groupOf(alert.kind),
        )
    }

    /**
     * Every grouped kind has an explicit group key (Android 16 forced auto-grouping,
     * android-surfaces §2.3).
     */
    public fun groupOf(kind: AlertKind): String? =
        when (kind) {
            AlertKind.Permission,
            AlertKind.Question -> AlertGroups.NEEDS_YOU
            AlertKind.Complete -> AlertGroups.FINISHED
            else -> null
        }
}

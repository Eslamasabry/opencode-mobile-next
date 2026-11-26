package io.github.eslamasabry.oc.core.model

import io.github.eslamasabry.oc.core.common.Redacted

/** Lifecycle status of an OpenCode server automation (B-605). */
public enum class AutomationStatus {
    Active,
    Paused,
    Done,
    Unknown,
}

/** Last run verdict of a server automation, worded plainly in the UI (never raw server prose). */
public enum class AutomationOutcome {
    Fired,
    Late,
    Skipped,
    Disabled,
    Failed,
    Expired,
    Unknown,
}

/** When a server automation runs. */
public sealed interface AutomationTrigger {
    public data class At(
        val atMs: Long? = null,
        val raw: String? = null,
        val timeZone: String? = null,
    ) : AutomationTrigger

    public data class Every(val duration: String) : AutomationTrigger

    public data class Cron(
        val expression: String,
        val timeZone: String? = null,
    ) : AutomationTrigger

    public data class Event(
        val server: String?,
        val event: String?,
        val args: Redacted? = null,
    ) : AutomationTrigger

    public data object Unknown : AutomationTrigger
}

/** What a server automation runs when triggered. */
public sealed interface AutomationAction {
    public data class Prompt(
        val text: String,
        val subagent: String? = null,
        val targetSessionId: String? = null,
    ) : AutomationAction

    public data class Shell(val command: String) : AutomationAction

    public data class Code(val source: String) : AutomationAction

    public data object Unknown : AutomationAction
}

/**
 * One automation owned by an OpenCode 2.4+ server (`/api/experimental/automation`, B-605).
 * Server-supplied output and event arguments are redacted before entering the model and shown only
 * in secondary disclosure surfaces.
 */
public data class ServerAutomation(
    val id: String,
    val name: String,
    val status: AutomationStatus,
    val trigger: AutomationTrigger,
    val action: AutomationAction,
    val timeZone: String? = null,
    val nextDueAtMs: Long? = null,
    val runCount: Int = 0,
    val lastRunAtMs: Long? = null,
    val lastOutcome: AutomationOutcome? = null,
    val lastOutput: Redacted? = null,
    val expiresAtMs: Long? = null,
    val sessionId: String? = null,
    val directory: String? = null,
    val projectId: String? = null,
)

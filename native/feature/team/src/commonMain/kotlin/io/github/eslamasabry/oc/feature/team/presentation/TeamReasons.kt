package io.github.eslamasabry.oc.feature.team.presentation

import io.github.eslamasabry.oc.engine.team.TeamAction
import io.github.eslamasabry.oc.engine.team.TeamTimelineEvent

/** A known engine code's plain sentence and way forward (Dart `_known`, team_refusal.dart). */
public enum class Reason {
    RepositoryEmpty, RepoPathInvalid, RepositoryLink, RepositoryDamaged, PlanTaskName, PlanPhase, UnsupportedCommand,
    BoundaryUnverified, ProtocolUnverified, EngineUnavailable, TransportUncertain, Busy, SaveFailed, ReadOnly, Closed,
    CommandRefused, ImportFailed, PayloadInvalid, SchemaUnsupported, EngineClosed, SessionFailed, ModelNotConfigured,
    ModelUnavailable, AuthFailed, CloneFailed, SessionUncertain, PlanInvalid, CheckerUnreadable, NeedsAnswer,
    RecoveryReview, AppStopped, ChatBusy, BudgetReached,
}

/** What the unknown-code sentence says the team could not do (Dart `_did`). */
public enum class Did { Plan, Quick, Approve, Spec, Promote, Stop, Pause, Resume, Save }

/** Dart team_refusal.dart and team_digest.dart: codes to words, the timeline's trailing code, the digest's rows. */
public object TeamReasons {
    private val known: Map<String, Reason> =
        mapOf(
            "repository_empty" to Reason.RepositoryEmpty,
            "repoPathInvalid" to Reason.RepoPathInvalid,
            "invalid_proot_object_link" to Reason.RepositoryLink,
            "repository_object_hash_mismatch" to Reason.RepositoryDamaged,
            "planTaskTitleRequired" to Reason.PlanTaskName,
            "planPhaseInvalid" to Reason.PlanPhase,
            "unsupportedCommand" to Reason.UnsupportedCommand,
            "boundaryUnverified" to Reason.BoundaryUnverified,
            "protocolUnverified" to Reason.ProtocolUnverified,
            "engineUnavailable" to Reason.EngineUnavailable,
            "transportUncertain" to Reason.TransportUncertain,
            "busy" to Reason.Busy,
            "saveFailed" to Reason.SaveFailed,
            "readOnly" to Reason.ReadOnly,
            "closed" to Reason.Closed,
            "commandRefused" to Reason.CommandRefused,
            "importFailed" to Reason.ImportFailed,
            "payloadInvalid" to Reason.PayloadInvalid,
            "schemaUnsupported" to Reason.SchemaUnsupported,
            "engineClosed" to Reason.EngineClosed,
            "sessionFailed" to Reason.SessionFailed,
            "modelNotConfigured" to Reason.ModelNotConfigured,
            "modelUnavailable" to Reason.ModelUnavailable,
            "modelInvalid" to Reason.ModelUnavailable,
            "invalid_model" to Reason.ModelUnavailable,
            "authentication_failed" to Reason.AuthFailed,
            "cloneFailed" to Reason.CloneFailed,
            "sessionUncertain" to Reason.SessionUncertain,
            "sessionUnknown" to Reason.SessionUncertain,
            "promptUncertain" to Reason.SessionUncertain,
            "sessionCreateUncertain" to Reason.SessionUncertain,
            "usageUncertain" to Reason.SessionUncertain,
            "invalidPlan" to Reason.PlanInvalid,
            "checkerAnswerUnreadable" to Reason.CheckerUnreadable,
            "structuredOutputInvalid" to Reason.PlanInvalid,
            "needsAnswer" to Reason.NeedsAnswer,
            "recoveryNeedsReview" to Reason.RecoveryReview,
            "restartNeedsReconciliation" to Reason.AppStopped,
            "pauseNeedsReconciliation" to Reason.AppStopped,
            "chatBusy" to Reason.ChatBusy,
            "budgetReached" to Reason.BudgetReached,
            "transport_unavailable" to Reason.EngineUnavailable,
            "serverOffline" to Reason.EngineUnavailable,
        )

    private val needsModel = setOf("modelNotConfigured", "modelUnavailable", "modelInvalid", "invalid_model", "sessionFailed")

    /** Dart `teamReasonFor`: null for a code it does not know. */
    public fun reason(code: String): Reason? = known[code]

    /** Dart `teamReasonNeedsModel`: picking a model fixes it (offer that action). */
    public fun needsModel(code: String): Boolean = code in needsModel

    /** Dart `_did`. */
    public fun did(action: String): Did =
        when (action) {
            TeamAction.CREATE_PROJECT -> Did.Plan
            TeamAction.CREATE_QUICK_TASK -> Did.Quick
            TeamAction.APPROVE_PLAN -> Did.Approve
            TeamAction.APPROVE_SPEC -> Did.Spec
            TeamAction.PROMOTE -> Did.Promote
            TeamAction.STOP_PROJECT -> Did.Stop
            TeamAction.PAUSE_PROJECT -> Did.Pause
            TeamAction.RESUME_PROJECT -> Did.Resume
            else -> Did.Save
        }

    private val trailingCode = Regex("""\s*\(([A-Za-z_]+)\)\.?$""")

    /** Dart `teamTimelineCode`: the code the engine put in parentheses at the end of a timeline row. */
    public fun timelineCode(text: String): String? = trailingCode.find(text)?.groupValues?.get(1)

    /**
     * Dart `teamTimelineWords` for the part that needs no words: an unknown code is cut off (it stays out of the page);
     * a known one is returned as [Reason] for the page to word.
     */
    public fun timelineText(text: String): Pair<String, Reason?> {
        val code = timelineCode(text) ?: return text to null
        val known = reason(code)
        return if (known != null) text to known else text.replaceFirst(trailingCode, ".") to null
    }

    /**
     * Dart `teamDigestEvents`: the rows newer than [readAtMs], one per item (the planner, or one task), the newest
     * state of each, newest item first.
     */
    public fun digest(timeline: List<TeamTimelineEvent>, readAtMs: Long?): List<TeamTimelineEvent> {
        val newest = LinkedHashMap<String, Pair<Int, TeamTimelineEvent>>()
        timeline.forEachIndexed { i, e ->
            val at = TeamRules.epochMs(e.at)
            if (readAtMs != null && (at == null || at <= readAtMs)) return@forEachIndexed
            val key =
                when {
                    e.kind == "planner" || e.kind == "planningCheckpoint" -> "planner"
                    (e.kind == "job" || e.kind == "taskCheckpoint") && e.taskId.isNotEmpty() -> "task:${e.taskId}"
                    else -> "row:${e.id.ifEmpty { i.toString() }}"
                }
            val held = newest[key]
            if (held != null) {
                val heldAt = TeamRules.epochMs(held.second.at)
                if (heldAt != null && at != null && at < heldAt) return@forEachIndexed
            }
            newest[key] = i to e
        }
        return newest.values.sortedByDescending { it.first }.map { it.second }
    }
}

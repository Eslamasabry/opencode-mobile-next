package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.dart.DartJson

/** A setup job's params as setup.json stores them: component id → its string facts (the native runner keeps them opaque). */
public typealias JobParams = Map<String, Map<String, String>>

/**
 * Facts about a job rather than a component, kept in the job's params under [KEY] so setup.json carries them without a
 * schema change (port of setup_contract.dart `SetupJobParams`; B-LINUX-007). Components read only their own id's entry.
 */
public object SetupJobParams {
    public const val KEY: String = "_job"

    /** Marks a job as the phone's first setup ([SetupProgress.firstSetup]). */
    public val firstSetup: JobParams = mapOf(KEY to mapOf("first" to "1"))

    public fun isFirstSetup(params: JobParams): Boolean = params[KEY]?.get("first") == "1"

    /** Marks a job as adding [ids] to a phone that is set up ([SetupProgress.adding]). */
    public fun adding(ids: Iterable<String>): JobParams = mapOf(KEY to mapOf("adding" to ids.joinToString(",")))

    public fun addingIds(params: JobParams): List<String> =
        params[KEY]?.get("adding").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Setup component ids the engine itself knows (setup components.dart `SetupComponentIds`). */
public object SetupIds {
    public const val OPEN_CODE: String = "opencode"

    /** The job step that starts OpenCode and connects (a `jobStep`, not something installed). */
    public const val START: String = "start"
}

/**
 * The params a job runs with (port of progress_helpers.dart `effectiveJobParams`). With no component params of its own
 * a job that continues one which can continue takes that job's params, so a continued Add tools job keeps its label.
 * Job facts given now are laid over the earlier ones. OpenCode always runs the only supported runtime (`opencode2`);
 * an OpenCode 1 version pin never leaks into it.
 */
public fun effectiveJobParams(requested: JobParams, last: SetupJobRecord?): JobParams {
    val facts = requested[SetupJobParams.KEY]
    val components = requested.filterKeys { it != SetupJobParams.KEY }
    val base = if (components.isEmpty() && last != null && last.canContinue) last.params else components
    val openCode = base[SetupIds.OPEN_CODE].orEmpty().toMutableMap()
    if (openCode["runtime"] == "opencode1" || openCode["version"]?.startsWith("1.") == true) openCode.remove("version")
    openCode["runtime"] = "opencode2"
    return buildMap {
        putAll(base)
        put(SetupIds.OPEN_CODE, openCode.toMap())
        if (facts != null) put(SetupJobParams.KEY, base[SetupJobParams.KEY].orEmpty() + facts)
    }
}

/**
 * The names an Add tools title lists, in the order added (port of `setupAddingTitle`): each id's short title, ids not in
 * the registry left out. One name stands alone; more are joined with [separator] and the last one by [pair]
 * (`phoneSetupStartListSeparator` / `phoneSetupStartListPair` from `:ui:words`). Null when nothing is named.
 */
public fun addingTitleNames(
    shortTitles: Map<String, String>,
    ids: List<String>,
    separator: String,
    pair: (String, String) -> String,
): String {
    val names = ids.mapNotNull { shortTitles[it] }
    return if (names.size <= 1) names.joinToString("") else pair(names.dropLast(1).joinToString(separator), names.last())
}

/** One component's row in setup.json. */
public data class SetupJobComponent(
    val id: String,
    /** pending | running | done | failed | skipped */
    val state: String,
    val version: String? = null,
    val error: String? = null,
    val data: Map<String, String> = emptyMap(),
) {
    val componentState: ComponentState
        get() =
            when (state) {
                "running" -> ComponentState.Running
                "done" -> ComponentState.Done
                "failed" -> ComponentState.Failed
                "skipped" -> ComponentState.Skipped
                else -> ComponentState.Pending
            }
}

/** setup.json, read (port of job_record.dart `SetupJobRecord`). */
public data class SetupJobRecord(
    val jobId: String,
    /** running | done | failed | cancelled | interrupted */
    val state: String,
    val components: List<SetupJobComponent>,
    val current: String? = null,
    val params: JobParams = emptyMap(),
    val host: String? = null,
    val persistenceFailure: Boolean = false,
) {
    val canContinue: Boolean
        get() = state == "failed" || state == "interrupted" || state == "cancelled"

    public fun component(id: String): SetupJobComponent? = components.firstOrNull { it.id == id }

    public companion object {
        /**
         * Null for no job or text that is not a job; unknown fields are ignored and missing ones take their empty value,
         * so an older or newer runner never breaks the screen. `order` decides the row order when present.
         */
        public fun parse(text: String?): SetupJobRecord? {
            if (text.isNullOrBlank()) return null
            val decoded = DartJson.decodeOrNull(text) as? Map<*, *> ?: return null
            val jobId = decoded["jobId"] as? String
            if (jobId.isNullOrEmpty()) return null
            val componentMap = decoded["components"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
            val order = decoded["order"]
            val ids = if (order is List<*>) order.filterIsInstance<String>() else componentMap.keys.filterIsInstance<String>()
            val params = buildMap {
                (decoded["params"] as? Map<*, *>)?.forEach { (key, value) ->
                    if (key is String && value is Map<*, *>) put(key, strings(value))
                }
            }
            return SetupJobRecord(
                jobId = jobId,
                host = decoded["host"] as? String,
                persistenceFailure = decoded["errorCode"] == "setup_persistence",
                state = decoded["state"] as? String ?: "interrupted",
                current = decoded["current"] as? String,
                components = ids.mapNotNull { id ->
                    val row = componentMap[id] as? Map<*, *> ?: return@mapNotNull null
                    SetupJobComponent(
                        id = id,
                        state = row["state"] as? String ?: "pending",
                        version = row["version"] as? String,
                        error = row["error"] as? String,
                        data = (row["data"] as? Map<*, *>)?.let(::strings).orEmpty(),
                    )
                },
                params = params,
            )
        }

        private fun strings(map: Map<*, *>): Map<String, String> = buildMap {
            map.forEach { (key, value) -> if (key is String && value is String) put(key, value) }
        }
    }
}

public enum class SetupState { Idle, Running, Done, Failed, Interrupted, Cancelled }

public enum class ComponentState { Pending, Checking, Running, Done, Failed, Skipped }

public data class ComponentProgress(val id: String, val state: ComponentState, val version: String? = null)

/**
 * What a setup screen shows (port of setup_contract.dart `SetupProgress`, without copy). [failure] is a value the words
 * turn into one sentence; its Details hold the technical text. [adding] and [firstSetup] travel in the job's params,
 * so they survive the app being killed.
 */
public data class SetupProgress(
    val state: SetupState,
    val components: List<ComponentProgress>,
    val jobId: String? = null,
    val current: String? = null,
    val failure: Failure? = null,
    val firstSetup: Boolean = false,
    val adding: List<String> = emptyList(),
) {
    val canContinue: Boolean
        get() = state == SetupState.Failed || state == SetupState.Interrupted || state == SetupState.Cancelled

    /** The job has ended, one way or another. */
    val ended: Boolean
        get() = canContinue || state == SetupState.Done

    /** Whether this is the moment a job ended (a first end of this job). */
    public fun endedBefore(previous: SetupProgress): Boolean = ended && (!previous.ended || previous.jobId != jobId)

    public companion object {
        public val Idle: SetupProgress = SetupProgress(SetupState.Idle, emptyList())
    }
}

/** A job record as progress (port of progress_helpers.dart `progressFromRecord`, rows and job facts only). */
public fun progressFromRecord(record: SetupJobRecord): SetupProgress {
    val state =
        when (record.state) {
            "running" -> SetupState.Running
            "done" -> SetupState.Done
            "failed" -> SetupState.Failed
            "cancelled" -> SetupState.Cancelled
            else -> SetupState.Interrupted
        }
    return SetupProgress(
        jobId = record.jobId,
        state = state,
        current = record.current,
        components = record.components.map { ComponentProgress(it.id, it.componentState, it.version) },
        failure =
            if (state != SetupState.Failed) {
                null
            } else {
                Failure.Runtime(if (record.persistenceFailure) RuntimeProblem.SetupPersistence else RuntimeProblem.Generic)
            },
        firstSetup = SetupJobParams.isFirstSetup(record.params),
        adding = SetupJobParams.addingIds(record.params),
    )
}

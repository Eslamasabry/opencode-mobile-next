package io.github.eslamasabry.oc.engine.runtime.setup

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The native side, scripted (port of test/setup_engine_test.dart's FakeLinux): a status, the check output, and a job
 * (setup.json as a map) the test moves along.
 */
class FakeSetupHost(var installed: Boolean = true) : SetupJobHost {
    var statusError: Failure? = null

    /** id to (passes, version) for the combined check run. */
    val checks = mutableMapOf<String, Pair<Boolean, String?>>()
    val runs = mutableListOf<String>()
    val agentRuns = mutableListOf<String>()
    var holdChecks: CompletableDeferred<Unit>? = null
    var checkError: Failure? = null
    var agentCheckError: Failure? = null
    var startError: Failure? = null

    var job: MutableMap<String, Any?>? = null
    val started = mutableListOf<SetupJobStart>()
    var cancels = 0
    val steps = mutableListOf<Map<String, Any?>>()
    var statusReads = 0

    /** What the next status reads do instead of answering: throw, hang, native, malformed, timeout. */
    val statusFaults = mutableListOf<String>()

    override suspend fun linuxInstalled(): Outcome<Boolean> = statusError?.failed() ?: installed.ok()

    override suspend fun setupStatus(): Outcome<String?> {
        statusReads++
        if (statusFaults.isNotEmpty()) {
            when (statusFaults.removeAt(0)) {
                "throw" -> throw IllegalStateException("the channel failed")
                "hang" -> awaitCancellation()
                "native" -> return Failure.Unknown().failed()
                "malformed" -> return "{unreadable setup".ok()
                "timeout" -> return Failure.TimedOut().failed()
            }
        }
        return job?.let { encode(it) }.ok()
    }

    override suspend fun runCheck(script: String, agentUser: Boolean): Outcome<SetupCheckRun> {
        if (agentUser) {
            agentRuns += script
            agentCheckError?.let { return it.failed() }
        } else {
            runs += script
        }
        checkError?.let { return it.failed() }
        holdChecks?.await()
        val out = StringBuilder()
        for (match in Regex("""::oc-check-begin %s\\n' '([a-z0-9]+)'""").findAll(script)) {
            val id = match.groupValues[1]
            val (ok, version) = checks[id] ?: (false to null)
            out.append('\n').append("::oc-check-begin ").append(id).append('\n')
            out.append(if (ok) version ?: "" else "command not found").append('\n').append('\n')
            out.append("::oc-check-end ").append(id).append(' ').append(if (ok) 0 else 1).append('\n')
        }
        return SetupCheckRun(0, out.toString()).ok()
    }

    override suspend fun startSetup(job: SetupJobStart): Outcome<Unit> {
        startError?.let { return it.failed() }
        started += job
        this.job = mutableMapOf(
            "jobId" to job.jobId,
            "state" to "running",
            "current" to null,
            "order" to job.components.map { it.id },
            "components" to job.components.associate {
                it.id to mutableMapOf<String, Any?>(
                    "state" to if (it.skipped) "skipped" else "pending",
                    "weight" to it.weight,
                    "version" to it.version,
                    "data" to it.data,
                )
            }.toMutableMap(),
            "startedAt" to 1_000L,
            "updatedAt" to 1_000L,
            "error" to null,
            "logTail" to "",
            "params" to job.params,
        )
        return Unit.ok()
    }

    override suspend fun cancelSetup(): Outcome<Unit> {
        cancels++
        job?.let { if (it["state"] == "running") it["state"] = "cancelled" }
        return Unit.ok()
    }

    override suspend fun completeStep(jobId: String, id: String, ok: Boolean, error: String?, version: String?): Outcome<Unit> {
        steps += mapOf("jobId" to jobId, "id" to id, "ok" to ok, "error" to error)
        val job = job!!
        @Suppress("UNCHECKED_CAST")
        val component = (job["components"] as MutableMap<String, MutableMap<String, Any?>>)[id]!!
        component["state"] = if (ok) "done" else "failed"
        component["error"] = error
        job["state"] = if (ok) "done" else "failed"
        job["error"] = error
        return Unit.ok()
    }

    /** Moves the running job to [id] with [fields]. */
    fun advance(id: String, fields: Map<String, Any?>) {
        val job = job!!
        job["current"] = id
        @Suppress("UNCHECKED_CAST")
        val components = job["components"] as MutableMap<String, MutableMap<String, Any?>>
        for ((key, value) in components) {
            if (key == id) {
                value["state"] = "running"
                value.putAll(fields)
                break
            }
            if (value["state"] == "pending" || value["state"] == "running") value["state"] = "done"
        }
    }

    fun spec(id: String): SetupStepSpec = started.last().components.first { it.id == id }

    fun ids(): List<String> = started.last().components.map { it.id }

    companion object {
        /** A job map from setup.json text (the recorded fixture). */
        fun jobFrom(text: String): MutableMap<String, Any?> {
            @Suppress("UNCHECKED_CAST")
            return decode(Json.parseToJsonElement(text)) as MutableMap<String, Any?>
        }

        private fun decode(element: JsonElement): Any? = when (element) {
            JsonNull -> null
            is JsonObject -> element.mapValues { decode(it.value) }.toMutableMap()
            is JsonArray -> element.map { decode(it) }
            is JsonPrimitive -> if (element.isString) element.content
            else element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull
        }

        fun encode(value: Any?): String = when (value) {
            null -> "null"
            is String -> JsonPrimitive(value).toString()
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) -> "${encode(k.toString())}:${encode(v)}" }
            is Iterable<*> -> value.joinToString(",", "[", "]") { encode(it) }
            else -> encode(value.toString())
        }
    }
}

/** A clock that moves one millisecond each time it is read, so every job id is new. */
class StepClock(private var millis: Long = 1_790_000_000_000L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis++)
}

/** The real registry's shape (ids, order, dependencies, required, weights, sizes) with marker scripts. */
object FixtureRegistry : SetupRegistry {
    const val PRELUDE = "# oc setup prelude\n"
    const val OPENCODE2_PIN = "f8c5a82075fede9b5b044ed6c8a6d13ad807a7e62caea1cd0a878adef49eb053"

    fun openCodeInstall(runtime: String): String =
        if (runtime == "opencode2") "install opencode2 sha=$OPENCODE2_PIN\n" else "npm install opencode-ai@1\n"

    override fun components(params: Map<String, Map<String, String>>): List<SetupComponent> {
        val runtime = params[SetupComponentIds.OPENCODE]?.get("runtime") ?: "opencode2"
        val command = if (runtime == "opencode2") "opencode2" else "opencode"
        return listOf(
            SetupComponent("linux", "Linux base", "Linux base", "linux-check", "", required = true, native = true,
                estimatedSeconds = 15, downloadBytes = 30_000_000),
            SetupComponent("essentials", "Git and SSH", "Git and SSH", "essentials-check", "essentials-install",
                dependsOn = listOf("linux"), required = true, estimatedSeconds = 60, downloadBytes = 45_000_000),
            SetupComponent("python", "Python", "Python", "python-check", "python-install",
                dependsOn = listOf("essentials"), defaultOn = true, estimatedSeconds = 40, downloadBytes = 25_000_000),
            SetupComponent("node", "Node.js", "Node.js", "node-check", "oc_download node\n",
                dependsOn = listOf("essentials"), required = true, estimatedSeconds = 20, downloadBytes = 58_000_000),
            SetupComponent("opencode", "OpenCode", "OpenCode",
                "oc_bin=\$(command -v $command)\n[ \"\$(readlink -f \"\$oc_bin\")\" = /opt/$command/opencode ]\n",
                openCodeInstall(runtime), dependsOn = listOf("node"), required = true, estimatedSeconds = 100,
                downloadBytes = 50_000_000),
            SetupComponent("aiteam", "AI Team", "AI Team", "aiteam-check", "aiteam-install",
                dependsOn = listOf("essentials", "opencode"), estimatedSeconds = 150),
            SetupComponent("start", "Start OpenCode", "Start OpenCode", "", "", dependsOn = listOf("opencode"),
                required = true, jobStep = true, estimatedSeconds = 15),
        )
    }
}

val fixtureWords = SetupWords(
    notificationChannel = "channel",
    notificationTitle = "title",
    notificationProgress = "{percent}%",
    notificationDone = "done",
    notificationStopped = "stopped",
    stageStarting = "Starting OpenCode",
    stageDownloadingLinux = "Downloading Linux base",
    stageUnpackingLinux = "Unpacking Linux base",
    stageRetrying = "Retrying",
    addingTitle = { names -> "Adding " + names.joinToString(" and ") },
)

const val RECORDED_INTERRUPTED: String = """
{"jobId":"setup-1790196784299510","state":"interrupted","current":"node",
"order":["linux","essentials","python","node","opencode","start"],
"components":{
"linux":{"state":"done","weight":25,"stage":"Unpacking Linux base","done":29564928,"total":30028293,"percent":null,"version":"24.04.5","error":null,"startedAt":1790196784379,"endedAt":1790196792830},
"essentials":{"state":"done","weight":60,"stage":"Installing packages","done":null,"total":null,"percent":99,"version":"2.43.0","error":null,"startedAt":1790196792830,"endedAt":1790196821793},
"python":{"state":"done","weight":60,"stage":"Installing packages","done":null,"total":null,"percent":98,"version":"3.12.3","error":null,"startedAt":1790196821793,"endedAt":1790196838476},
"node":{"state":"pending","weight":30,"stage":"Downloading Node.js 24.21.0","done":23134208,"total":58088022,"percent":null,"version":null,"error":null,"startedAt":1790196838476,"endedAt":null},
"opencode":{"state":"pending","weight":240,"stage":null,"done":null,"total":null,"percent":null,"version":null,"error":null,"startedAt":null,"endedAt":null},
"start":{"state":"pending","weight":20,"stage":null,"done":null,"total":null,"percent":null,"version":null,"error":null,"startedAt":null,"endedAt":null,"data":{"openCodeChanged":"true","runtime":"opencode1"}}},
"overall":0.33,"startedAt":1790196784379,"updatedAt":1790196840162,"error":null,
"logTail":"Setting up python3-pip (24.0+dfsg-1ubuntu1.3) ...\nProcessing triggers for libc-bin (2.39-0ubuntu8.8) ...\n==> Downloading Node.js 24.21.0\n",
"params":{}}
"""

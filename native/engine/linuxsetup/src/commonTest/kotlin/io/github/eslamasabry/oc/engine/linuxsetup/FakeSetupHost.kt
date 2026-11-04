package io.github.eslamasabry.oc.engine.linuxsetup

import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Clock
import kotlin.time.Instant

/** The native side, scripted (FakeLinux / _InventoryLinux in the Flutter tests). */
internal class FakeSetupHost : SetupLinuxHost {
    var installed = true
    var statusRefusal: HostReply.Refused? = null
    var runRefusal: HostReply.Refused? = null
    var runResult: SetupRunResult? = null

    /** The tools the next check observes (taken when the run starts). */
    var tools: Set<String> = setOf("python", "aiteam")
    val scripts = mutableListOf<String>()
    var holdFirstProbe: CompletableDeferred<Unit>? = null
    var job: String? = null
    val started = mutableListOf<Pair<String, List<SetupRunnerSpec>>>()
    val startedParams = mutableListOf<Map<String, Map<String, String>>>()
    val statusReads = mutableListOf<String>()

    override suspend fun status(): HostReply<SetupLinuxStatus> =
        statusRefusal ?: HostReply.Ok(SetupLinuxStatus(installed = installed))

    override suspend fun run(script: String): HostReply<SetupRunResult> {
        scripts.add(script)
        val observed = tools.toSet()
        if (scripts.size == 1) holdFirstProbe?.await()
        runRefusal?.let { return it }
        runResult?.let { return HostReply.Ok(it) }
        val out = StringBuilder()
        for (match in Regex("""::oc-check-begin %s\\n' '([a-z0-9]+)'""").findAll(script)) {
            val id = match.groupValues[1]
            val ok = id in observed
            out.append("::oc-check-begin $id\n").append(if (ok) "1.0" else "command not found").append('\n')
            out.append("::oc-check-end $id ${if (ok) 0 else 1}\n")
        }
        return HostReply.Ok(SetupRunResult(0, out.toString()))
    }

    override suspend fun setupStatus(): HostReply<String?> {
        statusReads.add("setupStatus")
        return HostReply.Ok(job)
    }

    override suspend fun startSetup(
        jobId: String,
        components: List<SetupRunnerSpec>,
        params: Map<String, Map<String, String>>,
        texts: SetupNotificationTexts,
    ): HostReply<Unit> {
        started.add(jobId to components)
        startedParams.add(params)
        return HostReply.Ok(Unit)
    }
}

internal class FixedClock(var now: Instant = Instant.fromEpochMilliseconds(1_790_196_900_000)) : Clock {
    override fun now(): Instant = now
}

internal class RecordingTrace : SetupTrace {
    data class Span(val name: String, val millis: Long, val attrs: Map<String, String>, val failed: Boolean)

    val spans = mutableListOf<Span>()

    override fun recordDuration(name: String, millis: Long, attrs: Map<String, String>, failed: Boolean) {
        spans.add(Span(name, millis, attrs, failed))
    }
}

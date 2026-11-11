package io.github.eslamasabry.opencode_mobile

import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit

/** Private requests retain their exact process until drainage is confirmed. */
internal class PhoneAgentAuthProbe(
    private val start: (String, List<String>) -> Process,
    private val stop: (Process) -> Boolean,
    private val prepare: (String, String) -> Boolean = { _, _ -> true },
    private val waitFor: (Process, Long) -> Boolean = { process, seconds ->
        process.waitFor(seconds, TimeUnit.SECONDS)
    },
    /** Runs a drain asked for on another thread ([ProbeStop]), which has no request of its own. */
    private val elsewhere: (() -> Boolean) -> Boolean = { it() },
) {
    private val owners = IdentityHashMap<Process, String>()
    private val retired = java.util.Collections.newSetFromMap(IdentityHashMap<Process, Boolean>())
    private val blocked = mutableSetOf<String>()

    /** [stop] (a read that gives way to a person's step) ends this request's process; it then answers an error. */
    fun run(arguments: Map<*, *>, stop: ProbeStop? = null): Map<String, Any?> {
        val request = PhoneAgentAuthRequest.parse(arguments) ?: return PhoneAgentAuthProjection.error("invalidContext")
        return if (retryDrain(request.profileId)) execute(request, stop)
            else PhoneAgentAuthProjection.error("hostUnavailable")
    }

    private fun retryDrain(profile: String): Boolean {
        val targets = synchronized(owners) { retired.filter { owners[it] == profile } }
        return targets.map { drain(it) }.all { it }
    }

    private fun execute(request: PhoneAgentAuthRequest, stop: ProbeStop?): Map<String, Any?> {
        val process = try {
            synchronized(owners) {
                check(stop?.isStopped != true)
                check(request.profileId !in blocked)
                check(owners.values.none { it == request.profileId })
                check(prepare(request.profileId, request.agentId))
                start(request.profileId, listOf("/bin/sh", "-c", request.script)).also {
                    owners[it] = request.profileId
                }
            }
        } catch (_: Exception) { return PhoneAgentAuthProjection.error("hostUnavailable") }
        val capture = PhoneAgentAuthCapture(process)
        var result = PhoneAgentAuthProjection.error("hostUnavailable")
        try {
            // Stopped between the start and here: no answer, the process is drained below.
            if (stop == null || stop.attach { elsewhere { drain(process) } }) {
                capture.start()
                result = if (!waitFor(process, request.timeoutSeconds)) PhoneAgentAuthProjection.error("timedOut")
                    else capture.result(process.exitValue())
            }
        } catch (_: Exception) { /* Only fixed failures cross the bridge. */ }
        finally {
            stop?.detach()
            val drained = drain(process)
            capture.close()
            if (capture.overflowed()) result = PhoneAgentAuthProjection.error("invalidResponse")
            if (!drained) result = PhoneAgentAuthProjection.error("hostUnavailable")
        }
        return synchronized(owners) {
            // A stopped request's answer is not news: its process may have been ended halfway.
            val ended = request.profileId in blocked || stop?.isStopped == true
            if (ended) PhoneAgentAuthProjection.error("hostUnavailable") else result
        }
    }

    private fun drain(process: Process): Boolean {
        return try {
            synchronized(process) {
                // Drained already from another thread (a stopped read): it left the ledger only once it was gone.
                if (synchronized(owners) { process !in owners }) return true
                val drained = stop(process)
                synchronized(owners) {
                    if (drained) { owners.remove(process); retired.remove(process) }
                    else retired.add(process)
                }
                drained
            }
        } catch (_: Exception) {
            synchronized(owners) { retired.add(process) }
            false
        }
    }

    fun blockProfile(profileId: String): Boolean {
        val targets = synchronized(owners) {
            blocked.add(profileId)
            owners.filterValues { it == profileId }.keys.toList()
        }
        return targets.map { drain(it) }.all { it }
    }
}

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
) {
    private val owners = IdentityHashMap<Process, String>()
    private val retired = java.util.Collections.newSetFromMap(IdentityHashMap<Process, Boolean>())
    private val blocked = mutableSetOf<String>()

    fun run(arguments: Map<*, *>): Map<String, Any?> {
        val request = PhoneAgentAuthRequest.parse(arguments) ?: return PhoneAgentAuthProjection.error("invalidContext")
        return if (retryDrain(request.profileId)) execute(request)
            else PhoneAgentAuthProjection.error("hostUnavailable")
    }

    private fun retryDrain(profile: String): Boolean {
        val targets = synchronized(owners) { retired.filter { owners[it] == profile } }
        return targets.map { drain(it) }.all { it }
    }

    private fun execute(request: PhoneAgentAuthRequest): Map<String, Any?> {
        val process = try {
            synchronized(owners) {
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
            capture.start()
            result = if (!waitFor(process, request.timeoutSeconds)) PhoneAgentAuthProjection.error("timedOut")
                else capture.result(process.exitValue())
        } catch (_: Exception) { /* Only fixed failures cross the bridge. */ }
        finally {
            val drained = drain(process)
            capture.close()
            if (capture.overflowed()) result = PhoneAgentAuthProjection.error("invalidResponse")
            if (!drained) result = PhoneAgentAuthProjection.error("hostUnavailable")
        }
        return synchronized(owners) {
            if (request.profileId in blocked) PhoneAgentAuthProjection.error("hostUnavailable") else result
        }
    }

    private fun drain(process: Process): Boolean = try {
        synchronized(process) {
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

    fun blockProfile(profileId: String): Boolean {
        val targets = synchronized(owners) {
            blocked.add(profileId)
            owners.filterValues { it == profileId }.keys.toList()
        }
        return targets.map { drain(it) }.all { it }
    }
}

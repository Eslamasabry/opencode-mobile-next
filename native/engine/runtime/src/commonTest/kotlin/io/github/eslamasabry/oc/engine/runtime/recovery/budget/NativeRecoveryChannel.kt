package io.github.eslamasabry.oc.engine.runtime.recovery.budget

import io.github.eslamasabry.oc.engine.runtime.bridge.BuiltinLinuxChannel
import io.github.eslamasabry.oc.engine.runtime.bridge.ChannelReply
import kotlinx.coroutines.CompletableDeferred

/** A budget map shaped like native's (Dart test `_budget`). */
fun budgetMap(
    attempts: Int = 0,
    revision: Long = 0,
    pending: Boolean = false,
    eventId: String? = null,
    generation: Long? = null,
    confirmedAt: Long? = null,
): Map<String, Any?> =
    mapOf(
        "version" to 1,
        "revision" to revision,
        "attempts" to attempts,
        "nextAt" to null,
        "pending" to pending,
        "eventId" to eventId,
        "recoveryGeneration" to generation,
        "confirmedAt" to confirmedAt,
    )

private fun Any?.long(): Long? = (this as? Number)?.toLong()

/**
 * Native crash recovery in memory, spoken over the built-in Linux channel (port of the Dart test's `_NativeLinux`):
 * native owns the durable reservation; the app can only consume receipt snapshots.
 */
class NativeRecoveryChannel(private val markers: Map<String, String>) : BuiltinLinuxChannel {
    var budget: Map<String, Any?>? = null
    val receipts = mutableListOf<Map<String, Any?>>()
    val migrations = mutableListOf<Map<*, *>?>()
    val stagedMigrations = mutableListOf<Map<*, *>>()
    val calls = mutableListOf<String>()
    val acknowledgements = mutableListOf<String>()
    var boundProfile: String? = null
    var enabled = false
    var wanted = true
    var running = false
    var failStart = false
    var scheduled = false
    var corruptBudget = false
    var stageWriteFails = false
    var counterWriteRejected = false
    var nativeOwned = false
    var manualStarted = false
    var presenceRefused = false
    var generation = 1L
    var directStarts = 0
    var backgroundStarts = 0
    var manualResets = 0
    var confirmations = 0
    var presenceResets = 0
    private var eventSequence = 0
    var writeGate: CompletableDeferred<Unit>? = null
    var writeEntered: CompletableDeferred<Unit>? = null
    var deleteGate: CompletableDeferred<Unit>? = null
    var deleteEntered: CompletableDeferred<Unit>? = null

    val attempts: Int get() = budget!!["attempts"].long()!!.toInt()

    val markerValid: Boolean
        get() = ServerRestartBudget.nativeMarker(markers["oc.builtinRecovery.${boundProfile ?: "phone"}"])

    private val refused = ChannelReply.Error("builtin_linux")

    private fun ok(value: Any? = null) = ChannelReply.Value(value)

    private fun revision(): Long = budget!!["revision"].long()!!

    override suspend fun invoke(method: String, arguments: Map<String, Any?>?): ChannelReply {
        val args = arguments.orEmpty()
        return when (method) {
            "status" ->
                ok(
                    mapOf(
                        "installed" to true,
                        "phase" to "ready",
                        "serverRunning" to running,
                        "serverRestartWanted" to wanted,
                        "serverRecoveryGeneration" to (if (nativeOwned && running) budget!!["recoveryGeneration"] else generation),
                        "serverRecoveryAuthority" to true,
                        "serverRecoveryScheduled" to scheduled,
                    ),
                )
            "stageServerRecovery" -> {
                calls += "stage"
                enabled = false
                stagedMigrations += args["legacyBudget"] as Map<*, *>
                if (stageWriteFails) return refused
                @Suppress("UNCHECKED_CAST")
                if (budget == null) budget = (args["legacyBudget"] as Map<String, Any?>).toMap()
                ok(budget!!.toMap())
            }
            "bindServerRecovery" -> {
                calls += "bind"
                boundProfile = args["profileId"] as String
                if (!markerValid) return refused
                migrations += args["legacyBudget"] as Map<*, *>?
                if (budget == null) return refused
                enabled = args["enabled"] as Boolean
                readBudget()
            }
            "serverRecoveryBudget" -> readBudget()
            "updateServerRecoveryReceipt" -> {
                calls += "receipt"
                @Suppress("UNCHECKED_CAST")
                val next = args["budget"] as Map<String, Any?>
                if (next["attempts"].long() != budget!!["attempts"].long() || next["revision"].long() != revision()) {
                    counterWriteRejected = true
                    return refused
                }
                budget = next + ("revision" to revision() + 1)
                ok(budget!!.toMap())
            }
            "run" -> {
                calls += "password-write"
                writeEntered?.complete(Unit)
                writeGate?.await()
                calls += "password-written"
                ok(mapOf("exitCode" to 0, "output" to ""))
            }
            "restartServer" -> {
                if (args["expectedGeneration"].long() != generation) return refused
                if (!reserve(background = false)) return refused
                ok()
            }
            "startServer" -> {
                generation++
                wanted = true
                running = !failStart
                nativeOwned = false
                manualStarted = true
                ok()
            }
            "confirmServerRecovery" -> {
                if (!wanted || !running || args["expectedGeneration"].long() != budget!!["recoveryGeneration"].long()) {
                    return refused
                }
                confirmations++
                ok()
            }
            "confirmServerPresence" -> {
                if (presenceRefused || boundProfile != args["profileId"] || !running || !wanted || scheduled ||
                    budget!!["pending"] == true
                ) {
                    return refused
                }
                if (attempts == 0) return ok(budget!!.toMap())
                budget = budgetMap(revision = revision() + 1)
                presenceResets++
                ok(budget!!.toMap())
            }
            "confirmManualServerStart" -> {
                if (boundProfile != args["profileId"] || !manualStarted || !running || !wanted) return refused
                if (budget!!["confirmedAt"] != null) receipts += budget!!.toMap()
                budget = budgetMap(revision = revision() + 1)
                manualStarted = false
                manualResets++
                ok(budget!!.toMap())
            }
            "serverRecoveryReceipts" -> ok(receipts.map { it.toMap() })
            "ackServerRecoveryReceipt" -> {
                val eventId = args["eventId"] as String
                acknowledgements += eventId
                receipts.removeAll { it["eventId"] == eventId }
                ok()
            }
            "cancelServerRecovery" -> {
                generation++
                if (!nativeOwned && budget?.get("pending") == true && budget?.get("confirmedAt") == null) running = false
                ok()
            }
            "unbindServerRecovery" -> {
                enabled = false
                scheduled = false
                calls += "unbind"
                ok()
            }
            "deleteServerRecovery" -> {
                enabled = false
                scheduled = false
                calls += "delete-entered"
                deleteEntered?.complete(Unit)
                deleteGate?.await()
                budget = null
                receipts.clear()
                calls += "deleted"
                ok()
            }
            else -> error("unexpected native call $method")
        }
    }

    private fun readBudget(): ChannelReply {
        val current = budget ?: return refused
        return ok(if (corruptBudget) mapOf("version" to 1, "attempts" to -1) else current.toMap())
    }

    private fun reserve(background: Boolean): Boolean {
        if (!wanted || !enabled || !markerValid || attempts >= 3) return false
        val current = budget!!
        if (current["confirmedAt"] != null) receipts += current.toMap()
        budget =
            budgetMap(
                attempts = attempts + 1,
                revision = revision() + 1,
                pending = true,
                eventId = "native-restart:${++eventSequence}",
                generation = generation,
            )
        nativeOwned = background
        running = !failStart
        scheduled = false
        if (background) backgroundStarts++ else directStarts++
        return true
    }

    /** Native restarted the server while the app was in the background. */
    fun backgroundRestart() {
        check(reserve(background = true)) { "background restart refused" }
    }
}

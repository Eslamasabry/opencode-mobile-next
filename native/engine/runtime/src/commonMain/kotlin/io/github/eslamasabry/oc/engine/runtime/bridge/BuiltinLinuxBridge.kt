package io.github.eslamasabry.oc.engine.runtime.bridge

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.getOrNull
import io.github.eslamasabry.oc.core.common.map
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Status reads in flight, shared by every [BuiltinLinuxBridge] over the same channel (Dart keyed them by channel).
 * Callers that ask while a read is in flight share it; after any other request on the channel, a read is fresh.
 */
public class StatusReadSharing {
    internal val lock = Mutex()
    internal var requests = 0L
    internal var shared: Pair<Long, CompletableDeferred<Outcome<RuntimeStatus>>>? = null
}

/**
 * The built-in Linux channel contract (port of the channel half of lib/builtin/builtin_linux.dart; B-LINUX-018…024,
 * 031, 049…052). Holds no state but the shared status read, validates every identity before anything reaches native,
 * and turns every native refusal into a fixed [Failure]: no native message ever becomes copy.
 *
 * [supported] is `PlatformCapabilities.builtinLinux` (Android only). On any other runner the idle, heartbeat and lease
 * methods answer "nothing" without dispatching, and every other method is [Failure.Unavailable].
 */
public class BuiltinLinuxBridge(
    private val channel: BuiltinLinuxChannel,
    private val supported: Boolean,
    private val statusReads: StatusReadSharing = StatusReadSharing(),
    /** Called after every runtime removal attempt (Dart `setupToolsChanged.value++`), success or not. */
    private val onSetupToolsChanged: () -> Unit = {},
) {
    // ---- Status

    /** One reading of `status`, shared with any read already in flight since the last other request. */
    public suspend fun status(): Outcome<RuntimeStatus> {
        if (!supported) return RuntimeStatusMap.Absent.ok()
        while (true) {
            var owner = false
            val read =
                statusReads.lock.withLock {
                    val current = statusReads.shared
                    if (current != null && current.first == statusReads.requests) {
                        current.second
                    } else {
                        CompletableDeferred<Outcome<RuntimeStatus>>().also {
                            owner = true
                            statusReads.shared = statusReads.requests to it
                        }
                    }
                }
            if (!owner) {
                try {
                    return read.await()
                } catch (e: CancellationException) {
                    // The owning caller was cancelled, not this one: read afresh.
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }
            try {
                val result = readStatus()
                read.complete(result)
                return result
            } catch (e: Throwable) {
                read.cancel()
                throw e
            } finally {
                withContext(NonCancellable) {
                    statusReads.lock.withLock { if (statusReads.shared?.second === read) statusReads.shared = null }
                }
            }
        }
    }

    private suspend fun readStatus(): Outcome<RuntimeStatus> =
        when (val reply = channel.invoke("status")) {
            is ChannelReply.Value ->
                when (val value = reply.value) {
                    null -> RuntimeStatusMap.parse(emptyMap<String, Any?>()).ok()
                    is Map<*, *> -> RuntimeStatusMap.parse(value).ok()
                    else -> Failure.UnreadableReply().failed()
                }
            else -> failureOf(reply).failed()
        }

    // ---- Idle policy (B-LINUX-018, B-LINUX-019)

    /**
     * Saves the authored idle policy on an APK supporting the full contract. Minutes outside 1…60 never reach native
     * (the picker bounds them, so this is a bug path: [Failure.Unexpected]).
     */
    public suspend fun setPhoneServerIdlePolicy(enabled: Boolean, idleMinutes: Int): Outcome<RuntimeStatus> {
        if (idleMinutes !in 1..60) return Failure.Unexpected().failed()
        return idleStatus("setPhoneServerIdlePolicy", mapOf("enabled" to enabled, "idleMinutes" to idleMinutes))
    }

    /** Resumes the idle-stopped server of exactly this owner generation; there is no start fallback. */
    public suspend fun resumeIdleStoppedPhoneServer(profileId: String, expectedIdleGeneration: Long): Outcome<RuntimeStatus> {
        if (!idleIdentityValid(profileId, expectedIdleGeneration)) return RuntimeProblem.IdleResumeStale.failure()
        return idleStatus(
            "resumeIdleStoppedPhoneServer",
            mapOf("profileId" to profileId, "expectedIdleGeneration" to expectedIdleGeneration),
            timeout = 30.seconds,
        )
    }

    public suspend fun completePhoneServerIdleResume(profileId: String, expectedIdleGeneration: Long): Outcome<RuntimeStatus> {
        if (!idleIdentityValid(profileId, expectedIdleGeneration)) return RuntimeProblem.IdleResumeStale.failure()
        return idleStatus(
            "completePhoneServerIdleResume",
            mapOf("profileId" to profileId, "expectedIdleGeneration" to expectedIdleGeneration),
        )
    }

    /**
     * The tri-state work heartbeat of one readable profile alias: busy, idle or unknown (null). It carries no work
     * text. A missing, refused or slow heartbeat never proves native idle, so every failure is swallowed.
     */
    public suspend fun observePhoneAgentWork(profileId: String, busy: Boolean?) {
        if (!supported || !PROFILE_ID.matches(profileId)) return
        withTimeoutOrNull(8.seconds) {
            countedInvoke("observePhoneAgentWork", mapOf("profileId" to profileId, "busy" to busy))
        }
    }

    private fun idleIdentityValid(profileId: String, generation: Long): Boolean =
        PROFILE_ID.matches(profileId) && generation > 0

    private suspend fun idleStatus(
        method: String,
        arguments: Map<String, Any?>,
        timeout: Duration = 8.seconds,
    ): Outcome<RuntimeStatus> {
        if (!supported) return RuntimeStatusMap.Absent.ok()
        val unavailable = RuntimeProblem.IdleResumeUnavailable.failure()
        val reply = withTimeoutOrNull(timeout) { countedInvoke(method, arguments) } ?: return unavailable
        val map = (reply as? ChannelReply.Value)?.value as? Map<*, *> ?: return unavailable
        val status = RuntimeStatusMap.parse(map)
        return if (status.serverIdleReceiptValid) status.ok() else unavailable
    }

    // ---- Install, run, start, stop (B-LINUX-020…022)

    /** Starts the download and unpack; returns at once. Poll [status]. */
    public suspend fun installUbuntu(): Outcome<Unit> = invokeUnit("installUbuntu")

    public suspend fun run(script: String, timeout: Duration = 2.minutes): Outcome<RunResult> =
        runWith(mapOf("script" to script, "timeoutSeconds" to timeout.inWholeSeconds))

    /** Setup checks in the fixed oc view. Never use for credentials or sign-in. */
    public suspend fun runAgentSetupCheck(script: String, timeout: Duration = 2.minutes): Outcome<RunResult> =
        runWith(mapOf("script" to script, "timeoutSeconds" to timeout.inWholeSeconds, "agentUser" to true))

    private suspend fun runWith(arguments: Map<String, Any?>): Outcome<RunResult> =
        invokeValue("run", arguments).map { raw ->
            val map = raw as? Map<*, *>
            RunResult(
                exitCode = RuntimeStatusMap.asLong(map?.get("exitCode"))?.toInt() ?: -1,
                output = map?.get("output")?.toString() ?: "",
            )
        }

    /**
     * Starts the server. With a [restoreRecipe] any refusal reads [RuntimeProblem.ServerStartUnavailable] (still with
     * the phone's details); without one, the phone's own refusal comes through with its details.
     */
    public suspend fun startServer(script: String, port: Int = SERVER_PORT, restoreRecipe: RestoreRecipe? = null): Outcome<Unit> =
        startWith("startServer", script, port, restoreRecipe, expectedGeneration = null)

    /** The app's opening start, only while the native intent generation the caller saw still stands. */
    public suspend fun startServerForLaunch(
        script: String,
        expectedGeneration: Long,
        port: Int = SERVER_PORT,
        restoreRecipe: RestoreRecipe? = null,
    ): Outcome<Unit> = startWith("startServerForLaunch", script, port, restoreRecipe, expectedGeneration)

    private suspend fun startWith(
        method: String,
        script: String,
        port: Int,
        restoreRecipe: RestoreRecipe?,
        expectedGeneration: Long?,
    ): Outcome<Unit> {
        val arguments = buildMap {
            put("script", script)
            put("port", port)
            if (expectedGeneration != null) put("expectedGeneration", expectedGeneration)
            if (restoreRecipe != null) put("restoreRecipe", restoreRecipe.toMap())
        }
        val result = invokeUnit(method, arguments)
        if (result !is Outcome.Failed || restoreRecipe == null) return result
        val failure = result.failure
        if (failure !is Failure.Runtime) return result
        return Failure.Runtime(RuntimeProblem.ServerStartUnavailable, failure.details).failed()
    }

    public suspend fun stopServer(): Outcome<Unit> = invokeUnit("stopServer")

    /** Setup alone may restore this stop if its following engine activation fails. */
    public suspend fun stopServerForPhoneEngineSetup(): Outcome<Unit> = invokeUnit("stopServer", mapOf("phoneEngineSetup" to true))

    public suspend fun serverLog(tailBytes: Int = 32768): Outcome<String> =
        invokeValue("serverLog", mapOf("tailBytes" to tailBytes)).map { it as? String ?: "" }

    /** Stops the runtime and removes Ubuntu and tools, preserving /root/projects. */
    public suspend fun uninstall(): Outcome<Unit> = remove()

    /**
     * Removes the runtime, keeping projects unless [alsoDeleteProjects] with the exact typed app name
     * ([DELETION_CONFIRMATION_NAME]). A distinct native method fails closed on an older APK.
     */
    public suspend fun remove(alsoDeleteProjects: Boolean = false, confirmationName: String? = null): Outcome<Unit> {
        if (alsoDeleteProjects && confirmationName != DELETION_CONFIRMATION_NAME) {
            return RuntimeProblem.ConfirmationRequired.failure()
        }
        val arguments = buildMap {
            put("alsoDeleteProjects", alsoDeleteProjects)
            if (alsoDeleteProjects) put("confirmationName", DELETION_CONFIRMATION_NAME)
        }
        try {
            val result = invokeUnit("removeRuntime", arguments)
            if (result is Outcome.Failed && result.failure is Failure.Runtime) return RuntimeProblem.RemovalFailed.failure()
            return result
        } finally {
            onSetupToolsChanged()
        }
    }

    // ---- Recovery (B-LINUX-021, B-LINUX-031)

    /** Restarts only a stopped server whose native intent and admission token still match. Does not opt in. */
    public suspend fun restartServer(script: String, expectedGeneration: Long, port: Int = SERVER_PORT): Outcome<Unit> =
        invokeRecovery("restartServer", mapOf("script" to script, "port" to port, "expectedGeneration" to expectedGeneration))

    /** Releases ownership of an automatic attempt after the health probe, for the same admission token. */
    public suspend fun confirmServerRecovery(expectedGeneration: Long): Outcome<Unit> =
        invokeRecovery("confirmServerRecovery", mapOf("expectedGeneration" to expectedGeneration))

    /** Invalidates pending automatic work without changing the person's intent. */
    public suspend fun cancelServerRecovery(): Outcome<Unit> = invokeRecovery("cancelServerRecovery", null)

    /** Saves the legacy count in native storage without arming crash recovery. */
    public suspend fun stageServerRecovery(profileId: ProfileId, legacyBudget: Map<String, Any?>): Outcome<Map<*, *>> =
        recoveryMap("stageServerRecovery", mapOf("profileId" to profileId.raw, "legacyBudget" to legacyBudget))

    public suspend fun bindServerRecovery(
        profileId: ProfileId,
        enabled: Boolean,
        legacyBudget: Map<String, Any?>? = null,
    ): Outcome<Map<*, *>> =
        recoveryMap(
            "bindServerRecovery",
            buildMap {
                put("profileId", profileId.raw)
                put("enabled", enabled)
                if (legacyBudget != null) put("legacyBudget", legacyBudget)
            },
        )

    /** The native receipts; a missing list or any non-map entry is [RuntimeProblem.RecoveryUnavailable]. */
    public suspend fun serverRecoveryReceipts(profileId: ProfileId): Outcome<List<Map<*, *>>> {
        val list = invokeValue("serverRecoveryReceipts", mapOf("profileId" to profileId.raw)).getOrNull() as? List<*>
        if (list == null || list.any { it !is Map<*, *> }) return RuntimeProblem.RecoveryUnavailable.failure()
        return list.map { it as Map<*, *> }.ok()
    }

    public suspend fun ackServerRecoveryReceipt(profileId: ProfileId, eventId: String): Outcome<Unit> =
        invokeRecovery("ackServerRecoveryReceipt", mapOf("profileId" to profileId.raw, "eventId" to eventId))

    public suspend fun serverRecoveryBudget(profileId: ProfileId): Outcome<Map<*, *>> =
        recoveryMap("serverRecoveryBudget", mapOf("profileId" to profileId.raw))

    public suspend fun updateServerRecoveryReceipt(profileId: ProfileId, budget: Map<String, Any?>): Outcome<Map<*, *>> =
        recoveryMap("updateServerRecoveryReceipt", mapOf("profileId" to profileId.raw, "budget" to budget))

    public suspend fun confirmManualServerStart(profileId: ProfileId): Outcome<Map<*, *>> =
        recoveryMap("confirmManualServerStart", mapOf("profileId" to profileId.raw))

    /** The app is open and the server answered: native starts the restore count again, unless a restore is in flight. */
    public suspend fun confirmServerPresence(profileId: ProfileId): Outcome<Map<*, *>> =
        recoveryMap("confirmServerPresence", mapOf("profileId" to profileId.raw))

    public suspend fun unbindServerRecovery(profileId: ProfileId, delete: Boolean = false): Outcome<Unit> =
        invokeRecovery(if (delete) "deleteServerRecovery" else "unbindServerRecovery", mapOf("profileId" to profileId.raw))

    /** Any refusal, a build without the channel, or a missing map: only the fixed recovery failure, no details. */
    private suspend fun recoveryMap(method: String, arguments: Map<String, Any?>): Outcome<Map<*, *>> {
        val value = invokeValue(method, arguments).getOrNull() as? Map<*, *>
        return value?.ok() ?: RuntimeProblem.RecoveryUnavailable.failure()
    }

    private suspend fun invokeRecovery(method: String, arguments: Map<String, Any?>?): Outcome<Unit> =
        if (invokeValue(method, arguments) is Outcome.Ok) Unit.ok() else RuntimeProblem.RecoveryUnavailable.failure()

    // ---- Work leases (B-LINUX-049…052)

    /**
     * Keeps the phone awake while a reply runs, for at most [hold] (Android caps it at 15 minutes). The legacy timed
     * hold: answers whether it is held, a non-boolean reply reading false.
     */
    public suspend fun holdAwakeForWork(on: Boolean, hold: Duration = 10.minutes): Outcome<Boolean> =
        invokeValue("holdAwakeForWork", mapOf("on" to on, "forMs" to hold.inWholeMilliseconds)).map { it as? Boolean ?: false }

    /**
     * Acquires or renews one opaque chat lease; off releases only [leaseId]. Native lifetime limits stay authoritative.
     * An invalid id is [RuntimeProblem.WorkLeaseInvalid] before any native call; an unsupported runner or a malformed
     * reply is [WorkLeaseStatus.NotAdmitted].
     */
    public suspend fun setChatWorkLease(leaseId: String, on: Boolean, hold: Duration = 10.minutes): Outcome<WorkLeaseStatus> {
        if (!supported) return WorkLeaseStatus.NotAdmitted.ok()
        if (!LEASE_ID.matches(leaseId)) return RuntimeProblem.WorkLeaseInvalid.failure()
        return invokeValue("setChatWorkLease", mapOf("leaseId" to leaseId, "on" to on, "forMs" to hold.inWholeMilliseconds))
            .map(WorkLeaseStatus::parse)
    }

    /** A local agent turn's own lease scope; off drains this exact opaque owner even after its helper stopped. */
    public suspend fun setPhoneAgentChatWorkLease(
        profileId: String,
        leaseId: String,
        on: Boolean,
        hold: Duration = 15.minutes,
    ): Outcome<WorkLeaseStatus> {
        if (!supported) return WorkLeaseStatus.NotAdmitted.ok()
        if (!LEASE_ID.matches(leaseId) || !PROFILE_ID.matches(profileId)) return RuntimeProblem.WorkLeaseInvalid.failure()
        return invokeValue(
            "setPhoneAgentChatWorkLease",
            mapOf("profileId" to profileId, "leaseId" to leaseId, "on" to on, "forMs" to hold.inWholeMilliseconds),
        ).map(WorkLeaseStatus::parse)
    }

    // ---- Plumbing

    private suspend fun invokeUnit(method: String, arguments: Map<String, Any?>? = null): Outcome<Unit> =
        invokeValue(method, arguments).map { }

    private suspend fun invokeValue(method: String, arguments: Map<String, Any?>? = null): Outcome<Any?> {
        if (!supported) return Failure.Unavailable(BuiltinLinuxFeature).failed()
        return when (val reply = countedInvoke(method, arguments)) {
            is ChannelReply.Value -> reply.value.ok()
            else -> failureOf(reply).failed()
        }
    }

    /** Every request but `status` makes the next status read fresh. */
    private suspend fun countedInvoke(method: String, arguments: Map<String, Any?>?): ChannelReply {
        statusReads.lock.withLock { statusReads.requests++ }
        return channel.invoke(method, arguments)
    }

    private fun failureOf(reply: ChannelReply): Failure =
        when (reply) {
            is ChannelReply.Error -> reply.toFailure()
            ChannelReply.Missing -> Failure.Unavailable(BuiltinLinuxFeature)
            is ChannelReply.Value -> Failure.Unexpected()
        }

    private fun RuntimeProblem.failure(): Outcome<Nothing> = Failure.Runtime(this).failed()

    public companion object {
        public const val CHANNEL_NAME: String = "io.github.eslamasabry.opencode_mobile/builtin_linux"

        /** Not Termux's 4096: a separate port keeps the in-app server apart from the Termux-managed one. */
        public const val SERVER_PORT: Int = 4097
        public const val SERVER_URL: String = "http://127.0.0.1:$SERVER_PORT"
        public const val SERVER_USERNAME: String = "opencode"

        /** The OpenCode server's name among the services (BuiltinLinux.kt SERVER). */
        public const val SERVER_SERVICE_NAME: String = "server"

        /** Literal app name the destructive confirmation asks the person to type. */
        public const val DELETION_CONFIRMATION_NAME: String = "OpenCode"

        /** A readable profile alias: letters, digits, `_` and `-`, 1…80. */
        public val PROFILE_ID: Regex = Regex("[A-Za-z0-9_-]{1,80}")

        /** An opaque lease name: letters, digits, `_`, `.` and `-`, 1…80. */
        public val LEASE_ID: Regex = Regex("[A-Za-z0-9_.-]{1,80}")

        private val HTTP_URL = Regex("http://([^/?#@:]*)(?::([0-9]*))?([/?#].*)?")

        /** Whether [value] is the in-app server's address: plain http, host 127.0.0.1, port [SERVER_PORT]. */
        public fun managesServerUrl(value: String?): Boolean {
            val match = value?.let { HTTP_URL.matchEntire(it) } ?: return false
            val portText = match.groupValues[2]
            val port = if (portText.isEmpty()) 80 else portText.toIntOrNull() ?: return false
            return match.groupValues[1] == "127.0.0.1" && port == SERVER_PORT
        }
    }
}

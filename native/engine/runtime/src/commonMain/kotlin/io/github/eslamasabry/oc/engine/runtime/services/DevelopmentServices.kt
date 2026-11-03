package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.BusyKind
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.runtime.devservers.TerminalText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What one project's Development services panel shows. */
public data class DevelopmentServicesState(
    val services: List<DevelopmentService> = emptyList(),
    val statuses: Map<String, DevelopmentServiceStatus> = emptyMap(),
    /** Plain-text log tails by service id; never saved. */
    val logs: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    /** False when the saved list cannot be read: nothing is changed until it can. */
    val readable: Boolean = true,
    val error: Failure? = null,
)

/**
 * One visible project panel (port of lib/state/development_services.dart, B-LINUX-060..064). Every remote change
 * resolves the current transport, then rechecks scope ([isCurrent] and the runtime generation that
 * [invalidateRuntime] moves). Only a receipt written by Start can authorize control of a shell; command text or a
 * discovered pid never grants ownership.
 *
 * Commands answer an [Outcome] and also put a failure in [state]'s `error`. One command runs at a time: a command
 * while another is busy is refused with `Busy`. Call [reload] once after construction. The caller confines the
 * object to one serial lane (the engine's), as the Flutter model ran on the UI isolate.
 *
 * Failure kinds: scope change `Refused(StaleScope)`; unsupported transport `Unavailable(developmentServices)`; no
 * reachable server `Unknown`; ownership not confirmed `Runtime(OwnershipUnknown)`; a previous run not yet confirmed
 * `Runtime(ConfirmationRequired)`; a stop the server has not confirmed `Busy(Stopping)`; an invalid service
 * `StorageRefused`; a bad log answer `UnreadableReply`.
 *
 * [newOwnerToken] must come from a secure random source (Flutter: 24 bytes of `Random.secure()` as hex).
 */
public class DevelopmentServices(
    private val store: DevelopmentServiceStore,
    public val directory: String,
    public val workspace: String?,
    private val resolveShells: suspend () -> ManagedShellPort?,
    private val isCurrent: () -> Boolean,
    private val supported: () -> Boolean,
    private val newOwnerToken: () -> String,
) {
    private val mutableState = MutableStateFlow(DevelopmentServicesState())
    public val state: StateFlow<DevelopmentServicesState> = mutableState.asStateFlow()

    private var services: List<DevelopmentService> = emptyList()
    private val observed = mutableMapOf<String, ManagedShell>()
    private val logs = mutableMapOf<String, String>()
    private val logSources = mutableMapOf<String, ManagedShell>()
    private var busy = false
    private var readable = true
    private var error: Failure? = null
    private var generation = 0
    private var closed = false

    /** Internal control flow: a step answered a failure, so the command ends with it. Never leaves this class. */
    private class Abort(val failure: Failure) : Exception()

    private fun abort(failure: Failure): Nothing = throw Abort(failure)

    private fun <T> Outcome<T>.orAbort(): T =
        when (this) {
            is Outcome.Ok -> value
            is Outcome.Failed -> abort(failure)
        }

    public suspend fun reload() {
        when (val loaded = store.load()) {
            is Outcome.Ok -> {
                services = loaded.value.filter { it.directory == directory && it.workspace == workspace }
                readable = true
            }
            is Outcome.Failed -> {
                services = emptyList()
                readable = false
                error = loaded.failure
            }
        }
        publish()
    }

    public fun status(service: DevelopmentService): DevelopmentServiceStatus {
        val run = service.run ?: return DevelopmentServiceStatus.NotStarted
        if (run.stopped) return DevelopmentServiceStatus.Stopped
        val shell = observed[service.id]
        if (shell == null || !matches(service, shell)) return DevelopmentServiceStatus.Unknown
        return when (shell.status) {
            ManagedShellStatus.Running -> DevelopmentServiceStatus.Running
            ManagedShellStatus.Exited, ManagedShellStatus.Killed, ManagedShellStatus.Timeout ->
                DevelopmentServiceStatus.Stopped
            ManagedShellStatus.Unknown -> DevelopmentServiceStatus.Unknown
        }
    }

    public fun exitCode(service: DevelopmentService): Int? = observed[service.id]?.exitCode

    public fun canStart(service: DevelopmentService): Boolean =
        supported() && !busy && isCurrent() && readable &&
            status(service).let { it == DevelopmentServiceStatus.NotStarted || it == DevelopmentServiceStatus.Stopped }

    public fun canStop(service: DevelopmentService): Boolean =
        supported() && !busy && isCurrent() && status(service) == DevelopmentServiceStatus.Running

    /** The connection or project changed: what was observed no longer counts, and work in flight stops changing it. */
    public fun invalidateRuntime() {
        generation++
        observed.clear()
        logs.clear()
        logSources.clear()
        busy = false
        publish()
    }

    public fun close() {
        closed = true
        generation++
    }

    private fun current(at: Int): Boolean = !closed && isCurrent() && at == generation

    private fun check(at: Int) {
        if (!current(at)) abort(Failure.Refused(RefusalReason.StaleScope))
    }

    private suspend fun shells(at: Int): ManagedShellPort {
        check(at)
        if (!supported()) abort(Failure.Unavailable(FEATURE))
        val port = resolveShells()
        check(at)
        return port ?: abort(Failure.Unknown())
    }

    private suspend fun action(body: suspend (Int) -> Unit): Outcome<Unit> {
        if (busy || !isCurrent() || closed || !readable) return Failure.Busy(BusyKind.SessionBusy).failed()
        val at = generation
        busy = true
        error = null
        publish()
        var result: Outcome<Unit> = Unit.ok()
        try {
            body(at)
        } catch (stop: Abort) {
            result = stop.failure.failed()
            if (current(at)) {
                error = stop.failure
                observed.clear()
            }
        } finally {
            if (current(at)) {
                busy = false
                reload()
            }
        }
        return result
    }

    /** Saves [service] for this project. Never runs it. */
    public suspend fun register(service: DevelopmentService): Outcome<Unit> = action {
        if (service.directory != directory || service.workspace != workspace || service.run != null ||
            service.name.isBlank() || service.name.length > 80 ||
            service.command.isBlank() || service.command.length > 4096
        ) {
            abort(Failure.StorageRefused())
        }
        store.save(service).orAbort()
    }

    private fun matches(service: DevelopmentService, shell: ManagedShell): Boolean {
        val run = service.run ?: return false
        return !run.stopped &&
            shell.ownerToken == run.ownerToken &&
            shell.command == service.command &&
            shell.directory == service.directory &&
            (run.shellId == null || shell.id == run.shellId) &&
            (run.startedAtMillis == null || shell.startedAtMillis == run.startedAtMillis)
    }

    private fun forget(id: String) {
        observed.remove(id)
        logs.remove(id)
        logSources.remove(id)
    }

    private suspend fun reconcile(service: DevelopmentService, port: ManagedShellPort, at: Int): ManagedShell? {
        val run = service.run ?: return null
        if (run.stopped) return null
        val shell =
            if (run.shellId != null) {
                port.get(run.shellId).orAbort()
            } else {
                // Recover only this saved, pre-POST ownership nonce. Never adopt a shell because it looks familiar.
                val inventory = port.running().orAbort()
                check(at)
                inventory.shells.filter { matches(service, it) }.singleOrNull()
            }
        check(at)
        if (shell == null || !matches(service, shell)) {
            forget(service.id)
            return null
        }
        if (run.shellId == null) {
            store.updateRun(
                service.id,
                DevelopmentServiceRun(run.ownerToken, shell.id, shell.startedAtMillis),
                expectedOwner = run.ownerToken,
            ).orAbort()
            check(at)
        }
        observed[service.id] = shell
        return shell
    }

    /** Re-reads the saved list and confirms each owned run with the server. A failure leaves nothing "running". */
    public suspend fun refresh(): Outcome<Unit> = action { at ->
        reload()
        observed.clear()
        if (!supported() || services.none { it.run != null && !it.run.stopped }) return@action
        val port = shells(at)
        for (service in services) reconcile(service, port, at)
    }

    public suspend fun start(id: String): Outcome<Unit> = action { at -> startIn(id, at) }

    private fun service(id: String): DevelopmentService =
        services.firstOrNull { it.id == id } ?: abort(Failure.Refused(RefusalReason.StaleScope))

    private suspend fun startIn(id: String, at: Int) {
        val service = service(id)
        val state = status(service)
        if (state != DevelopmentServiceStatus.NotStarted && state != DevelopmentServiceStatus.Stopped) {
            abort(Failure.Runtime(RuntimeProblem.ConfirmationRequired))
        }
        val port = shells(at)
        val owner = newOwnerToken()
        store.updateRun(id, DevelopmentServiceRun(ownerToken = owner)).orAbort()
        reload()
        check(at)
        // A failure or a lost answer deliberately leaves the pending nonce saved.
        val shell = port.start(service.command, directory, owner).orAbort()
        val pending = service.withRun(DevelopmentServiceRun(ownerToken = owner))
        if (!matches(pending, shell) || !shell.id.startsWith("sh_")) {
            abort(Failure.Runtime(RuntimeProblem.OwnershipUnknown))
        }
        // Keep the receipt even if this screen closed while the POST was pending. The store refuses writes after the
        // profile was deleted or a newer start replaced the nonce.
        store.updateRun(id, DevelopmentServiceRun(owner, shell.id, shell.startedAtMillis), expectedOwner = owner)
            .orAbort()
        if (current(at)) {
            observed[id] = shell
            logs.remove(id)
            logSources.remove(id)
        }
    }

    public suspend fun stop(id: String): Outcome<Unit> = action { at -> stopIn(id, at) }

    private suspend fun stopIn(id: String, at: Int) {
        val service = service(id)
        val port = shells(at)
        val shell = reconcile(service, port, at) ?: abort(Failure.Runtime(RuntimeProblem.OwnershipUnknown))
        check(at)
        port.stop(shell.id).orAbort()
        check(at)
        val remaining = port.get(shell.id).orAbort()
        check(at)
        if (remaining != null) abort(Failure.Busy(BusyKind.Stopping))
        val owner = service.run!!.ownerToken
        store.updateRun(id, DevelopmentServiceRun(owner, shell.id, shell.startedAtMillis, stopped = true), owner)
            .orAbort()
        forget(id)
        reload()
    }

    public suspend fun restart(id: String): Outcome<Unit> = action { at ->
        stopIn(id, at)
        check(at)
        startIn(id, at)
    }

    /** Loads the end of [id]'s output: one 1-byte read for the size, then at most 64 KB, colour codes removed. */
    public suspend fun readLogs(id: String): Outcome<Unit> = action { at ->
        logs.remove(id)
        logSources.remove(id)
        val port = shells(at)
        val service = service(id)
        val shell = reconcile(service, port, at) ?: abort(Failure.Runtime(RuntimeProblem.OwnershipUnknown))
        val head = port.output(shell.id, cursor = 0, limit = 1).orAbort()
        check(at)
        if (head.size < 0) abort(Failure.UnreadableReply())
        val start = maxOf(0L, head.size - LOG_TAIL)
        val tail = port.output(shell.id, cursor = start, limit = LOG_TAIL).orAbort()
        check(at)
        if (tail.cursor < start || tail.cursor > tail.size) abort(Failure.UnreadableReply())
        val text = tail.text.let { if (it.length > LOG_TAIL) it.substring(it.length - LOG_TAIL) else it }
        logs[id] = TerminalText.plain(text)
        logSources[id] = shell
    }

    /** Forgets an unknown run locally. Never stops anything on the server. */
    public suspend fun forgetRun(id: String): Outcome<Unit> = action {
        store.updateRun(id, null).orAbort()
        forget(id)
    }

    /** Removes the saved service. Never stops anything on the server. */
    public suspend fun remove(id: String): Outcome<Unit> = action {
        store.remove(id).orAbort()
        forget(id)
    }

    private fun publish() {
        if (closed) return
        mutableState.value =
            DevelopmentServicesState(
                services = services,
                statuses = services.associate { it.id to status(it) },
                logs = logs.toMap(),
                busy = busy,
                readable = readable,
                error = error,
            )
    }

    public companion object {
        public val FEATURE: FeatureId = FeatureId("developmentServices")
        public const val LOG_TAIL: Int = 65_536
    }
}

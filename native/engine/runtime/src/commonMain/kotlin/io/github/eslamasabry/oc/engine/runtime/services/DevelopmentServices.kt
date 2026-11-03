package io.github.eslamasabry.oc.engine.runtime.services

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlin.math.max
import kotlin.random.Random

/*
 * Port of lib/state/development_services.dart (one visible project panel: register, start, stop, refresh, read logs
 * and the failed-run report). Every remote mutation resolves the current transport, then rechecks scope. Only a
 * receipt written by Start can authorize control of a shell; command text or a discovered PID never grants ownership.
 */

/** Why the last action did not complete (Dart threw a `StateError`/`FormatException` with these meanings). */
public enum class ServiceRefusal {
    /** `Invalid project service`. */
    InvalidService,

    /** `The connection or project changed`. */
    ScopeChanged,

    /** `Service control is unavailable`. */
    Unsupported,

    /** `Reconnect to check this service`. */
    NoConnection,

    /** `Confirm the previous run before starting another`. */
    PreviousRunUnconfirmed,

    /** `The server did not confirm ownership of this command`. */
    OwnershipNotConfirmed,

    /** `Ownership could not be confirmed; nothing was stopped`. */
    StopOwnershipUnknown,

    /** `The server has not confirmed the stop`. */
    StopNotConfirmed,

    /** `The command log could not be confirmed`. */
    LogNotConfirmed,

    /** `Invalid log size` / `Invalid log cursor`. */
    InvalidLog,

    /** The service id is not in this panel. */
    UnknownService,
}

/** The panel's last error: an app refusal, or the store/server's own failure value. */
public sealed interface ServiceError {
    public data class Refused(val reason: ServiceRefusal) : ServiceError

    public data class Failed(val failure: Failure) : ServiceError
}

public class DevelopmentServices(
    public val store: DevelopmentServiceStore,
    public val directory: String,
    public val workspace: String?,
    private val resolveGateway: suspend () -> ManagedShellGateway?,
    private val isCurrent: () -> Boolean,
    private val supported: () -> Boolean,
    /** Display masking for the report (the kit's redactor); applied before bounding. */
    private val redact: (String) -> String,
    private val newOwnerToken: () -> String = ::newId,
) {
    public var services: List<DevelopmentService> = emptyList()
        private set
    private val observed = mutableMapOf<String, ManagedShell>()
    private val logSources = mutableMapOf<String, ManagedShell>()
    private val logText = mutableMapOf<String, String>()

    /** Plain-text log tails read this session; never persisted. */
    public val logs: Map<String, String> get() = logText

    public var busy: Boolean = false
        private set
    public var readable: Boolean = true
        private set
    public var error: ServiceError? = null
        private set
    private var generation = 0
    private var disposed = false

    init {
        reload()
    }

    public fun reload() {
        when (val loaded = store.load()) {
            is Outcome.Ok -> {
                services = loaded.value.filter { it.directory == directory && it.workspace == workspace }
                readable = true
            }
            is Outcome.Failed -> {
                services = emptyList()
                readable = false
                error = ServiceError.Failed(loaded.failure)
            }
        }
    }

    public fun status(service: DevelopmentService): DevelopmentServiceStatus {
        val run = service.run ?: return DevelopmentServiceStatus.NotStarted
        if (run.stopped) return DevelopmentServiceStatus.Stopped
        val shell = observed[service.id]
        if (shell == null || !matches(service, shell)) return DevelopmentServiceStatus.Unknown
        return when (shell.status) {
            ManagedShellStatus.Running -> DevelopmentServiceStatus.Running
            ManagedShellStatus.Exited, ManagedShellStatus.Killed, ManagedShellStatus.Timeout -> DevelopmentServiceStatus.Stopped
            ManagedShellStatus.Unknown -> DevelopmentServiceStatus.Unknown
        }
    }

    public fun exitCode(service: DevelopmentService): Int? = observed[service.id]?.exitCode

    /**
     * Snapshot for an explicit Report action; call [readLogs] first to load the excerpt. Null means the failure or
     * ownership is unconfirmed, or this scope is obsolete. A log is attached only when it was read from this exact
     * owned shell.
     */
    public fun failedReport(id: String): FailedServiceReport? {
        if (disposed || !isCurrent() || !supported() || !readable) return null
        val shell = observed[id] ?: return null
        val service = services.firstOrNull { it.id == id } ?: return null
        val source = logSources[id]
        val tail = if (source != null && matches(service, source)) logText[id].orEmpty() else ""
        return FailedServiceReport.of(service, shell, tail, redact)
    }

    public fun canStart(service: DevelopmentService): Boolean =
        supported() && !busy && isCurrent() && readable &&
            (status(service) == DevelopmentServiceStatus.NotStarted || status(service) == DevelopmentServiceStatus.Stopped)

    public fun canStop(service: DevelopmentService): Boolean =
        supported() && !busy && isCurrent() && status(service) == DevelopmentServiceStatus.Running

    /** The connection or runtime changed: everything observed (shells, logs, sources) is dropped, pending work stales. */
    public fun invalidateRuntime() {
        generation++
        observed.clear()
        logText.clear()
        logSources.clear()
        busy = false
    }

    public fun dispose() {
        disposed = true
        generation++
    }

    private fun current(gen: Int): Boolean = !disposed && isCurrent() && gen == generation

    private fun forget(id: String) {
        observed.remove(id)
        logText.remove(id)
        logSources.remove(id)
    }

    /** One step's answer: [value] or the [error] that ends the action. */
    private class Step<out T>(val value: T?, val error: ServiceError?)

    private fun <T> ok(value: T): Step<T> = Step(value, null)

    private fun refused(reason: ServiceRefusal): Step<Nothing> = Step(null, ServiceError.Refused(reason))

    private fun failed(failure: Failure): Step<Nothing> = Step(null, ServiceError.Failed(failure))

    private fun check(gen: Int): ServiceError? = if (current(gen)) null else ServiceError.Refused(ServiceRefusal.ScopeChanged)

    private suspend fun gateway(gen: Int): Step<ManagedShellGateway> {
        check(gen)?.let { return Step(null, it) }
        if (!supported()) return refused(ServiceRefusal.Unsupported)
        val gateway = resolveGateway()
        check(gen)?.let { return Step(null, it) }
        return if (gateway == null) refused(ServiceRefusal.NoConnection) else ok(gateway)
    }

    private suspend fun action(body: suspend (Int) -> ServiceError?) {
        if (busy || !isCurrent() || disposed || !readable) return
        val gen = generation
        busy = true
        error = null
        val failure = body(gen)
        if (failure != null && current(gen)) {
            error = failure
            observed.clear()
        }
        if (current(gen)) {
            val kept = error
            reload()
            if (kept != null && error == null) error = kept
            busy = false
        }
    }

    public suspend fun register(service: DevelopmentService): Unit =
        action { _ ->
            if (service.directory != directory ||
                service.workspace != workspace ||
                service.run != null ||
                service.name.isBlank() ||
                service.name.length > 80 ||
                service.command.isBlank() ||
                service.command.length > 4096
            ) {
                ServiceError.Refused(ServiceRefusal.InvalidService)
            } else {
                (store.save(service) as? Outcome.Failed)?.let { ServiceError.Failed(it.failure) }
            }
        }

    private fun matches(service: DevelopmentService, shell: ManagedShell): Boolean {
        val run = service.run ?: return false
        return !run.stopped &&
            shell.ownerToken == run.ownerToken &&
            shell.command == service.command &&
            shell.directory == service.directory &&
            (run.shellId == null || shell.id == run.shellId) &&
            (run.startedAt == null || shell.startedAt == run.startedAt)
    }

    /** The owned shell of [service], or null (and its observation and log forgotten) when ownership is unconfirmed. */
    private suspend fun reconcile(service: DevelopmentService, gateway: ManagedShellGateway, gen: Int): Step<ManagedShell?> {
        val run = service.run
        if (run == null || run.stopped) return ok(null)
        var shell: ManagedShell? = null
        if (run.shellId != null) {
            when (val got = gateway.getManagedShell(run.shellId)) {
                is Outcome.Failed -> return failed(got.failure)
                is Outcome.Ok -> shell = got.value
            }
        } else {
            // Recover only this persisted, pre-POST ownership nonce; never adopt a familiar-looking shell.
            when (val inventory = gateway.loadRunningShells()) {
                is Outcome.Failed -> return failed(inventory.failure)
                is Outcome.Ok -> {
                    check(gen)?.let { return Step(null, it) }
                    val matching = inventory.value.filter { matches(service, it) }
                    if (matching.size == 1) shell = matching.single()
                }
            }
        }
        check(gen)?.let { return Step(null, it) }
        if (shell == null || !matches(service, shell)) {
            forget(service.id)
            return ok(null)
        }
        if (run.shellId == null) {
            val updated = store.updateRun(service.id, DevelopmentServiceRun(run.ownerToken, shell.id, shell.startedAt), run.ownerToken)
            if (updated is Outcome.Failed) return failed(updated.failure)
            check(gen)?.let { return Step(null, it) }
        }
        observed[service.id] = shell
        return ok(shell)
    }

    public suspend fun refresh(): Unit =
        action { gen ->
            reload()
            observed.clear()
            if (!supported() || services.none { it.run != null && !it.run.stopped }) return@action null
            val gateway = gateway(gen)
            gateway.error?.let { return@action it }
            for (service in services) {
                reconcile(service, gateway.value!!, gen).error?.let { return@action it }
            }
            null
        }

    public suspend fun start(id: String): Unit = action { gen -> startRun(id, gen) }

    private suspend fun startRun(id: String, gen: Int): ServiceError? {
        val service = services.firstOrNull { it.id == id } ?: return ServiceError.Refused(ServiceRefusal.UnknownService)
        val state = status(service)
        if (state != DevelopmentServiceStatus.NotStarted && state != DevelopmentServiceStatus.Stopped) {
            return ServiceError.Refused(ServiceRefusal.PreviousRunUnconfirmed)
        }
        val gateway = gateway(gen)
        gateway.error?.let { return it }
        val owner = newOwnerToken()
        (store.updateRun(id, DevelopmentServiceRun(owner)) as? Outcome.Failed)?.let { return ServiceError.Failed(it.failure) }
        reload()
        check(gen)?.let { return it }
        // Failure or a lost response deliberately leaves the pending nonce saved.
        val shell =
            when (val started = gateway.value!!.startManagedShell(service.command, directory, owner)) {
                is Outcome.Failed -> return ServiceError.Failed(started.failure)
                is Outcome.Ok -> started.value
            }
        val pending = service.withRun(DevelopmentServiceRun(owner))
        if (!matches(pending, shell) || !shell.id.startsWith("sh_")) {
            return ServiceError.Refused(ServiceRefusal.OwnershipNotConfirmed)
        }
        // Preserve the receipt even if this route closed while POST was pending.
        val saved = store.updateRun(id, DevelopmentServiceRun(owner, shell.id, shell.startedAt), owner)
        if (saved is Outcome.Failed) return ServiceError.Failed(saved.failure)
        if (current(gen)) {
            observed[id] = shell
            logText.remove(id)
            logSources.remove(id)
        }
        return null
    }

    public suspend fun stop(id: String): Unit = action { gen -> stopRun(id, gen) }

    private suspend fun stopRun(id: String, gen: Int): ServiceError? {
        val service = services.firstOrNull { it.id == id } ?: return ServiceError.Refused(ServiceRefusal.UnknownService)
        val gateway = gateway(gen)
        gateway.error?.let { return it }
        val reconciled = reconcile(service, gateway.value!!, gen)
        reconciled.error?.let { return it }
        val shell = reconciled.value ?: return ServiceError.Refused(ServiceRefusal.StopOwnershipUnknown)
        check(gen)?.let { return it }
        (gateway.value.stopManagedShell(shell.id) as? Outcome.Failed)?.let { return ServiceError.Failed(it.failure) }
        check(gen)?.let { return it }
        val remaining =
            when (val got = gateway.value.getManagedShell(shell.id)) {
                is Outcome.Failed -> return ServiceError.Failed(got.failure)
                is Outcome.Ok -> got.value
            }
        check(gen)?.let { return it }
        if (remaining != null) return ServiceError.Refused(ServiceRefusal.StopNotConfirmed)
        val owner = service.run!!.ownerToken
        val saved = store.updateRun(id, DevelopmentServiceRun(owner, shell.id, shell.startedAt, stopped = true), owner)
        if (saved is Outcome.Failed) return ServiceError.Failed(saved.failure)
        forget(id)
        reload()
        return null
    }

    public suspend fun restart(id: String): Unit =
        action { gen ->
            stopRun(id, gen) ?: check(gen) ?: startRun(id, gen)
        }

    /** Reads at most the last 64 KiB of the owned shell's output; a failed read leaves no previous excerpt behind. */
    public suspend fun readLogs(id: String): Unit =
        action { gen ->
            logText.remove(id)
            logSources.remove(id)
            val gateway = gateway(gen)
            gateway.error?.let { return@action it }
            val service = services.firstOrNull { it.id == id } ?: return@action ServiceError.Refused(ServiceRefusal.UnknownService)
            val reconciled = reconcile(service, gateway.value!!, gen)
            reconciled.error?.let { return@action it }
            val shell = reconciled.value ?: return@action ServiceError.Refused(ServiceRefusal.LogNotConfirmed)
            val head =
                when (val read = gateway.value.readManagedShellOutput(shell.id, cursor = 0, limit = 1)) {
                    is Outcome.Failed -> return@action ServiceError.Failed(read.failure)
                    is Outcome.Ok -> read.value
                }
            check(gen)?.let { return@action it }
            if (head.size < 0) return@action ServiceError.Refused(ServiceRefusal.InvalidLog)
            val start = max(0L, head.size - LOG_WINDOW)
            val tail =
                when (val read = gateway.value.readManagedShellOutput(shell.id, cursor = start, limit = LOG_WINDOW.toInt())) {
                    is Outcome.Failed -> return@action ServiceError.Failed(read.failure)
                    is Outcome.Ok -> read.value
                }
            check(gen)?.let { return@action it }
            if (tail.cursor < start || tail.cursor > tail.size) return@action ServiceError.Refused(ServiceRefusal.InvalidLog)
            var text = tail.text
            if (text.length > LOG_WINDOW) text = text.substring(text.length - LOG_WINDOW.toInt())
            logText[id] = TerminalText.withoutTerminalCodes(text)
            logSources[id] = shell
            null
        }

    public suspend fun forgetRun(id: String): Unit =
        action { _ ->
            (store.updateRun(id, null) as? Outcome.Failed)?.let { return@action ServiceError.Failed(it.failure) }
            forget(id)
            null
        }

    public suspend fun remove(id: String): Unit =
        action { _ ->
            (store.remove(id) as? Outcome.Failed)?.let { return@action ServiceError.Failed(it.failure) }
            forget(id)
            null
        }

    public companion object {
        private const val LOG_WINDOW = 65536L

        /** 24 random bytes as hex: the ownership nonce written before the create request. */
        public fun newId(): String = Random.nextBytes(24).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

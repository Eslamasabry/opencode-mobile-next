package io.github.eslamasabry.oc.feature.devservices.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.engine.runtime.devservers.DetectedDevServers
import io.github.eslamasabry.oc.engine.runtime.devservers.DetectedServer
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentService
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStatus
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServiceStore
import io.github.eslamasabry.oc.engine.runtime.services.commands.DevelopmentServices
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One project's Development services page (Flutter DevelopmentServicesScreen): the saved commands,
 * started as owned shells and controlled only through their receipts (:engine:runtime
 * DevelopmentServices, B-LINUX-060..064), and the dev servers found running another way
 * (DetectedDevServers, B-LINUX-054..059). Runs on [scope]'s one lane, as the Flutter model ran on
 * the UI isolate.
 */
public class DevServicesModel(
    profile: ProfileId,
    private val directory: String,
    host: DevServicesHost,
    private val pacer: Pacer,
    private val scope: CoroutineScope,
    private val onBack: () -> Unit,
) : DevServicesActions {
    private val context: DevServicesContext? = host.open(profile, directory)
    private val ui =
        MutableStateFlow(
            DevServicesUi(
                available = context != null,
                directory = directory,
                loading = context != null,
            )
        )
    public val state: StateFlow<DevServicesUi> = ui.asStateFlow()

    private val services: DevelopmentServices? = context?.let { c ->
        DevelopmentServices(
            store = DevelopmentServiceStore(c.settings, profile, c.canWrite),
            directory = directory,
            workspace = c.workspace,
            resolveShells = { if (c.supported()) c.shells() else null },
            isCurrent = { visible && c.isCurrent() },
            supported = c.supported,
            newOwnerToken = c.newId,
        )
    }

    private val detected: DetectedDevServers? = context?.let { c ->
        DetectedDevServers(
            directory = directory,
            resolveShells = { if (c.supported()) c.shells() else null },
            isCurrent = { visible && c.isCurrent() },
            runOnPhone = c.phone,
            ownedShellIds = {
                services?.state?.value?.services.orEmpty().mapNotNull { it.run?.shellId }.toSet()
            },
            clock = c.clock,
        )
    }

    private var visible = false
    private var ticks = 0
    private val shownFlow = MutableStateFlow(false)

    init {
        if (services != null && detected != null && context != null) {
            ui.update {
                it.copy(
                    workspace = context.workspace,
                    supported = context.supported(),
                    scansPhone = context.phone != null,
                )
            }
            scope.launch {
                combine(services.state, detected.state) { s, d -> s to d }
                    .collect { (s, d) -> publish(s.services, d.servers) }
            }
            scope.launch {
                services.reload()
                ui.update { it.copy(loading = false) }
            }
            // The refresh timer runs only while the page is shown; hiding it cancels the ticks.
            scope.launch {
                shownFlow.collectLatest { shown ->
                    if (shown) pacer.ticks(DevServicesTimers.refresh).collect { tick() }
                }
            }
        }
    }

    private fun publish(saved: List<DevelopmentService>, found: List<DetectedServer>) {
        val engine = services ?: return
        val scan = detected ?: return
        val s = engine.state.value
        val d = scan.state.value
        ui.update { old ->
            old.copy(
                supported = context?.supported() == true,
                readable = s.readable,
                busy = s.busy,
                services =
                    saved
                        .map { service ->
                            ServiceRowUi(
                                id = service.id,
                                name = service.name,
                                command = service.command,
                                url = service.url,
                                status = engine.status(service),
                                exitCode = engine.exitCode(service),
                                canStart = engine.canStart(service),
                                canStop = engine.canStop(service),
                                ports =
                                    service.run
                                        ?.shellId
                                        ?.let { scan.portsForShell(it) }
                                        .orEmpty()
                                        .toImmutableList(),
                                log = s.logs[service.id],
                            )
                        }
                        .toImmutableList(),
                found =
                    found
                        .map { server ->
                            FoundRowUi(
                                id = server.id,
                                name = server.name.ifBlank { server.command },
                                command = server.command,
                                ports = server.ports.toImmutableList(),
                                canStop = server.canStop,
                                hasLogs = server.hasLogs,
                                canSave = server.canSave,
                                log = foundLogs[server.id],
                            )
                        }
                        .toImmutableList(),
                foundFailed = d.error != null && d.scanned,
                problem = old.problem ?: s.error?.let(::problemOf),
                failure = old.failure ?: s.error,
            )
        }
    }

    private val foundLogs = mutableMapOf<String, String>()

    private fun republish() =
        publish(services?.state?.value?.services.orEmpty(), detected?.servers.orEmpty())

    override fun shown(visible: Boolean) {
        this.visible = visible
        shownFlow.value = visible
        if (!visible) {
            services?.invalidateRuntime()
            detected?.invalidate()
            return
        }
        refresh()
    }

    private suspend fun tick() {
        services?.refresh()
        // Looking for servers started some other way costs a script on the phone: every other tick
        // (Flutter).
        if (ticks++ % 2 == 0) detected?.refresh()
        readOpenLogs()
    }

    override fun refresh() {
        scope.launch {
            ui.update { it.copy(problem = null, failure = null) }
            services?.refresh()?.let(::report)
            detected?.refresh()
        }
    }

    override fun add() {
        if (!canRegister()) return
        ui.update { it.copy(editor = EditorUi()) }
    }

    override fun saveFound(id: String) {
        val found = detected?.servers?.firstOrNull { it.id == id } ?: return
        if (!canRegister() || !found.canSave) return
        val url = found.firstPort?.let { "http://localhost:$it" }.orEmpty()
        ui.update {
            it.copy(editor = EditorUi(name = found.name, command = found.command, url = url))
        }
    }

    private fun canRegister(): Boolean = services != null && ui.value.readable && !ui.value.busy

    override fun edit(name: String, command: String, url: String) {
        ui.update { it.copy(editor = it.editor?.copy(name = name, command = command, url = url)) }
    }

    override fun save() {
        val editor = ui.value.editor ?: return
        val engine = services ?: return
        val c = context ?: return
        val name = editor.name.trim()
        val command = editor.command.trim()
        val url = editor.url.trim()
        val taken = engine.state.value.services.map { it.name.lowercase() }.toSet()
        val checked =
            editor.copy(
                nameError =
                    if (name.isEmpty()) NameError.Required
                    else if (name.lowercase() in taken) NameError.Duplicate else null,
                commandMissing = command.isEmpty(),
                urlInvalid = url.isNotEmpty() && !DevServicesRules.safePreviewUrl(url),
            )
        if (checked.nameError != null || checked.commandMissing || checked.urlInvalid) {
            ui.update { it.copy(editor = checked) }
            return
        }
        val service =
            DevelopmentService(
                id = c.newId(),
                name = name,
                command = command,
                directory = directory,
                url = url,
                workspace = c.workspace,
            )
        scope.launch {
            when (val saved = engine.register(service)) {
                is Outcome.Ok -> ui.update { it.copy(editor = null) }
                is Outcome.Failed -> report(saved)
            }
        }
    }

    override fun closeEditor() {
        ui.update { it.copy(editor = null) }
    }

    override fun start(id: String) {
        val engine = services ?: return
        val name = nameOf(id) ?: return
        scope.launch {
            val started = engine.start(id)
            report(started)
            if (started is Outcome.Ok)
                ui.update { it.copy(receipt = DevServicesReceipt.Started(name)) }
        }
    }

    override fun askStop(id: String) {
        nameOf(id)?.let { name -> ui.update { it.copy(ask = DevServicesAsk.Stop(id, name)) } }
    }

    override fun askRestart(id: String) {
        nameOf(id)?.let { name -> ui.update { it.copy(ask = DevServicesAsk.Restart(id, name)) } }
    }

    override fun askForget(id: String) {
        nameOf(id)?.let { name -> ui.update { it.copy(ask = DevServicesAsk.Forget(id, name)) } }
    }

    override fun askRemove(id: String) {
        val row = ui.value.services.firstOrNull { it.id == id } ?: return
        ui.update {
            it.copy(
                ask =
                    DevServicesAsk.Remove(
                        id,
                        row.name,
                        running = row.status == DevelopmentServiceStatus.Running,
                    )
            )
        }
    }

    override fun askStopFound(id: String) {
        val row = ui.value.found.firstOrNull { it.id == id && it.canStop } ?: return
        ui.update { it.copy(ask = DevServicesAsk.StopFound(id, row.name)) }
    }

    override fun confirm() {
        val ask = ui.value.ask ?: return
        ui.update { it.copy(ask = null) }
        val engine = services ?: return
        scope.launch {
            when (ask) {
                is DevServicesAsk.Stop -> report(engine.stop(ask.id))
                is DevServicesAsk.Restart -> report(engine.restart(ask.id))
                is DevServicesAsk.Forget -> report(engine.forgetRun(ask.id))
                is DevServicesAsk.Remove -> {
                    val removed = engine.remove(ask.id)
                    report(removed)
                    if (removed is Outcome.Ok)
                        ui.update { it.copy(receipt = DevServicesReceipt.Removed(ask.name)) }
                }
                is DevServicesAsk.StopFound -> {
                    val server = detected?.servers?.firstOrNull { it.id == ask.id } ?: return@launch
                    val stopped = detected.stop(server)
                    report(stopped)
                    republish()
                }
            }
        }
    }

    override fun logs(id: String) {
        val row = ui.value.services.firstOrNull { it.id == id } ?: return
        ui.update { it.copy(log = DevServiceLog(id, false, row.name, row.log.orEmpty())) }
        refreshLogs()
    }

    override fun foundLogs(id: String) {
        val row = ui.value.found.firstOrNull { it.id == id && it.hasLogs } ?: return
        ui.update { it.copy(log = DevServiceLog(id, true, row.name, row.log.orEmpty())) }
        refreshLogs()
    }

    private var readingLogs = false

    private suspend fun readOpenLogs() {
        val shown = ui.value.log ?: return
        if (!visible || readingLogs || context?.isCurrent() != true) return
        readingLogs = true
        try {
            val read: Outcome<String> =
                if (shown.found) {
                    val scan = detected ?: return
                    val server =
                        scan.servers.firstOrNull { it.id == shown.id && it.hasLogs } ?: return
                    scan.readLog(server)
                } else {
                    val engine = services ?: return
                    when (val loaded = engine.readLogs(shown.id)) {
                        is Outcome.Failed -> loaded
                        is Outcome.Ok -> Outcome.Ok(engine.state.value.logs[shown.id].orEmpty())
                    }
                }
            if (
                !visible ||
                    context?.isCurrent() != true ||
                    ui.value.log?.id != shown.id ||
                    ui.value.log?.found != shown.found
            )
                return
            when (read) {
                is Outcome.Ok -> {
                    if (shown.found) foundLogs[shown.id] = read.value
                    val text = read.value
                    ui.update { it.copy(log = it.log?.copy(text = text, failed = false)) }
                }
                is Outcome.Failed -> ui.update { it.copy(log = it.log?.copy(failed = true)) }
            }
        } finally {
            readingLogs = false
        }
    }

    override fun refreshLogs() {
        scope.launch { readOpenLogs() }
    }

    override fun closeLogs() {
        ui.update { it.copy(log = null) }
    }

    override fun visit(id: String) {
        val row = ui.value.services.firstOrNull { it.id == id } ?: return
        if (row.url.isBlank()) return
        if (context?.openLink?.invoke(row.url) != true) {
            ui.update { it.copy(problem = DevServicesProblem.Failed, failure = null) }
        }
    }

    override fun cancel() {
        ui.update { it.copy(ask = null) }
    }

    override fun dismiss() {
        ui.update { it.copy(problem = null, failure = null, receipt = null) }
    }

    override fun back(): Unit = onBack()

    public fun close() {
        shownFlow.value = false
        services?.close()
        detected?.close()
    }

    private fun nameOf(id: String): String? = ui.value.services.firstOrNull { it.id == id }?.name

    private fun report(outcome: Outcome<*>, kind: DevServicesProblem? = null) {
        if (outcome !is Outcome.Failed) return
        ui.update {
            it.copy(problem = kind ?: problemOf(outcome.failure), failure = outcome.failure)
        }
    }

    internal companion object {
        fun problemOf(failure: Failure): DevServicesProblem =
            when (failure) {
                is Failure.Refused ->
                    if (failure.reason == RefusalReason.StaleScope) DevServicesProblem.ScopeChanged
                    else DevServicesProblem.Failed
                is Failure.Unavailable -> DevServicesProblem.Unavailable
                is Failure.Unknown,
                is Failure.Network,
                is Failure.TimedOut -> DevServicesProblem.Offline
                is Failure.StorageRefused -> DevServicesProblem.NotSaved
                is Failure.UnreadableReply -> DevServicesProblem.LogFailed
                else -> DevServicesProblem.Failed
            }
    }
}

/** Pure rules of the page. */
public object DevServicesRules {
    /**
     * A preview address must be http or https with a host and no user name or password (Flutter
     * safeExternalLinkUri).
     */
    public fun safePreviewUrl(url: String): Boolean {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme != "http" && scheme != "https") return false
        val authority = url.substringAfter("://").takeWhile { it != '/' && it != '?' && it != '#' }
        if ('@' in authority) return false
        val host = authority.substringBeforeLast(':').removePrefix("[").removeSuffix("]")
        return host.isNotBlank() && url.none { it.isWhitespace() } && url.length <= 2048
    }
}

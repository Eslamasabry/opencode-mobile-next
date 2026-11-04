package io.github.eslamasabry.oc.feature.linux.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.procs.DetectedDevelopmentServer
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentService
import io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServiceStatus
import io.github.eslamasabry.oc.engine.runtime.services.FailedServiceReport
import io.github.eslamasabry.oc.engine.runtime.services.ServiceError
import io.github.eslamasabry.oc.engine.runtime.services.ServiceRefusal
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.LinuxRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class LinuxServiceRow(
    val service: DevelopmentService,
    val status: DevelopmentServiceStatus,
    val canStart: Boolean,
    val canStop: Boolean,
    val canReport: Boolean,
)

public enum class ServiceAskKind {
    Stop,
    Remove,
    Forget,
    StopDetected,
}

public data class LinuxServiceAsk(val kind: ServiceAskKind, val id: String)

public data class LinuxServicesUi(
    val directory: String,
    val loading: Boolean = true,
    val working: Boolean = false,
    val supported: Boolean = false,
    val readable: Boolean = true,
    val services: ImmutableList<LinuxServiceRow> = persistentListOf(),
    val detected: ImmutableList<DetectedDevelopmentServer> = persistentListOf(),
    val failure: Failure? = null,
    val refusal: ServiceRefusal? = null,
    val selected: String? = null,
    val adding: Int = 0,
    val draftName: String = "",
    val ask: LinuxServiceAsk? = null,
    val log: String? = null,
    val report: FailedServiceReport? = null,
)

public interface LinuxServicesActions {
    public fun shown(visible: Boolean)

    public fun refresh()

    public fun add()

    public fun finishName(value: String?)

    public fun finishCommand(value: String?)

    public fun select(id: String)

    public fun start(id: String)

    public fun restart(id: String)

    public fun logs(id: String)

    public fun report(id: String)

    public fun sendReport() {}

    public fun ask(kind: ServiceAskKind, id: String)

    public fun confirm()

    public fun detectedLogs(id: String)

    public fun dismiss()

    public fun back()
}

/** One project page; saved commands are loaded and reconciled, never automatically started. */
@AssistedInject
public class LinuxServicesViewModel(
    @Assisted private val route: LinuxRoute.Services,
    private val source: LinuxServicesSource,
    private val nav: AppNavigator,
) : ViewModel(), LinuxServicesActions {
    private val mutable = MutableStateFlow(LinuxServicesUi(route.directory))
    public val state: StateFlow<LinuxServicesUi> = mutable.asStateFlow()
    private var session: LinuxServicesSession? = null
    private var visible = false
    private var generation = 0L
    private var detectorWatch: Job? = null
    private var reportTarget: String? = null

    init {
        viewModelScope.launch { source.changes.collect { if (visible) open() } }
    }

    override fun shown(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) viewModelScope.launch { open() } else invalidate()
    }

    private fun invalidate() {
        reportTarget = null
        generation++
        detectorWatch?.cancel()
        session?.services?.dispose()
        session?.detected?.invalidate()
        session = null
        mutable.update {
            it.copy(
                selected = null,
                adding = 0,
                ask = null,
                log = null,
                report = null,
                working = false,
            )
        }
    }

    private suspend fun open() {
        invalidate()
        val admitted = generation
        mutable.update {
            it.copy(
                loading = true,
                failure = null,
                refusal = null,
                services = persistentListOf(),
                detected = persistentListOf(),
            )
        }
        when (
            val answer =
                source.open(route.profile, route.directory, route.workspace) {
                    visible && generation == admitted
                }
        ) {
            is Outcome.Failed ->
                if (visible && generation == admitted)
                    mutable.update { it.copy(loading = false, failure = answer.failure) }
            is Outcome.Ok -> {
                if (!visible || generation != admitted) {
                    answer.value.services.dispose()
                    return
                }
                session = answer.value
                detectorWatch = viewModelScope.launch {
                    answer.value.detected.state.collect { detected ->
                        if (generation == admitted)
                            mutable.update {
                                it.copy(
                                    detected = detected.servers.toImmutableList(),
                                    failure = detected.failure ?: it.failure,
                                )
                            }
                    }
                }
                publish(answer.value)
                refresh()
            }
        }
    }

    private fun publish(live: LinuxServicesSession) {
        val model = live.services
        val error = model.error
        mutable.update {
            it.copy(
                loading = false,
                supported = live.supported,
                readable = model.readable,
                services =
                    model.services
                        .map { service ->
                            LinuxServiceRow(
                                service,
                                model.status(service),
                                model.canStart(service),
                                model.canStop(service),
                                model.failedReport(service.id) != null,
                            )
                        }
                        .toImmutableList(),
                failure = (error as? ServiceError.Failed)?.failure ?: it.failure,
                refusal = (error as? ServiceError.Refused)?.reason,
            )
        }
    }

    private fun perform(body: suspend (LinuxServicesSession) -> Unit) {
        val live = session ?: return
        if (!visible || mutable.value.working) return
        val admitted = generation
        mutable.update { it.copy(working = true, failure = null, refusal = null, selected = null) }
        viewModelScope.launch {
            live.command { body(live) }
            if (visible && admitted == generation) {
                publish(live)
                mutable.update { it.copy(working = false) }
            }
        }
    }

    override fun refresh() = perform {
        it.services.refresh()
        it.detected.refresh()
    }

    override fun add() {
        if (mutable.value.readable && !mutable.value.working)
            mutable.update { it.copy(adding = 1, draftName = "") }
    }

    override fun finishName(value: String?) {
        if (value == null) {
            dismiss()
            return
        }
        val name = value.trim()
        if (name.isEmpty() || name.length > 80) return
        mutable.update { it.copy(adding = 2, draftName = name) }
    }

    override fun finishCommand(value: String?) {
        if (value == null) {
            dismiss()
            return
        }
        val command = value.trim()
        if (command.isEmpty() || command.length > 4096) return
        val name = mutable.value.draftName
        mutable.update { it.copy(adding = 0, draftName = "") }
        perform { live ->
            live.services.register(
                DevelopmentService(
                    io.github.eslamasabry.oc.engine.runtime.services.DevelopmentServices.newId(),
                    name,
                    command,
                    route.directory,
                    "",
                    route.workspace,
                )
            )
        }
    }

    override fun select(id: String) {
        if (!mutable.value.working && mutable.value.services.any { it.service.id == id })
            mutable.update { it.copy(selected = id) }
    }

    override fun start(id: String) = perform {
        it.services.start(id)
        it.detected.refresh()
    }

    override fun restart(id: String) = perform {
        it.services.restart(id)
        it.detected.refresh()
    }

    override fun logs(id: String) = perform { live ->
        live.services.readLogs(id)
        if (live === session && visible)
            mutable.update { it.copy(log = live.services.logs[id]?.let(live::redact)) }
    }

    override fun report(id: String) = perform { live ->
        live.services.readLogs(id)
        if (live === session && visible) {
            val report = live.services.failedReport(id)
            reportTarget = id.takeIf { report != null }
            mutable.update { it.copy(report = report) }
        }
    }

    override fun sendReport() {
        val id = reportTarget ?: return
        val displayed = mutable.value.report ?: return
        perform { live ->
            live.services.readLogs(id)
            if (live !== session || !visible) return@perform
            val fresh = live.services.failedReport(id)
            if (
                fresh == null ||
                    fresh.jobId != displayed.jobId ||
                    fresh.status != displayed.status ||
                    fresh.exitCode != displayed.exitCode
            ) {
                mutable.update {
                    it.copy(
                        report = null,
                        failure =
                            Failure.Refused(
                                io.github.eslamasabry.oc.core.common.RefusalReason.StaleScope
                            ),
                    )
                }
                return@perform
            }
            when (val opened = live.openReport(fresh)) {
                is Outcome.Ok -> mutable.update { it.copy(report = null) }
                is Outcome.Failed ->
                    mutable.update { it.copy(report = null, failure = opened.failure) }
            }
        }
    }

    override fun detectedLogs(id: String) = perform { live ->
        when (val read = live.detected.readLog(id)) {
            is Outcome.Ok ->
                if (live === session && visible)
                    mutable.update { it.copy(log = live.redact(read.value)) }
            is Outcome.Failed ->
                if (live === session && visible) mutable.update { it.copy(failure = read.failure) }
        }
    }

    override fun ask(kind: ServiceAskKind, id: String) {
        mutable.update { it.copy(selected = null, ask = LinuxServiceAsk(kind, id)) }
    }

    override fun confirm() {
        val ask = mutable.value.ask ?: return
        mutable.update { it.copy(ask = null) }
        perform { live ->
            when (ask.kind) {
                ServiceAskKind.Stop -> live.services.stop(ask.id)
                ServiceAskKind.Remove -> live.services.remove(ask.id)
                ServiceAskKind.Forget -> live.services.forgetRun(ask.id)
                ServiceAskKind.StopDetected ->
                    when (val stopped = live.detected.stop(ask.id)) {
                        is Outcome.Failed ->
                            if (live === session && visible)
                                mutable.update { it.copy(failure = stopped.failure) }
                        is Outcome.Ok -> Unit
                    }
            }
            live.detected.refresh()
        }
    }

    override fun dismiss() {
        reportTarget = null
        mutable.update {
            it.copy(
                selected = null,
                adding = 0,
                draftName = "",
                ask = null,
                log = null,
                report = null,
                failure = null,
                refusal = null,
            )
        }
    }

    override fun back(): Unit = nav.back()

    override fun onCleared() {
        visible = false
        invalidate()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: LinuxRoute.Services): LinuxServicesViewModel
    }
}

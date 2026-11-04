package io.github.eslamasabry.oc.feature.linux.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.pace.Cadence
import io.github.eslamasabry.oc.core.common.pace.Pacer
import io.github.eslamasabry.oc.core.common.pace.TimerSpec
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptAct
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptField
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptRestart
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptService
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServiceDef
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServices
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

public data class KeptAsk(val name: String, val action: KeptAct)

public data class KeptServicesUi(
    val loading: Boolean = true,
    val working: Boolean = false,
    val services: ImmutableList<KeptService> = persistentListOf(),
    val selected: String? = null,
    val adding: Boolean = false,
    val invalid: KeptField? = null,
    val failure: Failure? = null,
    val ask: KeptAsk? = null,
    val log: String = "",
    val logQuery: String = "",
    val nowMs: Long = 0,
)

public interface KeptServicesActions {
    public fun shown(visible: Boolean)

    public fun refresh()

    public fun select(name: String)

    public fun add()

    public fun save(
        name: String,
        command: String,
        directory: String,
        port: String,
        restart: Boolean,
    )

    public fun act(name: String, action: KeptAct)

    public fun confirm()

    public fun searchLog(query: String)

    public fun copyLog()

    public fun preview(port: Int)

    public fun dismiss()

    public fun back()
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class KeptServicesViewModel(
    private val source: KeptServices,
    private val nav: AppNavigator,
    private val opener: PortPreviewOpener,
    private val pacer: Pacer,
    private val clock: Clock,
) : ViewModel(), KeptServicesActions {
    private val mutable = MutableStateFlow(KeptServicesUi())
    public val state: StateFlow<KeptServicesUi> = mutable.asStateFlow()
    private val visible = MutableStateFlow(false)
    private var reading = false
    private var epoch = 0L

    init {
        viewModelScope.launch {
            visible.collectLatest { shown ->
                if (!shown) return@collectLatest
                val ensured = source.ensureSupervisor()
                read()
                if (ensured is Outcome.Failed) mutable.update { it.copy(failure = ensured.failure) }
                pacer.ticks(TIMER).collect { read() }
            }
        }
    }

    override fun shown(visible: Boolean) {
        this.visible.value = visible
        if (!visible) epoch++
    }

    private suspend fun read() {
        if (!visible.value || reading || mutable.value.working) return
        reading = true
        val current = epoch
        try {
            when (val result = source.read()) {
                is Outcome.Ok ->
                    if (current == epoch)
                        mutable.update { old ->
                            old.copy(
                                loading = false,
                                failure = null,
                                services = result.value.toImmutableList(),
                                selected =
                                    old.selected?.takeIf { name ->
                                        result.value.any { it.definition.name == name }
                                    },
                                nowMs = clock.now().toEpochMilliseconds(),
                            )
                        }
                is Outcome.Failed ->
                    if (current == epoch)
                        mutable.update { it.copy(loading = false, failure = result.failure) }
            }
            val name = mutable.value.selected
            if (name != null)
                when (val log = source.log(name)) {
                    is Outcome.Ok ->
                        if (current == epoch && mutable.value.selected == name)
                            mutable.update { it.copy(log = log.value) }
                    is Outcome.Failed ->
                        if (current == epoch) mutable.update { it.copy(failure = log.failure) }
                }
        } finally {
            reading = false
        }
    }

    override fun refresh() {
        viewModelScope.launch {
            val ensured = source.ensureSupervisor()
            read()
            if (ensured is Outcome.Failed) mutable.update { it.copy(failure = ensured.failure) }
        }
    }

    override fun select(name: String) {
        if (mutable.value.working || mutable.value.services.none { it.definition.name == name })
            return
        mutable.update { it.copy(selected = name, log = "", logQuery = "", adding = false) }
        refresh()
    }

    override fun add() {
        if (!mutable.value.working)
            mutable.update {
                it.copy(adding = true, selected = null, invalid = null, failure = null)
            }
    }

    override fun save(
        name: String,
        command: String,
        directory: String,
        port: String,
        restart: Boolean,
    ) {
        if (mutable.value.working || !visible.value || !mutable.value.adding) return
        val def =
            KeptServiceDef(
                name.trim(),
                command.trim(),
                directory = directory.trim(),
                port = port.trim().toIntOrNull(),
                restart = if (restart) KeptRestart.OnFailure else KeptRestart.Never,
            )
        val invalid =
            if (port.isNotBlank() && def.port == null) KeptField.Port
            else def.invalid(mutable.value.services.map { it.definition }, true)
        mutable.update { it.copy(invalid = invalid) }
        if (invalid != null) return
        perform {
            when (val saved = source.save(def)) {
                is Outcome.Failed -> saved
                is Outcome.Ok -> {
                    mutable.update { it.copy(adding = false) }
                    source.act(def.name, KeptAct.Start)
                }
            }
        }
    }

    override fun act(name: String, action: KeptAct) {
        if (mutable.value.working || mutable.value.services.none { it.definition.name == name })
            return
        if (action == KeptAct.Remove) mutable.update { it.copy(ask = KeptAsk(name, action)) }
        else perform { source.act(name, action) }
    }

    private fun perform(command: suspend () -> Outcome<Unit>) {
        if (mutable.value.working || !visible.value) return
        mutable.update { it.copy(working = true, failure = null, ask = null) }
        viewModelScope.launch {
            val result = command()
            mutable.update {
                it.copy(working = false, failure = (result as? Outcome.Failed)?.failure)
            }
            read()
            if (result is Outcome.Failed) mutable.update { it.copy(failure = result.failure) }
        }
    }

    override fun confirm() {
        val ask = mutable.value.ask ?: return
        perform { source.act(ask.name, ask.action) }
    }

    override fun searchLog(query: String) {
        mutable.update { it.copy(logQuery = query) }
    }

    override fun copyLog() {
        mutable.value.selected?.let { name -> perform { source.copyLog(name) } }
    }

    override fun preview(port: Int) {
        val row = mutable.value.services.firstOrNull { it.definition.port == port } ?: return
        if (row.phase != io.github.eslamasabry.oc.engine.runtime.services.kept.KeptPhase.Running)
            return
        if (!opener.open("http://127.0.0.1:$port/"))
            mutable.update { it.copy(failure = Failure.Device()) }
    }

    override fun dismiss() {
        mutable.update { it.copy(ask = null, failure = null, invalid = null) }
    }

    override fun back() {
        if (mutable.value.selected != null || mutable.value.adding)
            mutable.update {
                it.copy(selected = null, adding = false, invalid = null, log = "", logQuery = "")
            }
        else nav.back()
    }

    public companion object {
        public val TIMER: TimerSpec =
            TimerSpec(
                "linux.keptServices",
                Cadence(foreground = 2.seconds, hiddenWatching = null, hiddenIdle = null),
                "Kept service status and selected log while the page is visible.",
                listOf("B-LINUX-008"),
            )
    }
}

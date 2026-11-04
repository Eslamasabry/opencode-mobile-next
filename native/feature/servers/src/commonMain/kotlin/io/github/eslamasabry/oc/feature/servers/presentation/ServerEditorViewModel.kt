package io.github.eslamasabry.oc.feature.servers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.feature.servers.pairing.*
import io.github.eslamasabry.oc.ui.routes.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

public enum class ServerEditorStep {
    Kind,
    Tailscale,
    Connect,
    Ready,
}

public data class ServerEditorUi(
    val step: ServerEditorStep = ServerEditorStep.Kind,
    val record: ServerEditorRecord? = null,
    val loading: Boolean = false,
    val checking: Boolean = false,
    val saving: Boolean = false,
    val slow: Boolean = false,
    val checked: ServerEditorProbe? = null,
    val failedCheck: Boolean = false,
    val saved: Boolean = false,
    val failure: Failure? = null,
    val certificate: ServerCertificate? = null,
    val inspecting: Boolean = false,
    val discard: Boolean = false,
    val fillGeneration: Int = 0,
    val pairingProblem: PairingProblem? = null,
    val canScan: Boolean = false,
    val canTailscale: Boolean = false,
    val canPhone: Boolean = false,
    val tailscaleApp: EditorTailscaleState? = null,
)

public interface ServerEditorActions {
    public fun keepDraft(input: ServerEditorInput)

    public fun takeDraft(): ServerEditorInput?

    public fun refreshTailscale()

    public fun openTailscale()

    public fun installTailscale()

    public fun back(dirty: Boolean)

    public fun discard()

    public fun keepEditing()

    public fun step(step: ServerEditorStep)

    public fun changed()

    public fun check(input: ServerEditorInput)

    public fun save(input: ServerEditorInput, anyway: Boolean = false)

    public fun cancelCheck()

    public fun inspect(address: String)

    public fun dismissCertificate()

    public fun scan()

    public fun paste(text: String?)

    public fun takePairing(): PairingFill?

    public fun phone()

    public fun agents()

    public fun openReady()
}

@AssistedInject
public class ServerEditorViewModel(
    @Assisted private val route: ServersRoute.Edit,
    private val port: ServerEditorPort,
    private val connections: ServerConnections,
    private val nav: AppNavigator,
    private val inbox: PairingInbox,
    private val pairingProbe: PairingProbe,
    platform: PlatformCapabilities,
) : ViewModel(), ServerEditorActions {
    private val mutable =
        MutableStateFlow(
            ServerEditorUi(
                step =
                    if (route.profile == null) ServerEditorStep.Kind else ServerEditorStep.Connect,
                loading = route.profile != null,
                canScan = platform.qrPairing,
                canTailscale = platform.tailscaleHandoff,
                canPhone = platform.builtinLinux,
            )
        )
    public val state: StateFlow<ServerEditorUi> = mutable.asStateFlow()
    private var work: Job? = null
    private var generation = 0
    private var fill: PairingFill? = null
    private var resultFor: ServerEditorInput? = null
    private var savedId: ProfileId? = route.profile
    private var draft: ServerEditorInput? = null

    override fun keepDraft(input: ServerEditorInput) {
        draft = input
    }

    override fun takeDraft(): ServerEditorInput? = draft.also { draft = null }

    override fun refreshTailscale() {
        if (mutable.value.canTailscale)
            mutable.update { it.copy(tailscaleApp = port.tailscaleState()) }
    }

    override fun openTailscale() {
        when (val result = port.openTailscale()) {
            is Outcome.Ok -> Unit
            is Outcome.Failed -> mutable.update { it.copy(failure = result.failure) }
        }
        refreshTailscale()
    }

    override fun installTailscale() {
        when (val result = port.installTailscale()) {
            is Outcome.Ok -> Unit
            is Outcome.Failed -> mutable.update { it.copy(failure = result.failure) }
        }
    }

    init {
        route.profile?.let { id ->
            viewModelScope.launch {
                when (val read = port.read(id)) {
                    is Outcome.Ok ->
                        mutable.update { it.copy(record = read.value, loading = false) }
                    is Outcome.Failed ->
                        mutable.update { it.copy(failure = read.failure, loading = false) }
                }
            }
        }
        viewModelScope.launch {
            inbox.arrivals.collect {
                inbox.take(EDITOR)?.let(::applyPairing)
            }
        }
    }

    override fun changed() {
        if (mutable.value.saving) return
        generation++
        work?.cancel()
        resultFor = null
        mutable.update {
            it.copy(
                checking = false,
                slow = false,
                checked = null,
                failedCheck = false,
                failure = null,
                certificate = null,
            )
        }
    }

    override fun cancelCheck(): Unit = changed()

    override fun step(step: ServerEditorStep) {
        if (mutable.value.saving) return
        changed()
        mutable.update { it.copy(step = step) }
        if (step == ServerEditorStep.Tailscale) refreshTailscale()
    }

    override fun back(dirty: Boolean) {
        if (mutable.value.saving) return
        if (mutable.value.step == ServerEditorStep.Ready) return openReady()
        if (route.profile == null && mutable.value.step != ServerEditorStep.Kind) {
            step(ServerEditorStep.Kind)
        } else if (dirty && mutable.value.step != ServerEditorStep.Kind) {
            mutable.update { it.copy(discard = true) }
        } else nav.back()
    }

    override fun discard() {
        changed()
        nav.back()
    }

    override fun keepEditing() {
        mutable.update { it.copy(discard = false) }
    }

    override fun check(input: ServerEditorInput) = probe(input, saveAfter = false, anyway = false)

    override fun save(input: ServerEditorInput, anyway: Boolean) =
        probe(input, saveAfter = true, anyway = anyway)

    private fun probe(input: ServerEditorInput, saveAfter: Boolean, anyway: Boolean) {
        if (mutable.value.saving || mutable.value.loading) return
        work?.cancel()
        val ticket = ++generation
        work = viewModelScope.launch {
            var checked = mutable.value.checked.takeIf { resultFor == input }
            if (!anyway && checked == null) {
                mutable.update {
                    it.copy(checking = true, failure = null, failedCheck = false, slow = false)
                }
                val slow = launch {
                    delay(8_000)
                    if (ticket == generation) mutable.update { it.copy(slow = true) }
                }
                val answer =
                    try {
                        port.check(input)
                    } finally {
                        slow.cancel()
                    }
                if (ticket != generation) return@launch
                mutable.update { it.copy(checking = false, slow = false) }
                when (answer) {
                    is Outcome.Failed -> {
                        resultFor = null
                        mutable.update { it.copy(failure = answer.failure, failedCheck = true) }
                        return@launch
                    }
                    is Outcome.Ok -> {
                        checked = answer.value
                        resultFor = input
                        mutable.update {
                            it.copy(checked = answer.value, failedCheck = false, failure = null)
                        }
                    }
                }
            }
            if (!saveAfter || ticket != generation) return@launch
            mutable.update { it.copy(saving = true, failure = null) }
            try {
                val saving = input.copy(id = savedId)
                when (val saved = port.save(saving, checked?.version)) {
                    is Outcome.Failed -> mutable.update { it.copy(failure = saved.failure) }
                    is Outcome.Ok -> {
                        savedId = saved.value
                        mutable.update { it.copy(saved = true) }
                        when (val baseline = port.read(saved.value)) {
                            is Outcome.Ok -> mutable.update { it.copy(record = baseline.value) }
                            is Outcome.Failed -> {
                                mutable.update { it.copy(failure = baseline.failure) }
                                return@launch
                            }
                        }
                        // The primary promises Save and connect; no mutation is replayed if
                        // connecting fails.
                        when (val connected = connections.connect(saved.value)) {
                            is Outcome.Failed ->
                                mutable.update { it.copy(failure = connected.failure) }
                            is Outcome.Ok ->
                                mutable.update { it.copy(step = ServerEditorStep.Ready) }
                        }
                    }
                }
            } finally {
                mutable.update { it.copy(saving = false) }
            }
        }
    }

    override fun inspect(address: String) {
        if (mutable.value.inspecting || mutable.value.saving) return
        val ticket = generation
        mutable.update { it.copy(inspecting = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = port.inspect(address)) {
                    is Outcome.Ok ->
                        if (ticket == generation)
                            mutable.update { it.copy(certificate = result.value) }
                    is Outcome.Failed ->
                        if (ticket == generation)
                            mutable.update { it.copy(failure = result.failure) }
                }
            } finally {
                mutable.update { it.copy(inspecting = false) }
            }
        }
    }

    override fun dismissCertificate() {
        mutable.update { it.copy(certificate = null) }
    }

    override fun scan() {
        if (!mutable.value.canScan || mutable.value.saving) return
        inbox.recipient = EDITOR
        nav.open(PairingRoute.Scanner)
    }

    override fun paste(text: String?) {
        val read = PairingText.read(text.orEmpty())
        val parsed = PairingFill.of(read)
        if (parsed == null)
            mutable.update { it.copy(pairingProblem = (read as? PairingRead.Problem)?.problem) }
        else applyPairing(parsed)
    }

    private fun applyPairing(parsed: PairingFill) {
        changed()
        val ticket = generation
        work = viewModelScope.launch {
            val address = PairingChoice.choose(parsed.candidates, pairingProbe)
            if (ticket != generation) {
                parsed.takePassword()
                return@launch
            }
            fill?.takePassword()
            fill = if (address == null || address == parsed.address) parsed else parsed.at(address)
            mutable.update {
                it.copy(
                    step = ServerEditorStep.Connect,
                    pairingProblem = null,
                    fillGeneration = it.fillGeneration + 1,
                )
            }
        }
    }

    override fun takePairing(): PairingFill? = fill.also { fill = null }

    override fun phone() {
        if (mutable.value.canPhone) {
            nav.back()
            nav.open(PhoneRoute.ThisPhone)
        }
    }

    override fun agents() {
        nav.back()
        nav.open(AgentsRoute.Home)
    }

    override fun openReady() {
        nav.back()
        nav.land()
    }

    override fun onCleared() {
        fill?.takePassword()
        resultFor = null
        draft = null
        if (inbox.recipient == EDITOR) inbox.recipient = null
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ServersRoute.Edit): ServerEditorViewModel
    }

    private companion object {
        const val EDITOR = "server-editor"
    }
}

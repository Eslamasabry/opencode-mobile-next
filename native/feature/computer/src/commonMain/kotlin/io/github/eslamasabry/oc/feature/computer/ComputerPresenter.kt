package io.github.eslamasabry.oc.feature.computer

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the update part shows after it ends. */
public sealed interface UpdateLine {
    public data class Done(val version: String?) : UpdateLine

    public data object Current : UpdateLine

    /** Did not finish: the computer refused, or the call failed ([failure] goes under Details). */
    public data class Failed(val failure: Failure?) : UpdateLine

    /** The link dropped while it ran; the gateway reconnects by itself. */
    public data object Dropped : UpdateLine
}

public sealed interface ImportPhase {
    public data object Loading : ImportPhase

    public data class Ready(val list: ImportList) : ImportPhase

    public data class Failed(val failure: Failure) : ImportPhase
}

/** No Compose and no copy here; the screen picks the words. */
public data class ComputerUi(
    val name: String = "",
    val version: String? = null,
    val confirming: Boolean = false,
    val updating: Boolean = false,
    /** The phase the running update last reported. */
    val step: UpdateStep? = null,
    val update: UpdateLine? = null,
    val imports: ImportPhase = ImportPhase.Loading,
    /** The conversation being imported right now; one at a time. */
    val importing: String? = null,
    val importFailed: Failure? = null,
)

public interface ComputerActions {
    public fun back()

    public fun askUpdate()

    public fun cancelUpdate()

    public fun confirmUpdate()

    public fun reloadImports()

    public fun import(key: String)
}

/**
 * The computer page's logic. Updating needs a confirmation and admits one run; progress never ends the request
 * (B-AGENT-167); a refused update is a plain line, never a thrown failure. Importing opens the conversation it made;
 * a refusal stays a failure and opens nothing (B-AGENT-174).
 */
public class ComputerPresenter(
    private val scope: CoroutineScope,
    private val profile: ProfileId,
    private val tools: ComputerTools,
    private val onBack: () -> Unit,
    private val openChat: (ConversationRef) -> Unit,
) : ComputerActions {
    private val local = MutableStateFlow(ComputerUi(name = tools.name(profile).orEmpty()))
    public val state: StateFlow<ComputerUi> = local.asStateFlow()

    init {
        readVersion()
        reloadImports()
    }

    private fun readVersion() {
        scope.launch {
            val read = tools.version(profile)
            if (read is Outcome.Ok) local.update { it.copy(version = read.value) }
        }
    }

    override fun back(): Unit = onBack()

    override fun askUpdate() {
        if (local.value.updating) return
        local.update { it.copy(confirming = true) }
    }

    override fun cancelUpdate() {
        local.update { it.copy(confirming = false) }
    }

    override fun confirmUpdate() {
        if (local.value.updating) return
        local.update { it.copy(confirming = false, updating = true, step = UpdateStep.Starting, update = null) }
        scope.launch {
            val answer = tools.update(profile) { step -> local.update { it.copy(step = step) } }
            val line =
                when (answer) {
                    is Outcome.Ok ->
                        when (val value = answer.value) {
                            is UpdateAnswer.Done -> UpdateLine.Done(value.version)
                            UpdateAnswer.Current -> UpdateLine.Current
                            UpdateAnswer.Refused -> UpdateLine.Failed(null)
                        }
                    is Outcome.Failed ->
                        if (answer.failure is Failure.Network) UpdateLine.Dropped else UpdateLine.Failed(answer.failure)
                }
            val version = (line as? UpdateLine.Done)?.version
            local.update {
                it.copy(updating = false, step = null, update = line, version = version ?: it.version)
            }
        }
    }

    override fun reloadImports() {
        if (local.value.importing != null) return
        local.update { it.copy(imports = ImportPhase.Loading, importFailed = null) }
        scope.launch {
            val phase =
                when (val read = tools.importable(profile)) {
                    is Outcome.Ok -> ImportPhase.Ready(read.value)
                    is Outcome.Failed -> ImportPhase.Failed(read.failure)
                }
            local.update { it.copy(imports = phase) }
        }
    }

    override fun import(key: String) {
        if (local.value.importing != null) return
        val item = (local.value.imports as? ImportPhase.Ready)?.list?.items?.firstOrNull { it.key == key } ?: return
        local.update { it.copy(importing = key, importFailed = null) }
        scope.launch {
            val made = tools.import(profile, item)
            local.update { it.copy(importing = null, importFailed = (made as? Outcome.Failed)?.failure) }
            if (made is Outcome.Ok) openChat(made.value)
        }
    }
}

package io.github.eslamasabry.oc.feature.chat.header.approval

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.requests.consent.ApprovalMode
import io.github.eslamasabry.oc.engine.requests.consent.ApprovalModes
import io.github.eslamasabry.oc.engine.requests.consent.AutoApprovalSweep
import io.github.eslamasabry.oc.engine.requests.consent.EffectiveApproval
import io.github.eslamasabry.oc.engine.requests.consent.SessionApproval
import io.github.eslamasabry.oc.feature.chat.presentation.ChatLauncher
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The three modes the chip offers, least to most permissive (Flutter `_ApprovalChoice`). */
public enum class ApprovalChoice {
    Ask,
    Auto,
    Everything,
}

/**
 * The approval chip beside the model in the composer row (no new bar). [offered] false hides it.
 */
public data class ApprovalChipUi(
    val offered: Boolean = false,
    val choice: ApprovalChoice = ApprovalChoice.Ask,
    val menuOpen: Boolean = false,
    /** "Approve everything?" waits for the person. */
    val confirmEverything: Boolean = false,
    val saveFailure: Failure? = null,
    /** Asks answered automatically in this conversation while it was open. */
    val autoAnswered: Int = 0,
)

public interface ApprovalChipActions {
    public fun openApprovalMenu()

    public fun closeApprovalMenu()

    public fun chooseApproval(choice: ApprovalChoice)

    public fun confirmEverything()

    public fun cancelEverything()

    public fun dismissApprovalFailure()
}

/**
 * The approval mode of one conversation (plan 09 §5.3; Flutter approval_mode_menu.dart). The chip
 * reads and writes the Flutter-format [ApprovalStore]; while the conversation is open, every
 * waiting permission ask of a conversation whose effective mode is automatic is answered "once"
 * through the requests engine (never "always"). Gated on `permissionRequests`, never on the backend
 * kind.
 */
public class ApprovalPresenter(
    private val ref: ConversationRef,
    private val store: ApprovalStore?,
    private val capabilities: StateFlow<ServerCapabilities>,
    private val port: ApprovalPort,
    private val launcher: ChatLauncher,
    clock: Clock,
) : ApprovalChipActions {
    private data class Local(
        val menuOpen: Boolean = false,
        val confirmEverything: Boolean = false,
        val saveFailure: Failure? = null,
        val autoAnswered: Int = 0,
    )

    private val local = MutableStateFlow(Local())
    private val sweep =
        AutoApprovalSweep(
            automatic = { session -> effective(session).automatic },
            reply = { request -> port.allowOnce(request.id) },
            clock = clock,
        )

    private fun settings(): Map<String, SessionApproval> = store?.settings(ref.profileId).orEmpty()

    private fun effective(session: SessionId): EffectiveApproval =
        ApprovalModes.effective(settings(), session, port::parentOf)

    private fun choiceOf(e: EffectiveApproval): ApprovalChoice =
        when {
            e.serverWide -> ApprovalChoice.Everything
            e.automatic -> ApprovalChoice.Auto
            else -> ApprovalChoice.Ask
        }

    public fun current(): ApprovalChipUi = present(capabilities.value, local.value)

    private fun present(caps: ServerCapabilities, l: Local): ApprovalChipUi =
        ApprovalChipUi(
            offered = store != null && caps.permissionRequests,
            choice = choiceOf(effective(ref.sessionId)),
            menuOpen = l.menuOpen,
            confirmEverything = l.confirmEverything,
            saveFailure = l.saveFailure,
            autoAnswered = l.autoAnswered,
        )

    /**
     * The chip's state, plus the sweep: every waiting ask and every mode change re-applies the
     * effective mode.
     */
    public fun states(scope: CoroutineScope, started: SharingStarted): StateFlow<ApprovalChipUi> {
        val revision = store?.revision ?: MutableStateFlow(0L)
        combine(port.waiting, revision) { waiting, _ -> waiting }
            .onEach { waiting ->
                if (store == null || !capabilities.value.permissionRequests) return@onEach
                val answered = sweep.sweep(waiting)
                val mine = answered.count { id ->
                    waiting.any { it.id == id && it.sessionId == ref.sessionId }
                }
                if (mine > 0) local.update { it.copy(autoAnswered = it.autoAnswered + mine) }
            }
            .launchIn(scope)
        return combine(capabilities, local, revision) { c, l, _ -> present(c, l) }
            .stateIn(scope, started, current())
    }

    override fun openApprovalMenu() {
        local.update { it.copy(menuOpen = true) }
    }

    override fun closeApprovalMenu() {
        local.update { it.copy(menuOpen = false) }
    }

    /**
     * Only "Approve everything" asks first; this conversation's own modes apply at once (Flutter).
     */
    override fun chooseApproval(choice: ApprovalChoice) {
        local.update { it.copy(menuOpen = false) }
        val now = choiceOf(effective(ref.sessionId))
        if (choice == now) return
        if (choice == ApprovalChoice.Everything) {
            local.update { it.copy(confirmEverything = true) }
            return
        }
        write(choice)
    }

    override fun confirmEverything() {
        local.update { it.copy(confirmEverything = false) }
        write(ApprovalChoice.Everything)
    }

    override fun cancelEverything() {
        local.update { it.copy(confirmEverything = false) }
    }

    override fun dismissApprovalFailure() {
        local.update { it.copy(saveFailure = null) }
    }

    /** The one write path (Flutter `_writeApprovalChoice`). */
    private fun write(choice: ApprovalChoice) {
        val s = store ?: return
        val profile = ref.profileId
        val session = ref.sessionId.raw
        launcher.launch {
            val leavingEverything =
                choice != ApprovalChoice.Everything &&
                    choiceOf(effective(ref.sessionId)) == ApprovalChoice.Everything
            var result: Outcome<Unit> = Outcome.Ok(Unit)
            // Leaving "Approve everything" turns the server-wide switch off: there is no other
            // place to undo it.
            if (leavingEverything) result = s.set(profile, ApprovalModes.SERVER_WIDE, null)
            if (result is Outcome.Ok) {
                result =
                    when (choice) {
                        ApprovalChoice.Ask ->
                            s.set(profile, session, SessionApproval(ApprovalMode.Ask))
                        ApprovalChoice.Auto ->
                            s.set(profile, session, SessionApproval(ApprovalMode.AutoOnce))
                        ApprovalChoice.Everything -> {
                            val all =
                                s.set(
                                    profile,
                                    ApprovalModes.SERVER_WIDE,
                                    SessionApproval(
                                        ApprovalMode.AutoOnce,
                                        inheritToChildren = true,
                                    ),
                                )
                            // A conversation's own choice outranks the server-wide one: drop it so
                            // this one follows.
                            if (all is Outcome.Ok && session in s.settings(profile))
                                s.set(profile, session, null)
                            else all
                        }
                    }
            }
            if (result is Outcome.Ok) result = port.syncPermissions()
            local.update { it.copy(saveFailure = (result as? Outcome.Failed)?.failure) }
        }
    }
}

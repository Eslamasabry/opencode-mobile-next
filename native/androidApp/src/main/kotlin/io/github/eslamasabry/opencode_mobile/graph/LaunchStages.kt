package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.chat.world.LaunchStage
import io.github.eslamasabry.oc.core.common.holdStages
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The one stage source: what a phone agent's (or This phone's own) conversation is really waiting for right now.
 *
 * READ (the chat page's waiting line, and the home's getting-ready row): [stageOfPhone] for This phone's own
 * OpenCode source, [stageOfHelper] for the agents' helper source (the saved "Phone agents" server). Each is a
 * [StateFlow] of the current [LaunchStage], or null while no signal names one (a reader then uses its generic words:
 * no guessing, no timer, no fake sequence). Every stage already holds at least `STAGE_HOLD_MS` before the next one
 * shows; null (nothing in progress) passes at once, so a ready page is never kept waiting by the hold.
 * [LaunchStage.Conversation] is not produced here: the chat page itself knows when the server is up and its read is
 * pending.
 *
 * FED only by real signals, each one named at the place that raises it:
 *  - phone: [PhoneServerSource.facts] (`starting`: the one start path runs, whoever asked for it, `setServerWanted`
 *    and the connection's wait for the server; `status.serverRunning`: its process runs), refreshed on every poll of
 *    the wait ([BuiltinRuntime]'s health gate);
 *  - helper wanted: its start loop's first window ([HelperJoins.starting], pushed by [PreviewRuntime]);
 *  - helper starting: [AppLocalAgentHostStarter.prepare], from `startPhoneAgentHelper` until the helper answers
 *    (`HelperAnswerWait`);
 *  - agent connecting: [PreviewRuntime.connectForHome] of the helper's source.
 */
@SingleIn(AppScope::class)
@Inject
class LaunchStages(
    private val appScope: AppCoroutineScope,
    // Lazy: the phone server source is built from the runtime that feeds it, so it is read at first use.
    private val server: Lazy<PhoneServerSource>,
) {
    private val helperWanted = MutableStateFlow<Set<ProfileId>>(emptySet())
    private val helperStart = MutableStateFlow<Set<ProfileId>>(emptySet())
    private val agentConnecting = MutableStateFlow<Set<ProfileId>>(emptySet())

    private val phone: StateFlow<Pair<Boolean, Boolean>> by lazy {
        server.value.facts
            .map { (it.starting) to (it.status?.serverRunning == true) }
            .distinctUntilChanged()
            .stateIn(appScope, SharingStarted.Eagerly, false to false)
    }

    private val phoneStage: StateFlow<LaunchStage?> by lazy {
        phone
            .map { (starting, running) -> phoneStageOf(starting, running) }
            .distinctUntilChanged()
            .holdStages { true }
            .stateIn(appScope, SharingStarted.Eagerly, null)
    }

    private val helperStages = HashMap<ProfileId, StateFlow<LaunchStage?>>()

    /** The stage of This phone's own OpenCode: Linux, then OpenCode; null when nothing is starting. */
    fun stageOfPhone(): StateFlow<LaunchStage?> = phoneStage

    /** The stage of the agents' helper [id]: Linux, Helper, then Agent; null when no signal names one. */
    fun stageOfHelper(id: ProfileId): StateFlow<LaunchStage?> =
        synchronized(helperStages) {
            helperStages.getOrPut(id) {
                combine(phone, helperWanted, helperStart, agentConnecting) { (starting, _), wanted, start, agent ->
                        helperStageOf(
                            wanted = id in wanted,
                            phoneStarting = starting,
                            helperStarting = id in start,
                            agentConnecting = id in agent,
                        )
                    }
                    .distinctUntilChanged()
                    .holdStages { true }
                    .stateIn(appScope, SharingStarted.Eagerly, null)
            }
        }

    /** The helper's start loop of [ids] is in its first window ([HelperJoins.starting]). */
    internal fun helperWanted(ids: Set<ProfileId>) {
        helperWanted.value = ids
    }

    /** [AppLocalAgentHostStarter] starts the helper of [id] (until it answers) or has stopped doing so. */
    internal fun helperStarting(id: ProfileId, on: Boolean) = toggle(helperStart, id, on)

    /** The helper's own source [id] is connecting (its agents are warming up) or has stopped. */
    internal fun agentConnecting(id: ProfileId, on: Boolean) = toggle(agentConnecting, id, on)

    private fun toggle(flow: MutableStateFlow<Set<ProfileId>>, id: ProfileId, on: Boolean) =
        flow.update { if (on) it + id else it - id }

    internal companion object {
        /** This phone's server: its start runs and the process is not up yet = Linux; up, not answering = OpenCode. */
        fun phoneStageOf(starting: Boolean, running: Boolean): LaunchStage? =
            when {
                !starting -> null
                !running -> LaunchStage.Linux
                else -> LaunchStage.OpenCode
            }

        /**
         * The helper: its source connecting, else its own start, else Linux while the phone's start runs, else the
         * helper's start while its start loop is still in its first window. Between two of the real signals (its own
         * start ended, its connection not begun; a refused try waiting to be asked again) the wait is still on, so it
         * keeps its name: a waiting state never clears by itself and returns (waiting is not failing).
         */
        fun helperStageOf(
            wanted: Boolean,
            phoneStarting: Boolean,
            helperStarting: Boolean,
            agentConnecting: Boolean,
        ): LaunchStage? =
            when {
                agentConnecting -> LaunchStage.Agent
                helperStarting -> LaunchStage.Helper
                wanted && phoneStarting -> LaunchStage.Linux
                wanted -> LaunchStage.Helper
                else -> null
            }
    }
}

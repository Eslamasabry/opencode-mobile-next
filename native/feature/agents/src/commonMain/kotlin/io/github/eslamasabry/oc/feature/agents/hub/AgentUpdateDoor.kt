package io.github.eslamasabry.oc.feature.agents.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.install.AgentRemovalFacts
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoice
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateChoices
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateOffer
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateStarted
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The second door to "Updates for your agents" (coordinator, 10110): on the Agents page, "Agent updates ready ·
 * Review" while an offer waits for a real decision (before any answer, and after Not now: Not now is "ask me later",
 * never "never"). Review raises the sheet again and goes Home, where it opens. Its own ViewModel, joined to the
 * Agents page's in its entry ([AgentsWithUpdateDoor]), so the page's own constructor does not grow.
 * [choices] is a NON-NULL key: Metro never fills a nullable one from the app's binding.
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class AgentUpdateDoorViewModel(
    private val nav: AppNavigator,
    private val choices: AgentUpdateChoices = NoUpdateChoicesHere,
) : ViewModel() {
    /** True while the Agents page shows the door. */
    public val ready: StateFlow<Boolean> =
        choices.offer
            .map(::reviewable)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_MS), reviewable(choices.offer.value))

    public fun review() {
        log.i { "agents page: review" }
        viewModelScope.launch { choices.review() }
        nav.land()
    }

    private companion object {
        const val KEEP_MS = 5_000L
        val log = AppLog.tagged("AgentUpdates")
    }
}

/** Home's rule, the same words: an offer exists and some Optional agent has no decision yet. */
public fun reviewable(offer: AgentUpdateOffer?): Boolean =
    offer != null &&
        (!offer.answered || offer.items.any { it.group == AgentUpdateGroup.Optional && it.choice == null })

/** The Agents page's actions with the door's Review joined in; everything else goes to [agents]. */
public class AgentsWithUpdateDoor(agents: AgentsActions, private val door: AgentUpdateDoorViewModel) :
    AgentsActions by agents {
    override fun reviewAgentUpdates(): Unit = door.review()
}

/** No engine bound (desktop, previews): never an offer, so no door. */
private object NoUpdateChoicesHere : AgentUpdateChoices {
    override val offer: StateFlow<AgentUpdateOffer?> = MutableStateFlow(null)

    override suspend fun decide(
        choices: Map<AgentId, AgentUpdateChoice>,
        waitForWifi: Boolean,
    ): Outcome<AgentUpdateStarted> =
        Outcome.Failed(Failure.Unavailable(FeatureId("agentUpdateChoices")))

    override suspend fun notNow(): Outcome<Unit> = Outcome.Ok(Unit)

    override fun removalFacts(id: AgentId): AgentRemovalFacts =
        AgentRemovalFacts(null, keepsSignIn = true, keepsConversations = true)

    override suspend fun review(raise: Boolean): Outcome<Unit> = Outcome.Ok(Unit)
}

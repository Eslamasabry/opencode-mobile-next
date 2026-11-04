package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.*
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.SessionSkillGateway
import io.github.eslamasabry.oc.core.domain.events.GatewayEvent
import io.github.eslamasabry.oc.core.domain.events.NoticeKind
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.catalog.*
import io.github.eslamasabry.oc.feature.prompts.presentation.SkillsSource
import io.github.eslamasabry.oc.feature.review.ReviewHub
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * B-STATE-120: a command-page skill always targets its captured graph, folder and undo revision.
 */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<SkillsSource>())
@Inject
class AndroidConversationSkills(
    private val registry: ConnectionRegistry,
    private val reviews: ReviewHub,
    appScope: AppCoroutineScope,
) : SkillsSource {
    private val engine = ConversationSkills()
    private val targets = mutableMapOf<ConversationRef, SkillTarget>()
    private val deleted = mutableSetOf<ConversationRef>()

    init {
        appScope.launch(registry.mainContext) {
            registry.live.collectLatest { graphs ->
                targets.entries.removeAll { !it.value.current() }
                deleted.removeAll { it.profileId !in graphs }
                coroutineScope {
                    for ((profile, graph) in graphs) launch {
                        graph.session.server.events.all.collect { envelope ->
                            val event = envelope.event
                            if (
                                event is GatewayEvent.Notice &&
                                    event.kind == NoticeKind.SkillChanged
                            ) {
                                // The event is a hint only: never retain or display its
                                // instruction/name fields.
                                reviews.review(ConversationRef(profile, event.sessionId)).refresh()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun available(ref: ConversationRef): Boolean =
        registry.graphFor(ref)?.session?.capabilities?.value?.sessionSkills == true

    override suspend fun list(ref: ConversationRef): Outcome<List<SkillInfo>> {
        val graph = registry.graphFor(ref) ?: return Failure.Network().failed()
        val gateway = graph.session.server
        val skillGateway =
            gateway as? SessionSkillGateway
                ?: return Failure.Unavailable(FeatureId("sessionSkills")).failed()
        if (!gateway.capabilities.value.sessionSkills)
            return Failure.Unavailable(FeatureId("sessionSkills")).failed()
        val location = gateway.location.value
        val review = reviews.review(ref)
        val target =
            SkillTarget(
                gateway,
                skillGateway,
                current = {
                    registry.graphFor(ref) === graph &&
                        gateway.location.value == location &&
                        ref !in deleted
                },
                historyRevision = { review.state.value.revision },
                revertPending = { review.state.value.working || review.state.value.staged != null },
                refreshed = { review.refresh() },
            )
        targets[ref] = target
        return engine.list(target)
    }

    override suspend fun activate(ref: ConversationRef, skill: String): SkillActivation {
        val target = targets[ref] ?: return SkillActivation.Refused(SessionSkillFailure.Changed)
        return engine.activate(ref, target, skill)
    }
}

package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueueMovePort
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueueMoved
import io.github.eslamasabry.oc.feature.servers.presentation.ServerQueuePlan

/**
 * Moving a whole server's queued prompts (screens-3 page) needs a batch move with Undo that the app's
 * offline queue does not have; the per-conversation queue move (chat header) is the working path. Until
 * the queue gains a batch move, this page states it is unavailable instead of moving without Undo.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<ServerQueueMovePort>())
@Inject
class NativeServerQueueMoves : ServerQueueMovePort {
    private val unavailable = Failure.Unavailable(FeatureId("queuedPromptServerMove"))

    override suspend fun prepare(source: ProfileId): Outcome<ServerQueuePlan> =
        Outcome.Failed(unavailable)

    override suspend fun move(
        token: String,
        ids: Set<String>,
        conversation: SessionId?,
    ): Outcome<ServerQueueMoved> = Outcome.Failed(unavailable)

    override suspend fun undo(token: String): Outcome<Int> = Outcome.Failed(unavailable)

    override fun forget(token: String) = Unit
}

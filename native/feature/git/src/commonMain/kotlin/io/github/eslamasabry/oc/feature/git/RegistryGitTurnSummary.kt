package io.github.eslamasabry.oc.feature.git

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.git.presentation.GitTurnSummary
import io.github.eslamasabry.oc.feature.git.presentation.turnSummary

/**
 * The conversation's newest page of messages from its own live server, read once when the Git page opens. A server
 * that is away or a read that fails just leaves "Write it for me" out; nothing is cached or logged.
 */
@Inject
@ContributesBinding(AppScope::class)
public class RegistryGitTurnSummary(private val registry: ConnectionRegistry) : GitTurnSummary {
    override suspend fun summary(ref: ConversationRef): String? {
        val gateway = registry.graphFor(ref)?.session?.server ?: return null
        return when (val page = gateway.messagePage(ref.sessionId, limit = PAGE)) {
            is Outcome.Ok -> turnSummary(page.value.items)
            is Outcome.Failed -> null
        }
    }

    private companion object {
        const val PAGE = 20
    }
}

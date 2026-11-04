package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorCause
import io.github.eslamasabry.oc.engine.agents.errors.AgentErrorText
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

/**
 * Runtime text is classified, never displayed as the headline. Details remains separately
 * credential-masked.
 */
@Composable
internal fun agentFailureWords(reason: String): Pair<String, String> {
    val keys =
        when (AgentErrorText.classify(reason)) {
            AgentErrorCause.ConnectionDropped ->
                Res.string.agentErrorConnectionDropped to Res.string.agentErrorConnectionDroppedHint
            AgentErrorCause.TimedOut ->
                Res.string.agentErrorTimedOut to Res.string.agentErrorTimedOutHint
            AgentErrorCause.ProviderUnreachable ->
                Res.string.agentErrorProviderUnreachable to
                    Res.string.agentErrorProviderUnreachableHint
            AgentErrorCause.RequestTooLarge ->
                Res.string.agentErrorRequestTooLarge to Res.string.agentErrorRequestTooLargeHint
            AgentErrorCause.RateLimited ->
                Res.string.agentErrorRateLimited to Res.string.agentErrorRateLimitedHint
            AgentErrorCause.ProviderBusy ->
                Res.string.agentErrorProviderBusy to Res.string.agentErrorProviderBusyHint
            AgentErrorCause.OutOfCredit ->
                Res.string.agentErrorOutOfCredit to Res.string.agentErrorOutOfCreditHint
            AgentErrorCause.FreeTierLimit ->
                Res.string.agentErrorFreeTier to Res.string.agentErrorFreeTierHint
            AgentErrorCause.UsageLimit ->
                Res.string.agentErrorUsageLimit to Res.string.agentErrorUsageLimitHint
            AgentErrorCause.ProviderAuth ->
                Res.string.agentErrorProviderAuth to Res.string.agentErrorProviderAuthHint
            AgentErrorCause.ServerDiskFull ->
                Res.string.agentErrorServerDiskFull to Res.string.agentErrorServerDiskFullHint
            null -> Res.string.agentErrorUnknown to Res.string.agentErrorUnknownHint
        }
    return stringResource(keys.first) to stringResource(keys.second)
}

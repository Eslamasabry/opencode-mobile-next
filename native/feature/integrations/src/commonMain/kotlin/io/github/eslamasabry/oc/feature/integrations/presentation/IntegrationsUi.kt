package io.github.eslamasabry.oc.feature.integrations.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.integrations.catalog.McpChatSnapshot
import io.github.eslamasabry.oc.engine.integrations.catalog.SkillConversationResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow

public data class IntegrationServer(val id: ProfileId, val name: String)

public data class ProviderMethodUi(val id: String, val label: String, val oauth: Boolean)

public data class ProviderUi(
    val id: String,
    val name: String,
    val connected: Boolean,
    val methods: ImmutableList<ProviderMethodUi>,
    val logo: String? = null,
    val keyPage: Boolean = false,
)

public data class ConnectorUi(val name: String, val connected: Boolean)

public data class IntegrationsData(
    val providers: ImmutableList<ProviderUi> = persistentListOf(),
    val connectors: ImmutableList<ConnectorUi> = persistentListOf(),
    val providerSupported: Boolean = false,
    val connectorSupported: Boolean = false,
)

public data class ProviderAuthorization(val attempt: String, val url: String)

public data class IntegrationLinkPrompt(val host: String, val insecure: Boolean)

public data class SkillDraftResult(val result: SkillConversationResult, val ref: ConversationRef?)

/**
 * A screen's pinned connection. Closing releases local authentication state, never remote
 * credentials.
 */
public interface IntegrationSession {
    public val profile: ProfileId
    public val connector: StateFlow<McpChatSnapshot?>

    public suspend fun read(): Outcome<IntegrationsData>

    public suspend fun saveKey(provider: String, key: String): Outcome<Unit>

    public suspend fun startOAuth(provider: String, method: String): Outcome<ProviderAuthorization>

    public suspend fun completeOAuth(attempt: String, input: String): Outcome<Unit>

    public suspend fun saveConnector(
        name: String,
        url: String,
        headerName: String,
        headerValue: String,
    ): Outcome<Unit>

    /** Connector sign-in: progress and failures arrive through [connector], never as text. */
    public suspend fun startConnectorSignIn()

    public suspend fun completeConnectorSignIn(input: String)

    public suspend fun cancelConnectorSignIn()

    public suspend fun writeSkill(requirements: String): SkillDraftResult

    public fun close()
}

public interface IntegrationsSource {
    public val servers: StateFlow<ImmutableList<IntegrationServer>>
    public val active: StateFlow<ProfileId?>

    public fun open(profile: ProfileId): IntegrationSession?

    public fun openLink(url: String, confirmed: Boolean = false): Outcome<IntegrationLinkPrompt?>

    public fun keyPage(provider: String): String?
}

public enum class IntegrationsNotice {
    Saved,
    Failed,
    Unsupported,
    Changed,
    Invalid,
    DraftSent,
    DraftUncertain,
}

public data class IntegrationsUi(
    val servers: ImmutableList<IntegrationServer> = persistentListOf(),
    val server: ProfileId? = null,
    val data: IntegrationsData = IntegrationsData(),
    val busy: Boolean = false,
    val notice: IntegrationsNotice? = null,
    val selected: String? = null,
    val authorization: ProviderAuthorization? = null,
    val connector: McpChatSnapshot? = null,
    val draft: ConversationRef? = null,
    val linkPrompt: IntegrationLinkPrompt? = null,
)

public interface IntegrationsActions {
    public fun back()

    public fun refresh()

    public fun chooseServer(id: ProfileId)

    public fun chooseProvider(id: String)

    public fun saveKey(key: String)

    public fun startOAuth(method: String)

    public fun completeOAuth(code: String)

    public fun openAuthorization()

    public fun openKeyPage()

    public fun confirmLink()

    public fun dismissLink()

    public fun saveConnector(name: String, url: String, headerName: String, headerValue: String)

    public fun startConnectorSignIn()

    public fun openConnectorSignIn()

    public fun completeConnectorSignIn(code: String)

    public fun cancelConnectorSignIn()

    public fun writeSkill(requirements: String)

    public fun openDraft()
}

package io.github.eslamasabry.oc.feature.chat.cards

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.engine.cards.AgentCard
import io.github.eslamasabry.oc.engine.integrations.catalog.McpChatConnect
import io.github.eslamasabry.oc.engine.integrations.catalog.McpChatFailure
import io.github.eslamasabry.oc.engine.integrations.catalog.McpChatPhase
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * A card carries only the catalogue id; real addresses are resolved separately after the person
 * asks.
 */
@Composable
internal fun ConnectorCardControls(
    card: AgentCard,
    host: ChatConnectorSlotHost,
    authorized: Boolean,
) {
    val scope = rememberCoroutineScope()
    var controller by
        remember(card.identity, card.revision) { mutableStateOf<McpChatConnect?>(null) }
    var preparing by remember(card.identity, card.revision) { mutableStateOf(false) }
    var unavailable by remember(card.identity, card.revision) { mutableStateOf(false) }
    Column {
        card.connector?.reason?.takeIf { it.isNotBlank() }?.let { KitText(it) }
        val connector = controller
        if (connector == null) {
            if (authorized)
                KitButton(
                    stringResource(Res.string.mcpNativeReview),
                    {
                        if (!preparing)
                            scope.launch {
                                preparing = true
                                try {
                                    controller = host.connector(card)
                                    unavailable = controller == null
                                } finally {
                                    preparing = false
                                }
                            }
                    },
                    working = preparing,
                )
            if (!authorized || unavailable) KitText(stringResource(Res.string.mcpNativeUnsupported))
        } else if (!authorized) {
            KitText(stringResource(Res.string.mcpNativeSourceChanged))
        } else {
            val snapshot by connector.snapshot.collectAsStateWithLifecycle()
            val draft = connector.item.draft
            draft?.url?.let {
                KitRow(
                    title = stringResource(Res.string.mcpNativeAddress),
                    supporting = it,
                    supportingMaxLines = 4,
                )
            }
            when (snapshot.phase) {
                McpChatPhase.Suggested ->
                    KitButton(
                        stringResource(Res.string.mcpNativeAdd),
                        { scope.launch { connector.connect(connector.item.serverName) } },
                    )
                McpChatPhase.Connecting,
                McpChatPhase.CheckingTools ->
                    KitButton(
                        stringResource(Res.string.mcpNativeCheck),
                        { scope.launch { connector.refresh() } },
                        working = true,
                    )
                McpChatPhase.ToolsReady -> KitText(stringResource(Res.string.mcpNativeToolsReady))
                McpChatPhase.ConnectedReadinessUnknown ->
                    KitText(stringResource(Res.string.mcpNativeAdded))
                McpChatPhase.NeedsAuthentication ->
                    KitButton(
                        stringResource(Res.string.mcpNativeSignIn),
                        { scope.launch { connector.startOAuth() } },
                    )
                McpChatPhase.Authorizing -> {
                    val code = rememberTextFieldState()
                    snapshot.authorizationUrl?.let { url ->
                        KitButton(
                            stringResource(Res.string.mcpNativeSignIn),
                            { host.openLink(url) },
                        )
                    }
                    KitField(
                        code,
                        stringResource(Res.string.mcpNativeCode),
                        kind = KitFieldKind.Secret,
                        maxLength = 8192,
                    )
                    KitButton(
                        stringResource(Res.string.mcpNativeFinishSignIn),
                        { scope.launch { connector.completeOAuth(code.text.toString()) } },
                    )
                    KitButton(
                        stringResource(Res.string.mcpNativeCancelSignIn),
                        { scope.launch { connector.cancelOAuth() } },
                        role = KitButtonRole.Tertiary,
                    )
                }
                McpChatPhase.Failed,
                McpChatPhase.Unavailable -> {
                    KitText(
                        stringResource(
                            when (snapshot.failure) {
                                McpChatFailure.SourceChanged -> Res.string.mcpNativeSourceChanged
                                McpChatFailure.NameConflict -> Res.string.mcpNativeConflict
                                McpChatFailure.SetupRequired -> Res.string.mcpNativeNeedsSettings
                                McpChatFailure.Unavailable,
                                McpChatFailure.OauthUnavailable -> Res.string.mcpNativeUnsupported
                                else -> Res.string.mcpNativeAddUncertain
                            }
                        )
                    )
                    if (authorized && snapshot.failure != McpChatFailure.Unavailable)
                        KitButton(
                            stringResource(Res.string.mcpNativeCheck),
                            { scope.launch { connector.refresh() } },
                            role = KitButtonRole.Secondary,
                        )
                }
            }
        }
        KitButton(
            stringResource(Res.string.mcpNativeTitle),
            host::openCatalogue,
            role = KitButtonRole.Tertiary,
        )
    }
}

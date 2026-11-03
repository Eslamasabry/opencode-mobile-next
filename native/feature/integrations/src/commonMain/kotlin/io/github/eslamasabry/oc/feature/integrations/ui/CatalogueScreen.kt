package io.github.eslamasabry.oc.feature.integrations.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.engine.connectors.catalog.CatalogueFact
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogueUi
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueActions
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueAsk
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueBlock
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueProblem
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueProblemKind
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueRow
import io.github.eslamasabry.oc.feature.integrations.presentation.CatalogueUi
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.intaCatalogAddBody
import io.github.eslamasabry.oc.ui.words.res.intaCatalogAddConfirm
import io.github.eslamasabry.oc.ui.words.res.intaCatalogAddFailed
import io.github.eslamasabry.oc.ui.words.res.intaCatalogAddTitle
import io.github.eslamasabry.oc.ui.words.res.intaCatalogAdding
import io.github.eslamasabry.oc.ui.words.res.intaCatalogCannotRemove
import io.github.eslamasabry.oc.ui.words.res.intaCatalogConsentBody
import io.github.eslamasabry.oc.ui.words.res.intaCatalogConsentLoad
import io.github.eslamasabry.oc.ui.words.res.intaCatalogConsentNotSaved
import io.github.eslamasabry.oc.ui.words.res.intaCatalogConsentTitle
import io.github.eslamasabry.oc.ui.words.res.intaCatalogEmpty
import io.github.eslamasabry.oc.ui.words.res.intaCatalogEmptyBody
import io.github.eslamasabry.oc.ui.words.res.intaCatalogFailed
import io.github.eslamasabry.oc.ui.words.res.intaCatalogFailedBody
import io.github.eslamasabry.oc.ui.words.res.intaCatalogForget
import io.github.eslamasabry.oc.ui.words.res.intaCatalogForgetFailed
import io.github.eslamasabry.oc.ui.words.res.intaCatalogHostedBy
import io.github.eslamasabry.oc.ui.words.res.intaCatalogInventoryFailed
import io.github.eslamasabry.oc.ui.words.res.intaCatalogInventoryFailedBody
import io.github.eslamasabry.oc.ui.words.res.intaCatalogLoading
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsDocker
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsKey
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsKeyBlock
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsNode
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsNodePhone
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsPython
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsSettings
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNeedsSettingsBlock
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNoEndpoint
import io.github.eslamasabry.oc.ui.words.res.intaCatalogNoMatch
import io.github.eslamasabry.oc.ui.words.res.intaCatalogPriceNote
import io.github.eslamasabry.oc.ui.words.res.intaCatalogRemoveBody
import io.github.eslamasabry.oc.ui.words.res.intaCatalogRemoveConfirm
import io.github.eslamasabry.oc.ui.words.res.intaCatalogRemoveFailed
import io.github.eslamasabry.oc.ui.words.res.intaCatalogRemoveTitle
import io.github.eslamasabry.oc.ui.words.res.intaCatalogRemoving
import io.github.eslamasabry.oc.ui.words.res.intaCatalogSearch
import io.github.eslamasabry.oc.ui.words.res.intaCatalogSearchFailed
import io.github.eslamasabry.oc.ui.words.res.intaCatalogSection
import io.github.eslamasabry.oc.ui.words.res.intaCatalogStale
import io.github.eslamasabry.oc.ui.words.res.intaCatalogTitle
import io.github.eslamasabry.oc.ui.words.res.intaCatalogUnavailable
import io.github.eslamasabry.oc.ui.words.res.intaCatalogUnavailableBody
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsNoServer
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsNoServerBody
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsOpenServers
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsRefresh
import io.github.eslamasabry.oc.ui.words.res.intaConnectorsTryAgain
import io.github.eslamasabry.oc.ui.words.res.intaDismiss
import io.github.eslamasabry.oc.ui.words.res.intaToggleFailedBody
import io.github.eslamasabry.oc.ui.words.res.kitCopyDetails
import io.github.eslamasabry.oc.ui.words.res.kitDetails
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import org.jetbrains.compose.resources.stringResource

/**
 * The MCP catalogue: consent first, then the public registry's servers as switches (on first, then addable, then not
 * addable), each with what it needs in plain words. Registry text is plain text; nothing in it opens. Kit parts only.
 */
@Composable
public fun CatalogueScreen(state: CatalogueUi, actions: CatalogueActions) {
    val gutter = LocalKitTokens.current.gutter
    val listing = !state.noServer && state.canForget && state.page !is McpCatalogueUi.Unavailable
    val loadingWords = stringResource(Res.string.intaCatalogLoading)
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.intaCatalogTitle),
                subtitle = state.serverName,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
                actions =
                    if (!listing) emptyList()
                    else
                        listOf(
                            KitLineAction(
                                stringResource(Res.string.intaConnectorsRefresh),
                                actions::retry,
                                enabled = !state.refreshing,
                                disabledReason = loadingWords,
                            ),
                            KitLineAction(stringResource(Res.string.intaCatalogForget), actions::forget),
                        ),
            )
        },
        search =
            if (!listing) null
            else {
                { KitSearchField(label = stringResource(Res.string.intaCatalogSearch), onSearch = actions::search) }
            },
        loading = state.refreshing,
        loadingLabel = loadingWords,
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
            state.problem?.let { problem ->
                item(key = "problem") {
                    KitErrorNotice(
                        problemBody(problem),
                        Modifier.padding(horizontal = gutter.dp),
                        title = problemTitle(problem),
                        failure = problem.failure,
                        detailsLabel = stringResource(Res.string.kitDetails),
                        copyLabel = stringResource(Res.string.kitCopyDetails),
                        retry = KitLineAction(stringResource(Res.string.intaDismiss), actions::dismissProblem),
                    )
                }
            }
            if (state.noServer) {
                item(key = "no-server") {
                    KitStateView(
                        kind = KitStateKind.Missing,
                        title = stringResource(Res.string.intaConnectorsNoServer),
                        body = stringResource(Res.string.intaConnectorsNoServerBody),
                        primary = KitAction(stringResource(Res.string.intaConnectorsOpenServers), actions::openServers),
                    )
                }
                return@LazyColumn
            }
            when (val page = state.page) {
                McpCatalogueUi.Unavailable ->
                    item(key = "unavailable") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.intaCatalogUnavailable),
                            body = stringResource(Res.string.intaCatalogUnavailableBody),
                        )
                    }
                McpCatalogueUi.Loading -> item(key = "loading") { KitSkeletonRows() }
                McpCatalogueUi.Consent ->
                    item(key = "consent") {
                        KitStateView(
                            kind = KitStateKind.Calm,
                            title = stringResource(Res.string.intaCatalogConsentTitle),
                            body = stringResource(Res.string.intaCatalogConsentBody),
                            primary = KitAction(stringResource(Res.string.intaCatalogConsentLoad), actions::load),
                        )
                    }
                McpCatalogueUi.InventoryFailed ->
                    item(key = "inventory-failed") {
                        KitErrorNotice(
                            stringResource(Res.string.intaCatalogInventoryFailedBody),
                            Modifier.padding(horizontal = gutter.dp),
                            title = stringResource(Res.string.intaCatalogInventoryFailed),
                            retry = KitLineAction(stringResource(Res.string.intaConnectorsTryAgain), actions::retry),
                        )
                    }
                is McpCatalogueUi.Failed ->
                    item(key = "failed") {
                        KitErrorNotice(
                            stringResource(Res.string.intaCatalogFailedBody),
                            Modifier.padding(horizontal = gutter.dp),
                            title =
                                stringResource(
                                    if (page.query == null) Res.string.intaCatalogFailed else Res.string.intaCatalogSearchFailed
                                ),
                            retry = KitLineAction(stringResource(Res.string.intaConnectorsTryAgain), actions::retry),
                        )
                    }
                is McpCatalogueUi.Empty ->
                    item(key = "empty") {
                        KitStateView(
                            kind = KitStateKind.Empty,
                            title =
                                page.query?.let { stringResource(Res.string.intaCatalogNoMatch, it) }
                                    ?: stringResource(Res.string.intaCatalogEmpty),
                            body = stringResource(Res.string.intaCatalogEmptyBody),
                        )
                    }
                is McpCatalogueUi.Rows -> {
                    if (page.stale) {
                        item(key = "stale") {
                            KitNotice(
                                stringResource(Res.string.intaCatalogStale),
                                Modifier.padding(horizontal = gutter.dp),
                                tone = StatusTone.Attention,
                                actions = listOf(KitLineAction(stringResource(Res.string.intaConnectorsTryAgain), actions::retry)),
                            )
                        }
                    }
                    item(key = "rows") {
                        KitRowGroup(
                            label = stringResource(Res.string.intaCatalogSection),
                            note = if (page.priceNote) stringResource(Res.string.intaCatalogPriceNote) else null,
                        ) {
                            state.rows.forEach { row -> CatalogueSwitch(row, actions) }
                        }
                    }
                }
            }
        }
        KitConfirm(confirmSpec(state.ask), onConfirm = actions::confirm, onCancel = actions::cancel)
    }
}

@Composable
private fun CatalogueSwitch(item: CatalogueRow, actions: CatalogueActions) {
    val row = item.row
    val facts = row.facts.map { factWords(it) }.joinToString(" · ").ifEmpty { null }
    val supporting = listOfNotNull(facts, row.description).joinToString("\n").ifEmpty { null }
    KitSwitchRow(
        title = row.title,
        checked = row.on,
        supporting = supporting,
        enabled = item.block?.let { KitEnabled.Disabled(blockWords(it)) } ?: KitEnabled.Enabled,
        onCheckedChange = { on -> actions.toggle(row.catalogId, on) },
    )
}

@Composable
private fun factWords(fact: CatalogueFact): String =
    when (fact) {
        is CatalogueFact.HostedBy -> stringResource(Res.string.intaCatalogHostedBy, fact.host)
        is CatalogueFact.NeedsNode ->
            stringResource(if (fact.onPhone) Res.string.intaCatalogNeedsNodePhone else Res.string.intaCatalogNeedsNode)
        CatalogueFact.NeedsPython -> stringResource(Res.string.intaCatalogNeedsPython)
        CatalogueFact.NeedsKey -> stringResource(Res.string.intaCatalogNeedsKey)
        CatalogueFact.NeedsSettings -> stringResource(Res.string.intaCatalogNeedsSettings)
    }

@Composable
private fun blockWords(block: CatalogueBlock): String =
    stringResource(
        when (block) {
            CatalogueBlock.Adding -> Res.string.intaCatalogAdding
            CatalogueBlock.Removing -> Res.string.intaCatalogRemoving
            CatalogueBlock.CannotRemove -> Res.string.intaCatalogCannotRemove
            CatalogueBlock.NeedsDocker -> Res.string.intaCatalogNeedsDocker
            CatalogueBlock.NoEndpoint -> Res.string.intaCatalogNoEndpoint
            CatalogueBlock.NeedsKey -> Res.string.intaCatalogNeedsKeyBlock
            CatalogueBlock.NeedsSettings -> Res.string.intaCatalogNeedsSettingsBlock
        }
    )

@Composable
private fun confirmSpec(ask: CatalogueAsk?): KitConfirmSpec? =
    when (ask) {
        null -> null
        is CatalogueAsk.Add ->
            KitConfirmSpec(
                title = stringResource(Res.string.intaCatalogAddTitle, ask.title),
                confirmLabel = stringResource(Res.string.intaCatalogAddConfirm),
                body = stringResource(Res.string.intaCatalogAddBody),
            )
        is CatalogueAsk.Remove ->
            KitConfirmSpec(
                title = stringResource(Res.string.intaCatalogRemoveTitle, ask.name),
                confirmLabel = stringResource(Res.string.intaCatalogRemoveConfirm),
                kind = KitConfirmKind.Destructive,
                body = stringResource(Res.string.intaCatalogRemoveBody),
            )
    }

@Composable
private fun problemTitle(problem: CatalogueProblem): String? =
    when (problem.kind) {
        CatalogueProblemKind.AddFailed -> stringResource(Res.string.intaCatalogAddFailed, problem.name.orEmpty())
        CatalogueProblemKind.RemoveFailed -> stringResource(Res.string.intaCatalogRemoveFailed, problem.name.orEmpty())
        CatalogueProblemKind.ForgetFailed, CatalogueProblemKind.ConsentNotSaved -> null
    }

@Composable
private fun problemBody(problem: CatalogueProblem): String =
    stringResource(
        when (problem.kind) {
            CatalogueProblemKind.AddFailed, CatalogueProblemKind.RemoveFailed -> Res.string.intaToggleFailedBody
            CatalogueProblemKind.ForgetFailed -> Res.string.intaCatalogForgetFailed
            CatalogueProblemKind.ConsentNotSaved -> Res.string.intaCatalogConsentNotSaved
        }
    )

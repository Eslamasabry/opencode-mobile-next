package io.github.eslamasabry.oc.feature.settings.ui

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import io.github.eslamasabry.oc.engine.setupassist.SetupRegistryStatus
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.ui.kit.*
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.state.*
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LibraryRegistrySheet(state: LibraryUi, actions: LibraryActions) {
    val registry = state.registry
    val search = rememberTextFieldState()

    LaunchedEffect(search) {
        snapshotFlow { search.text.toString() }.collect(actions::registrySearch)
    }
    KitSheet(
        true,
        { actions.show(null) },
        stringResource(Res.string.nlibRegistry),
        height = KitSheetHeight.Full,
        primary =
            if (registry?.optedIn == true) null
            else KitAction(stringResource(Res.string.nlibRegistryLoad), actions::registryEnable),
        tertiary =
            buildList {
                add(
                    KitAction(
                        stringResource(Res.string.nlibRegistryManual),
                        { actions.show(LibrarySheet.AddMcp()) },
                    )
                )
                if (registry?.optedIn == true) {
                    add(
                        KitAction(
                            stringResource(Res.string.nlibRefresh),
                            { actions.registryRefresh(search.text.toString()) },
                        )
                    )
                    add(
                        KitAction(
                            stringResource(Res.string.nlibRegistryForget),
                            actions::registryForget,
                        )
                    )
                }
            },
    ) {
        if (
            state.notice ==
                io.github.eslamasabry.oc.engine.integrations.catalog.LibraryNotice.Storage
        )
            KitNotice(stringResource(Res.string.nlibRegistrySaveFailed))
        if (registry == null) KitSkeletonRows()
        else if (!registry.optedIn) KitText(stringResource(Res.string.nlibRegistryConsent))
        else {
            KitField(search, stringResource(Res.string.nlibSearch))
            if (registry.status == SetupRegistryStatus.Loading) KitSkeletonRows()
            if (registry.status in listOf(SetupRegistryStatus.Error, SetupRegistryStatus.Offline)) {
                KitNotice(stringResource(Res.string.nlibRegistryFailed))
                KitButton(
                    stringResource(Res.string.nlibRefresh),
                    { actions.registryRefresh(search.text.toString()) },
                    role = KitButtonRole.Secondary,
                )
            }
            val inventoryFailed = "mcp" in state.inventory.failedSections
            if (inventoryFailed) {
                KitNotice(stringResource(Res.string.nlibLoadFailed))
                KitButton(
                    stringResource(Res.string.nlibRefresh),
                    actions::refresh,
                    role = KitButtonRole.Secondary,
                )
            }
            val installed = state.inventory.servers.associateBy { it.name }
            val rows =
                registry.entries
                    .map(::libraryRegistryItem)
                    .withIndex()
                    .sortedWith(
                        compareBy<IndexedValue<LibraryRegistryItem>> {
                                when {
                                    it.value.name in installed -> 0
                                    it.value.draft != null -> 1
                                    else -> 2
                                }
                            }
                            .thenBy { it.index }
                    )
                    .map { it.value }
            if (rows.isEmpty() && registry.status != SetupRegistryStatus.Loading)
                KitStateView(KitStateKind.Calm, stringResource(Res.string.nlibNoMatch))
            KitRowGroup {
                rows.forEach { item ->
                    val existing = installed[item.name]
                    val supported = item.draft != null && "mcp" !in state.inventory.failedSections
                    KitRow(
                        item.entry.title.ifBlank { item.name },
                        supporting =
                            (item.entry.description.ifBlank {
                                stringResource(Res.string.nlibRegistryManual)
                            }) +
                                (if (item.needsSettings)
                                    " · " + stringResource(Res.string.nlibRegistryNeedsSettings)
                                else ""),
                        supportingMaxLines = 3,
                        enabled = !inventoryFailed && (existing != null || supported),
                        disabledReason =
                            if (inventoryFailed) stringResource(Res.string.nlibLoadFailed)
                            else if (existing != null || supported) null
                            else stringResource(Res.string.nlibRegistryUnsupported),
                        trailing = KitRowTrailing.Chevron,
                        onClick = {
                            if (existing != null) actions.show(LibrarySheet.Mcp(existing))
                            else if (item.node && state.onPhone)
                                item.draft?.let { actions.show(LibrarySheet.RegistryNode(it)) }
                            else item.draft?.let { actions.show(LibrarySheet.AddMcp(it)) }
                        },
                    )
                }
            }
        }
    }
}

package io.github.eslamasabry.oc.feature.about.managespace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarClose
import io.github.eslamasabry.oc.ui.words.res.manageSpaceCacheCleared
import io.github.eslamasabry.oc.ui.words.res.manageSpaceCacheFailed
import io.github.eslamasabry.oc.ui.words.res.manageSpaceClearCache
import io.github.eslamasabry.oc.ui.words.res.manageSpaceClearCacheDetail
import io.github.eslamasabry.oc.ui.words.res.manageSpaceClearCacheKeeps
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteAll
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteAllDetail
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteBody
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeleteTitle
import io.github.eslamasabry.oc.ui.words.res.manageSpaceDeletedLabel
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptAll
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptComputers
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptGit
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptLabel
import io.github.eslamasabry.oc.ui.words.res.manageSpaceKeptTermux
import io.github.eslamasabry.oc.ui.words.res.manageSpaceIntroNothingToExport
import io.github.eslamasabry.oc.ui.words.res.manageSpaceLostServer
import io.github.eslamasabry.oc.ui.words.res.manageSpaceLostSettings
import io.github.eslamasabry.oc.ui.words.res.manageSpaceLostSettings__eq0
import io.github.eslamasabry.oc.ui.words.res.manageSpaceMeasuring
import io.github.eslamasabry.oc.ui.words.res.manageSpaceSavedServers
import io.github.eslamasabry.oc.ui.words.res.manageSpaceSavedServers__eq0
import io.github.eslamasabry.oc.ui.words.res.manageSpaceServer
import io.github.eslamasabry.oc.ui.words.res.manageSpaceServerDetail
import io.github.eslamasabry.oc.ui.words.res.manageSpaceSettings
import io.github.eslamasabry.oc.ui.words.res.manageSpaceTitle
import io.github.eslamasabry.oc.ui.words.res.manageSpaceTryAgain
import io.github.eslamasabry.oc.ui.words.res.manageSpaceWaitForExport
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › Apps › OpenCode Mobile › Clear storage (Flutter `ManageSpaceScreen`): the intro, Clear the app's cache
 * only, Delete everything (confirmed, with what is lost and what stays), and the two lists of what clearing deletes and
 * what stays. Kit parts only.
 */
@Composable
public fun ManageSpaceScreen(state: ManageSpaceUi, actions: ManageSpaceActions) {
    val gutter = LocalKitTokens.current.gutter.dp
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.manageSpaceTitle),
                exit = KitTopBarExit.Close,
                exitLabel = stringResource(Res.string.kitTopBarClose),
                onExit = actions::onClose,
            )
        },
        loading = state.facts == null,
        loadingLabel = stringResource(Res.string.manageSpaceMeasuring),
        width = KitScreenWidth.Reading,
    ) {
        val facts = state.facts
        if (facts == null) return@KitScreen
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitText(stringResource(Res.string.manageSpaceIntroNothingToExport), Modifier.padding(horizontal = gutter))
            state.notice?.let { notice ->
                when (notice) {
                    is CacheNotice.Cleared ->
                        KitNotice(
                            stringResource(Res.string.manageSpaceCacheCleared, formatPhoneStorage(notice.freedBytes)),
                            Modifier.padding(horizontal = gutter),
                            tone = StatusTone.Ok,
                        )
                    CacheNotice.Failed ->
                        KitNotice(
                            stringResource(Res.string.manageSpaceCacheFailed),
                            Modifier.padding(horizontal = gutter),
                            tone = StatusTone.Failure,
                            actions = listOf(KitLineAction(stringResource(Res.string.manageSpaceTryAgain), actions::onClearCache)),
                        )
                }
            }
            val wait = stringResource(Res.string.manageSpaceWaitForExport)
            KitRowGroup {
                val cache = facts.cacheBytes
                KitRow(
                    title = stringResource(Res.string.manageSpaceClearCache),
                    supporting =
                        if (cache != null && cache > 0) stringResource(Res.string.manageSpaceClearCacheDetail, formatPhoneStorage(cache))
                        else stringResource(Res.string.manageSpaceClearCacheKeeps),
                    supportingMaxLines = 2,
                    enabled = !state.busy,
                    disabledReason = wait,
                    onClick = actions::onClearCache,
                )
                KitRow(
                    title = stringResource(Res.string.manageSpaceDeleteAll),
                    supporting = stringResource(Res.string.manageSpaceDeleteAllDetail),
                    supportingMaxLines = 2,
                    enabled = !state.busy,
                    disabledReason = wait,
                    onClick = actions::onDeleteAll,
                )
            }
            KitRowGroup(label = stringResource(Res.string.manageSpaceDeletedLabel)) {
                if (facts.phoneServer) {
                    KitRow(
                        title = stringResource(Res.string.manageSpaceServer),
                        supporting = stringResource(Res.string.manageSpaceServerDetail),
                        supportingMaxLines = 2,
                    )
                }
                KitRow(
                    title = stringResource(Res.string.manageSpaceSettings),
                    supporting =
                        if (facts.savedServers == 0) stringResource(Res.string.manageSpaceSavedServers__eq0)
                        else pluralStringResource(Res.plurals.manageSpaceSavedServers, facts.savedServers, facts.savedServers),
                )
            }
            KitRowGroup(label = stringResource(Res.string.manageSpaceKeptLabel)) {
                KitRow(title = stringResource(Res.string.manageSpaceKeptTermux))
                KitRow(title = stringResource(Res.string.manageSpaceKeptComputers))
                KitRow(title = stringResource(Res.string.manageSpaceKeptGit))
            }
        }
        val consequences =
            listOfNotNull(
                if (facts.phoneServer) stringResource(Res.string.manageSpaceLostServer) else null,
                if (facts.savedServers == 0) stringResource(Res.string.manageSpaceLostSettings__eq0)
                else pluralStringResource(Res.plurals.manageSpaceLostSettings, facts.savedServers, facts.savedServers),
                stringResource(Res.string.manageSpaceKeptAll),
            )
        KitConfirm(
            spec =
                if (state.confirmingDelete) {
                    KitConfirmSpec(
                        title = stringResource(Res.string.manageSpaceDeleteTitle),
                        confirmLabel = stringResource(Res.string.manageSpaceDeleteAll),
                        kind = KitConfirmKind.Destructive,
                        body = stringResource(Res.string.manageSpaceDeleteBody),
                        consequences = consequences,
                    )
                } else {
                    null
                },
            onConfirm = actions::onConfirmDelete,
            onCancel = actions::onCancelDelete,
        )
    }
}

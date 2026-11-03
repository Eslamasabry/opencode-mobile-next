package io.github.eslamasabry.oc.feature.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.feature.about.presentation.NoticesActions
import io.github.eslamasabry.oc.feature.about.presentation.NoticesPhase
import io.github.eslamasabry.oc.feature.about.presentation.NoticesUi
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitTokens
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.aboutBundledComponents
import io.github.eslamasabry.oc.ui.words.res.e7SettingsDetailUi19
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.kitTryAgain
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › About › Open source notices (Flutter About's notices viewer): the build notice ("About this build":
 * provenance and desktop limits, no release-channel claim), then the bundled components' notices without the
 * maintainer intro or the regeneration steps (B-NAV-001). The notices are Markdown source shown as reading text: a
 * link in them never opens without the link policy.
 */
@Composable
public fun NoticesScreen(state: NoticesUi, actions: NoticesActions) {
    val gutter = LocalKitTokens.current.gutter.dp
    KitScreen(
        topBar = {
            KitTopBar(
                title = AboutPendingWords.noticesTitle.text(),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::onBack,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Space.S3.dp),
            verticalArrangement = Arrangement.spacedBy(Space.S3.dp),
        ) {
            KitSectionLabel(stringResource(Res.string.e7SettingsDetailUi19))
            KitText(provenanceText(state), Modifier.padding(horizontal = gutter))
            KitSectionLabel(stringResource(Res.string.aboutBundledComponents))
            when (state.phase) {
                NoticesPhase.Loading -> KitSkeletonRows()
                NoticesPhase.Failed ->
                    KitStateView(
                        kind = KitStateKind.Error,
                        title = AboutPendingWords.noticesFailed.text(),
                        onRetry = actions::onRetry,
                        retryLabel = stringResource(Res.string.kitTryAgain),
                    )
                NoticesPhase.Ready -> KitText(state.text, Modifier.padding(horizontal = gutter))
            }
        }
    }
}

@Composable
private fun provenanceText(state: NoticesUi): String = buildString {
    state.provenance.forEachIndexed { i, line ->
        if (i > 0) append(' ')
        append(AboutPendingWords.provenance(line).text())
    }
}

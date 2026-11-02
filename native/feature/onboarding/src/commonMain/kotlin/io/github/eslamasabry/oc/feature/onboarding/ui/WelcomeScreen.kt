package io.github.eslamasabry.oc.feature.onboarding.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.appTitle
import io.github.eslamasabry.oc.ui.words.res.e7LocaleUiSettings
import io.github.eslamasabry.oc.ui.words.res.welcomeComputerConnect
import org.jetbrains.compose.resources.stringResource

/**
 * The welcome a new device starts on (07 §5 A03): the app's name, one obvious action to connect a server, and
 * Settings (language and look) as the visible second way. Kit parts only.
 */
@Composable
public fun WelcomeScreen(actions: WelcomeActions) {
    KitScreen(width = KitScreenWidth.Reading, page = true) {
        // B15 (QA wave 1): the buttons keep the screen's side margin instead of touching its edges.
        Box(Modifier.padding(horizontal = Space.S4.dp).testTag("welcome-body")) {
            KitStateView(
                kind = KitStateKind.Calm,
                title = stringResource(Res.string.appTitle),
                primary = KitAction(stringResource(Res.string.welcomeComputerConnect), actions::connect),
                secondary = KitAction(stringResource(Res.string.e7LocaleUiSettings), actions::openSettings),
            )
        }
    }
}

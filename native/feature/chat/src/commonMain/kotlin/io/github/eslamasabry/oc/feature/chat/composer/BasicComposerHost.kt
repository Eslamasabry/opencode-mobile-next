package io.github.eslamasabry.oc.feature.chat.composer

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerActions
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerUi
import io.github.eslamasabry.oc.feature.chat.composer.presentation.BasicComposerViewModel
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitComposerAddToTurn
import io.github.eslamasabry.oc.ui.words.res.kitComposerField
import io.github.eslamasabry.oc.ui.words.res.kitComposerSend
import io.github.eslamasabry.oc.ui.words.res.kitComposerSendAfter
import io.github.eslamasabry.oc.ui.words.res.kitComposerSending
import io.github.eslamasabry.oc.ui.words.res.kitTurnLiveStopShort
import io.github.eslamasabry.oc.ui.words.res.phoneServerCardStopping
import org.jetbrains.compose.resources.stringResource

/**
 * The slice-2 composer (plan 09 §6.3): the field, Send, and Stop in the same row while a reply runs. No drafts,
 * model chip or tray yet (S3's `FullComposerHost` replaces this binding in the same merge that deletes it).
 */
@Inject
@ContributesBinding(AppScope::class)
public class BasicComposerHost : ComposerHost {
    @Composable
    override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
        val vm =
            assistedMetroViewModel<BasicComposerViewModel, BasicComposerViewModel.Factory>(key = "composer:${target.profileId}:${target.sessionId}") {
                create(target)
            }
        val state by vm.state.collectAsStateWithLifecycle()
        BasicComposer(state, vm, chrome, modifier)
    }
}

/** The field and its controls, from kit parts. Tags are the Flutter keys (plan 09 §15.5). */
@Composable
public fun BasicComposer(state: BasicComposerUi, actions: BasicComposerActions, chrome: ComposerChrome, modifier: Modifier = Modifier) {
    val field = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(field) { snapshotFlow { field.text.toString() }.collect(actions::draftChanged) }
    // "Ready to type" (07 §2.5, B-NAV-005): focus once per arrival, then acknowledge it.
    val focusId = chrome.focusRequest()
    LaunchedEffect(focusId) {
        if (focusId != null) {
            focus.requestFocus()
            chrome.focusDone(focusId)
        }
    }
    val busy = chrome.busy()
    val send = {
        if (actions.send(field.text.toString())) {
            field.edit { replace(0, length, "") }
            chrome.onSent()
        }
    }
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.S2.dp)) {
        KitField(
            state = field,
            label = stringResource(Res.string.kitComposerField),
            kind = KitFieldKind.Multiline,
            focusRequester = focus,
            modifier = Modifier.weight(1f).testTag("chat-composer-field"),
        )
        if (busy) {
            KitButton(
                label = stringResource(if (chrome.stopping()) Res.string.phoneServerCardStopping else Res.string.kitTurnLiveStopShort),
                onClick = chrome.onStop,
                role = KitButtonRole.Secondary,
                working = chrome.stopping(),
                modifier = Modifier.testTag("chat-stop-button"),
            )
        }
        val label =
            when {
                state.sending -> Res.string.kitComposerSending
                busy && state.steers -> Res.string.kitComposerAddToTurn
                busy -> Res.string.kitComposerSendAfter
                else -> Res.string.kitComposerSend
            }
        KitButton(
            label = stringResource(label),
            onClick = send,
            enabled = if (state.available) KitEnabled.Enabled else KitEnabled.Disabled(stringResource(Res.string.kitComposerField)),
            working = state.sending,
            haptic = KitHaptic.Send,
            modifier = Modifier.testTag("chat-send-button"),
        )
    }
}

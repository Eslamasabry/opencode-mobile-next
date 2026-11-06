package io.github.eslamasabry.oc.feature.chat.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingIds
import io.github.eslamasabry.oc.engine.settings.teaching.TeachingSource
import io.github.eslamasabry.oc.engine.settings.teaching.TipController
import io.github.eslamasabry.oc.engine.settings.teaching.TipMoment
import io.github.eslamasabry.oc.engine.settings.teaching.TipVisit
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.tipMessageDetails
import org.jetbrains.compose.resources.stringResource

/** The same controller as Help › Tips; one visit and one app-wide teaching slot. */
internal data class MessageTeaching(val controller: TipController)

internal val LocalMessageTeaching = compositionLocalOf<MessageTeaching?> { null }

@Composable
internal fun MessageTeachingScope(
    source: TeachingSource,
    eligible: Boolean,
    blocked: Boolean,
    content: @Composable () -> Unit,
) {
    var controller by remember(source) { mutableStateOf<TipController?>(null) }
    val visit = remember { TipVisit() }
    val keyboard = kitKeyboardVisible()
    LaunchedEffect(source) { controller = source.controller() }
    LaunchedEffect(controller, eligible, blocked, keyboard) {
        val tips = controller ?: return@LaunchedEffect
        if (!eligible || blocked || keyboard) {
            tips.dismiss(TeachingIds.MessageDetails)
        } else {
            tips.offer(
                TeachingIds.MessageDetails,
                visit = visit,
                maxCount = 2,
                moment = TipMoment(questionWaiting = blocked, keyboardOverAnchor = keyboard),
            )
        }
    }
    DisposableEffect(controller) {
        onDispose { controller?.dismiss(TeachingIds.MessageDetails) }
    }
    CompositionLocalProvider(
        LocalMessageTeaching provides controller?.let { MessageTeaching(it) },
        content = content,
    )
}

/** Only the newest completed reply anchors the contextual message-actions tip. */
@Composable
internal fun MessageDetailsTip(eligible: Boolean, content: @Composable () -> Unit) {
    val teaching = LocalMessageTeaching.current
    if (teaching == null || !eligible) {
        content()
        return
    }
    val active by teaching.controller.active.collectAsStateWithLifecycle()
    KitTip(
        message = stringResource(Res.string.tipMessageDetails),
        visible = active?.id == TeachingIds.MessageDetails,
        onDismissed = { teaching.controller.dismiss(TeachingIds.MessageDetails) },
        anchor = content,
    )
}

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
internal data class MessageTeaching(val controller: TipController, val visit: TipVisit = TipVisit())

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
        LocalMessageTeaching provides controller?.let { MessageTeaching(it, visit) },
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

/**
 * S28 story 15 (Flutter `chat_schedule_send.dart` `_offerSendTip`, `chat_stop_after_step.dart` `_queueStopTip`): when
 * the hidden holds on Send and Stop are worth teaching. Send: words wait in the box, no reply runs (Send is Stop
 * then) and Send offers "Send later or repeat". Stop: a reply has run for [STOP_TIP_AFTER_MS] while Stop offers
 * "Stop after this step". The visible ways stay: Send later in the tools list, Stop after this step in Stop's menu.
 */
internal object ComposerTipRules {
    const val STOP_TIP_AFTER_MS: Long = 10_000

    fun sendTipDue(typed: Boolean, busy: Boolean, canSendLater: Boolean): Boolean = typed && !busy && canSendLater

    /** The wait itself runs in [OfferComposerTip] (`afterMs`): the moment holds only if this stays true for it. */
    fun stopTipDue(busy: Boolean, canStopAfterStep: Boolean): Boolean = busy && canStopAfterStep
}

/** Offers [id] while [due] holds (after [afterMs]), on the chat's one visit; it leaves when [due] stops holding. */
@Composable
internal fun OfferComposerTip(id: io.github.eslamasabry.oc.engine.settings.teaching.TipId, due: Boolean, blocked: Boolean, afterMs: Long = 0) {
    val teaching = LocalMessageTeaching.current ?: return
    LaunchedEffect(teaching, id, due, blocked) {
        if (!due || blocked) {
            if (teaching.controller.isActive(id)) teaching.controller.dismiss(id)
            return@LaunchedEffect
        }
        if (afterMs > 0) kotlinx.coroutines.delay(afterMs)
        val entry = io.github.eslamasabry.oc.engine.settings.teaching.TeachingRegistry.defaults.entry(id)
        teaching.controller.offer(
            id,
            visit = teaching.visit,
            maxCount = entry?.maxCount ?: TipController.DEFAULT_MAX_COUNT,
            priority = entry?.priority ?: 0,
            moment = TipMoment(questionWaiting = blocked),
        )
    }
}

/** The tip [message] over [content] while [id] is the tip on screen; used marks it learnt. */
@Composable
internal fun ComposerTip(
    id: io.github.eslamasabry.oc.engine.settings.teaching.TipId,
    message: String,
    content: @Composable () -> Unit,
) {
    val teaching = LocalMessageTeaching.current
    if (teaching == null) {
        content()
        return
    }
    val active by teaching.controller.active.collectAsStateWithLifecycle()
    KitTip(
        message = message,
        visible = active?.id == id,
        onDismissed = { teaching.controller.dismiss(id) },
        anchor = content,
    )
}

/** The person used what [id] teaches: it never shows again. */
@Composable
internal fun rememberTipUsed(id: io.github.eslamasabry.oc.engine.settings.teaching.TipId): () -> Unit {
    val teaching = LocalMessageTeaching.current
    return remember(teaching, id) { { teaching?.controller?.markUsed(id) } }
}

package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible
import io.github.eslamasabry.oc.ui.kit.lamp.KitLamp
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.shell.ShellPageLight
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.lampLabel
import io.github.eslamasabry.oc.ui.words.res.lampLabelWith
import io.github.eslamasabry.oc.ui.words.res.lampSpokenFailed
import io.github.eslamasabry.oc.ui.words.res.lampSpokenJoin
import io.github.eslamasabry.oc.ui.words.res.lampSpokenNeeds
import io.github.eslamasabry.oc.ui.words.res.lampSpokenWorking
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The edge light on a page of a conversation (plan v3.1 slice S1a, phase A). [page] is the conversation the page
 * belongs to (it gets no mark); [top] and [bottom] are the page's own chrome (the light sits between them); [lowAbove]
 * is how far above [bottom] the Low position is.
 *
 * It sits on the physical edge of the window (no inset on its edge side), at the saved side and height, and takes only
 * taps: no gesture exclusion is registered, so a sideways swipe from the edge is the system's Back, and a vertical drag
 * that starts on it is shared with the page behind (see [sharePointerInputWithSiblings]).
 *
 * TODO(phase B): the tap opens the real panel; phase A opens the existing All conversations page as a placeholder.
 * TODO(KC1): Low is the page's bottom chrome plus a token until the message box publishes its top as a kit value.
 * TODO(phase B/K1 layer): hide under a long message that reaches it, and under the Glass look while a request card
 *   waits at the bottom; those need hooks from the chat and the kit's cover sink, which do not exist yet.
 */
@Composable
internal fun EdgeLightMount(
    port: AppEdgeDockPort,
    page: ConversationRef?,
    top: Dp,
    bottom: Dp,
    lowAbove: Dp,
    modifier: Modifier = Modifier,
) {
    val list by port.lightList.collectAsStateWithLifecycle()
    val place by port.lightPlace.collectAsStateWithLifecycle()
    val face = remember(list, page) { EdgeLightRules.face(list, page) }
    val counts = remember(list, page) { EdgeLightRules.counts(list, page) }
    val label = edgeLightLabel(counts)
    val keyboardUp = kitKeyboardVisible()
    BoxWithConstraints(modifier.fillMaxSize().padding(top = top, bottom = bottom)) {
        val offset = EdgeLightRules.bottomOffsetDp(place.height, maxHeight.value, lowAbove.value).dp
        KitLamp(
            face = face,
            label = label,
            onClick = port::openAllConversations,
            modifier =
                Modifier.align(if (place.side == KitLampSide.Start) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(bottom = offset)
                    .sharePointerInputWithSiblings(),
            side = place.side,
            visible = EdgeLightRules.visible(keyboardUp),
        )
    }
}

/** "Other conversations, 1 needs you, 1 failed, 3 working"; "Other conversations" when nothing is going on. */
@Composable
private fun edgeLightLabel(counts: EdgeLightCounts): String {
    val parts = buildList {
        if (counts.needs > 0) add(pluralStringResource(Res.plurals.lampSpokenNeeds, counts.needs, counts.needs))
        if (counts.failed > 0) add(pluralStringResource(Res.plurals.lampSpokenFailed, counts.failed, counts.failed))
        if (counts.working > 0) add(pluralStringResource(Res.plurals.lampSpokenWorking, counts.working, counts.working))
    }
    return if (parts.isEmpty()) stringResource(Res.string.lampLabel)
    else stringResource(Res.string.lampLabelWith, parts.joinToString(stringResource(Res.string.lampSpokenJoin)))
}

/**
 * The light on a conversation's Files, Changes and Git pages: the shell hosts it above the page (the conversation's own
 * page mounts it inside its frame, see [AppChatEdgeDock]). Not on Home, Terminal, Preview or Settings. The rail
 * appears only on Home and Settings, never on these pages, so a wide window changes nothing here: the light sits on
 * the window's edge.
 *
 * TODO(K1 layer): this overlay is above the page's own sheets and menus, which the conversation's mount is not;
 * until the kit's cover sink exists the light stays on top of a Files sheet's scrim.
 */
@ContributesIntoSet(AppScope::class, binding = binding<ShellPageLight>())
@Inject
class AppEdgeLightPages(private val port: AppEdgeDockPort) : ShellPageLight {
    @Composable
    override fun Content(top: AppRoute?) {
        val mode by port.mode.collectAsStateWithLifecycle()
        val page = EdgeLightRules.shellPageConversation(top)
        if (!EdgeLightRules.mounts(mode).light || page == null) return
        EdgeLightMount(
            port = port,
            page = page,
            top = 0.dp,
            bottom = 0.dp,
            lowAbove = EdgeLightRules.LOW_ABOVE_SAFE_DP.dp,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)),
        )
    }
}

/**
 * Lets a pointer that lands on the light also reach the siblings drawn behind it. By default a node that takes a
 * pointer hides everything behind it from that pointer, which would make a vertical drag that starts on the light dead.
 * The node itself reads nothing and consumes nothing; the light's own click still decides a tap.
 */
internal fun Modifier.sharePointerInputWithSiblings(): Modifier = this.then(ShareWithSiblingsElement)

private object ShareWithSiblingsElement : ModifierNodeElement<ShareWithSiblingsNode>() {
    override fun create(): ShareWithSiblingsNode = ShareWithSiblingsNode()

    override fun update(node: ShareWithSiblingsNode) = Unit

    override fun hashCode(): Int = 17

    override fun equals(other: Any?): Boolean = other === this
}

private class ShareWithSiblingsNode : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) = Unit

    override fun onCancelPointerInput() = Unit

    override fun sharePointerInputWithSiblings(): Boolean = true
}

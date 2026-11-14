package io.github.eslamasabry.opencode_mobile.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalView
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
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampFace
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampMarkKind
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPlacePicker
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPlaceWords
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.shell.ShellPageLight
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.edgeLightFaceClose
import io.github.eslamasabry.oc.ui.words.res.lampLabel
import io.github.eslamasabry.oc.ui.words.res.lampLabelWith
import io.github.eslamasabry.oc.ui.words.res.lampSpokenFailed
import io.github.eslamasabry.oc.ui.words.res.lampSpokenJoin
import io.github.eslamasabry.oc.ui.words.res.lampSpokenNeeds
import io.github.eslamasabry.oc.ui.words.res.lampSpokenWorking
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The edge light on a page of a conversation (plan v3.1 slices S1a and S1b). [page] is the conversation the page
 * belongs to (it gets no mark); [top] and [bottom] are the page's own chrome (the light sits between them); [lowAbove]
 * is how far above [bottom] the Low position is.
 *
 * It sits on the physical edge of the window (no inset on its edge side), at the saved side and height, and takes only
 * taps: no gesture exclusion is registered, so a sideways swipe from the edge is the system's Back, and a vertical drag
 * that starts on it is shared with the page behind (see [sharePointerInputWithSiblings]). A tap opens the panel
 * ([EdgeLightPanelLayer]) out of the light; the light becomes its close tab.
 *
 * TODO(KC1): Low is the page's bottom chrome plus a token until the message box publishes its top as a kit value.
 * TODO(chat): hide under a long message that reaches it, and under the Glass look while a request card waits at the
 *   bottom; those need hooks from the chat and the kit's cover sink, which do not exist yet.
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
    val scope = rememberCoroutineScope()
    val controller = remember(port) { port.lampController(scope) }
    var open by remember { mutableStateOf(false) }
    var closedJustNow by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var placing by remember { mutableStateOf(false) }
    // A tap on the open light reaches the scrim behind it too (the light shares its pointer): one close, never a reopen.
    LaunchedEffect(closedJustNow) {
        if (closedJustNow) {
            withFrameNanos { }
            withFrameNanos { }
            closedJustNow = false
        }
    }
    // The keyboard comes up: the panel goes with the light.
    LaunchedEffect(keyboardUp) { if (keyboardUp) open = false }
    AnnounceNewNeeds(face, label, open)
    Box(modifier.fillMaxSize()) {
        EdgeLightPanelLayer(
            port = port,
            controller = controller,
            open = open,
            side = place.side,
            anchor = { anchor },
            top = top,
            bottom = bottom,
            onClose = {
                open = false
                closedJustNow = true
            },
            onMove = {
                open = false
                placing = true
            },
        )
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = top, bottom = bottom)) {
            val offset = EdgeLightRules.bottomOffsetDp(place.height, maxHeight.value, lowAbove.value).dp
            KitLamp(
                face = face.copy(open = open),
                label = if (open) stringResource(Res.string.edgeLightFaceClose) else label,
                onClick = { if (!closedJustNow) open = !open },
                modifier =
                    Modifier.align(if (place.side == KitLampSide.Start) Alignment.BottomStart else Alignment.BottomEnd)
                        .padding(bottom = offset)
                        .onGloballyPositioned { anchor = it.boundsInWindow() }
                        .sharePointerInputWithSiblings(),
                side = place.side,
                visible = EdgeLightRules.visible(keyboardUp),
            )
        }
        if (placing) {
            val ui by controller.ui.collectAsStateWithLifecycle()
            val t = rememberLampTexts(ui)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                KitLampPlacePicker(
                    place = LampPanelMap.place(ui.place),
                    words =
                        KitLampPlaceWords(
                            title = t[LampWord.PlaceTitle],
                            body = t[LampWord.PlaceBody],
                            left = t[LampWord.Left],
                            right = t[LampWord.Right],
                            high = t[LampWord.High],
                            middle = t[LampWord.Middle],
                            low = t[LampWord.Low],
                            done = t[LampWord.Done],
                        ),
                    onPick = { controller.move(LampPanelMap.place(it)) },
                    onDone = { placing = false },
                )
            }
        }
    }
}

/**
 * TalkBack hears a new "needs you" once: the light's spoken label when a conversation that was not waiting on the
 * person before starts to. Nothing at start, nothing for the same request again, nothing while the panel is open
 * (the panel says it itself).
 */
@Composable
private fun AnnounceNewNeeds(face: KitLampFace, label: String, open: Boolean) {
    val view = LocalView.current
    val needs = face.marks.filter { it.kind == KitLampMarkKind.NeedsYou }.mapTo(HashSet()) { it.key }
    val seen = remember { HashSet<Any>() }
    val started = remember { BooleanArray(1) }
    val latest by rememberUpdatedState(label)
    LaunchedEffect(needs) {
        val fresh = needs.any { it !in seen }
        seen.addAll(needs)
        if (started[0] && fresh && !open) view.announceForAccessibility(latest)
        started[0] = true
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
    else {
        var joined = parts.first()
        for (next in parts.drop(1)) joined = stringResource(Res.string.lampSpokenJoin, joined, next)
        stringResource(Res.string.lampLabelWith, joined)
    }
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

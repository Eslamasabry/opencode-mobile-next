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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.ui.kit.frame.kitKeyboardVisible
import io.github.eslamasabry.oc.ui.kit.modal.KitModalOpen
import io.github.eslamasabry.oc.ui.kit.modal.KitOwnModalLayer
import io.github.eslamasabry.oc.ui.kit.lamp.KitLamp
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampFace
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampMarkKind
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPlaceLayer
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampPlaceWords
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampRules
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.teaching.KitTipPresence
import io.github.eslamasabry.oc.ui.kit.teaching.KitTipPlacement
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.shell.ShellPageLight
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.dockModeEdgeLightHint
import io.github.eslamasabry.oc.ui.words.res.edgeLightFaceClose
import io.github.eslamasabry.oc.ui.words.res.lampLabel
import io.github.eslamasabry.oc.ui.words.res.lampLabelWith
import io.github.eslamasabry.oc.ui.words.res.lampSpokenFailed
import io.github.eslamasabry.oc.ui.words.res.lampSpokenJoin
import io.github.eslamasabry.oc.ui.words.res.lampSpokenNeeds
import io.github.eslamasabry.oc.ui.words.res.lampSpokenWorking
import kotlinx.coroutines.delay
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
    var open by remember { mutableStateOf(false) }
    var closedJustNow by remember { mutableStateOf(false) }
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var placing by remember { mutableStateOf(false) }
    // Tap on open light hits scrim behind it (shared pointer): one close, never a reopen.
    LaunchedEffect(closedJustNow) {
        if (closedJustNow) {
            withFrameNanos { }
            withFrameNanos { }
            closedJustNow = false
        }
    }
    LaunchedEffect(keyboardUp) { if (keyboardUp) open = false }
    val modalOpen = KitModalOpen.isOpen
    LaunchedEffect(modalOpen) { if (modalOpen) open = false }
    val lightVisible = EdgeLightRules.visible(keyboardUp, modalOpen)
    // The touch box is the page gutter plus whatever the cutout or a bar takes on the light's physical side.
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val sideInset =
        with(density) {
            (if (KitLampRules.onLeft(place.side, direction == LayoutDirection.Rtl)) insets.getLeft(density, direction) else insets.getRight(density, direction)).toDp()
        }
    val mountOverlay = rememberOverlayMounted(open, placing)
    val tipState = rememberFirstUseTip(port, lightVisible, open, placing, EdgeLightRules.requestOnPage(list, page))
    AnnounceNewNeeds(face, label, open)
    Box(modifier.fillMaxSize()) {
        // Closed light stays cheap: list controller, state and panel strings mount only when opened or moving.
        if (mountOverlay) {
            EdgeLightOverlay(
                port = port,
                spec = EdgeLightOverlaySpec(open, placing, place.side, { anchor }, top, bottom),
                onClose = {
                    open = false
                    closedJustNow = true
                },
                onMove = {
                    open = false
                    placing = true
                },
                onDonePlacing = { placing = false },
            )
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(top = top)) {
            val offset = EdgeLightRules.bottomFromPageDp(place.height, maxHeight.value, bottom.value, lowAbove.value).dp
            val edgeAlign = if (place.side == KitLampSide.Start) Alignment.BottomStart else Alignment.BottomEnd
            KitTip(
                message = stringResource(Res.string.dockModeEdgeLightHint),
                visible = tipState.value,
                onDismissed = { tipState.value = false },
                modifier = Modifier.align(edgeAlign).padding(bottom = offset),
                placement = KitTipPlacement.BesideEdge,
                yieldsToOthers = true,
            ) {
                KitLamp(
                    face = face.copy(open = open),
                    label = if (open) stringResource(Res.string.edgeLightFaceClose) else label,
                    onClick = {
                        when {
                            placing -> placing = false
                            !closedJustNow -> open = !open
                        }
                    },
                    modifier =
                        Modifier.onGloballyPositioned { anchor = it.boundsInWindow() }
                            .sharePointerInputWithSiblings(),
                    side = place.side,
                    visible = lightVisible,
                    sideInset = sideInset,
                )
            }
        }
    }
}

@Composable
private fun rememberOverlayMounted(open: Boolean, placing: Boolean): Boolean {
    var hadOverlay by remember { mutableStateOf(false) }
    var lingering by remember { mutableStateOf(false) }
    LaunchedEffect(open, placing) {
        if (open || placing) {
            hadOverlay = true
            lingering = true
        } else if (hadOverlay) {
            delay(EdgeLightRules.PANEL_LINGER_MS)
            lingering = false
        }
    }
    return EdgeLightRules.mountOverlay(open, placing, lingering)
}

@Composable
private fun rememberFirstUseTip(
    port: AppEdgeDockPort,
    lightVisible: Boolean,
    open: Boolean,
    placing: Boolean,
    requestOnPage: Boolean,
): androidx.compose.runtime.MutableState<Boolean> {
    val taught by port.lightTaught.collectAsStateWithLifecycle()
    val prefsLoaded by port.lightPrefsLoaded.collectAsStateWithLifecycle()
    val showing = remember { mutableStateOf(false) }
    // Waits for a calm moment (no keyboard, no request card on the page) and then shows; once per mount, so a keyboard
    // that opens later does not bring it back or count it twice.
    val shownOnce = remember { mutableStateOf(false) }
    val canShow = EdgeLightRules.showTip(prefsLoaded, taught.tips, lightVisible, open, placing, requestOnPage, KitTipPresence.others > 0)
    LaunchedEffect(canShow) {
        if (canShow && !showing.value && !shownOnce.value) {
            shownOnce.value = true
            showing.value = true
            port.recordLightTipShown()
        }
    }
    LaunchedEffect(open, placing, lightVisible, requestOnPage) {
        if (open || placing || !lightVisible || requestOnPage) showing.value = false
    }
    return showing
}

private class EdgeLightOverlaySpec(
    val open: Boolean,
    val placing: Boolean,
    val side: KitLampSide,
    val anchor: () -> Rect,
    val top: Dp,
    val bottom: Dp,
)

@Composable
private fun EdgeLightOverlay(
    port: AppEdgeDockPort,
    spec: EdgeLightOverlaySpec,
    onClose: () -> Unit,
    onMove: () -> Unit,
    onDonePlacing: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember(port) { port.lampController(scope) }
    // Start folded on the first frame after lazy mount so KitGrowFromAnchor animates out of the light.
    var panelOpen by remember { mutableStateOf(false) }
    LaunchedEffect(spec.open) { panelOpen = spec.open }
    KitOwnModalLayer {
        EdgeLightPanelLayer(
            port = port,
            controller = controller,
            open = panelOpen,
            side = spec.side,
            anchor = spec.anchor,
            top = spec.top,
            bottom = spec.bottom,
            onClose = {
                // The list opens as a list next time, not still in Edit (device 2026-11-14).
                controller.done()
                onClose()
            },
            onMove = {
                controller.done()
                onMove()
            },
        )
        if (spec.placing) {
            EdgeLightPlaceOverlay(controller = controller, onDone = onDonePlacing)
        }
    }
}

@Composable
private fun EdgeLightPlaceOverlay(
    controller: io.github.eslamasabry.oc.feature.chats.lamp.LampListController,
    onDone: () -> Unit,
) {
    // Move…: the six spots over a dimmed page; the real light is composed after it, so it stays lit above the
    // scrim and moves as a spot is picked. Back or a tap on the dimmed page closes only the picker.
    val placeUi by controller.ui.collectAsStateWithLifecycle()
    val placeWords = rememberLampTexts(placeUi)
    KitLampPlaceLayer(
        visible = true,
        place = LampPanelMap.place(placeUi.place),
        words =
            KitLampPlaceWords(
                title = placeWords[LampWord.PlaceTitle],
                body = placeWords[LampWord.PlaceBody],
                left = placeWords[LampWord.Left],
                right = placeWords[LampWord.Right],
                high = placeWords[LampWord.High],
                middle = placeWords[LampWord.Middle],
                low = placeWords[LampWord.Low],
                done = placeWords[LampWord.Done],
            ),
        onPick = { controller.move(LampPanelMap.place(it)) },
        onDone = onDone,
    )
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
 * This overlay is above the page's own sheets and menus (the conversation's mount is not), so the light stands down
 * while any kit modal part is open ([KitModalOpen]) and comes back when it has closed.
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

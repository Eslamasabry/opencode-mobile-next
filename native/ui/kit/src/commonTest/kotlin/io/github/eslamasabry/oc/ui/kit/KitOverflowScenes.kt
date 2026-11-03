package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.bidi.KitLtrText
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityOffer
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityRow
import io.github.eslamasabry.oc.ui.kit.capability.KitCapabilityState
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableFlowRegistry
import io.github.eslamasabry.oc.ui.kit.capability.KitEnableFlows
import io.github.eslamasabry.oc.ui.kit.capability.KitHost
import io.github.eslamasabry.oc.ui.kit.capability.LocalKitEnableFlows
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceListMulti
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceRow
import io.github.eslamasabry.oc.ui.kit.foundation.KitFrameText
import io.github.eslamasabry.oc.ui.kit.modal.KitAlert
import io.github.eslamasabry.oc.ui.kit.modal.KitBackGuard
import io.github.eslamasabry.oc.ui.kit.modal.KitConsequences
import io.github.eslamasabry.oc.ui.kit.modal.KitMenu
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedRows
import io.github.eslamasabry.oc.ui.kit.motion.KitEntrance
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.kit.motion.KitPresence
import io.github.eslamasabry.oc.ui.kit.motion.KitSpin
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabStrip
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.status.KitStatusContribution
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLineFold
import io.github.eslamasabry.oc.ui.kit.status.KitStatusScope
import io.github.eslamasabry.oc.ui.kit.status.KitStatusSlot
import io.github.eslamasabry.oc.ui.kit.swatch.KitSwatch
import io.github.eslamasabry.oc.ui.kit.swatch.KitSwatchGrid
import io.github.eslamasabry.oc.ui.kit.swatch.KitThemePreview
import io.github.eslamasabry.oc.ui.kit.teaching.KitTip
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/**
 * One scene of the large-text overflow matrix (Flutter `test/kit/kit_overflow_scenes.dart`, B-KIT-060): the kit
 * [parts] it shows, drawn by [content] at the largest text size on a narrow phone. Every public kit part has a scene
 * and every scene names only real parts (`KitOverflowMatrixTest`). Sample copy is long on purpose (tests only).
 */
class KitOverflowScene(val id: String, val parts: Set<String>, val content: @Composable () -> Unit)

private val a = PartAWords.En
private val b = PartBWords.En
private val k = KitbWords.En
private val s = SceneWords.En
private const val LONG = "A long name that keeps going past the edge of a narrow phone at the largest text size"

private fun parts(vararg names: String) = names.toSet()

/** Every scene, in a stable order; ids never repeat. */
val kitOverflowScenes: List<KitOverflowScene> = listOf(
    KitOverflowScene("buttons", parts("KitButton", "KitTappable", "KitText")) { ButtonsScene(s) },
    KitOverflowScene("text-scale", parts("KitText")) { TextScaleScene(s) },
    KitOverflowScene("sheet", parts("KitScreen", "KitSheet")) { SheetScene(s, visible = true, onDismiss = {}) },
    KitOverflowScene("motion", parts("KitAnimatedValue", "KitChevron", "KitReveal", "KitSwapCount", "KitSwap")) { MotionPartsScene(k) },
    KitOverflowScene("tabs", parts("KitTabSwitcher")) { TabsScene(k) },
    KitOverflowScene("search", parts("KitSearchField", "KitSearchNoMatch")) { SearchScene(k) },
    KitOverflowScene("filter-chips", parts("KitChip", "KitFilterChips")) { FilterChipsScene(k) },
    KitOverflowScene("jump-pill", parts("KitJumpPill", "KitJumpPillLayer")) { JumpPillScene(k) },
    KitOverflowScene("tip-bubble", parts("KitTipBubble")) { TipScene(k) },
    KitOverflowScene("refresh", parts("KitRefresh")) { RefreshScene(k) },
    KitOverflowScene("sheet-step", parts("KitSheetStep")) { SheetStepScene(k) },
    KitOverflowScene("side-sheet", parts("KitSideSheet")) { SideSheetScene(k) },
    KitOverflowScene("screen", parts("KitScreen", "KitTopBar")) { ScreenScene(a, status = reconnecting(a), loading = true) },
    KitOverflowScene("rows", parts("KitRow", "KitRowGroup")) { RowsScene(a) },
    KitOverflowScene("status", parts("KitStatusLine")) { StatusScene(a) },
    KitOverflowScene("notices", parts("KitCostNotice", "KitDetailsFold", "KitErrorNotice", "KitNotice", "KitOfferNotice")) { NoticeScene(a) },
    KitOverflowScene("heading", parts("KitPageHeading")) { HeadingScene(a) },
    KitOverflowScene("nav", parts("KitNavRail", "KitSidebar")) { NavScene(a) },
    KitOverflowScene("pane", parts("KitPaneScreen")) { PaneScene(a, selected = true) },
    KitOverflowScene("theme", parts("KitTheme")) { ThemeScene(a) },
    KitOverflowScene("actions", parts("KitActionBlock", "KitIconButton")) { ActionsScene(b) },
    KitOverflowScene("chips", parts("KitChipWrap", "KitNeedsYou")) { ChipsScene(b) },
    KitOverflowScene("fields", parts("KitField")) { FieldsScene(b) },
    KitOverflowScene("choices", parts("KitExpandRow", "KitSwitchRow", "KitSegmented", "KitChoiceList")) { ChoicesScene(b) },
    KitOverflowScene("states", parts("KitLoadingBar", "KitProgressView", "KitReceipt", "KitSkeletonRows", "KitStateView")) { StatesScene(b) },
    KitOverflowScene("sheet-v2", parts("KitSheet")) { SheetV2Scene(b) },
    KitOverflowScene("confirm", parts("KitConfirm", "KitConsequences")) { ConfirmScene(b) },
    KitOverflowScene("input-dialog", parts("KitInputDialog")) { InputDialogScene(b) },
    KitOverflowScene("menu-panel", parts("KitMenuPanel")) { MenuScene(b) },
    KitOverflowScene("undo", parts("KitUndoBar")) { UndoScene(b) },
    // ---- parts the earlier scenes did not show ----
    KitOverflowScene("alert", parts("KitAlert", "KitBackGuard", "KitSheetHost")) {
        KitSheetHost {
            KitBackGuard(enabled = false) {}
            KitAlert(visible = true, title = LONG, onDismissRequest = {}, body = a.notice, primary = KitAction(b.save, {}))
        }
    },
    KitOverflowScene("choice-rows", parts("KitChoiceRow", "KitChoiceListMulti", "KitConsequences")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KitChoiceRow(title = LONG, selected = true, supporting = a.rowSupporting, onClick = {})
            KitChoiceListMulti(
                choices = listOf(KitChoice("a", LONG, a.rowSupporting), KitChoice("b", b.deep)),
                selected = setOf("a"),
                semanticsLabel = b.model,
                onChange = {},
            )
            KitConsequences(listOf(LONG, a.notice))
        }
    },
    KitOverflowScene("motion-wrappers", parts("KitEntrance", "KitPresence", "KitSpin", "KitGrowPage", "KitFrameText", "KitAnimatedRows")) {
        Column {
            KitEntrance { KitText(LONG) }
            KitPresence(visible = true) { KitText(LONG) }
            KitSpin(turns = 0f) { KitText(a.value) }
            KitFrameText { KitText(LONG, kind = KitTextKind.Frame) }
            KitGrowPage(growKey = null) { KitText(LONG) }
            KitAnimatedRows(listOf(LONG, a.rowTitle), key = { it }) { KitText(it) }
        }
    },
    KitOverflowScene("labels", parts("KitSectionLabel", "KitGroupNote", "KitLtrText")) {
        Column {
            KitSectionLabel(LONG)
            KitGroupNote(LONG)
            KitLtrText("/home/me/projects/a-very-long-folder-name/and/a/file_with_a_long_name.kt")
        }
    },
    KitOverflowScene("menu-and-tip", parts("KitMenu", "KitTip", "KitTabStrip")) {
        Column {
            KitMenu(expanded = false, items = listOf(KitMenuItem(LONG, {})), onDismissRequest = {}) { KitButton(b.more, onClick = {}) }
            KitTip(message = k.tip, visible = false, onDismissed = {}) { KitText(LONG) }
            KitTabStrip(listOf(KitTab(LONG, count = 12), KitTab(a.files, needsYou = 1)), 0, {})
        }
    },
    KitOverflowScene("status-slot", parts("KitStatusScope", "KitStatusSlot", "KitStatusContribution", "KitStatusLineFold")) {
        KitStatusScope(conditions = emptyList()) {
            KitStatusSlot(status = null) { shown ->
                KitStatusContribution(reconnecting(a))
                KitStatusLineFold(shown)
            }
        }
    },
    KitOverflowScene("capability", parts("KitCapabilityRow", "KitCapabilityState", "KitCapabilityOffer")) {
        val flows = KitEnableFlowRegistry().apply { KitEnableFlows.all.forEach { register(it) {} } }
        CompositionLocalProvider(LocalKitEnableFlows provides flows) {
            Column {
                KitCapabilityRow("flag:fileBrowsing+terminal", host = KitHost.Codex, serverName = LONG)
                KitCapabilityState("voice.model", cost = listOf(LONG))
                KitCapabilityOffer("team.on", onNotNow = {})
            }
        }
    },
    KitOverflowScene("swatches", parts("KitSwatchGrid", "KitThemePreview")) {
        val roles = LocalKitColors.current
        Column {
            KitSwatchGrid(
                a.valueTitle,
                listOf(KitSwatch.Theme(roles, LONG, true, {}), KitSwatch.Theme(roles, LONG, false, null, disabledReason = LONG)),
            )
            KitSwatchGrid(a.valueTitle, List(8) { KitSwatch.Accent(roles.accent, a.value, it == 0, {}) })
            KitThemePreview(roles, a.value)
        }
    },
)

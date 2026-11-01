package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitFilterChips
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpEdge
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPill
import io.github.eslamasabry.oc.ui.kit.frame.KitJumpPillLayer
import io.github.eslamasabry.oc.ui.kit.frame.kitJumpLatestLabel
import io.github.eslamasabry.oc.ui.kit.input.KitSearchField
import io.github.eslamasabry.oc.ui.kit.input.KitSearchNoMatch
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetStep
import io.github.eslamasabry.oc.ui.kit.modal.KitSideSheet
import io.github.eslamasabry.oc.ui.kit.motion.KitAnimatedValue
import io.github.eslamasabry.oc.ui.kit.motion.KitChevron
import io.github.eslamasabry.oc.ui.kit.motion.KitDimLevel
import io.github.eslamasabry.oc.ui.kit.motion.KitRefresh
import io.github.eslamasabry.oc.ui.kit.motion.KitReveal
import io.github.eslamasabry.oc.ui.kit.motion.KitSwap
import io.github.eslamasabry.oc.ui.kit.motion.KitSwapCount
import io.github.eslamasabry.oc.ui.kit.motion.kitDim
import io.github.eslamasabry.oc.ui.kit.nav.KitTab
import io.github.eslamasabry.oc.ui.kit.nav.KitTabSwitcher
import io.github.eslamasabry.oc.ui.kit.teaching.KitTipBubble
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors

/** The scene words for S2-KITB's scenes (the parts' own words come from :ui:words). */
data class KitbWords(
    val working: String,
    val waiting: String,
    val done: String,
    val searchSettings: String,
    val query: String,
    val models: String,
    val symbols: String,
    val removeSymbols: String,
    val allProjects: String,
    val needsYou: String,
    val running: String,
    val pinned: String,
    val archived: String,
    val earlier: String,
    val tip: String,
    val stepOne: String,
    val stepTwo: String,
    val sideTitle: String,
    val row: String,
    val taps: String,
    val notReady: String,
) {
    companion object {
        val En =
            KitbWords(
                working = "Working", waiting = "Waiting", done = "Done", searchSettings = "Search settings", query = "opus",
                models = "models", symbols = "Symbols", removeSymbols = "Remove filter Symbols", allProjects = "All projects",
                needsYou = "Needs you", running = "Running", pinned = "Pinned", archived = "Archived and older",
                earlier = "Earlier messages", tip = "Hold the mic to keep talking hands-free.", stepOne = "Pick a model",
                stepTwo = "Discard your changes?", sideTitle = "Details", row = "Row", taps = "Taps", notReady = "Connect a server first",
            )
        val Ar =
            KitbWords(
                working = "قيد العمل", waiting = "بانتظار", done = "تم", searchSettings = "ابحث في الإعدادات", query = "opus",
                models = "النماذج", symbols = "الرموز", removeSymbols = "أزل عامل التصفية الرموز", allProjects = "كل المشاريع",
                needsYou = "يحتاجك", running = "قيد التشغيل", pinned = "مثبّتة", archived = "مؤرشفة وأقدم",
                earlier = "رسائل أقدم", tip = "اضغط مطولًا على الميكروفون لتواصل الحديث دون يديك.", stepOne = "اختر نموذجًا",
                stepTwo = "هل تتجاهل تغييراتك؟", sideTitle = "التفاصيل", row = "صف", taps = "نقرات", notReady = "اتصل بخادم أولًا",
            )

        fun of(tag: String): KitbWords = if (tag.startsWith("ar")) Ar else En
    }
}

/** The settled frames of the motion parts (plan 08 §9.4: settled, not mid-motion). */
@Composable
fun MotionPartsScene(w: KitbWords, swapped: Boolean = true, count: Int = 12, expanded: Boolean = true, value: Float = 0.8f) {
    val colors = LocalKitColors.current
    Column(Modifier.testTag("motion").padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KitSwap(if (swapped) w.done else w.working) { KitText(it, role = TypeRole.RowTitle, kind = KitTextKind.Frame) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KitSwapCount(count) { KitText(it.toString(), role = TypeRole.Headline, kind = KitTextKind.Frame) }
            KitChevron(expanded = false)
            KitChevron(expanded = expanded)
            Box(Modifier.size(24.dp).kitDim(level = KitDimLevel.Stale).background(colors.accent))
            Box(Modifier.size(24.dp).kitDim(level = KitDimLevel.Disabled).background(colors.accent))
        }
        KitAnimatedValue(value) { v ->
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.surface3)) {
                Box(Modifier.fillMaxWidth(v).height(4.dp).background(colors.accent))
            }
        }
        KitReveal(visible = expanded) { KitText(w.stepOne, role = TypeRole.Body, tone = TextTone.Secondary) }
    }
}

/** B-KIT-050: three tabs with a count, counts loading, and a needs-you marker; body 0 keeps a saved counter. */
@Composable
fun TabsScene(w: KitbWords, index: Int = 0, onSelected: (Int) -> Unit = {}) {
    val tabs = listOf(KitTab(w.working, count = 3), KitTab(w.waiting, needsYou = 2), KitTab(w.done, count = 12))
    KitTabSwitcher(
        tabs = tabs,
        index = index,
        onSelected = onSelected,
        modifier = Modifier.testTag("tabs").height(240.dp),
        bodies = listOf({ CounterBody(w) }, { KitText(w.waiting + " · " + w.row) }, { KitText(w.done + " · " + w.row) }),
    )
}

@Composable
private fun CounterBody(w: KitbWords) {
    var taps by rememberSaveable { mutableIntStateOf(0) }
    KitButton(label = "${w.taps}: $taps", onClick = { taps++ }, role = KitButtonRole.Secondary)
}

/** B-KIT-030: the field in its states. */
@Composable
fun SearchScene(w: KitbWords, onSearch: (String) -> Unit = {}) {
    Column(Modifier.testTag("search").padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KitSearchField(label = w.searchSettings, onSearch = onSearch)
        KitSearchField(
            label = w.searchSettings,
            onSearch = {},
            state = rememberTextFieldState(w.query),
            resultCount = 12,
            activeFilter = w.symbols,
            removeFilterLabel = w.removeSymbols,
            onClearFilter = {},
        )
        KitSearchField(label = w.searchSettings, onSearch = {}, state = rememberTextFieldState(w.query), resultCount = 4, partial = true)
        KitSearchField(label = w.searchSettings, onSearch = {}, enabled = false, disabledReason = w.notReady)
        KitSearchNoMatch(query = w.query, onClear = {}, what = w.models)
    }
}

/** The filter line, overflowing so the end edge fades. */
@Composable
fun FilterChipsScene(w: KitbWords, selected: Int = 0) {
    Column(Modifier.testTag("chips").padding(vertical = 16.dp)) {
        KitFilterChips {
            listOf(w.allProjects, w.needsYou, w.running, w.pinned, w.archived).forEachIndexed { i, label ->
                KitChip(
                    label = label,
                    selected = i == selected,
                    tone = if (i == 1) KitChipTone.Attention else KitChipTone.Neutral,
                    onClick = {},
                )
            }
        }
    }
}

/** Both pills over a scroll area. */
@Composable
fun JumpPillScene(w: KitbWords, newCount: Int = 3, visible: Boolean = true, onJump: () -> Unit = {}) {
    val colors = LocalKitColors.current
    KitJumpPillLayer(
        modifier = Modifier.testTag("jump").fillMaxWidth().height(320.dp).background(colors.ground),
        pill = { KitJumpPill(kitJumpLatestLabel(newCount), onJump, visible) },
    ) {
        KitJumpPillLayer(Modifier.fillMaxSize(), edge = KitJumpEdge.Top, pill = { KitJumpPill(w.earlier, {}, visible, edge = KitJumpEdge.Top) }) {
            Column(Modifier.padding(16.dp)) { repeat(4) { KitText("${w.row} ${it + 1}") } }
        }
    }
}

/** A tip bubble (its drawing; placement is a popup over the anchor). */
@Composable
fun TipScene(w: KitbWords) {
    Box(Modifier.testTag("tip").padding(16.dp)) { KitTipBubble(w.tip) }
}

/** Pull to refresh, refreshing, as a still frame. */
@Composable
fun RefreshScene(w: KitbWords, refreshing: Boolean = true, onRefresh: () -> Unit = {}) {
    KitRefresh(refreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.testTag("refresh").fillMaxWidth().height(240.dp)) {
        Column(Modifier.fillMaxSize().padding(top = 72.dp, start = 16.dp)) { repeat(3) { KitText("${w.row} ${it + 1}") } }
    }
}

/** A sheet's second view, and the end-side sheet over a page. */
@Composable
fun SheetStepScene(w: KitbWords, second: Boolean = true) {
    Box(Modifier.testTag("sheet-step").padding(16.dp)) {
        KitSheetStep(second) { KitText(if (it) w.stepTwo else w.stepOne, role = TypeRole.Headline, kind = KitTextKind.Frame) }
    }
}

@Composable
fun SideSheetScene(w: KitbWords, visible: Boolean = true, onDismiss: () -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) { repeat(5) { KitText("${w.row} ${it + 1}") } }
        KitSideSheet(visible = visible, onDismissRequest = onDismiss, widthDp = 300) {
            Column(Modifier.padding(16.dp)) { KitText(w.sideTitle, role = TypeRole.Headline, kind = KitTextKind.Frame) }
        }
    }
}

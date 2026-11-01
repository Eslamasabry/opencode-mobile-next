package io.github.eslamasabry.oc.ui.kit

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.action.ActionBlockRules
import io.github.eslamasabry.oc.ui.kit.action.KitActionLayout
import io.github.eslamasabry.oc.ui.kit.choice.ChoiceRules
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.SegmentedRules
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.input.KitFieldRules
import io.github.eslamasabry.oc.ui.kit.modal.ConfirmRules
import io.github.eslamasabry.oc.ui.kit.modal.InputDialogRules
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuEntry
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuGroup
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHeight
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetPlacement
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.modal.MenuLayout
import io.github.eslamasabry.oc.ui.kit.modal.SheetLayout
import io.github.eslamasabry.oc.ui.kit.modal.UndoRules
import io.github.eslamasabry.oc.ui.kit.row.SwitchRules
import io.github.eslamasabry.oc.ui.kit.row.SwitchTap
import io.github.eslamasabry.oc.ui.kit.state.ErrorWay
import io.github.eslamasabry.oc.ui.kit.state.EtaWords
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.ProgressRules
import io.github.eslamasabry.oc.ui.kit.state.ReceiptRules
import io.github.eslamasabry.oc.ui.kit.state.ReceiptWords
import io.github.eslamasabry.oc.ui.kit.state.StateViewRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Logic halves of the part-B catalog rows (plan 06 §17), pure and shared by every target. */
class PartBRulesTest {
    private fun item(label: String, group: KitMenuGroup? = null, destructive: Boolean = false) =
        KitMenuItem(label, onSelect = {}, group = group, destructive = destructive)

    private fun labels(entries: List<KitMenuEntry>): List<String> =
        entries.map {
            when (it) {
                KitMenuEntry.Divider -> "—"
                is KitMenuEntry.Heading -> "#${it.group.heading}"
                is KitMenuEntry.Item -> it.item.label
            }
        }

    // B-KIT-022: menus keep order, put destructive items last after one divider, and lead named groups with a heading.

    @Test
    fun `B-KIT-022 items keep their order and destructive items move last after one divider`() {
        val got = MenuLayout.arrange(listOf(item("Rename"), item("Delete", destructive = true), item("Pin"), item("Share")))
        assertEquals(listOf("Rename", "Pin", "Share", "—", "Delete"), labels(got))
    }

    @Test
    fun `B-KIT-022 all destructive items get no divider`() {
        val got = MenuLayout.arrange(listOf(item("Delete", destructive = true), item("Wipe", destructive = true)))
        assertEquals(listOf("Delete", "Wipe"), labels(got))
    }

    @Test
    fun `B-KIT-022 groups keep the order of their first item and a named group leads with its heading`() {
        val a = KitMenuGroup("a")
        val b = KitMenuGroup("b", heading = "Move to")
        val got = MenuLayout.arrange(listOf(item("Copy", a), item("Paste", a), item("Inbox", b), item("Archive", b)))
        assertEquals(listOf("Copy", "Paste", "—", "#Move to", "Inbox", "Archive"), labels(got))
    }

    @Test
    fun `B-KIT-022 an empty menu arranges to nothing`() {
        assertTrue(MenuLayout.arrange(emptyList()).isEmpty())
    }

    // B-KIT-025: staged progress, known progress, forward only, ETA rounding.

    @Test
    fun `B-KIT-025 staged value is step minus one plus step value over of`() {
        assertEquals(0.5f, ProgressRules.value(KitProgress.Staged(step = 3, of = 5, label = "Installing", stepValue = 0.5f)))
        assertEquals(0.4f, ProgressRules.value(KitProgress.Staged(step = 3, of = 5, label = "Installing")))
        assertNull(ProgressRules.value(KitProgress.Waiting()))
        assertEquals(0.25f, ProgressRules.value(KitProgress.Known(0.25f)))
    }

    @Test
    fun `B-KIT-025 known progress stays between 0 and 1 and a step stays between 1 and of`() {
        assertTrue(ProgressRules.valid(KitProgress.Known(1f)))
        assertFalse(ProgressRules.valid(KitProgress.Known(1.2f)))
        assertFalse(ProgressRules.valid(KitProgress.Staged(step = 0, of = 3, label = "x")))
        assertFalse(ProgressRules.valid(KitProgress.Staged(step = 4, of = 3, label = "x")))
        assertFalse(ProgressRules.valid(KitProgress.Staged(step = 1, of = 3, label = "x", stepValue = -0.1f)))
    }

    @Test
    fun `B-KIT-025 the bar never goes backwards within a job but a new job may start lower`() {
        assertEquals(0.6f, ProgressRules.forward(shown = 0.6f, next = 0.4f, sameJob = true))
        assertEquals(0.4f, ProgressRules.forward(shown = 0.6f, next = 0.4f, sameJob = false))
        assertEquals(0.7f, ProgressRules.forward(shown = 0.6f, next = 0.7f, sameJob = true))
    }

    @Test
    fun `B-KIT-025 the eta rounds up and never promises less than measured`() {
        assertEquals(EtaWords(EtaWords.Unit.Seconds, 10), ProgressRules.eta(1.seconds))
        assertEquals(EtaWords(EtaWords.Unit.Seconds, 50), ProgressRules.eta(41.seconds))
        assertEquals(EtaWords(EtaWords.Unit.Minutes, 2), ProgressRules.eta(61.seconds))
        assertEquals(EtaWords(EtaWords.Unit.Hours, 3), ProgressRules.eta(2.hours + 54.minutes))
        assertNull(ProgressRules.eta(0.seconds))
        assertNull(ProgressRules.eta(null))
    }

    // B-KIT-031: segmented control.

    @Test
    fun `B-KIT-031 segmented needs 2 to 4 segments`() {
        assertFalse(SegmentedRules.validCount(1))
        assertTrue(SegmentedRules.validCount(2))
        assertTrue(SegmentedRules.validCount(4))
        assertFalse(SegmentedRules.validCount(5))
    }

    @Test
    fun `B-KIT-031 a tap on the chosen or a disabled segment does nothing`() {
        val day = KitSegment("day", "Day")
        val week = KitSegment("week", "Week")
        val off = KitSegment("year", "Year", enabled = KitEnabled.Disabled("Needs history"))
        assertNull(SegmentedRules.onTap(day, selected = "day", groupEnabled = true))
        assertEquals("week", SegmentedRules.onTap(week, selected = "day", groupEnabled = true))
        assertNull(SegmentedRules.onTap(off, selected = "day", groupEnabled = true))
        assertNull(SegmentedRules.onTap(week, selected = "day", groupEnabled = false))
    }

    @Test
    fun `B-KIT-031 the row stacks when a word does not fit its share or at 2x text`() {
        assertFalse(SegmentedRules.stacks(listOf(30f, 40f, 35f), availableDp = 360f, paddingDp = 24f, fontScale = 1f))
        assertTrue(SegmentedRules.stacks(listOf(30f, 120f, 35f), availableDp = 360f, paddingDp = 24f, fontScale = 1f))
        assertTrue(SegmentedRules.stacks(listOf(30f, 40f), availableDp = 360f, paddingDp = 24f, fontScale = 2f))
    }

    // B-KIT-015: confirm.

    @Test
    fun `B-KIT-015 the title is a question and the confirm names the act`() {
        assertTrue(ConfirmRules.titleIsQuestion("Delete this server?"))
        assertTrue(ConfirmRules.titleIsQuestion("حذف هذا الخادم؟"))
        assertFalse(ConfirmRules.titleIsQuestion("Delete this server"))
        assertFalse(ConfirmRules.labelNamesTheAct("OK"))
        assertFalse(ConfirmRules.labelNamesTheAct(" yes "))
        assertTrue(ConfirmRules.labelNamesTheAct("Delete server"))
    }

    @Test
    fun `B-KIT-015 a typed name must match exactly and only a neutral question confirms on Enter`() {
        assertFalse(ConfirmRules.confirmEnabled("prod-box", "prod"))
        assertFalse(ConfirmRules.confirmEnabled("prod-box", "Prod-box"))
        assertTrue(ConfirmRules.confirmEnabled("prod-box", "prod-box"))
        assertTrue(ConfirmRules.confirmEnabled(null, ""))
        assertTrue(ConfirmRules.enterConfirms(KitConfirmKind.Neutral))
        assertFalse(ConfirmRules.enterConfirms(KitConfirmKind.Destructive))
        assertEquals(KitHaptic.Commit, ConfirmRules.haptic(KitConfirmKind.Stop))
        assertNull(ConfirmRules.haptic(KitConfirmKind.Neutral))
    }

    // B-KIT-016: dialogs answer null when unchanged.

    @Test
    fun `B-KIT-016 an unchanged or cancelled input dialog answers null`() {
        assertNull(InputDialogRules.result(initial = "Notes", typed = "Notes", submitted = true))
        assertNull(InputDialogRules.result(initial = "Notes", typed = "Ideas", submitted = false))
        assertEquals("Ideas", InputDialogRules.result(initial = "Notes", typed = "Ideas", submitted = true))
    }

    // B-KIT-034: state view escalation and error defaults.

    @Test
    fun `B-KIT-034 a wait escalates at 8 seconds`() {
        assertFalse(StateViewRules.isSlow(7.9.seconds))
        assertTrue(StateViewRules.isSlow(8.seconds))
    }

    @OptIn(RedactorApi::class)
    @Test
    fun `B-KIT-034 an error offers retry and the network fix or a report, and only handlers that exist`() {
        val offline = Failure.Network(NetworkWhy.Offline, details = Redacted("Offline"))
        assertEquals(
            listOf(ErrorWay.TryAgain, ErrorWay.SwitchServer, ErrorWay.CopyDetails),
            StateViewRules.errorWays(offline, canRetry = true, canSwitch = true, canReport = true),
        )
        val disk = Failure.StorageRefused()
        assertEquals(listOf(ErrorWay.TryAgain, ErrorWay.ReportProblem), StateViewRules.errorWays(disk, canRetry = true, canSwitch = true, canReport = true))
        assertEquals(emptyList(), StateViewRules.errorWays(disk, canRetry = false, canSwitch = false, canReport = false))
    }

    @Test
    fun `B-KIT-034 a missing prerequisite without its enable action is refused`() {
        assertFalse(StateViewRules.missingIsValid(prerequisite = true, hasEnable = false))
        assertTrue(StateViewRules.missingIsValid(prerequisite = true, hasEnable = true))
        assertTrue(StateViewRules.missingIsValid(prerequisite = false, hasEnable = false))
    }

    // B-KIT-027: switch rows.

    @Test
    fun `B-KIT-027 a locked switch row refuses false and a risky step asks before turning on`() {
        assertEquals(SwitchTap.Refuse, SwitchRules.onTap(checked = true, enabled = true, locked = true, risky = false))
        assertEquals(SwitchTap.AskRisk, SwitchRules.onTap(checked = false, enabled = true, locked = false, risky = true))
        assertEquals(SwitchTap.Flip, SwitchRules.onTap(checked = true, enabled = true, locked = false, risky = true))
        assertEquals(SwitchTap.Ignore, SwitchRules.onTap(checked = false, enabled = false, locked = false, risky = false))
        assertEquals(KitHaptic.ToggleOn, SwitchRules.haptic(true))
        assertEquals(KitHaptic.ToggleOff, SwitchRules.haptic(false))
    }

    // B-KIT-014: choice lists.

    @Test
    fun `B-KIT-014 a multi tap toggles exactly one value and Current shows only for a pending change`() {
        assertEquals(setOf("a", "c"), ChoiceRules.toggle(setOf("a"), "c"))
        assertEquals(setOf("a"), ChoiceRules.toggle(setOf("a", "c"), "c"))
        assertTrue(ChoiceRules.showsCurrent(value = "a", current = "a", selected = "b"))
        assertFalse(ChoiceRules.showsCurrent(value = "a", current = "a", selected = "a"))
        assertFalse(ChoiceRules.showsCurrent(value = "b", current = "a", selected = "b"))
    }

    // B-KIT-011: action block layout.

    @Test
    fun `B-KIT-011 compact windows and a destructive tertiary stack, extras go into More`() {
        val plain = listOf(KitAction("Copy", {}), KitAction("Share", {}), KitAction("Export", {}))
        assertEquals(KitActionLayout.Stack, ActionBlockRules.layout(412f, plain))
        assertEquals(KitActionLayout.Row, ActionBlockRules.layout(900f, plain))
        assertEquals(KitActionLayout.Stack, ActionBlockRules.layout(900f, plain + KitAction("Delete", {}, destructive = true)))
        val (shown, more) = ActionBlockRules.split(plain)
        assertEquals(listOf("Copy", "Share"), shown.map { it.label })
        assertEquals(listOf("Export"), more.map { it.label })
    }

    @Test
    fun `B-KIT-011 an action runs only with a callback and while enabled`() {
        assertTrue(KitAction("Send", {}).runnable)
        assertFalse(KitAction("Send", null, enabled = KitEnabled.Disabled("Connect first")).runnable)
        assertFalse(KitAction("Send", {}, enabled = KitEnabled.Disabled("Connect first")).runnable)
    }

    // B-KIT-032: sheets adapt to the window and ask before losing unsaved input.

    @Test
    fun `B-KIT-032 a sheet sits at the bottom on phones, centred or at the end on wide windows`() {
        assertEquals(KitSheetPlacement.Bottom, SheetLayout.placement(412f, KitSheetHeight.Full))
        assertEquals(KitSheetPlacement.Bottom, SheetLayout.placement(700f, KitSheetHeight.Content))
        assertEquals(KitSheetPlacement.Centred, SheetLayout.placement(1200f, KitSheetHeight.Content))
        assertEquals(KitSheetPlacement.End, SheetLayout.placement(1200f, KitSheetHeight.Full))
    }

    @Test
    fun `B-KIT-032 unsaved input without a draft asks before closing`() {
        assertTrue(SheetLayout.asksBeforeClosing(dirty = true, draft = false))
        assertFalse(SheetLayout.asksBeforeClosing(dirty = true, draft = true))
        assertFalse(SheetLayout.asksBeforeClosing(dirty = false, draft = false))
    }

    // KitField (G37 input half of B-L10N-012; counter rule).

    @Test
    fun `B-L10N-012 a number field maps Arabic-Indic digits and the Arabic decimal separator to ASCII`() {
        assertEquals("123.45", KitFieldRules.normalizeNumber("١٢٣٫٤٥"))
        assertEquals("789", KitFieldRules.normalizeNumber("۷۸۹"))
        assertEquals("-12.50", KitFieldRules.normalizeNumber("-1a2.5.0"), "letters and a second separator drop out")
    }

    @Test
    fun `KitField the counter shows from 80 percent of the limit`() {
        assertFalse(KitFieldRules.showCounter(79, 100))
        assertTrue(KitFieldRules.showCounter(80, 100))
        assertFalse(KitFieldRules.showCounter(99, null))
    }

    // KitReceipt: "Sent" is never "Done".

    @Test
    fun `KitReceipt sent is never done and turns into not confirmed after 8 seconds`() {
        assertEquals(ReceiptWords.Sent, ReceiptRules.words(KitReceiptState.Sent, unconfirmedTooLong = false))
        assertEquals(ReceiptWords.NotConfirmed, ReceiptRules.words(KitReceiptState.Sent, unconfirmedTooLong = true))
        assertEquals(ReceiptWords.Confirmed, ReceiptRules.words(KitReceiptState.Confirmed, unconfirmedTooLong = true))
        assertEquals(ReceiptWords.AnsweredOn, ReceiptRules.words(KitReceiptState.AnsweredElsewhere("laptop"), false))
        assertEquals(ReceiptWords.Refused, ReceiptRules.words(KitReceiptState.Refused(), false))
    }

    // KitUndoBar: 8 s stretched by accessibility; no timeout under accessible navigation.

    @Test
    fun `KitUndoBar waits 8 seconds stretched by the accessibility timeout and never times out under accessible navigation`() {
        val offer = KitUndoOffer("1", "Chat archived")
        assertEquals(8_000L, UndoRules.timeoutMs(false, offer) { it })
        assertEquals(20_000L, UndoRules.timeoutMs(false, offer) { 20_000L })
        assertNull(UndoRules.timeoutMs(true, offer) { it })
        assertNull(UndoRules.timeoutMs(false, offer.copy(working = true)) { it })
    }
}

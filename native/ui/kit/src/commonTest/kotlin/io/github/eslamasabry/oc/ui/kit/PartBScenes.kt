package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.action.KitActionBlock
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.chip.KitChip
import io.github.eslamasabry.oc.ui.kit.chip.KitChipKind
import io.github.eslamasabry.oc.ui.kit.chip.KitChipTone
import io.github.eslamasabry.oc.ui.kit.chip.KitChipWrap
import io.github.eslamasabry.oc.ui.kit.choice.KitChoice
import io.github.eslamasabry.oc.ui.kit.choice.KitChoiceList
import io.github.eslamasabry.oc.ui.kit.choice.KitSegment
import io.github.eslamasabry.oc.ui.kit.choice.KitSegmented
import io.github.eslamasabry.oc.ui.kit.foundation.KitEnabled
import io.github.eslamasabry.oc.ui.kit.foundation.KitPartGlyphs
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.input.SecretFieldPresence
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.modal.KitInputDialog
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuGroup
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuItem
import io.github.eslamasabry.oc.ui.kit.modal.KitMenuPanel
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoBar
import io.github.eslamasabry.oc.ui.kit.modal.KitUndoOffer
import io.github.eslamasabry.oc.ui.kit.row.KitExpandRow
import io.github.eslamasabry.oc.ui.kit.row.KitSwitchRow
import io.github.eslamasabry.oc.ui.kit.state.KitLoadingBar
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYou
import io.github.eslamasabry.oc.ui.kit.state.KitNeedsYouReason
import io.github.eslamasabry.oc.ui.kit.state.KitProgress
import io.github.eslamasabry.oc.ui.kit.state.KitProgressView
import io.github.eslamasabry.oc.ui.kit.state.KitReceipt
import io.github.eslamasabry.oc.ui.kit.state.KitReceiptState
import io.github.eslamasabry.oc.ui.kit.state.KitSkeletonRows
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import kotlin.time.Duration.Companion.minutes

/** Sample copy for the part-B scenes (tests only; screens take theirs from `:ui:words`). */
class PartBWords(
    val send: String, val attach: String, val cancel: String, val run: String, val reason: String, val delete: String,
    val more: String, val copy: String, val share: String, val export: String,
    val chipModel: String, val chipFile: String, val chipFilter: String, val removeFile: String, val chipQueued: String,
    val name: String, val nameHelp: String, val url: String, val port: String, val token: String, val note: String, val tooLong: String,
    val range: String, val day: String, val week: String, val month: String,
    val model: String, val fast: String, val fastLine: String, val deep: String, val deepLine: String, val local: String, val needsDownload: String,
    val keepRunning: String, val keepLine: String, val allowAll: String, val risk: String, val always: String, val advanced: String, val advancedBody: String,
    val loading: String, val installing: String, val failedTitle: String, val failedBody: String, val retry: String, val switchServer: String,
    val sheetTitle: String, val sheetSub: String, val sheetBody: String, val save: String,
    val confirmTitle: String, val confirmAct: String, val confirmBody: String, val c1: String, val c2: String, val serverName: String,
    val renameTitle: String, val renameLabel: String, val rename: String, val chatName: String,
    val menuTitle: String, val pin: String, val markRead: String, val moveTo: String, val inbox: String, val archive: String, val noServer: String,
    val undone: String, val screen: String, val twoMin: String,
) {
    companion object {
        val En = PartBWords(
            send = "Send", attach = "Attach", cancel = "Cancel", run = "Run again", reason = "Connect a server first", delete = "Delete chat",
            more = "More", copy = "Copy link", share = "Share", export = "Export",
            chipModel = "Sonnet", chipFile = "main.kt", chipFilter = "Unread", removeFile = "Remove main.kt", chipQueued = "Queued",
            name = "Server name", nameHelp = "Shown in the list and on the top bar.", url = "Address", port = "Port", token = "Access token",
            note = "Note", tooLong = "Keep it under 60 characters.",
            range = "Time range", day = "Day", week = "Week", month = "Month",
            model = "Model", fast = "Fast", fastLine = "Quick replies for small edits", deep = "Deep", deepLine = "Thinks longer on hard problems",
            local = "On this phone", needsDownload = "Download the voice pack first",
            keepRunning = "Keep running", keepLine = "Stays on while a reply is coming", allowAll = "Allow every command",
            risk = "Commands run without asking. Turn this on only for projects you trust.", always = "Basic tools",
            advanced = "Advanced", advancedBody = "Proxy, timeouts and the raw log.",
            loading = "Loading conversations", installing = "Installing", failedTitle = "Couldn't reach the server",
            failedBody = "Check that the computer is awake and on the same network.", retry = "Try again", switchServer = "Switch server",
            sheetTitle = "Rename chat", sheetSub = "Fix the login bug", sheetBody = "The new name shows in the list and on your other devices.", save = "Save name",
            confirmTitle = "Delete this server?", confirmAct = "Delete server", confirmBody = "Its chats stay on the computer.",
            c1 = "The saved password is removed from this phone.", c2 = "Drafts for this server are deleted.", serverName = "office-box",
            renameTitle = "Rename chat?", renameLabel = "Name", rename = "Rename", chatName = "Fix the login bug",
            menuTitle = "Chat actions", pin = "Pin", markRead = "Mark as read", moveTo = "Move to", inbox = "Inbox", archive = "Archive",
            noServer = "Connect a server first",
            undone = "Chat archived", screen = "Conversations", twoMin = "2 min",
        )
        val Ar = PartBWords(
            send = "إرسال", attach = "إرفاق", cancel = "إلغاء", run = "شغّل مرة أخرى", reason = "صِل بخادم أولًا", delete = "احذف المحادثة",
            more = "المزيد", copy = "انسخ الرابط", share = "مشاركة", export = "تصدير",
            chipModel = "Sonnet", chipFile = "main.kt", chipFilter = "غير المقروءة", removeFile = "أزِل main.kt", chipQueued = "في الانتظار",
            name = "اسم الخادم", nameHelp = "يظهر في القائمة وفي الشريط العلوي.", url = "العنوان", port = "المنفذ", token = "رمز الوصول",
            note = "ملاحظة", tooLong = "اجعلها أقل من ٦٠ حرفًا.",
            range = "المدة", day = "يوم", week = "أسبوع", month = "شهر",
            model = "النموذج", fast = "سريع", fastLine = "ردود سريعة للتعديلات الصغيرة", deep = "متعمّق", deepLine = "يفكّر أطول في المسائل الصعبة",
            local = "على هذا الهاتف", needsDownload = "نزّل حزمة الصوت أولًا",
            keepRunning = "أبقِه يعمل", keepLine = "يبقى يعمل ما دام ردّ في الطريق", allowAll = "اسمح بكل الأوامر",
            risk = "تعمل الأوامر دون سؤال. فعّل هذا فقط للمشاريع التي تثق بها.", always = "الأدوات الأساسية",
            advanced = "متقدّم", advancedBody = "الوكيل والمهلات والسجل الخام.",
            loading = "جارٍ تحميل المحادثات", installing = "جارٍ التثبيت", failedTitle = "تعذّر الوصول إلى الخادم",
            failedBody = "تحقّق من أن الحاسوب يعمل وعلى الشبكة نفسها.", retry = "حاول مرة أخرى", switchServer = "بدّل الخادم",
            sheetTitle = "أعد تسمية المحادثة", sheetSub = "إصلاح خطأ الدخول", sheetBody = "يظهر الاسم الجديد في القائمة وعلى أجهزتك الأخرى.", save = "احفظ الاسم",
            confirmTitle = "أتحذف هذا الخادم؟", confirmAct = "احذف الخادم", confirmBody = "تبقى محادثاته على الحاسوب.",
            c1 = "تُزال كلمة المرور المحفوظة من هذا الهاتف.", c2 = "تُحذف المسودات الخاصة بهذا الخادم.", serverName = "office-box",
            renameTitle = "أتعيد تسمية المحادثة؟", renameLabel = "الاسم", rename = "أعد التسمية", chatName = "إصلاح خطأ الدخول",
            menuTitle = "إجراءات المحادثة", pin = "تثبيت", markRead = "علّمها مقروءة", moveTo = "انقل إلى", inbox = "الوارد", archive = "الأرشيف",
            noServer = "صِل بخادم أولًا",
            undone = "أُرشفت المحادثة", screen = "المحادثات", twoMin = "دقيقتين",
        )

        fun of(localeTag: String): PartBWords = if (localeTag.startsWith("ar")) Ar else En
    }
}

@Composable
private fun Ground(tag: String, content: @Composable () -> Unit) {
    Column(
        Modifier.background(LocalKitColors.current.ground).padding(16.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
fun ActionsScene(w: PartBWords, onPrimary: () -> Unit = {}, onIcon: () -> Unit = {}) {
    Ground("actions") {
        KitButton(w.send, onClick = onPrimary, modifier = Modifier.fillMaxWidth(), haptic = KitHaptic.Send)
        KitButton(w.attach, onClick = {}, modifier = Modifier.fillMaxWidth(), role = KitButtonRole.Secondary, icon = KitPartGlyphs.Paste)
        KitButton(w.run, onClick = {}, modifier = Modifier.fillMaxWidth(), role = KitButtonRole.Secondary, working = true)
        KitButton(w.run, onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = KitEnabled.Disabled(w.reason))
        KitButton(w.delete, onClick = {}, modifier = Modifier.fillMaxWidth(), destructive = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            KitButton(w.cancel, onClick = {}, role = KitButtonRole.Tertiary)
            KitIconButton(KitPartGlyphs.Copy, label = w.copy, onClick = onIcon)
            KitIconButton(KitPartGlyphs.More, label = w.more, onClick = {}, enabled = KitEnabled.Disabled(w.reason))
        }
        KitActionBlock(
            primary = KitAction(w.send, {}),
            secondary = KitAction(w.attach, null, enabled = KitEnabled.Disabled(w.reason)),
            tertiary = listOf(KitAction(w.copy, {}), KitAction(w.share, {}), KitAction(w.export, {})),
        )
    }
}

@Composable
fun ChipsScene(w: PartBWords, onRemove: () -> Unit = {}) {
    Ground("chips") {
        KitChipWrap {
            KitChip(w.chipModel)
            KitChip(w.chipFile, kind = KitChipKind.Removable, removeLabel = w.removeFile, onRemove = onRemove)
            KitChip(w.chipFilter, kind = KitChipKind.Count, count = 3, selected = true, onClick = {})
            KitChip(w.chipQueued, kind = KitChipKind.Action, tone = KitChipTone.Active, onClick = {})
            KitChip(w.chipQueued, tone = KitChipTone.Attention)
        }
        KitNeedsYou(KitNeedsYouReason.Decision, waiting = w.twoMin)
        KitNeedsYou(KitNeedsYouReason.Consent)
    }
}

@Composable
fun FieldsScene(w: PartBWords) {
    Ground("fields") {
        KitField(rememberTextFieldState("office-box"), w.name, helper = w.nameHelp)
        KitField(rememberTextFieldState("https://office.example.com"), w.url, kind = KitFieldKind.Url)
        KitField(rememberTextFieldState("4096"), w.port, kind = KitFieldKind.Number)
        KitField(rememberTextFieldState(""), w.token, kind = KitFieldKind.Secret)
        KitField(rememberTextFieldState(""), w.token, kind = KitFieldKind.Secret, secretPresence = SecretFieldPresence.Saved, onReplaceSecret = {})
        KitField(
            rememberTextFieldState("A long note that keeps going past the limit the field allows here"),
            w.note,
            kind = KitFieldKind.Multiline,
            error = w.tooLong,
            maxLength = 70,
        )
    }
}

@Composable
fun ChoicesScene(
    w: PartBWords,
    segment: String = "week",
    choice: String? = "fast",
    keepOn: Boolean = true,
    allowAll: Boolean = false,
    expanded: Boolean = true,
    onSegment: (String) -> Unit = {},
    onChoice: (String) -> Unit = {},
    onKeep: (Boolean) -> Unit = {},
    onAllow: (Boolean) -> Unit = {},
    onExpand: (Boolean) -> Unit = {},
) {
    Ground("choices") {
        KitSegmented(
            segments = listOf(KitSegment("day", w.day), KitSegment("week", w.week), KitSegment("month", w.month)),
            selected = segment,
            semanticsLabel = w.range,
            onChange = onSegment,
        )
        KitChoiceList(
            choices = listOf(
                KitChoice("fast", w.fast, w.fastLine),
                KitChoice("deep", w.deep, w.deepLine, recommended = true),
                KitChoice("local", w.local, enabled = KitEnabled.Disabled(w.needsDownload)),
            ),
            selected = choice,
            current = "deep",
            semanticsLabel = w.model,
            onSelect = onChoice,
        )
        KitSwitchRow(w.keepRunning, checked = keepOn, supporting = w.keepLine, onCheckedChange = onKeep)
        KitSwitchRow(w.allowAll, checked = allowAll, risk = w.risk, onCheckedChange = onAllow)
        KitSwitchRow(w.always, checked = true, locked = true, onCheckedChange = {})
        KitExpandRow(w.advanced, expanded = expanded, onExpandedChange = onExpand) {
            KitText(w.advancedBody, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }
}

@Composable
fun StatesScene(w: PartBWords) {
    Ground("states") {
        KitLoadingBar(loading = true, label = w.loading)
        KitSkeletonRows(count = 2)
        KitProgressView(KitProgress.Staged(step = 3, of = 5, label = w.installing, stepValue = 0.5f, eta = 2.minutes))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            KitReceipt(KitReceiptState.Sent)
            KitReceipt(KitReceiptState.Confirmed)
        }
        KitStateView(
            kind = KitStateKind.Error,
            title = w.failedTitle,
            body = w.failedBody,
            failure = Failure.Network(NetworkWhy.Offline),
            onRetry = {},
            retryLabel = w.retry,
            onSwitchServer = {},
            switchServerLabel = w.switchServer,
        )
    }
}

@Composable
fun SheetV2Scene(w: PartBWords, visible: Boolean = true, dirty: Boolean = false, onDismiss: () -> Unit = {}) {
    KitScreen(title = w.screen) {
        KitText(w.sheetBody, role = TypeRole.Secondary, tone = TextTone.Secondary)
        KitSheet(
            visible = visible,
            onDismissRequest = onDismiss,
            title = w.sheetTitle,
            subtitle = w.sheetSub,
            icon = KitPartGlyphs.Paste,
            primary = KitAction(w.save, onDismiss),
            tertiary = listOf(KitAction(w.cancel, onDismiss)),
            dirty = dirty,
        ) {
            KitField(rememberTextFieldState(w.chatName), w.renameLabel)
            KitText(w.sheetBody, role = TypeRole.Secondary, tone = TextTone.Secondary)
        }
    }
}

@Composable
fun ConfirmScene(w: PartBWords, shown: Boolean = true, onConfirm: () -> Unit = {}, onCancel: () -> Unit = {}) {
    KitScreen(title = w.screen) {
        KitText(w.sheetBody, role = TypeRole.Secondary, tone = TextTone.Secondary)
        KitConfirm(
            spec = if (!shown) null else KitConfirmSpec(
                title = w.confirmTitle,
                confirmLabel = w.confirmAct,
                kind = KitConfirmKind.Destructive,
                body = w.confirmBody,
                consequences = listOf(w.c1, w.c2),
                typedName = w.serverName,
            ),
            onConfirm = onConfirm,
            onCancel = onCancel,
        )
    }
}

@Composable
fun InputDialogScene(w: PartBWords, visible: Boolean = true, onResult: (String?) -> Unit = {}) {
    KitScreen(title = w.screen) {
        KitText(w.sheetBody, role = TypeRole.Secondary, tone = TextTone.Secondary)
        KitInputDialog(visible = visible, title = w.renameTitle, fieldLabel = w.renameLabel, confirmLabel = w.rename, initial = w.chatName, onResult = onResult)
    }
}

@Composable
fun MenuScene(w: PartBWords, onChoose: (KitMenuItem) -> Unit = {}) {
    val move = KitMenuGroup("move", heading = w.moveTo)
    Box(Modifier.background(LocalKitColors.current.ground).padding(16.dp).testTag("menu")) {
        KitMenuPanel(
            items = listOf(
                KitMenuItem(w.delete, {}, destructive = true),
                KitMenuItem(w.pin, {}, checked = true),
                KitMenuItem(w.markRead, {}),
                KitMenuItem(w.inbox, {}, group = move),
                KitMenuItem(w.archive, {}, group = move, enabled = KitEnabled.Disabled(w.noServer)),
            ),
            title = w.menuTitle,
            onChoose = onChoose,
        )
    }
}

@Composable
fun UndoScene(w: PartBWords, offer: KitUndoOffer? = KitUndoOffer("1", w.undone), onUndo: () -> Unit = {}, onTimeout: () -> Unit = {}) {
    Box(Modifier.background(LocalKitColors.current.ground).padding(vertical = 16.dp).testTag("undo")) {
        KitUndoBar(offer = offer, onUndo = onUndo, onTimeout = onTimeout)
    }
}

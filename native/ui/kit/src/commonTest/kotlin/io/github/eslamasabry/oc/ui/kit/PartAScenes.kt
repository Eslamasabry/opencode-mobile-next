package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
import io.github.eslamasabry.oc.ui.design.color.Packs
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.type.TextTone
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.frame.KitPageHeading
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.nav.KitNavDestination
import io.github.eslamasabry.oc.ui.kit.nav.KitNavRail
import io.github.eslamasabry.oc.ui.kit.nav.KitSidebar
import io.github.eslamasabry.oc.ui.kit.row.KitGroupNote
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowGroup
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitCostNotice
import io.github.eslamasabry.oc.ui.kit.state.KitDetailsFold
import io.github.eslamasabry.oc.ui.kit.state.KitErrorNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitOfferNotice
import io.github.eslamasabry.oc.ui.kit.state.KitTechnicalValue
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow

/**
 * Part-A scenes (S1-KITA1), shared by the desktop tests and the Roborazzi goldens so both ways test the same
 * composition. Copy is sample copy for tests only; screens take theirs from `:ui:words`.
 */
class PartAWords(
    val conversations: String, val back: String, val settings: String, val newChat: String, val search: String,
    val rename: String, val connected: String, val reconnecting: String, val tryAgain: String, val switchServer: String,
    val report: String, val details: String, val copyAll: String, val showAll: String,
    val rowTitle: String, val rowSupporting: String, val disabledRow: String, val disabledReason: String,
    val valueTitle: String, val value: String, val fileName: String, val groupLabel: String, val groupNote: String,
    val delete: String, val notice: String, val couldNotLoad: String, val cost: List<String>, val offer: String,
    val turnOn: String, val notNow: String, val addressLabel: String, val pickOne: String, val heading: String,
    val headingLine: String, val chats: String, val files: String, val needsYouWords: String, val dismiss: String,
) {
    companion object {
        val En = PartAWords(
            conversations = "Conversations", back = "Back", settings = "Settings", newChat = "New", search = "Search",
            rename = "Rename", connected = "Connected to laptop", reconnecting = "Reconnecting to laptop…",
            tryAgain = "Try again", switchServer = "Switch server", report = "Report a problem", details = "Details",
            copyAll = "Copy all", showAll = "Show all lines", rowTitle = "Fix the login redirect",
            rowSupporting = "laptop · 2 min ago · the reviewer checks it next", disabledRow = "Voice input",
            disabledReason = "Voice needs a model on this phone.", valueTitle = "Theme", value = "Graphite",
            fileName = "release_notes-2026-11-01_final_version.md", groupLabel = "Appearance",
            groupNote = "Changes apply at once on this phone.", delete = "Delete conversation",
            notice = "The server restarted, so the list was refreshed.", couldNotLoad = "Couldn't load files",
            cost = listOf("About 208 MB", "about 4 min", "uses battery while it installs"),
            offer = "laptop also runs an AI team. Turn it on?", turnOn = "Turn on", notNow = "Not now",
            addressLabel = "Address", pickOne = "Pick a conversation", heading = "Settings",
            headingLine = "This phone and its servers", chats = "Chats", files = "Files",
            needsYouWords = "2 need you", dismiss = "Dismiss",
        )
        val Ar = PartAWords(
            conversations = "المحادثات", back = "رجوع", settings = "الإعدادات", newChat = "جديد", search = "بحث",
            rename = "إعادة تسمية", connected = "متصل بالحاسوب", reconnecting = "إعادة الاتصال بالحاسوب…",
            tryAgain = "حاول مرة أخرى", switchServer = "بدّل الخادم", report = "أبلغ عن مشكلة", details = "التفاصيل",
            copyAll = "انسخ الكل", showAll = "اعرض كل الأسطر", rowTitle = "أصلِح إعادة توجيه تسجيل الدخول",
            rowSupporting = "الحاسوب · قبل دقيقتين · يراجعه المراجع بعد ذلك", disabledRow = "الإدخال الصوتي",
            disabledReason = "يحتاج الصوت إلى نموذج على هذا الهاتف.", valueTitle = "السمة", value = "Graphite",
            fileName = "release_notes-2026-11-01_final_version.md", groupLabel = "المظهر",
            groupNote = "تُطبَّق التغييرات فورًا على هذا الهاتف.", delete = "احذف المحادثة",
            notice = "أُعيد تشغيل الخادم، فحُدِّثت القائمة.", couldNotLoad = "تعذّر تحميل الملفات",
            cost = listOf("نحو 208 ميغابايت", "نحو 4 دقائق", "يستهلك البطارية أثناء التثبيت"),
            offer = "يشغّل الحاسوب أيضًا فريق ذكاء. أتريد تشغيله؟", turnOn = "شغّل", notNow = "ليس الآن",
            addressLabel = "العنوان", pickOne = "اختر محادثة", heading = "الإعدادات",
            headingLine = "هذا الهاتف وخوادمه", chats = "المحادثات", files = "الملفات",
            needsYouWords = "2 تحتاجك", dismiss = "تجاهل",
        )

        fun of(localeTag: String): PartAWords = if (localeTag.startsWith("ar")) Ar else En
    }
}

/** Test-only redacted text (the Redactor is the only constructor in the app). */
@OptIn(RedactorApi::class)
fun redacted(text: String): Redacted = Redacted(text)

/** Pins the window a scene is laid out in, so window-class behaviour is deterministic. */
@Composable
fun InWindow(widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(widthDp.toFloat(), heightDp.toFloat())) {
        Box(Modifier.requiredSize(widthDp.dp, heightDp.dp).testTag("window")) { content() }
    }
}

fun reconnecting(w: PartAWords, onTry: () -> Unit = {}) =
    KitStatus(
        kind = KitStatusKind.Connection, id = "connection:laptop", message = w.reconnecting, tone = StatusTone.Progress,
        action = KitLineAction(w.tryAgain, onTry),
    )

@Composable
fun ScreenScene(w: PartAWords, status: KitStatus? = null, loading: Boolean = false, width: KitScreenWidth = KitScreenWidth.Full) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = w.conversations, subtitle = w.connected, exit = KitTopBarExit.Back, exitLabel = w.back, onExit = {},
                actions = listOf(KitLineAction(w.newChat, {})),
            )
        },
        status = status,
        header = { KitText(w.search, Modifier.padding(horizontal = 16.dp).testTag("scene-header"), role = TypeRole.Secondary, tone = TextTone.Secondary) },
        loading = loading,
        loadingLabel = w.search,
        width = width,
        bottom = { KitButton(w.newChat, onClick = {}, modifier = Modifier.fillMaxWidth()) },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("scene-body")) { RowsScene(w) }
    }
}

@Composable
fun RowsScene(w: PartAWords) {
    Column(Modifier.background(LocalKitColors.current.ground)) {
        KitRowGroup(label = w.groupLabel, note = w.groupNote, destructive = { KitRow(w.delete, onClick = {}) }) {
            KitRow(w.rowTitle, supporting = w.rowSupporting, trailing = KitRowTrailing.Chevron, onClick = {})
            KitRow(w.valueTitle, trailing = KitRowTrailing.Value(w.value), onClick = {})
            KitRow(w.disabledRow, enabled = false, disabledReason = w.disabledReason, onClick = {})
            KitRow(w.fileName, titleIsFileName = true, leading = KitRowLeading.Initials("MD"), onClick = {})
            KitRow(w.turnOn, supporting = w.offer, trailing = KitRowTrailing.Action(KitLineAction(w.turnOn, {})))
        }
    }
}

@Composable
fun StatusScene(w: PartAWords) {
    Column(Modifier.background(LocalKitColors.current.ground), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KitStatusLine(reconnecting(w))
        KitStatusLine(
            KitStatus(KitStatusKind.Work, w.rowTitle, tone = StatusTone.Ok, next = w.rowSupporting),
        )
        KitStatusLine(
            KitStatus(KitStatusKind.Info, w.notice, tone = StatusTone.Neutral, onDismiss = {}, dismissLabel = w.dismiss),
        )
    }
}

@Composable
fun NoticeScene(w: PartAWords, open: Boolean = true) {
    Column(
        Modifier.background(LocalKitColors.current.ground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KitNotice(w.notice, tone = StatusTone.Ok, actions = listOf(KitLineAction(w.tryAgain, {})))
        KitCostNotice(w.cost)
        KitOfferNotice(w.offer, KitLineAction(w.turnOn, {}), KitLineAction(w.notNow, {}))
        KitErrorNotice(
            w.couldNotLoad, failure = Failure.Network(NetworkWhy.Offline),
            retry = KitLineAction(w.tryAgain, {}), switchServer = KitLineAction(w.switchServer, {}),
            report = KitLineAction(w.report, {}),
        )
        KitDetailsFold(
            label = w.details, copyLabel = w.copyAll, initiallyExpanded = open,
            values = listOf(KitTechnicalValue(w.addressLabel, redacted("http://laptop.local:4096"))),
            text = redacted("GET /api/session → 503\nretry-after: 2"),
        )
    }
}

@Composable
fun HeadingScene(w: PartAWords) {
    Column(Modifier.background(LocalKitColors.current.ground)) {
        KitPageHeading(w.heading, line = w.headingLine)
    }
}

fun destinations(w: PartAWords) =
    listOf(
        KitNavDestination("chats", w.chats, needsYou = 2, needsYouWords = w.needsYouWords),
        KitNavDestination("files", w.files),
        KitNavDestination("settings", w.settings),
    )

@Composable
fun NavScene(w: PartAWords) {
    Row(Modifier.background(LocalKitColors.current.ground)) {
        KitNavRail(destinations(w), selectedId = "chats", onSelect = {})
        KitSidebar(destinations(w), selectedId = "files", onSelect = {})
    }
}

@Composable
fun PaneScene(w: PartAWords, selected: Boolean) {
    KitPaneScreen(
        list = { Column { RowsScene(w) } },
        detail = if (selected) ({ Column { HeadingScene(w); StatusScene(w) } }) else null,
        emptyDetail = { KitText(w.pickOne, Modifier.padding(24.dp).testTag("scene-empty"), role = TypeRole.Body) },
        side = { NoticeScene(w, open = false) },
        topBar = { KitTopBar(title = w.conversations) },
    )
}

/** A pack swatch strip for the theme goldens: ground, surfaces, text and accents of the theme in force. */
@Composable
fun ThemeScene(w: PartAWords) {
    Column(Modifier.background(LocalKitColors.current.ground).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KitText(w.conversations, role = TypeRole.Title)
        KitText(w.rowTitle, role = TypeRole.RowTitle)
        KitText(w.rowSupporting, role = TypeRole.Secondary, tone = TextTone.Secondary)
        KitText(w.connected, role = TypeRole.Label, tone = TextTone.Accent)
        KitButton(w.newChat, onClick = {})
        StatusScene(w)
    }
}

/** Every static pack, for the contrast floor over rendered colours (unused by goldens). */
val allStaticPacks = Packs.static

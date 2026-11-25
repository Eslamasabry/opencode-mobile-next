package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.domain.platform.AppNotice
import io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.ConversationRowUi
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.feature.chats.ui.homeAppNoticeStatus
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerFailure
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.icon.KitIcon
import io.github.eslamasabry.oc.ui.kit.icon.KitIconSize
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/**
 * The polish sweep of Home (2026-11-25), finding by finding. Each test proves what its finding expects on the real
 * screen at 360 dp; `polish renders` writes the before and after picture of every finding under build/polish-render.
 */
@OptIn(ExperimentalTestApi::class)
class PolishHomeTest {
    private val savedLocale: Locale = Locale.getDefault()
    private val arabicSymbols = NumberSymbols(zeroDigit = '\u0660', grouping = '\u066c', decimal = '\u066b')
    private val outDir = File("build/polish-render")

    /** The theme's accent, read while the Home is composed. */
    private var accent: Color = Color.Unspecified

    private val marks =
        object : KitLogoLoader {
            private fun mark(name: String) =
                File(System.getProperty("user.dir"), "../../androidApp/src/main/assets/marks/$name.svg")
                    .takeIf { it.isFile }
                    ?.readText()

            override suspend fun svg(id: String, agent: Boolean): String? = mark(id)

            override suspend fun colorSvg(id: String): String? =
                if (id in setOf("claude", "gemini", "qwen", "copilot")) mark("$id-color") else null
        }

    @AfterTest
    fun restoreEnvironment() {
        Numbers.pinned = null
        Locale.setDefault(savedLocale)
    }

    /** The words and digits of [locale], as a phone set to it has them (the kit's words follow the default locale). */
    private fun inLanguage(locale: String) {
        Locale.setDefault(Locale.forLanguageTag(locale))
        Numbers.pinned = if (locale == "ar") arabicSymbols else NumberSymbols.Latin
    }

    private fun openCodeRow(id: String, title: String, project: String) =
        ConversationRowUi(
            key = id,
            ref = ConversationRef(ProfileId("p"), SessionId(id)),
            title = RowTitle.Text(title),
            project = project,
            status = RowStatus.None,
            agentMark = "opencode",
        )

    private fun arabicChipState() =
        ChatsUi(
            phase = ChatsPhase.List,
            settingsAvailable = true,
            runningShown = 1,
            allCount = 1,
            recent = persistentListOf(openCodeRow("s1", "إصلاح خطأ تسجيل الدخول", "api")),
        )

    private fun appClosedArabicState() =
        ChatsUi(
            phase = ChatsPhase.List,
            settingsAvailable = true,
            allCount = 0,
            recent =
                persistentListOf(
                    openCodeRow("login", "إصلاح خطأ تسجيل الدخول", "api"),
                    openCodeRow("notes", "ملاحظات الإصدار", "docs"),
                ),
        )

    private fun appClosedArabicNotice() =
        AppNotice(
            id = "app-exit",
            message = "أوقف أندرويد التطبيق عند ٣:١٠ م، فتوقّف OpenCode. شغّله من جديد من «هذا الهاتف».",
            action = AppNoticeAction.KeepRunning,
            actionLabel = "أبقِه يعمل",
            dismissLabel = "إغلاق التنبيه",
            stoppedApp = true,
        )

    private fun failedOnThisPhone() =
        ChatsUi(
            phase = ChatsPhase.Opening,
            settingsAvailable = true,
            home =
                ChatsHomeExtraState(
                    opening =
                        ChatsOpeningUi(
                            phase = ChatsOpeningPhase.Failed,
                            serverName = "This phone",
                            failure = Failure.Network(),
                            profile = ProfileId("phone"),
                            onThisPhone = true,
                        ),
                ),
        )

    /** A saved computer that did not answer (the sweep's picture of finding 12): its card says what to check. */
    private fun failedComputer() =
        ChatsUi(
            phase = ChatsPhase.Opening,
            settingsAvailable = true,
            home =
                ChatsHomeExtraState(
                    opening =
                        ChatsOpeningUi(
                            phase = ChatsOpeningPhase.Failed,
                            serverName = "Home PC",
                            failure = Failure.Network(),
                            address = "studio.example.com",
                        ),
                ),
        )

    private fun laptopNotAnsweringState(locale: String = "en"): ChatsUi {
        val titles =
            if (locale == "en") listOf("Fix the login bug", "Plan the release notes")
            else listOf("إصلاح خطأ تسجيل الدخول", "ملاحظات الإصدار")
        return ChatsUi(
            phase = ChatsPhase.List,
            settingsAvailable = true,
            allCount = 2,
            recent =
                persistentListOf(
                    openCodeRow("login", titles[0], "api"),
                    openCodeRow("notes", titles[1], "api"),
                ),
            home =
                ChatsHomeExtraState(
                    sources =
                        persistentListOf(
                            ChatsListSourceUi(
                                profile = ProfileId("laptop"),
                                name = "Laptop",
                                shown = true,
                                main = true,
                                onThisPhone = false,
                                unreachable = true,
                            ),
                        ),
                ),
        )
    }

    /** OpenCode rows beside a Claude Code row and a Codex row (finding 10). No saved server, so there is no pill. */
    private fun openCodeRowsState(locale: String = "en"): ChatsUi {
        val titles =
            if (locale == "en") {
                listOf(
                    "Fix the login bug",
                    "Run the tests before the release",
                    "Write the release notes",
                    "Explain this project",
                    "Review the pull request",
                )
            } else {
                listOf(
                    "إصلاح خطأ تسجيل الدخول",
                    "تشغيل الاختبارات قبل الإصدار",
                    "كتابة ملاحظات الإصدار",
                    "شرح هذا المشروع",
                    "مراجعة طلب السحب",
                )
            }
        return ChatsUi(
            phase = ChatsPhase.List,
            settingsAvailable = true,
            allCount = 5,
            recent =
                persistentListOf(
                    openCodeRow("login", titles[0], "api"),
                    openCodeRow("tests", titles[1], "api"),
                    openCodeRow("notes", titles[2], "api"),
                    openCodeRow("claude", titles[3], "api")
                        .copy(agentMark = "claude", agentName = "Claude Code"),
                    openCodeRow("codex", titles[4], "api")
                        .copy(agentMark = "codex", agentName = "Codex"),
                ),
        )
    }

    /** The test's now. */
    private val now: Long = 1_760_000_000_000L

    /** A row that last moved [minutesAgo] before the test's now. */
    private fun timedRow(
        id: String,
        title: String,
        minutesAgo: Long,
        status: RowStatus = RowStatus.None,
    ): ConversationRowUi =
        openCodeRow(id, title, "api").copy(status = status, updatedAtMs = now - minutesAgo * 60_000L)

    /** Short titles, a few letters each: nothing but the time reaches the end of these rows. */
    private fun briefTitles(english: Boolean): List<String> =
        if (english) {
            listOf("Login bug", "Notes", "Docs")
        } else {
            listOf("الدخول", "ملاحظات", "دليل")
        }

    /** Three rows a few minutes, hours and days old (the test of finding 15). */
    private fun briefTimesState(locale: String): ChatsUi {
        val titles = briefTitles(locale == "en")
        return ChatsUi(
            phase = ChatsPhase.List,
            settingsAvailable = true,
            nowMs = now,
            allCount = 3,
            recent =
                persistentListOf(
                    timedRow("m", titles[0], 5),
                    timedRow("h", titles[1], 120),
                    timedRow("d", titles[2], 3 * 24 * 60L),
                ),
        )
    }

    /** Finding 15's titles: long ones, the sweep's cut rows. */
    private fun longTitles(english: Boolean): List<String> =
        if (english) {
            listOf(
                "Fix the reconnect loop before the release",
                "Write the notes for the next release",
                "Review the pull request from last week",
                "Start the release notes",
            )
        } else {
            listOf(
                "إصلاح حلقة إعادة الاتصال قبل الإصدار",
                "اكتب ملاحظات الإصدار القادم",
                "راجع طلب السحب من الأسبوع الماضي",
                "ابدأ ملاحظات الإصدار",
            )
        }

    /** Finding 15's Home: long titles, one waiting on the person, times from minutes to days. */
    private fun longTitlesState(locale: String): ChatsUi {
        val titles = longTitles(locale == "en")
        return ChatsUi(
            phase = ChatsPhase.List,
            settingsAvailable = true,
            nowMs = now,
            needsYou = 2,
            allCount = 4,
            recent =
                persistentListOf(
                    timedRow("s1", titles[0], 5, RowStatus.NeedsYou),
                    timedRow("s2", titles[1], 120),
                    timedRow("s3", titles[2], 3 * 24 * 60L),
                    timedRow("s4", titles[3], 9, RowStatus.Running),
                ),
        )
    }

    /** How the Home is drawn for a picture or a check. */
    private class Look(val dark: Boolean, val style: Style = Style.Solid)

    private fun ComposeUiTest.showHome(
        state: ChatsUi,
        locale: String,
        look: Look,
        fontScale: Float = 1f,
        notice: AppNotice? = null,
    ): (ChatsUi) -> Unit {
        var shown by mutableStateOf(state)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ChatEnvironment {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = if (look.dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                                style = look.style,
                            ),
                        platform = DesktopKitPlatform(),
                        localeTag = locale,
                        stillFrame = true,
                    ) {
                        accent = LocalKitColors.current.accent
                        CompositionLocalProvider(
                            LocalKitRedactor provides Redactor(),
                            LocalKitLogoLoader provides marks,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                val status = notice?.let { homeAppNoticeStatus(it, {}, {}) }
                                ChatsScreen(shown, RecordingChatsActions(), appStatus = status)
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
        return { next ->
            shown = next
            waitForIdle()
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
        }
    }

    private data class RenderShot(
        val name: String,
        val dark: Boolean,
        val locale: String,
        val fontScale: Float,
        val style: Style,
    )

    private fun renderOne(shot: RenderShot, state: ChatsUi, notice: AppNotice?): FlickerFailure? =
        try {
            kitUiTest(360, 800) {
                showHome(state, shot.locale, Look(shot.dark, shot.style), shot.fontScale, notice)
                val file = File(outDir, "${shot.name}.png")
                file.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                assertTrue(file.isFile)
            }
            null
        } catch (e: FlickerFailure) {
            e
        }

    /** One Home to picture: its finding, language, state and the phase its pictures are named after. */
    private class Scene(
        val finding: Int,
        val locale: String,
        val state: ChatsUi,
        val notice: AppNotice? = null,
        val phase: String = PHASE,
        val style: Style = Style.Solid,
    )

    /** Writes the scene's pictures and says, short, what the flicker rules found on them; null when nothing. */
    private fun renderScene(scene: Scene): String? {
        inLanguage(scene.locale)
        val shots =
            listOf(true to "dark", false to "light").flatMap { (dark, tone) ->
                listOf("100" to 1f, "130" to 1.3f).map { (scaleTag, fontScale) ->
                    val name = "${scene.finding}-${scene.phase}-$tone-${scene.locale}-$scaleTag"
                    RenderShot(name, dark, scene.locale, fontScale, scene.style)
                }
            }
        val firstFlicker = shots.mapNotNull { renderOne(it, scene.state, scene.notice) }.firstOrNull()
        // Said short, rule by rule: the test machine's log keeps only the first line of a failure.
        return firstFlicker?.let { flicker ->
            "${scene.finding}-${scene.locale}: " + flicker.findings.joinToString(" | ") { "${it.rule} [${it.label}]" }
        }
    }

    // ---- reading the page: ink on the picture, words of the tree ----

    /** The surface a row's words are drawn on: its own top corner, inside the row and outside every word. */
    private fun rowGround(map: PixelMap, row: Rect): Color = map[(row.left + 6f).toInt(), (row.top + 4f).toInt()]

    /** Every column of [columns] that holds ink within [band] pixels of the height [cy]. */
    private fun inkColumns(map: PixelMap, columns: IntRange, cy: Float, band: Int, ground: Color): List<Int> {
        val xs = mutableListOf<Int>()
        for (x in columns) {
            val drawn = ((-band)..band).any { dy -> far(map[x, (cy + dy).toInt()], ground) }
            if (drawn) {
                xs += x
            }
        }
        return xs
    }

    /**
     * How wide the ink of a row's time is: the run that starts near the row's end (its start side in right-to-left,
     * where the end lies) and goes on while no blank stretch of a few columns parts it from the words.
     */
    private fun ComposeUiTest.timeInkWidth(row: Rect, rtl: Boolean): Float {
        val map = onRoot().captureToImage().toPixelMap()
        val edge = if (rtl) row.left.toInt() + 2 else row.right.toInt() - 2
        val step = if (rtl) 1 else -1
        val room = edge..(edge + step * END_ROOM)
        val scan = minOf(room.first, room.last)..maxOf(room.first, room.last)
        val columns = inkColumns(map, scan, row.center.y, END_BAND, rowGround(map, row))
        val first = columns.firstOrNull() ?: return 0f
        var last = first
        for (x in columns) {
            if (x - last > GAP_PX) {
                break
            }
            last = x
        }
        return abs(last - first) + 1f
    }

    /** The first ink at the row's start on its middle line (the agent's mark): its leftmost to its rightmost pixel. */
    private fun ComposeUiTest.markInk(row: Rect): IntRange? {
        val map = onRoot().captureToImage().toPixelMap()
        val xs = inkColumns(map, (row.left.toInt() + 4)..(row.left.toInt() + 44), row.center.y, 0, rowGround(map, row))
        return if (xs.isEmpty()) null else xs.min()..xs.max()
    }

    /** A captured picture with a square box on it, addressed in fractions of the box (0 to 1 on each side). */
    private class BoxView(
        private val map: PixelMap,
        private val cx: Float,
        private val cy: Float,
        private val ground: Color,
    ) {
        fun ink(fx: Float, fy: Float): Boolean =
            far(map[(cx + (fx - .5f) * MARK_DP).toInt(), (cy + (fy - .5f) * MARK_DP).toInt()], ground)

        /** Whether the window holds no ink, read on a lattice of nine points a side. */
        fun clear(x0: Float, x1: Float, y0: Float, y1: Float): Boolean =
            (0..LATTICE).all { i ->
                (0..LATTICE).all { j -> !ink(x0 + (x1 - x0) * i / LATTICE, y0 + (y1 - y0) * j / LATTICE) }
            }
    }

    /** Every text on the page (a row's own words are cleared from the tree; its spoken sentence stays). */
    private fun ComposeUiTest.texts(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { node -> node.config[SemanticsProperties.Text].joinToString(" ", transform = AnnotatedString::text) }

    private fun SemanticsNode.textsDeep(): List<String> =
        (config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList()) +
            children.flatMap { it.textsDeep() }

    /** One glyph, drawn as a notice draws its mark. */
    @Composable
    private fun Glyph(icon: ImageVector) {
        ChatEnvironment {
            KitTheme(
                appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
                platform = DesktopKitPlatform(),
                localeTag = "en",
                stillFrame = true,
            ) {
                Box(Modifier.testTag("glyph")) { KitIcon(icon, size = KitIconSize.Small) }
            }
        }
    }

    /** The one letter the kit draws [icon] with (a glyph is a character of the icon font, so it can be read back). */
    private fun glyphOf(icon: ImageVector): String {
        var out = ""
        kitUiTest(120, 120) {
            setContent { Glyph(icon) }
            waitForIdle()
            out = onNodeWithTag("glyph", useUnmergedTree = true).fetchSemanticsNode().textsDeep().single()
        }
        return out
    }

    private val dark = Look(dark = true)

    // ---- the first round: Arabic digits, the count, This phone ----

    @Test
    fun `B-585 the Arabic Running chip uses Arabic digits`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        Numbers.pinned = arabicSymbols
        kitUiTest(360, 800) {
            showHome(arabicChipState(), "ar", dark)
            onNodeWithContentDescription("قيد العمل · ١").assertExists()
            onNodeWithText("قيد العمل · ١", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `B-555 This phone that does not answer names no computer and no VPN`() {
        Locale.setDefault(Locale.forLanguageTag("en"))
        Numbers.pinned = NumberSymbols.Latin
        kitUiTest(360, 800) {
            showHome(failedOnThisPhone(), "en", dark)
            val computerNodes =
                onAllNodesWithText("computer", substring = true, ignoreCase = true).fetchSemanticsNodes()
            val vpnNodes =
                onAllNodesWithText("VPN", substring = true, ignoreCase = true).fetchSemanticsNodes()
            assertEquals(0, computerNodes.size)
            assertEquals(0, vpnNodes.size)
        }
    }

    // ---- the lead's rounds: the OpenCode mark ----

    @Test
    fun `B-553 an OpenCode row wears the open portal mark, never a hollow rectangle`() {
        kitUiTest(360, 800) {
            showHome(openCodeRowsState(), "en", dark)
            val rows = onAllNodesWithTag("kit-recent-row", useUnmergedTree = true).fetchSemanticsNodes()
            val row = rows.first().boundsInRoot
            waitUntil(timeoutMillis = 5_000L) { markInk(row) != null }
            val ink = markInk(row) ?: error("the mark is not drawn")
            val map = onRoot().captureToImage().toPixelMap()
            val mark = BoxView(map, (ink.first + ink.last + 1) / 2f, row.center.y, rowGround(map, row))
            assertTrue(mark.ink(.35f, .12f), "the mark is drawn: its top edge, a little in from the start corner")
            // The portal is open at its top end and at its bottom start; a hollow rectangle is closed all round.
            assertTrue(mark.clear(.74f, .90f, .05f, .14f), "closed at its top end: a hollow rectangle")
            assertTrue(mark.clear(.10f, .26f, .86f, .95f), "closed at its bottom start: a hollow rectangle")
        }
    }

    // ---- the lead's rounds: short times and one word for Needs you ----

    @Test
    fun `B-558 a Home row shows a short time beside its title and still says the whole time`() {
        for (locale in listOf("en", "ar")) {
            val rtl = locale == "ar"
            inLanguage(locale)
            kitUiTest(360, 800) {
                showHome(briefTimesState(locale), locale, dark, fontScale = 1.3f)
                val rows = onAllNodesWithTag("kit-recent-row", useUnmergedTree = true).fetchSemanticsNodes()
                assertEquals(3, rows.size)
                for (row in rows) {
                    val said = row.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
                    val whole = if (rtl) "منذ" in said else " ago" in said
                    assertTrue(whole, "[$locale] a screen reader hears the whole time: $said")
                    val width = timeInkWidth(row.boundsInRoot, rtl)
                    assertTrue(width in 1f..BRIEF_PX, "[$locale] the time beside the title is short: $width px ($said)")
                }
            }
        }
    }

    @Test
    fun `B-558 Arabic Home says Needs you with one word, in its link and on its row`() {
        inLanguage("ar")
        kitUiTest(360, 800) {
            val swap = showHome(longTitlesState("ar"), "ar", dark)
            for (count in listOf(2, 1)) {
                swap(longTitlesState("ar").copy(needsYou = count))
                val links = texts().filter { "إليك" in it || "بانتظارك" in it }
                val one = links.isNotEmpty() && links.all { "بانتظارك" in it && "يحتاج" !in it }
                assertTrue(one, "count $count: the link under the title says Needs you with one word: $links")
                val rows = onAllNodes(hasContentDescription("بانتظارك", substring = true)).fetchSemanticsNodes()
                assertTrue(rows.isNotEmpty(), "count $count: the row says the same word")
            }
        }
    }

    // ---- the lead's rounds: the Glass + (closed: owner 2026-11-14 decided pure glass) ----

    @Test
    fun `B-570 Solid New conversation button keeps the accent while Glass stays pure glass`() {
        for (darkNow in listOf(true, false)) {
            for (style in listOf(Style.Solid, Style.Glass)) {
                kitUiTest(360, 800) {
                    showHome(openCodeRowsState(), "en", Look(darkNow, style))
                    val button = onNodeWithContentDescription("New conversation").fetchSemanticsNode().boundsInRoot
                    val map = onRoot().captureToImage().toPixelMap()
                    val cy = button.center.y.toInt()
                    val start = map[(button.left + button.width * .22f).toInt(), cy]
                    val end = map[(button.right - button.width * .22f).toInt(), cy]
                    val bothAccent = sameInk(start, accent) && sameInk(end, accent)
                    val anyAccent = sameInk(start, accent) || sameInk(end, accent)
                    val ok = if (style == Style.Solid) bothAccent else !anyAccent
                    assertTrue(
                        ok,
                        "$style dark=$darkNow: Solid uses accent $accent, Glass stays pure glass ($start, $end)",
                    )
                }
            }
        }
    }

    // ---- the lead's rounds: the opening card's checks and its quiet actions ----

    @Test
    fun `B-555 a server that did not answer lists its checks in one start-aligned column`() {
        kitUiTest(360, 800) {
            showHome(failedComputer(), "en", dark)
            val lefts = mutableListOf<Float>()
            for (words in listOf("opencode serve", "same network or VPN", "Did the address change")) {
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(words, substring = true, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                for (line in 0 until layouts.single().lineCount) lefts += layouts.single().getLineLeft(line)
            }
            assertTrue(lefts.isNotEmpty() && lefts.max() - lefts.min() < 1f, "every check starts in one column: $lefts")
        }
    }

    @Test
    fun `B-555 Change server sits under the middle of Try again`() {
        kitUiTest(360, 800) {
            showHome(failedComputer(), "en", dark)
            val again = onNodeWithText("Try again", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val change = onNodeWithText("Change server", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val off = abs(change.center.x - again.center.x)
            assertTrue(off < 1f, "Change server is centred under Try again: $change vs $again")
        }
    }

    // ---- the lead's rounds: the offline notice ----

    @Test
    fun `B-573 the offline notice wears the warning mark and its Try again sits on the content edge`() {
        val warning = glyphOf(KitIcons.warning)
        val info = glyphOf(KitIcons.info)
        assertTrue(warning.isNotEmpty() && warning != info, "the two glyphs are read back: '$warning' and '$info'")
        kitUiTest(360, 800) {
            showHome(laptopNotAnsweringState(), "en", dark)
            val notice = onNodeWithTag("kit-notice", useUnmergedTree = true).fetchSemanticsNode()
            val drawn = notice.textsDeep()
            assertTrue(warning in drawn, "the notice's mark is the warning glyph: $drawn")
            assertTrue(info !in drawn, "the notice's mark is not the information glyph: $drawn")
            val again = onNodeWithText("Try again").fetchSemanticsNode().boundsInRoot
            assertTrue(again.left - notice.boundsInRoot.left < EDGE_PX, "Try again starts on the content edge: $again")
        }
    }

    @Test
    fun `B-573 Not answering appears once, in the server pill, and the notice gives the reason`() {
        for (locale in listOf("en", "ar")) {
            inLanguage(locale)
            val pillWord = if (locale == "ar") "لا يستجيب" else "Not answering"
            val repeatStem = if (locale == "ar") "يستجيب" else "answering"
            val tryAgain = if (locale == "ar") "إعادة المحاولة" else "Try again"
            kitUiTest(360, 800) {
                showHome(laptopNotAnsweringState(locale), locale, dark)
                val pill =
                    onNodeWithTag("kit-status-pill-word", useUnmergedTree = true).fetchSemanticsNode().textsDeep()
                assertTrue(pill.any { pillWord in it }, "[$locale] pill names $pillWord: $pill")
                val notice =
                    onNodeWithTag("kit-notice-message", useUnmergedTree = true).fetchSemanticsNode().textsDeep()
                assertTrue(
                    notice.isNotEmpty() && notice.any { "Laptop" in it },
                    "[$locale] notice names server: $notice",
                )
                assertTrue(
                    notice.none { repeatStem in it.lowercase() },
                    "[$locale] notice must not repeat '$repeatStem': $notice",
                )
                val hits = texts().filter { repeatStem in it.lowercase() }
                assertEquals(1, hits.size, "[$locale] '$repeatStem' appears once, in the pill only: $hits")
                onNodeWithText(tryAgain).assertExists()
            }
        }
    }

    @Test
    fun `polish renders`() {
        // The lead's rounds (before and after): 10 the mark, 15 times and Needs you, 27 the Glass +,
        // 12 This phone's card, 121 a computer's card with its checks, 30 the offline notice.
        val flickers =
            listOf(
                Scene(42, "ar", arabicChipState()),
                Scene(11, "ar", appClosedArabicState(), appClosedArabicNotice()),
                Scene(10, "en", openCodeRowsState("en"), phase = ROUND),
                Scene(10, "ar", openCodeRowsState("ar"), phase = ROUND),
                Scene(15, "en", longTitlesState("en"), phase = ROUND),
                Scene(15, "ar", longTitlesState("ar"), phase = ROUND),
                Scene(27, "en", openCodeRowsState("en"), phase = ROUND, style = Style.Glass),
                Scene(27, "ar", openCodeRowsState("ar"), phase = ROUND, style = Style.Glass),
                Scene(12, "en", failedOnThisPhone(), phase = ROUND),
                Scene(12, "ar", failedOnThisPhone(), phase = ROUND),
                Scene(121, "en", failedComputer(), phase = ROUND),
                Scene(121, "ar", failedComputer(), phase = ROUND),
                Scene(30, "en", laptopNotAnsweringState("en"), phase = NOTICE_ROUND),
                Scene(30, "ar", laptopNotAnsweringState("ar"), phase = NOTICE_ROUND),
            ).mapNotNull { renderScene(it) }
        if (flickers.isNotEmpty()) throw AssertionError(flickers.joinToString(" ; "))
    }
}

/** The first round's pictures are named "after": they were taken after that round's fix. */
private const val PHASE = "after"

/** The lead's rounds: their pictures after the fix (the "before" ones were written by the red commit). */
private const val ROUND = "after"

/** Finding 30's non-repeating notice copy: "before" on its red commit, "after" on its fix. */
private const val NOTICE_ROUND = "after"

/** A row's agent mark, in dp (KitRecentRowRules.MARK_DP). */
private const val MARK_DP = 28f

/** The side of the lattice a mark's windows are read on. */
private const val LATTICE = 8

/** How far from a row's end its time may lie, how tall a band of it is read, and the blank that ends it. */
private const val END_ROOM = 160
private const val END_BAND = 22
private const val GAP_PX = 8

/** The widest a short time ("5m") is at 1.3 text; the whole "5 min ago" is about twice that. */
private const val BRIEF_PX = 40f

/** How far Try again may sit from the notice's edge (a button's own padding at most). */
private const val EDGE_PX = 20f

/** Far from [ground]: a pixel the eye reads as drawn (ink on a surface of [ground]'s colour). */
private fun far(c: Color, ground: Color): Boolean =
    abs(c.red - ground.red) + abs(c.green - ground.green) + abs(c.blue - ground.blue) > .25f

/** Two colours of the same ink (one fill, read twice). */
private fun sameInk(a: Color, b: Color): Boolean =
    abs(a.red - b.red) < .06f && abs(a.green - b.green) < .06f && abs(a.blue - b.blue) < .06f

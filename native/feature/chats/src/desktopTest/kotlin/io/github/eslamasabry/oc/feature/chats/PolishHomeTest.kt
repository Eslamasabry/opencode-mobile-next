package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import io.github.eslamasabry.oc.ui.gate.flicker.FlickerFailure
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.logo.KitLogoLoader
import io.github.eslamasabry.oc.ui.kit.logo.LocalKitLogoLoader
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kitchat.LocalKitRedactor
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class PolishHomeTest {
    private val savedLocale: Locale = Locale.getDefault()
    private val arabicSymbols = NumberSymbols(zeroDigit = '\u0660', grouping = '\u066c', decimal = '\u066b')
    private val outDir = File("build/polish-render")

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

    private fun laptopNotAnsweringState() =
        ChatsUi(
            phase = ChatsPhase.List,
            settingsAvailable = true,
            allCount = 2,
            recent =
                persistentListOf(
                    openCodeRow("login", "Fix the login bug", "api"),
                    openCodeRow("notes", "Plan the release notes", "docs"),
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

    private fun ComposeUiTest.showHome(
        state: ChatsUi,
        dark: Boolean,
        locale: String,
        fontScale: Float = 1f,
        notice: AppNotice? = null,
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                ChatEnvironment {
                    KitTheme(
                        appearance =
                            KitAppearance(
                                mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                motion = MotionLevel.Off,
                            ),
                        platform = DesktopKitPlatform(),
                        localeTag = locale,
                        stillFrame = true,
                    ) {
                        CompositionLocalProvider(
                            LocalKitRedactor provides Redactor(),
                            LocalKitLogoLoader provides marks,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                val status = notice?.let { homeAppNoticeStatus(it, {}, {}) }
                                ChatsScreen(state, RecordingChatsActions(), appStatus = status)
                            }
                        }
                    }
                }
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()
    }

    private data class RenderShot(val name: String, val dark: Boolean, val locale: String, val fontScale: Float)

    private fun renderOne(shot: RenderShot, state: ChatsUi, notice: AppNotice?): FlickerFailure? =
        try {
            kitUiTest(360, 800) {
                showHome(state, dark = shot.dark, locale = shot.locale, fontScale = shot.fontScale, notice = notice)
                val file = File(outDir, "${shot.name}.png")
                file.parentFile.mkdirs()
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                assertTrue(file.isFile)
            }
            null
        } catch (e: FlickerFailure) {
            e
        }

    private fun renderScene(
        finding: Int,
        locale: String,
        state: ChatsUi,
        notice: AppNotice? = null,
    ) {
        Locale.setDefault(Locale.forLanguageTag(locale))
        Numbers.pinned = if (locale == "ar") arabicSymbols else NumberSymbols.Latin
        val shots =
            listOf(true to "dark", false to "light").flatMap { (dark, tone) ->
                listOf("100" to 1f, "130" to 1.3f).map { (scaleTag, fontScale) ->
                    RenderShot("$finding-$PHASE-$tone-$locale-$scaleTag", dark, locale, fontScale)
                }
            }
        val firstFlicker = shots.mapNotNull { renderOne(it, state, notice) }.firstOrNull()
        firstFlicker?.let { throw it }
    }

    @Test
    fun `B-585 the Arabic Running chip uses Arabic digits`() {
        Locale.setDefault(Locale.forLanguageTag("ar"))
        Numbers.pinned = arabicSymbols
        kitUiTest(360, 800) {
            showHome(arabicChipState(), dark = true, locale = "ar")
            onNodeWithContentDescription("قيد العمل · ١").assertExists()
            onNodeWithText("قيد العمل · ١", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun `B-555 This phone that does not answer names no computer and no VPN`() {
        Locale.setDefault(Locale.forLanguageTag("en"))
        Numbers.pinned = NumberSymbols.Latin
        kitUiTest(360, 800) {
            showHome(failedOnThisPhone(), dark = true, locale = "en")
            val computerNodes =
                onAllNodesWithText("computer", substring = true, ignoreCase = true).fetchSemanticsNodes()
            val vpnNodes =
                onAllNodesWithText("VPN", substring = true, ignoreCase = true).fetchSemanticsNodes()
            assertEquals(0, computerNodes.size)
            assertEquals(0, vpnNodes.size)
        }
    }

    @Test
    fun `polish renders`() {
        renderScene(42, "ar", arabicChipState())
        renderScene(11, "ar", appClosedArabicState(), notice = appClosedArabicNotice())
        renderScene(12, "en", failedOnThisPhone())
        renderScene(12, "ar", failedOnThisPhone())
        renderScene(30, "en", laptopNotAnsweringState())
    }

    private companion object {
        const val PHASE = "after"
    }
}

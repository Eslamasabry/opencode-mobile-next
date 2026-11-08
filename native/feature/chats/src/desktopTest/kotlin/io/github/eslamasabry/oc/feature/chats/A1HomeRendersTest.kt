package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedPermission
import io.github.eslamasabry.oc.engine.agents.feed.FeedRunning
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsListSourceUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
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
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A1 Conversations home, rendered for the eye (docs/native/qa/home-a1-2026-11-08): one phone screen per picture at the
 * mockup's size (412 x 915 dp at 2.5x), Glass and Solid, dark and light, with running, needs-you and more than five
 * recent conversations. The agent marks are the app's own bundled marks. Writes `build/a1-renders/<name>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class A1HomeRendersTest {
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val marks = KitLogoLoader { id, _ ->
        File(System.getProperty("user.dir"), "../../androidApp/src/main/assets/marks/$id.svg").takeIf { it.isFile }?.readText()
    }

    private fun feedItem(
        id: String,
        title: String,
        minutesAgo: Long,
        preview: String = "",
        dir: String = "/root/projects/oc_app",
        status: ChatStatus = ChatStatus.Idle,
        agent: String = "opencode",
    ): ChatFeedItem =
        item(id, dir, at = NOW - minutesAgo * 60_000L, title = title, status = status)
            .copy(preview = preview, agentId = agent)

    private fun state(project: String? = null, arabic: Boolean = false): ChatsUi {
        val ar = arabic
        val items =
            listOf(
                feedItem(
                    "explain", if (ar) "اشرح هذا المشروع" else "Explain this project", 0,
                    if (ar) "أقرأ lib/ui/kit لأفهم كيف تتصل الأجزاء" else "Reading lib/ui/kit to map how the parts fit together",
                    status = ChatStatus.Running, agent = "claude",
                ).copy(running = FeedRunning(NOW - 4 * 60_000L)),
                feedItem("ask", if (ar) "شغّل الاختبارات" else "Run the tests", 5, status = ChatStatus.NeedsYou)
                    .copy(permission = FeedPermission("req1", "npm test")),
                feedItem("hi", if (ar) "مرحبًا" else "Hi", 60 * 26, if (ar) "مرحبًا! على ماذا تريد أن نعمل؟" else "Hi! What would you like to work on today?"),
                feedItem(
                    "who", if (ar) "تعرف من أنت وماذا تفعل" else "You know who you are and what you do", 60 * 27,
                    if (ar) "أنا OpenCode، وكيل برمجة يعمل في مجلدك" else "I’m OpenCode, a coding agent working in your folder",
                    dir = "/root/projects/site",
                ),
                feedItem("ready", if (ar) "ابدأ" else "Start the release notes", 60 * 30, if (ar) "جاهز متى كنت جاهزًا." else "Ready when you are.", agent = "codex"),
                feedItem("build", if (ar) "ماذا نبني؟" else "What should we build?", 60 * 50, if (ar) "أهلًا، ماذا نبني اليوم؟" else "Hi there — what should we build?"),
                feedItem("help", if (ar) "مساعدة" else "Fix the login bug", 60 * 52, dir = "/root/projects/site"),
                feedItem("older1", "Older one", 60 * 80, "This one is past the five"),
                feedItem("older2", "Older two", 60 * 90, "So is this one"),
            )
        val base =
            ChatsState.build(
                ChatFeedSnapshot(items.toImmutableList()),
                ReturnBrief.None,
                ChatsFilters(projectDirectory = project),
                startOfTodayMs = NOW - 12 * 3_600_000L,
                settingsAvailable = true,
                nowMs = NOW,
            )
        return base.copy(
            needsYou = 0,
            home =
                base.home.copy(
                    sources =
                        persistentListOf(
                            ChatsListSourceUi(
                                profile = ProfileId("this-phone"),
                                name = if (ar) "هذا الهاتف" else "This phone",
                                shown = true,
                                main = true,
                                onThisPhone = true,
                                unreachable = false,
                            )
                        )
                ),
        )
    }

    private fun render(name: String, style: Style, dark: Boolean, ui: ChatsUi, locale: String = "en") {
        Locale.setDefault(Locale.forLanguageTag(locale))
        runDesktopComposeUiTest(W_PX, H_PX) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(SCALE, 1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance =
                                KitAppearance(
                                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                    motion = MotionLevel.Off,
                                    style = style,
                                ),
                            platform = DesktopKitPlatform(),
                            localeTag = locale,
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitRedactor provides Redactor(), LocalKitLogoLoader provides marks) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ChatsScreen(ui, RecordingChatsActions())
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()
            mainClock.advanceTimeBy(1_500)
            waitForIdle()
            val file = File("build/a1-renders/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
            assertTrue(file.isFile)
        }
    }

    @Test
    fun `A1-HOME-16 render Glass and Solid, dark and light, with running, needs-you and more than five recent`() {
        render("home-a1-glass-dark", Style.Glass, dark = true, state())
        render("home-a1-glass-light", Style.Glass, dark = false, state())
        render("home-a1-solid-dark", Style.Solid, dark = true, state())
        render("home-a1-solid-light", Style.Solid, dark = false, state())
        render("home-a1-solid-dark-project", Style.Solid, dark = true, state(project = "/root/projects/oc_app"))
        render("home-a1-glass-dark-ar", Style.Glass, dark = true, state(arabic = true), locale = "ar")
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val SCALE = 2.5f
        const val W_PX = 1030
        const val H_PX = 2288
    }
}

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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SessionId
import io.github.eslamasabry.oc.core.model.SessionTime
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedSnapshot
import io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsState
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.design.style.Style
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.toImmutableList

/** "Mark all seen" is an action: it is drawn as a button like the home's other inline actions, not as a heading. */
@OptIn(ExperimentalTestApi::class)
class HomeInlineActionTest {
    @Test
    fun `Mark all seen has a face of its own like Try again, not bare bold words`() =
        runDesktopComposeUiTest(360, 900) {
            Locale.setDefault(Locale.ENGLISH)
            val brief =
                ReturnBrief.build(
                    listOf(Session(SessionId("old"), directory = "/work/api", time = SessionTime(idleMs = 10))),
                    true, false, { true }, { false }, { null }, scope = BRIEF_SCOPE,
                )
            val ui = ChatsState.build(ChatFeedSnapshot(listOf(item("a", at = 12_000)).toImmutableList()), brief, ChatsFilters(), 10_000, true)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off, style = Style.Solid),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) { ChatsScreen(ui, RecordingChatsActions()) }
                        }
                    }
                }
            }
            waitForIdle()
            val bounds = onNodeWithText("Mark all seen").getBoundsInRoot()
            val image = onRoot().captureToImage().toAwtImage()
            val y = ((bounds.top + bounds.bottom).value / 2).toInt()
            val ground = image.getRGB(2, y)
            // Just inside the button's own edge, clear of its words: a face, not the page.
            val inside = image.getRGB(bounds.left.value.toInt() + 4, y)
            assertTrue(ground != inside, "the action at $bounds has no face of its own")
        }
}

package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotice
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNoticeKind
import io.github.eslamasabry.oc.feature.chats.presentation.HomeAgentNotices
import io.github.eslamasabry.oc.feature.chats.presentation.RowStatus
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Issue 147: a run that failed because the agent's sign-in lapsed reads "Sign in needed" on the Home row, not "Failed". */
@OptIn(ExperimentalTestApi::class)
class HomeSignedOutRowTest {
    private fun words(ui: ChatsUi): String {
        var out = ""
        kitUiTest(412, 1200) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    ChatEnvironment {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Light, motion = MotionLevel.Off),
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
            out =
                onAllNodes(SemanticsMatcher("has words") { it.config.getOrNull(SemanticsProperties.ContentDescription) != null })
                    .fetchSemanticsNodes()
                    .joinToString(" | ") { it.config[SemanticsProperties.ContentDescription].joinToString(" ") }
        }
        return out
    }

    private fun failedHome(signedOut: Boolean): ChatsUi {
        val base = mixed()
        val rows = base.recent.map { it.copy(status = RowStatus.Failed) }.toImmutableList()
        return base.copy(
            recent = rows,
            agentNotices =
                if (signedOut) HomeAgentNotices(lines = persistentListOf(HomeAgentNotice("opencode", "OpenCode", HomeAgentNoticeKind.SignedOut)))
                else HomeAgentNotices(),
        )
    }

    @Test
    fun `a failed run of a signed-out agent says Sign in needed, a failed run of a signed-in one still says Failed`() {
        val out = words(failedHome(signedOut = true))
        assertTrue("Sign in needed" in out && "Failed" !in out, "signed out: $out")
        val ok = words(failedHome(signedOut = false))
        assertTrue("Failed" in ok && "Sign in needed" !in ok, "signed in: $ok")
    }
}

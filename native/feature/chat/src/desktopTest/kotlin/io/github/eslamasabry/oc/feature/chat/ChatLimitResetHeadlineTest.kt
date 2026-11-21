package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.ChatPresenter
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kitchat.code.KitStandaloneRedaction
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted

/**
 * signin-followups: a failed reply that hit a plan limit names the reset in its headline, in the person's time zone
 * and clock format, not only under Details.
 */
@OptIn(ExperimentalTestApi::class)
class ChatLimitResetHeadlineTest {
    private val said = kotlin.time.Instant.parse("2026-11-16T03:00:00Z").toEpochMilliseconds()
    private val sentence = "You've hit your weekly limit · resets Nov 20, 1am (UTC)"

    /** The phone's own formatter: Dubai (+4) and the 12 or 24 hour setting, as the Android platform follows it. */
    private fun phone(pattern: String) =
        object : KitPlatform by DesktopKitPlatform() {
            override fun dateTime(atMs: Long, localeTag: String): String =
                java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH)
                    .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Dubai") }
                    .format(java.util.Date(atMs))
        }

    private val composer =
        object : ComposerHost {
            @Composable
            override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
                KitText("Composer slot", modifier)
            }
        }

    private class Outside : ChatOutside {
        override fun mask(text: String): String = text

        override fun copy(text: String): Boolean = true

        override fun share(text: String): Boolean = true

        override fun checkLink(url: String): LinkCheck = LinkCheck.Ask("example.com")

        override fun openLink(url: String): Boolean = true
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(platform: KitPlatform, reason: String, createdMs: Long?) {
        val world = FakeChatWorld()
        world.transcript.value =
            stateOf(
                prompt("u1", "go"),
                FooterRow("footer:u1", "u1", failed = true, reason = reason, createdMs = createdMs),
            )
        val p = ChatPresenter(world, RecordingNavigator(), Immediate, outside = Outside())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val states = p.states(scope, Dispatchers.Unconfined, SharingStarted.Eagerly)
        setContent {
            val state by states.collectAsState()
            KitTheme(
                platform = platform,
                appearance = KitAppearance(motion = MotionLevel.Off),
                localeTag = "en",
                stillFrame = true,
            ) {
                KitStandaloneRedaction { ChatScreen(state, p, composer) }
            }
        }
    }

    @Test
    fun `SIGNIN-C-1 a limit's headline carries the reset on a 12 hour clock`() = kitUiTest {
        show(phone("MMM d, h:mm a"), sentence, said)
        onNodeWithText("Plan limit reached · resets Nov 20, 5:00 AM").assertExists()
        onNodeWithText("This model's usage limit is reached for now.").assertDoesNotExist()
    }

    @Test
    fun `SIGNIN-C-2 and on a 24 hour clock`() = kitUiTest {
        show(phone("MMM d, HH:mm"), sentence, said)
        onNodeWithText("Plan limit reached · resets Nov 20, 05:00").assertExists()
    }

    @Test
    fun `SIGNIN-C-3 a limit with no reset keeps today's headline`() = kitUiTest {
        show(phone("MMM d, h:mm a"), "You've reached your usage limit", said)
        onNodeWithText("This model's usage limit is reached for now.").assertExists()
        onNodeWithText("· resets", substring = true).assertDoesNotExist()
    }

    @Test
    fun `SIGNIN-C-4 a reply with no time keeps today's headline`() = kitUiTest {
        show(phone("MMM d, h:mm a"), sentence, null)
        onNodeWithText("This model's usage limit is reached for now.").assertExists()
    }
}

package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * S31 story 6: Settings › Conversations with Flutter's two switches, "Show reasoning" and "Show time, tokens and
 * cost" (`settings_group_page.dart` SettingsGroup.chat), when the host serves them. With OC_S31_SHOTS set the page is
 * also written at Flutter's golden size beside `p310_settings_group_chat_dark.png`.
 */
@OptIn(ExperimentalTestApi::class)
class S31ConversationsTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test
    fun `B-SET-271 Conversations shows Flutter's switches and row titles, and each switch flips its choice`() =
        runDesktopComposeUiTest(412, 915) {
            val choices = FakeChoices()
            lateinit var presenter: SettingsPresenter
            setContent {
                SettingsThemed(dark = true) {
                    val scope = rememberCoroutineScope()
                    val p = remember {
                        SettingsPresenter(
                                scope,
                                MutableStateFlow(phoneFacts().copy(phraseChips = true, transcriptDisplay = true)),
                                choices,
                                RecordingOpeners(),
                            )
                            .also { presenter = it }
                    }
                    val state by p.state.collectAsState()
                    SettingsScreen(state, p)
                }
            }
            waitForIdle()
            presenter.onOpen(SettingsPage.Group(SettingsGroupId.Conversations))
            waitForIdle()
            onNodeWithText("Show reasoning").assertExists()
            onNodeWithText("Show time, tokens and cost").assertExists()
            // Flutter's row titles for the shell and voice rows.
            onNodeWithText("Shell for commands").assertExists()
            onNodeWithText("Voice typing").assertExists()
            System.getenv("OC_S31_SHOTS")?.let(::File)?.let { out ->
                out.mkdirs()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "native-settings-conversations-dark.png"),
                )
            }
            onNodeWithText("Show reasoning").performClick()
            onNodeWithText("Show time, tokens and cost").performClick()
            waitForIdle()
            assertTrue(choices.value.value.reasoning, "Show reasoning saved on")
            assertTrue(choices.value.value.timestamps, "Show time, tokens and cost saved on")
        }
}

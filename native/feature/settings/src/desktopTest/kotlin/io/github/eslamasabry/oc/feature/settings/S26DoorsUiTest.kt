package io.github.eslamasabry.oc.feature.settings

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.MemoryPreviewSetting
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow

/** s26: the AI Team and FactsBook doors in Settings, found by their visible words. */
@OptIn(ExperimentalTestApi::class)
class S26DoorsUiTest {
    private val openers = RecordingOpeners()
    private lateinit var presenter: SettingsPresenter
    private val memory =
        object : MemoryPreviewSetting {
            var on = false

            override fun enabled(): Boolean = on

            override suspend fun setEnabled(on: Boolean): Boolean {
                this.on = on
                return true
            }

            override val phoneNode: Boolean = true
        }

    @BeforeTest fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun ComposeUiTest.show() {
        setContent {
            SettingsThemed {
                val scope = rememberCoroutineScope()
                val p =
                    remember {
                        SettingsPresenter(scope, MutableStateFlow(phoneFacts()), FakeChoices(), openers, memoryPreview = memory)
                            .also { presenter = it }
                    }
                val state by p.state.collectAsState()
                SettingsScreen(state, p)
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.tap(text: String) {
        onNodeWithText(text).performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `B-SET-260 the Agent group has AI Team and a tap opens the team page`() = runComposeUiTest {
        show()
        presenter.onOpen(SettingsPage.Group(SettingsGroupId.Agent)); waitForIdle()
        tap("AI Team")
        assertEquals(listOf(SettingsOutside.AiTeam), openers.opened)
    }

    @Test
    fun `B-SET-261 About Developer turns Memory on, then offers Use memory on this phone`() = runComposeUiTest {
        show()
        presenter.onOpen(SettingsPage.About); waitForIdle()
        onNodeWithText("Developer").performScrollTo().assertExists()
        onNodeWithText("Use memory on this phone").assertDoesNotExist()
        tap("Memory (preview)")
        assertEquals(true, memory.on)
        tap("Use memory on this phone")
        assertEquals(listOf(SettingsOutside.MemoryOnPhone), openers.opened)
    }
}

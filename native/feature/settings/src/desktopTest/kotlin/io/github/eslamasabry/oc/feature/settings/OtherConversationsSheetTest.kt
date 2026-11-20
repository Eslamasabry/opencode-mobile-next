package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.EdgeDockSetting
import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPresenter
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** B-LAMP-005: Settings › Conversations › Other conversations shows only Edge light and Hidden and stores the choice. */
@OptIn(ExperimentalTestApi::class)
class OtherConversationsSheetTest {
    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    private class Setting(start: OtherConversationsMode) : EdgeDockSetting {
        private val state = MutableStateFlow(start)
        override val mode: StateFlow<OtherConversationsMode> get() = state

        override fun setMode(mode: OtherConversationsMode) {
            state.value = mode
        }
    }

    @Test
    fun `B-LAMP-005 Settings Other conversations offers only Edge light and Hidden and saves the choice`() =
        runDesktopComposeUiTest(412, 915) {
            val setting = Setting(OtherConversationsMode.EdgeLight)
            lateinit var presenter: SettingsPresenter
            setContent {
                SettingsThemed(dark = true) {
                    val scope = rememberCoroutineScope()
                    val p = remember {
                        SettingsPresenter(
                                scope,
                                MutableStateFlow(phoneFacts()),
                                FakeChoices(),
                                RecordingOpeners(),
                                edgeDock = setting,
                            )
                            .also { presenter = it }
                    }
                    val state by p.state.collectAsState()
                    KitSheetHost(Modifier.fillMaxSize()) { SettingsScreen(state, p) }
                }
            }
            waitForIdle()
            presenter.onOpen(SettingsPage.Group(SettingsGroupId.Conversations))
            waitForIdle()
            onNodeWithText("Other conversations").performClick()
            waitForIdle()
            val inSheet = hasAnyAncestor(hasTestTag("other-conversations-choices"))
            onNode(inSheet and hasText("Edge light")).assertExists()
            onNode(inSheet and hasText("Hidden")).assertExists()
            onNode(inSheet and hasText("Rings")).assertDoesNotExist()
            onNode(inSheet and hasText("Hidden")).performClick()
            waitForIdle()
            assertEquals(OtherConversationsMode.Hidden, setting.mode.value)
        }
}

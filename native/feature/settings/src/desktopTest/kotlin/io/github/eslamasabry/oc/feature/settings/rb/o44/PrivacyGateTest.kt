package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.settings.ui.localBytes
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class PrivacyGateTest {
    private fun privacy() =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Privacy),
            localData =
                LocalDataUi(
                    LocalDataFacts(
                        queued = 3,
                        queueBytes = 18 * 1024L,
                        drafts = 2,
                        draftBytes = 3 * 1024L,
                        servers = persistentListOf(ProfileId("laptop")),
                    )
                ),
        )

    @Test
    fun settings_privacy_loaded() {
        FlutterGoldenGate.check("settings_privacy_loaded", frame = FlutterGoldenGate.Frame.Screen) {
            SettingsScreen(privacy(), RecordingActions())
        }
    }

    @Test
    fun settings_privacy_loaded_1280x800() {
        FlutterGoldenGate.check(
            "settings_privacy_loaded_1280x800",
            frame = FlutterGoldenGate.Frame.Screen,
            modes = listOf("dark"),
        ) {
            SettingsScreen(privacy(), RecordingActions())
        }
    }

    @Test
    fun `B-STATE-080 delete asks before invoking the existing confirmed action`() =
        runComposeUiTest {
            val state = mutableStateOf(privacy())
            var deletes = 0
            val actions =
                object : SettingsActions by RecordingActions() {
                    override fun onAskDiscard(kind: LocalDataKind) {
                        state.value =
                            state.value.copy(
                                localData = state.value.localData!!.copy(confirm = kind)
                            )
                    }

                    override fun onConfirmDiscard() {
                        deletes++
                    }
                }
            setContent { SettingsThemed { SettingsScreen(state.value, actions) } }
            onNodeWithText("Storage used").assertExists()
            onNodeWithText("Clear drafts").performClick()
            assertEquals(0, deletes)
            onNodeWithText("Delete 2 drafts").performClick()
            assertEquals(1, deletes)
        }

    @Test
    fun `B-STATE-080 a queue with prompts kept for review asks one question that names them`() =
        runComposeUiTest {
            var deletes = 0
            val asked =
                privacy().let {
                    it.copy(
                        localData =
                            LocalDataUi(
                                it.localData!!.facts!!.copy(held = 1),
                                confirm = LocalDataKind.QueueHeld,
                            )
                    )
                }
            val actions =
                object : SettingsActions by RecordingActions() {
                    override fun onConfirmDiscard() {
                        deletes++
                    }
                }
            setContent { SettingsThemed { SettingsScreen(asked, actions) } }
            onNodeWithText("Delete queued prompts?").assertExists()
            onNodeWithText(
                "1 of them may already have reached the server. Deleting only removes them from this phone, " +
                    "and nothing more is sent."
            ).assertExists()
            onNodeWithText("Delete 3 queued prompts").performClick()
            assertEquals(1, deletes)
        }

    @Test
    fun `B-STATE-080 unreadable storage does not enable an unsupported destructive path`() =
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        privacy()
                            .copy(localData = LocalDataUi(LocalDataFacts(queueReadable = false))),
                        RecordingActions(),
                    )
                }
            }
            onNodeWithText("Clear queued prompts").assertIsNotEnabled()
        }

    @Test
    fun `B-STATE-080 byte readout rounds the final decimal as Flutter does`() {
        assertEquals("0 B", localBytes(0))
        assertEquals("1.1 KB", localBytes(1127))
        assertEquals("21.0 KB", localBytes(21 * 1024L))
        assertEquals("1.0 MB", localBytes(1024 * 1024L))
    }
}

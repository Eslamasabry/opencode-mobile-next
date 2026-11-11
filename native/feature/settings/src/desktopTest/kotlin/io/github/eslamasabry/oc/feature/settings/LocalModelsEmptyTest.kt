@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.LocalModelStorageFacts
import io.github.eslamasabry.oc.feature.settings.ui.LocalModelsBody
import kotlin.test.Test
import kotlin.test.assertTrue

/** ST1: This app › Storage with no assistant model leads to where the app's storage is measured. */
class LocalModelsEmptyTest {
    @Test
    fun `ST1 no assistant model offers Open This phone, where App storage measures the app`() {
        val actions = RecordingActions()
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    LocalModelsBody(LocalModelStorageFacts(loading = false, phoneStorage = true), actions)
                }
            }
            onNodeWithText("No assistant model is stored on this phone").assertExists()
            onNodeWithText("Open This phone").performClick()
        }
        assertTrue("outside:ThisPhone" in actions.calls)
    }

    @Test
    fun `ST1 without This phone the empty page offers no dead door`() = runComposeUiTest {
        setContent {
            SettingsThemed { LocalModelsBody(LocalModelStorageFacts(loading = false), RecordingActions()) }
        }
        onNodeWithText("Open This phone").assertDoesNotExist()
    }
}

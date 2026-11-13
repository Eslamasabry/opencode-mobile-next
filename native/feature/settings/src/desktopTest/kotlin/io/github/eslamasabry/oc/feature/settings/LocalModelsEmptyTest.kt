@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.LocalModelStorageFacts
import io.github.eslamasabry.oc.feature.settings.ui.LocalModelsBody
import kotlin.test.Test
import kotlin.test.assertTrue

/** ST1: This app > Storage is the app's storage; the assistant model is one row in it, only when stored. */
class LocalModelsEmptyTest {
    @Test
    fun `ST1 no assistant model still shows the app's storage and never an empty headline`() {
        val actions = RecordingActions()
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    LocalModelsBody(LocalModelStorageFacts(loading = false), actions)
                }
            }
            onNodeWithText("No assistant model is stored on this phone").assertDoesNotExist()
            onNodeWithText("Assistant model on this phone").assertDoesNotExist()
            onNodeWithText("Clear this app's storage").performClick()
        }
        assertTrue("outside:AppStorage" in actions.calls)
    }

    @Test
    fun `ST1 with an assistant model stored the page adds its row and Delete beside the app's storage`() {
        val actions = RecordingActions()
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    LocalModelsBody(
                        LocalModelStorageFacts(loading = false, present = true, size = "1.2 GB"),
                        actions,
                    )
                }
            }
            onNodeWithText("1.2 GB").assertExists()
            onNodeWithText("Delete model").assertExists()
            onNodeWithText("Clear this app's storage").assertExists()
        }
    }
}

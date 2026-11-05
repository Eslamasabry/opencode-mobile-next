@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitContentRow
import kotlin.test.Test

class ContentRowTest {
    @Test
    fun `below content keeps the row title and explanation and invents no tap`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    KitContentRow("Install and start", supporting = "On your computer") {
                        KitText("Command preview")
                    }
                }
            }
            onNodeWithText("Install and start").assertExists()
            onNodeWithText("On your computer").assertExists()
            onNodeWithText("Command preview").assertExists()
            onAllNodes(hasClickAction()).assertCountEquals(0)
        }
}

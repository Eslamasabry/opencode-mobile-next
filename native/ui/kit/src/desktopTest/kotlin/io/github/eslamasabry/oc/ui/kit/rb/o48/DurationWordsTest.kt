@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o48

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.text.kitDurationWords
import kotlin.test.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationWordsTest {
    @Test
    fun `durations follow Flutter largest units and clamp negative elapsed time`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Column {
                        KitText(kitDurationWords((-1).seconds))
                        KitText(kitDurationWords(45.minutes))
                        KitText(kitDurationWords(3.hours + 20.minutes))
                        KitText(kitDurationWords(1.days + 21.hours))
                        KitText(kitDurationWords(2.days))
                    }
                }
            }
            onNodeWithText("less than a minute").assertExists()
            onNodeWithText("45 min").assertExists()
            onNodeWithText("3 h 20 min").assertExists()
            onNodeWithText("1 d 21 h").assertExists()
            onNodeWithText("2 d").assertExists()
        }
}

package io.github.eslamasabry.oc.feature.chatcomposer.rb.o43

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.input.KitFieldKind
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComposerFieldTest {
    @Test
    fun `C19 one line stays centered in its target as text grows without an empty footer`() =
        runComposeUiTest {
            var scale by mutableStateOf(1f)
            val field = TextFieldState("Hello")
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                    CompositionLocalProvider(LocalDensity provides Density(1f, scale)) {
                        KitField(
                            field,
                            "Message",
                            Modifier.testTag("composer-field"),
                            kind = KitFieldKind.Multiline,
                            multilineLines = 1..8,
                            framed = false,
                            labelShown = false,
                            composer = true,
                        )
                    }
                }
            }
            val normal = onNodeWithTag("composer-field").fetchSemanticsNode().boundsInRoot.height
            assertEquals(48f, normal, 1f)
            runOnIdle { scale = 2f }
            val enlarged = onNodeWithTag("composer-field").fetchSemanticsNode().boundsInRoot.height
            assertEquals(48f, enlarged, 1f)
            onNode(hasSetTextAction()).performTextReplacement("First line\nSecond line")
            assertTrue(
                onNodeWithTag("composer-field").fetchSemanticsNode().boundsInRoot.height > enlarged
            )
        }

    @Test
    fun `C19 composer helper remains available when it has actual content`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                KitField(
                    TextFieldState(),
                    "Message",
                    helper = "Your draft stays here",
                    framed = false,
                    labelShown = false,
                    composer = true,
                )
            }
        }
        onNodeWithText("Your draft stays here").assertExists()
        onNode(hasSetTextAction()).assertExists()
    }
}

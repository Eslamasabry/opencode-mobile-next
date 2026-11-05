package io.github.eslamasabry.oc.feature.chats.rb.o41

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitStartBox
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StartBoxTest {
    @Test fun typingUpdatesTheOwnedFieldWithoutStartingAConversation() = runComposeUiTest {
        val field = TextFieldState()
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en") { KitStartBox(field, "What would you like to build?", minLines = 4) } }
        onNode(hasSetTextAction()).performTextInput("Build a shop")
        assertEquals("Build a shop", field.text.toString())
    }
}

package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitPressOrigin
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPressOrigin
import io.github.eslamasabry.oc.ui.kit.frame.KitKeyboardSource
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.LocalKitKeyboardSource
import kotlin.test.Test
import kotlin.test.assertTrue

/** Issue 147 (F-037): a menu opened with the keyboard up stays above the keyboard (and scrolls when taller). */
@OptIn(ExperimentalTestApi::class)
class KitMenuKeyboardTest {
    @Test
    fun `F-037 a menu opened with the keyboard up ends above the keyboard`() =
        runDesktopComposeUiTest(400, 800) {
            var open by mutableStateOf(false)
            setContent {
                CompositionLocalProvider(
                    LocalKitPressOrigin provides KitPressOrigin(),
                    LocalKitKeyboardSource provides KitKeyboardSource(WindowInsets(bottom = 300.dp), WindowInsets(bottom = 300.dp)),
                ) {
                    KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                        KitScreen(topBar = { KitTopBar("Chat") }) {
                            Box(Modifier.fillMaxSize()) {
                                KitText("Behind")
                                KitMenu(
                                    expanded = open,
                                    items = (1..14).map { KitMenuItem("Item $it", onSelect = {}) },
                                    onDismissRequest = { open = false },
                                    modifier = Modifier.align(Alignment.TopEnd),
                                ) {}
                            }
                        }
                    }
                }
            }
            open = true
            waitForIdle()
            val panel = onNodeWithTag("kit-menu").fetchSemanticsNode().boundsInWindow
            assertTrue(panel.bottom <= 800f - 300f, "the menu ends at ${panel.bottom}, the keyboard starts at 500: it lies under it")
        }
}

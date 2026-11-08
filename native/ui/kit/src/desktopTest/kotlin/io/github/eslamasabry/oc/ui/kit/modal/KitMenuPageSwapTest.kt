package io.github.eslamasabry.oc.ui.kit.modal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.FakeKitPlatform
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.action.KitIconButton
import io.github.eslamasabry.oc.ui.kit.foundation.KitPressOrigin
import io.github.eslamasabry.oc.ui.kit.foundation.LocalKitPressOrigin
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * B-178: the chat header's More menu opens its pages ("Go to", "More") by swapping the items of the one open menu. The
 * open menu showed the items it was first given and ignored the swap, so the page never opened.
 */
@OptIn(ExperimentalTestApi::class)
class KitMenuPageSwapTest {
    @Test
    fun `B-178 choosing a row that swaps the menu's items shows the new items and they can be chosen`() =
        runDesktopComposeUiTest(400, 800) {
            var chosen = ""
            setContent {
                KitTheme(platform = FakeKitPlatform(), localeTag = "en") {
                    var page by remember { mutableStateOf("main") }
                    val items =
                        if (page == "main")
                            listOf(
                                KitMenuItem("Copy transcript", onSelect = {}, dismissal = KitMenuDismissal.ByAction),
                                KitMenuItem(
                                    "Go to",
                                    onSelect = { page = "go" },
                                    group = KitMenuGroup("g"),
                                    dismissal = KitMenuDismissal.ByAction,
                                ),
                            )
                        else
                            listOf(
                                KitMenuItem("Back", onSelect = { page = "main" }, dismissal = KitMenuDismissal.ByAction),
                                KitMenuItem("Files", onSelect = { chosen = "Files" }, dismissal = KitMenuDismissal.ByAction),
                            )
                    Box(Modifier.fillMaxSize()) {
                        KitMenu(expanded = true, items = items, onDismissRequest = {}) {
                            Box(Modifier.padding(16.dp).testTag("anchor"))
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Go to").performClick()
            waitForIdle()
            onNodeWithText("Files").assertIsDisplayed()
            onNodeWithText("Files").performClick()
            waitForIdle()
            assertEquals("Files", chosen)
        }
}

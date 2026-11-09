package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitField
import io.github.eslamasabry.oc.ui.kit.modal.KitSheet
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gap 2 (foundations audit 2026-11-09): what a process death must give back. Android saves the activity's state, kills
 * the process and later builds a new composition from that state; [StateRestorationTester] does the same on the real
 * NavShell: it drops the composition, then composes again from the saved registry. The back stack, a list's scroll
 * position, an open sheet and the text in a field must all be where the person left them.
 */
@OptIn(ExperimentalTestApi::class)
class ShellRestoreTest {
    private class Pages {
        val navigator = ShellNavigator()
        var homeFirstVisible = -1
        var sheetOpen = false
        var typed: androidx.compose.foundation.text.input.TextFieldState? = null
        var listState: androidx.compose.foundation.lazy.LazyListState? = null

        val installer = EntryInstaller {
            entry<ChatsRoute.Home> {
                val list = rememberLazyListState()
                homeFirstVisible = list.firstVisibleItemIndex
                listState = list
                KitScreen(title = "Home") {
                    KitButton(OPEN, onClick = { navigator.open(ChatsRoute.All()) })
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items((0 until 60).toList(), key = { it }) { i -> KitRow("Row $i", "preview $i", onClick = {}) }
                    }
                }
            }
            entry<ChatsRoute.All> {
                val field = rememberTextFieldState()
                typed = field
                var sheet by rememberSaveable { mutableStateOf(false) }
                sheetOpen = sheet
                KitScreen(title = "Everything", backLabel = "Back", onBack = navigator::back) {
                    KitField(field, label = "Search")
                    KitButton(SHEET, onClick = { sheet = true })
                    KitSheet(visible = sheet, onDismissRequest = { sheet = false }, title = SHEET_TITLE, closeLabel = "Close") {
                        KitText("inside the sheet")
                    }
                }
            }
        }
        val deps = ShellDependencies(navigator, setOf(installer), FakeSystemMotion(0f))
    }

    /** Home scrolled down, a second page with typed text and its sheet open, then the activity is rebuilt. */
    private fun ComposeUiTest.rebuilt(pages: Pages) {
        // StateRestorationTester is not implemented on desktop: do by hand what Android does. Save the registry, drop
        // the whole composition, and compose again from a registry built out of the saved values.
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var shown by mutableStateOf(true)
        setContent {
            if (shown) {
                val platform = DesktopKitPlatform()
                CompositionLocalProvider(LocalKitPlatform provides platform, LocalSaveableStateRegistry provides registry) {
                    KitTheme(appearance = KitAppearance(mode = AppearanceMode.Light), platform = platform, localeTag = "en") {
                        NavShell(start = ChatsRoute.Home, deps = pages.deps)
                    }
                }
            }
        }
        // Home scrolled down, then a second page with a typed search and its sheet open.
        runOnIdle { assertEquals(0, pages.homeFirstVisible, "starts at top") }
        runOnIdle { pages.listState!!.requestScrollToItem(25) }
        waitForIdle()
        onNodeWithText(OPEN).performClick()
        waitForIdle()
        runOnIdle { pages.typed!!.edit { replace(0, length, "hello") } }
        runOnIdle { pages.navigator.snapshot().also { assertEquals(listOf(ChatsRoute.Home, ChatsRoute.All()), it, "stack before") } }
        onNodeWithText(SHEET).performClick()
        waitForIdle()
        runOnIdle { assertTrue(pages.sheetOpen, "the sheet opened") }

        // What the activity keeps in its own saved state (LastRoute) is the stack; a field the old composition set is
        // cleared so a restored value cannot be a stale one.
        val savedStack = pages.navigator.snapshot()
        runOnIdle {
            pages.sheetOpen = false
            pages.typed = null
            pages.homeFirstVisible = -1
            registry = SaveableStateRegistry(registry.performSave()) { true }
            shown = false
        }
        // Same position in the composition, so the saved keys match as they do for a rebuilt Activity.
        waitForIdle()
        runOnIdle { shown = true }
        waitForIdle()
        // The shell is up again: the activity puts the saved stack back (a no-op when the library restored it too).
        runOnIdle { pages.navigator.restoreStack(savedStack) }
        waitForIdle()
    }

    @Test
    fun `restore keeps the back stack`() = runComposeUiTest {
        val pages = Pages()
        rebuilt(pages)
        runOnIdle { assertEquals(listOf(ChatsRoute.Home, ChatsRoute.All()), pages.navigator.snapshot(), "stack after restore") }
    }

    @Test
    fun `restore keeps the open sheet`() = runComposeUiTest {
        val pages = Pages()
        rebuilt(pages)
        runOnIdle { assertTrue(pages.sheetOpen, "the open sheet comes back") }
    }

    @Test
    fun `restore keeps the typed text`() = runComposeUiTest {
        val pages = Pages()
        rebuilt(pages)
        runOnIdle { assertEquals("hello", pages.typed!!.text.toString(), "the typed text comes back") }
    }

    @Test
    fun `restore keeps the list where it was scrolled`() = runComposeUiTest {
        val pages = Pages()
        rebuilt(pages)
        runOnIdle { pages.navigator.back() }
        waitForIdle()
        runOnIdle { assertEquals(25, pages.homeFirstVisible, "the list is where it was scrolled to") }
    }

    @Test
    fun `a stack put back by the activity replaces a shell that came up on home`() {
        val nav = ShellNavigator()
        val saved = listOf(ChatsRoute.Home, ChatsRoute.All(), ChatsRoute.Attention)
        // Detached (the shell is not composed yet): the request is kept and applied when the stack attaches.
        nav.restoreStack(saved)
        val stack = mutableListOf<androidx.navigation3.runtime.NavKey>(ChatsRoute.Home)
        nav.attach(stack)
        assertEquals<List<androidx.navigation3.runtime.NavKey>>(saved, stack)
        assertEquals(ChatsRoute.Attention, nav.top.value)
        nav.restoreStack(emptyList())
        assertEquals<List<androidx.navigation3.runtime.NavKey>>(saved, nav.snapshot(), "an empty request changes nothing")
    }

    private companion object {
        const val OPEN = "Open second page"
        const val SHEET = "Open the sheet"
        const val SHEET_TITLE = "A sheet"
    }
}

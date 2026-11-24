package io.github.eslamasabry.oc.ui.kit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.kit.frame.KitPaneWindow
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KitPaneWindowTest {
    @Test
    fun `B-KIT-300 a part inside a pane reads the pane's window`() = runDesktopComposeUiTest(1400, 900) {
        var seen: KitWindowInfo? = null
        setContent {
            CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(1280f, 900f, finePointer = true)) {
                Box(Modifier.width(420.dp).fillMaxHeight()) {
                    KitPaneWindow { seen = LocalKitWindow.current }
                }
            }
        }
        waitForIdle()
        val s = checkNotNull(seen)
        assertEquals(420f, s.widthDp)
        assertEquals(WindowClass.Compact, s.cls)
        assertEquals(900f, s.heightDp)
        assertEquals(true, s.finePointer, "the pointer kind stays the parent's")
    }

    @Test
    fun `B-KIT-301 a pane window follows its parent`() = runDesktopComposeUiTest(1400, 900) {
        var parent by mutableStateOf(1280f)
        var seen: KitWindowInfo? = null
        setContent {
            CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(parent, 900f)) {
                Box(Modifier.width((parent / 2).dp).fillMaxHeight()) {
                    KitPaneWindow { seen = LocalKitWindow.current }
                }
            }
        }
        waitForIdle()
        assertEquals(640f, checkNotNull(seen).widthDp)
        parent = 800f
        waitForIdle()
        assertEquals(400f, checkNotNull(seen).widthDp)
        parent = 1280f
        waitForIdle()
        assertEquals(640f, checkNotNull(seen).widthDp)
    }
}

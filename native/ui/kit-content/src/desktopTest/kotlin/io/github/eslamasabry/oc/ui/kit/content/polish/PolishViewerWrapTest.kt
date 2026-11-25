package io.github.eslamasabry.oc.ui.kit.content.polish

import io.github.eslamasabry.oc.ui.design.layout.WindowClass
import io.github.eslamasabry.oc.ui.kit.content.viewer.KitViewerKind
import io.github.eslamasabry.oc.ui.kit.content.viewer.kitViewerDefaultWrap
import kotlin.test.Test
import kotlin.test.assertTrue

class PolishViewerWrapTest {
    @Test
    fun `B-580 code wraps on a phone by default`() {
        assertTrue(
            kitViewerDefaultWrap(WindowClass.Compact, KitViewerKind.Code),
            "Code should wrap by default on Compact windows",
        )
    }
}

package io.github.eslamasabry.oc.feature.chat.rb.o42

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kitchat.transcript.KitReplyFooter
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ReplyFooterTest {
    @Test fun copyAndMoreHaveSeparateVisibleActions() = runComposeUiTest {
        var copied = 0
        var opened = 0
        setContent { KitTheme(platform = DesktopKitPlatform()) { KitReplyFooter("Copy reply", "More", { copied++ }, { opened++ }) } }
        onNodeWithContentDescription("Copy reply").performClick()
        assertEquals(1, copied)
        assertEquals(0, opened)
        onNodeWithContentDescription("More").performClick()
        assertEquals(1, opened)
    }
}

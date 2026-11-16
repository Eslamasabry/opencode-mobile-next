package io.github.eslamasabry.oc.feature.search

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.feature.search.ui.agentLeading
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F-038: Recent conversations drew the bare mark, an empty box on the phone; it uses the row's mark tile like the home. */
@OptIn(ExperimentalTestApi::class)
class SearchRecentMarkTest {
    @Test
    fun `I147-FIRST-RUN a recent conversation leads with its agent's mark in the tile, never the bare mark`() =
        runDesktopComposeUiTest(360, 200) {
            var shown: KitRowLeading? = null
            var none: KitRowLeading? = null
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en") {
                    shown = agentLeading("codex", "Codex")
                    none = agentLeading("", null)
                }
            }
            waitForIdle()
            val logo = shown as KitRowLeading.Logo
            assertEquals("codex", logo.id)
            assertFalse(logo.bare, "the bare mark drew an empty box in Search")
            assertTrue(none is KitRowLeading.Glyph)
        }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.kit.rb.o49

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TabularTextTest {
    @Test
    fun `code figures request tabular glyphs without changing ordinary title text`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    Column {
                        KitText("TEST-1234", role = TypeRole.Title, tabular = true)
                        KitText("Normal title", role = TypeRole.Title)
                    }
                }
            }
            val code = mutableListOf<TextLayoutResult>()
            onNodeWithText("TEST-1234").performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(code)
            }
            assertEquals("tnum", code.single().layoutInput.style.fontFeatureSettings)
            val title = mutableListOf<TextLayoutResult>()
            onNodeWithText("Normal title").performSemanticsAction(
                SemanticsActions.GetTextLayoutResult
            ) {
                it(title)
            }
            assertNotEquals("tnum", title.single().layoutInput.style.fontFeatureSettings)
        }
}

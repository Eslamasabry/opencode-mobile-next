package io.github.eslamasabry.oc.ui.kit

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowLeading
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class Kita1RowValueTest {
    @Test
    fun `B-KIT-027 finding 3 measured long value moves below title at normal scale and in RTL`() =
        runComposeUiTest {
            val rtl = mutableStateOf(false)
            val width = mutableStateOf(320)
            val long = mutableStateOf(true)
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    KitTheme(
                        KitAppearance(motion = MotionLevel.Off),
                        FakeKitPlatform(),
                        if (rtl.value) "ar" else "en",
                        stillFrame = true,
                    ) {
                        InWindow(width.value, 600) {
                            KitRow(
                                "Connection",
                                leading = KitRowLeading.Initials("PC"),
                                trailing =
                                    KitRowTrailing.Value(
                                        if (long.value)
                                            "A very long connection name that cannot share the title line"
                                        else "Home"
                                    ),
                            )
                        }
                    }
                }
            }
            for (isRtl in listOf(false, true)) {
                rtl.value = isRtl
                for (rowWidth in listOf(320, 412)) {
                    width.value = rowWidth
                    long.value = true
                    waitForIdle()
                    val title = onNodeWithTag("kit-row-title", true).getBoundsInRoot()
                    val value = onNodeWithTag("kit-row-value", true).getBoundsInRoot()
                    assertTrue(
                        value.top >= title.bottom,
                        "Long value must move below: $title $value",
                    )
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithTag("kit-row-title", true).performSemanticsAction(
                        SemanticsActions.GetTextLayoutResult
                    ) {
                        it(layouts)
                    }
                    assertFalse(
                        layouts.single().isLineEllipsized(0),
                        "Title remains readable: $title",
                    )
                    long.value = false
                    waitForIdle()
                    val shortTitle = onNodeWithTag("kit-row-title", true).getBoundsInRoot()
                    val shortValue = onNodeWithTag("kit-row-value", true).getBoundsInRoot()
                    assertTrue(shortValue.top < shortTitle.bottom, "Short value stays inline")
                }
            }
        }
}

package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.feature.settings.presentation.HealthContract
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.feature.settings.ui.collectWords
import io.github.eslamasabry.oc.ui.words.Res
import io.github.eslamasabry.oc.ui.words.collectNever
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

/** Every disclosure row, including Never sent, at the owner's small-phone width and larger text. */
@OptIn(ExperimentalTestApi::class)
class WhatWeCollectRowsWholeTest {
    private fun allRowsFit(locale: String, scale: Float) {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(locale))
        try {
            runDesktopComposeUiTest(360, 915) {
                var words = emptyList<String>()
                setContent {
                    SettingsThemed(locale = locale, fontScale = scale, width = 360f) {
                        words = HealthContract.items.values.flatten().map { stringResource(collectWords(it)) } +
                            stringResource(Res.string.collectNever)
                        SettingsScreen(
                            SettingsUi(
                                stack = persistentListOf(SettingsPage.WhatWeCollect),
                                privacy = PrivacyUi(null, crashReports = false, usage = false),
                            ),
                            RecordingActions(),
                        )
                    }
                }
                waitForIdle()
                assertTrue(words.size >= 25, "the complete disclosure, not just the two reported rows")
                val failures = mutableListOf<String>()
                for (text in words.distinct()) {
                    val node = onNodeWithText(text, useUnmergedTree = true).performScrollTo().fetchSemanticsNode()
                    val results = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
                    val layout = results.single()
                    if (layout.hasVisualOverflow || (0 until layout.lineCount).any(layout::isLineEllipsized)) {
                        failures += text.take(90)
                    }
                    assertEquals(if (locale == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, layout.layoutInput.layoutDirection)
                }
                assertFalse(failures.isNotEmpty(), "$locale at $scale has cut disclosure rows: $failures")
            }
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `B-SEC-191 every What we collect row is whole at 360dp in English`() = allRowsFit("en", 1f)

    @Test
    fun `B-SEC-191 every What we collect row is whole at 360dp in large English`() = allRowsFit("en", 1.3f)

    @Test
    fun `B-SEC-191 every What we collect row is whole at 360dp in Arabic RTL`() = allRowsFit("ar", 1f)

    @Test
    fun `B-SEC-191 every What we collect row is whole at 360dp in large Arabic RTL`() = allRowsFit("ar", 1.3f)
}

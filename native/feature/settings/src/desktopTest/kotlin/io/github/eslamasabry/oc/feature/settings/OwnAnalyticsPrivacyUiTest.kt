package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.collectDevice
import io.github.eslamasabry.oc.ui.words.res.collectRules
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class OwnAnalyticsPrivacyUiTest {
    private fun wordsFit(locale: String, fontScale: Float) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(locale))
        try {
            runDesktopComposeUiTest(360, 915) {
                var rules = ""
                var device = ""
                setContent {
                    SettingsThemed(locale = locale, fontScale = fontScale, width = 360f) {
                        rules = stringResource(Res.string.collectRules)
                        device = stringResource(Res.string.collectDevice)
                        SettingsScreen(
                            SettingsUi(stack = persistentListOf(SettingsPage.WhatWeCollect),
                                privacy = PrivacyUi(null, usage = false, crashReports = false)),
                            RecordingActions(),
                        )
                    }
                }
                waitForIdle()
                assertTrue("Umami" in rules && "Railway" in rules && "Google" in rules)
                for (text in listOf(device, rules)) {
                    val node = onNodeWithText(text, useUnmergedTree = true).performScrollTo().fetchSemanticsNode()
                    val results = mutableListOf<TextLayoutResult>()
                    node.config[SemanticsActions.GetTextLayoutResult].action!!(results)
                    assertFalse(results.single().hasVisualOverflow, "$locale $fontScale privacy words are clipped")
                    assertEquals(if (locale == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
                        results.single().layoutInput.layoutDirection)
                }
            }
        } finally { Locale.setDefault(previous) }
    }

    @Test
    fun `B-SEC-191 the sender notice and device description fit 360dp in English`() = wordsFit("en", 1f)

    @Test
    fun `B-SEC-191 the sender notice and device description fit 360dp in Arabic RTL`() = wordsFit("ar", 1f)

    @Test
    fun `B-SEC-191 large text keeps the sender notice and device description complete`() = wordsFit("en", 1.3f)
}

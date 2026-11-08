package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.onChild
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.presentation.PrivacyUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

/** s28-app: Settings › Privacy and data as Flutter 2.1.5 draws it. */
@OptIn(ExperimentalTestApi::class)
class S28PrivacyUiTest {
    private val privacy = PrivacyUi(null, crashReports = false, offerCrashReports = true, usage = false)

    @Test
    fun `B-SEC-207 Privacy shows Offer crash reports, Usage and app health and Crash reports, both off`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent {
                SettingsThemed {
                    SettingsScreen(SettingsUi(stack = persistentListOf(SettingsPage.Privacy), privacy = privacy), actions)
                }
            }
            onNodeWithText("Offer crash reports").performScrollTo().assertExists()
            onNodeWithTag("privacy-send-usage").performScrollTo().onChild().assertIsOff()
            onNodeWithTag("privacy-crash-reports").performScrollTo().onChild().assertIsOff()
            onNodeWithText("Usage and app health").performClick()
            onNodeWithTag("privacy-what-we-collect").performScrollTo().performClick()
            assertEquals(listOf("open:${SettingsPage.WhatWeCollect}"), actions.calls.filter { it.startsWith("open:") })
        }

    @Test
    fun `B-SEC-208 What we collect groups the contract by switch and lists what is never sent`() =
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        SettingsUi(stack = persistentListOf(SettingsPage.Privacy, SettingsPage.WhatWeCollect), privacy = privacy),
                        RecordingActions(),
                    )
                }
            }
            onNodeWithTag("collect-not-sent").assertExists()
            onNodeWithText("With Usage and app health").assertExists()
            onNodeWithText("Why a connection dropped and whether it came back").performScrollTo().assertExists()
            onNodeWithText("Your last 30 steps, as short words such as chat.send").performScrollTo().assertExists()
            onNodeWithText("Never sent").performScrollTo().assertExists()
        }

    @Test
    fun `B-SEC-209 the Arabic page reads in Arabic`() = runComposeUiTest {
        val before = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
        try {
        setContent {
            SettingsThemed(locale = "ar") {
                SettingsScreen(SettingsUi(stack = persistentListOf(SettingsPage.Privacy), privacy = privacy), RecordingActions())
            }
        }
        onNodeWithText("الاستخدام وصحة التطبيق").performScrollTo().assertExists()
        onNodeWithText("ما الذي نجمعه").performScrollTo().assertExists()
        } finally {
            java.util.Locale.setDefault(before)
        }
    }
}

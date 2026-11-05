package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.settings.consent.*
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.PermissionSettingsContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class PermissionSettingsTest {
    private val facts =
        PermissionSettingsFacts(
            profile = ProfileId("laptop"),
            name = "Laptop",
            ticket = 1,
            answers =
                persistentListOf(
                    InFlowConsentRow(InFlowConsentKind.BatteryExemption, ConsentChoice.Accepted),
                    InFlowConsentRow(InFlowConsentKind.MakerAutoStart, ConsentChoice.Offered),
                    InFlowConsentRow(InFlowConsentKind.NeedsYouNotifications, ConsentChoice.Denied),
                ),
            writable = true,
            declined = 1,
        )
    private val calls = mutableListOf<String>()

    @Composable
    private fun Page(ui: PermissionSettingsUi) {
        SettingsThemed {
            PermissionSettingsContent(
                ui,
                { calls += "back" },
                { calls += "refresh" },
                { calls += it.name },
                { calls += "again" },
                { calls += "grants" },
                { calls += "confirm" },
                { calls += "cancel" },
            )
        }
    }

    @Test
    fun `B-SEC-093 unanswered and declined answers precede offers and allowed answers`() =
        runComposeUiTest {
            setContent { Page(PermissionSettingsUi(facts)) }
            val tags =
                listOf(
                    "makerAutoStart",
                    "needsYouNotifications",
                    "always-allow",
                    "batteryExemption",
                )
            val tops = tags.map {
                onNodeWithTag("automation-consent-$it").fetchSemanticsNode().boundsInRoot.top
            }
            assertTrue(tops.zipWithNext().all { (a, b) -> a < b })
            onNodeWithTag("automation-consent-needsYouNotifications").assertTextContains("Off")
            onNodeWithTag("automation-consent-batteryExemption").performClick()
            onNodeWithTag("automation-consent-always-allow").performClick()
            assertEquals(listOf("BatteryExemption", "again"), calls)
        }

    @Test
    fun `B-SEC-093 unreadable storage keeps granted settings accessible and unanswered settings disabled`() =
        runComposeUiTest {
            setContent { Page(PermissionSettingsUi(facts.copy(writable = false))) }
            onNodeWithTag("automation-consent-makerAutoStart").assertIsNotEnabled()
            onNodeWithTag("automation-consent-needsYouNotifications").assertIsNotEnabled()
            onNodeWithTag("automation-consent-batteryExemption").assertIsEnabled().performClick()
            assertEquals(listOf("BatteryExemption"), calls)
        }

    @Test
    fun `B-SEC-093 unasked questions never become rows`() = runComposeUiTest {
        setContent {
            Page(
                PermissionSettingsUi(
                    facts.copy(
                        answers =
                            persistentListOf(
                                InFlowConsentRow(
                                    InFlowConsentKind.BatteryExemption,
                                    ConsentChoice.Unseen,
                                )
                            ),
                        declined = 0,
                        savedGrants = true,
                    )
                )
            )
        }
        onNodeWithTag("automation-consent-batteryExemption").assertDoesNotExist()
        onNodeWithTag("automation-saved-permissions").performClick()
        assertEquals(listOf("grants"), calls)
    }

    @Test
    fun `B-SEC-093 initial read shows loading before it is known to have failed`() =
        runComposeUiTest {
            setContent { Page(PermissionSettingsUi()) }
            onNodeWithTag("kit-skeleton-rows").assertExists()
            onNodeWithText("Try again").assertDoesNotExist()
        }

    @Test
    fun `B-SEC-093 failed read exposes retry`() = runComposeUiTest {
        setContent { Page(PermissionSettingsUi(failed = true)) }
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun `B-SEC-093 permissions confirmation works inside the page modal host`() = runComposeUiTest {
        setContent {
            Page(PermissionSettingsUi(facts, confirm = InFlowConsentKind.NeedsYouNotifications))
        }
        assertEquals(emptyList(), calls)
        onNodeWithText("Turn on notifications").performClick()
        assertEquals(listOf("confirm"), calls)
    }
}

package io.github.eslamasabry.oc.feature.settings.rb.o44

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.settings.RecordingActions
import io.github.eslamasabry.oc.feature.settings.SettingsThemed
import io.github.eslamasabry.oc.feature.settings.presentation.*
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class NotificationsGateTest {
    private fun notifications() =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Notifications),
            notifications =
                NotificationFacts(
                    blocked = true,
                    usageAlertsShown = true,
                    profiles =
                        persistentListOf(
                            NotificationProfile(ProfileId("laptop"), "Laptop", false, true)
                        ),
                    background = BackgroundState.Off,
                ),
        )

    @Test
    fun settings_notifications_loaded() {
        FlutterGoldenGate.check(
            "settings_notifications_loaded",
            frame = FlutterGoldenGate.Frame.Screen,
        ) {
            SettingsScreen(notifications(), RecordingActions())
        }
    }

    @Test
    fun `B-KIT-027 blocked notification switches retain their explanation and cannot mutate`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent { SettingsThemed { SettingsScreen(notifications(), actions) } }
            onNodeWithText("Finished work").assertIsNotEnabled().performClick()
            onNodeWithText("Several arrive as one notification.").assertExists()
            assertEquals(emptyList(), actions.calls)
            onNodeWithText("Turn on notifications in Android").performClick()
            assertEquals(listOf("outside:AndroidNotificationSettings"), actions.calls)
        }

    @Test
    fun `B-KIT-027 test notification action stays available while Android blocks notifications`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent { SettingsThemed { SettingsScreen(notifications(), actions) } }
            onNodeWithText("Send a test notification").performScrollTo().performClick()
            assertEquals(listOf("test"), actions.calls)
        }
}

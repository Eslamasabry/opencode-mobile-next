@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringActions
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringSection
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringServer
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringState
import io.github.eslamasabry.oc.feature.settings.monitor.MonitoringUi
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

/** Notifications says there are no servers to watch only when the list it draws is really empty. */
class MonitoringSectionTest {
    private val actions =
        object : MonitoringActions {
            override fun enabled(id: String, on: Boolean) = Unit

            override fun checkIn(id: String, minutes: Int?) = Unit
        }

    @Test
    fun `an empty list says there are no servers to watch`() = runComposeUiTest {
        setContent { SettingsThemed { Column { MonitoringSection(MonitoringUi(), actions) } } }
        onNodeWithText("No servers to watch").assertExists()
    }

    @Test
    fun `a saved server is listed and the empty words are gone`() = runComposeUiTest {
        val server = MonitoringServer("p1", "This phone", false, null, MonitoringState.Off, null)
        setContent {
            SettingsThemed {
                Column { MonitoringSection(MonitoringUi(servers = persistentListOf(server)), actions) }
            }
        }
        onNodeWithText("This phone").assertExists()
        onNodeWithText("No servers to watch").assertDoesNotExist()
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.ui.shell.rb.o53

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind
import io.github.eslamasabry.oc.ui.kit.status.KitStatusLine
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.connectionReconnectTo
import io.github.eslamasabry.oc.ui.words.res.connectionRestartPhoneServer
import io.github.eslamasabry.oc.ui.words.res.e7BannerDetails
import io.github.eslamasabry.oc.ui.words.res.workServerNotAnswering
import io.github.eslamasabry.oc.ui.words.res.workServerNotAnsweringPhone
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.stringResource

/**
 * The app's connection line (screen A07: the shell's status slot) with the inputs of
 * test/revamp/shared_shell_1_golden_test.dart:194-231: a saved server ("Studio box", the only one) that is not
 * answering, with no retry scheduled. The words and actions are those of `connectionKitStatus`
 * (lib/ui/widgets/connection_status_banner.dart:209-393): a server elsewhere offers "Reconnect to Studio box", this
 * phone's server offers "Restart OpenCode" and keeps Reconnect in More. Details always waits in More.
 */
class ConnectionLineGateTest {
    private val server = "Studio box"

    @Composable
    private fun line(phone: Boolean, onRetry: () -> Unit = {}, onRestart: () -> Unit = {}) {
        val retry = KitLineAction(stringResource(Res.string.connectionReconnectTo, server), onRetry)
        val restart = KitLineAction(stringResource(Res.string.connectionRestartPhoneServer), onRestart)
        val details = KitLineAction(stringResource(Res.string.e7BannerDetails), {})
        val status =
            KitStatus(
                kind = KitStatusKind.Connection,
                id = "connection:studio",
                icon = KitIcons.cloudOff, // F: lib/ui/widgets/connection_status_banner.dart:353-355
                tone = StatusTone.Failure, // F: lib/ui/widgets/connection_status_banner.dart:356-358
                message =
                    if (phone) stringResource(Res.string.workServerNotAnsweringPhone)
                    else stringResource(Res.string.workServerNotAnswering, server),
                action = if (phone) restart else retry, // F: lib/ui/widgets/connection_status_banner.dart:361
                more = if (phone) listOf(retry, details) else listOf(details), // F: lib/ui/widgets/connection_status_banner.dart:362-364
            )
        // F: test/revamp/shared_shell_1_golden_test.dart:208-210 (Column(children: [ConnectionStatusBanner]))
        Column(Modifier.fillMaxSize()) { KitStatusLine(status) }
    }

    private fun shot(name: String, phone: Boolean) {
        FlutterGoldenGate.check(name, frame = FlutterGoldenGate.Frame.Screen, pixelRatio = 1f) { line(phone) }
    }

    @Test fun lost() = shot("shell_embedded_connection_status_banner_lost", phone = false)

    @Test fun lost_wide() = shot("shell_embedded_connection_status_banner_lost_1280x800", phone = false)

    @Test fun phone_stopped() = shot("shell_embedded_connection_status_banner_phone_stopped", phone = true)

    private fun tapAction(phone: Boolean): Pair<Int, Int> {
        var retried = 0
        var restarted = 0
        runComposeUiTest {
            setContent {
                KitTheme(
                    appearance = KitAppearance(mode = AppearanceMode.Dark),
                    platform = DesktopKitPlatform(),
                    localeTag = "en",
                    stillFrame = true,
                ) {
                    line(phone = phone, onRetry = { retried++ }, onRestart = { restarted++ })
                }
            }
            if (phone) {
                onNodeWithText("OpenCode on this phone isn't answering").assertExists()
                onAllNodesWithText("Reconnect to Studio box").assertCountEquals(0)
            } else {
                onNodeWithText("Studio box isn't answering").assertExists()
                onAllNodesWithText("Restart OpenCode").assertCountEquals(0)
            }
            onNodeWithTag("kit-status-action").performClick()
        }
        return retried to restarted
    }

    @Test
    fun `B-SHELL-053 a server elsewhere offers Reconnect by name as the line's action`() {
        assertEquals(1 to 0, tapAction(phone = false))
    }

    @Test
    fun `B-SHELL-054 this phone's server offers Restart OpenCode as the line's action`() {
        assertEquals(0 to 1, tapAction(phone = true))
    }
}

package io.github.eslamasabry.oc.ui.kitchat.sweepb

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.ui.design.color.wcagContrast
import io.github.eslamasabry.oc.ui.design.generated.FlutterKitRoles
import io.github.eslamasabry.oc.ui.kitchat.ChatTestTheme
import io.github.eslamasabry.oc.ui.kitchat.request.KitPermissionCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PermissionCardSweepBTest {
    @Test
    fun `sweep-b 4 Details takes only its own touch target and sits straight under the answers`() = runComposeUiTest {
        var opened = 0
        setContent {
            ChatTestTheme {
                KitPermissionCard(
                    title = "Read this file?", summary = null, subject = ".",
                    allowOnceLabel = "Allow", alwaysLabel = null, denyLabel = "Deny",
                    onAllowOnce = {}, onAlways = null, onDeny = {}, onDetails = { opened++ },
                )
            }
        }
        val row = onNodeWithTag("permission-card-details-row").getBoundsInRoot()
        val allow = onNodeWithTag("permission-card-allow").getBoundsInRoot()
        println("sweep-b details row height=${row.height} gap=${row.top - allow.bottom}")
        assertTrue(row.height.value <= 48.5f, "Details row is one touch target, not a band: ${row.height}")
        assertEquals(0f, (row.top - allow.bottom).value, "no air between the answers and Details")
        onNodeWithTag("permission-card-details").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `sweep-b 4 Details words are the secondary ink and hold 4_5 to 1 on the card in dark and light`() {
        for ((name, r) in listOf("dark" to FlutterKitRoles.graphiteDark, "light" to FlutterKitRoles.graphiteLight)) {
            val ratio = wcagContrast(r.text2, r.surface1)
            assertTrue(ratio >= 4.5, "$name secondary on surface1 $ratio")
        }
    }
}

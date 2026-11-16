@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.phone

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.settings.consent.InFlowConsentKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneConsentUi
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneFirstStartConsents
import io.github.eslamasabry.oc.feature.phone.ui.PhoneFirstStartConsentHost
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small-faults 301: the "Keep the server running?" question waits for a quiet moment and never leaves once shown. */
class FirstStartConsentMomentTest {
    private class Queue : PhoneFirstStartConsents {
        val ask = MutableStateFlow<PhoneConsentUi?>(PhoneConsentUi(InFlowConsentKind.BatteryExemption, ""))
        override val pending: StateFlow<PhoneConsentUi?> = ask

        override suspend fun request(
            profile: ProfileId,
            batteryAlreadyExempt: Boolean,
            makerHasAutoStartStep: Boolean,
            maker: String,
        ) {}

        override suspend fun answer(allow: Boolean): Outcome<Unit> {
            ask.value = null
            return Outcome.Ok(Unit)
        }
    }

    @Test
    fun `SMALL-301 a waiting question stays closed until the moment is quiet, then stays until answered`() =
        runComposeUiTest {
            val queue = Queue()
            var quiet by mutableStateOf(false)
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    KitSheetHost(Modifier.fillMaxSize()) { PhoneFirstStartConsentHost(queue, quiet = quiet) }
                }
            }
            onNodeWithText("Keep the server running?").assertDoesNotExist()
            quiet = true
            waitForIdle()
            onNodeWithText("Keep the server running?").assertExists()
            // Something else opens or the person leaves the list: the question already on screen is not withdrawn.
            quiet = false
            waitForIdle()
            onNodeWithText("Keep the server running?").assertExists()
            onNodeWithText("Not now").performClickAndIdle(this)
            onNodeWithText("Keep the server running?").assertDoesNotExist()
        }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.performClickAndIdle(
    test: androidx.compose.ui.test.ComposeUiTest
) {
    performClick()
    test.waitForIdle()
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.servers.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.ui.ServerEditorScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test

class ServerEditorParityTest {
    private class Actions : ServerEditorActions {
        override fun keepDraft(input: ServerEditorInput) {}
        override fun takeDraft(): ServerEditorInput? = null
        override fun refreshTailscale() {}
        override fun openTailscale() {}
        override fun installTailscale() {}
        override fun back(dirty: Boolean) {}
        override fun discard() {}
        override fun keepEditing() {}
        override fun step(step: ServerEditorStep) {}
        override fun changed() {}
        override fun check(input: ServerEditorInput) {}
        override fun save(input: ServerEditorInput, anyway: Boolean) {}
        override fun cancelCheck() {}
        override fun inspect(address: String) {}
        override fun dismissCertificate() {}
        override fun scan() {}
        override fun paste(text: String?) {}
        override fun takePairing(): PairingFill? = null
        override fun phone() {}
        override fun agents() {}
        override fun openReady() {}
    }
    @Test fun `a new OpenCode server leads with pairing and manual fields open by a visible fold`() = runComposeUiTest {
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            ServerEditorScreen(ServerEditorUi(step = ServerEditorStep.Connect), Actions())
        } }
        onNodeWithText("Enter the address instead").assertExists().performScrollTo().performClick()
        onNodeWithText("Server URL").assertExists()
        onNodeWithText("Password").assertExists()
    }
}

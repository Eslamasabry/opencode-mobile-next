@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package io.github.eslamasabry.oc.feature.linux.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.feature.linux.presentation.*
import io.github.eslamasabry.oc.feature.linux.ui.LinuxPackagesScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class LinuxPackagesScreenTest {
    @Test fun `install opens its input and checking dispatches the typed names without starting a job`() = runComposeUiTest {
        var checked: String? = null
        var confirmed = false
        val actions = object : LinuxPackagesActions {
            override fun shown(visible: Boolean) {}
            override fun retry() {}
            override fun search(text: String) {}
            override fun checkInstall(typed: String) { checked = typed }
            override fun askRemove(name: String) {}
            override fun askUpdate() {}
            override fun confirm() { confirmed = true }
            override fun dismiss() {}
            override fun back() {}
            override fun askStopJob() {}
            override fun dismissJob() {}
        }
        setContent { KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
            LinuxPackagesScreen(LinuxPackagesUi(loading = false), actions)
        } }
        onNodeWithText("Check").assertDoesNotExist()
        onNodeWithText("Install a package").performClick()
        onNode(hasSetTextAction()).performTextInput("htop")
        onNodeWithText("Check").performClick()
        assertEquals("htop", checked)
        assertEquals(false, confirmed)
    }
}

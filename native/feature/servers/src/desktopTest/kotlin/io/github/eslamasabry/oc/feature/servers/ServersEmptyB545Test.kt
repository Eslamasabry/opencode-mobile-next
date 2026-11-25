package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.KitWindowInfo
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitWindow
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ServersEmptyB545Test {
    private val out = File("build/b545-renders").also { it.mkdirs() }

    private fun actions(calls: MutableList<String>): ServersActions =
        object : ServersActions {
            override fun back() { calls += "back" }

            override fun allowCleartext(allowed: Boolean, address: String) { calls += "cleartext:$allowed" }

            override fun save(name: String, address: String, username: String, password: String) { calls += "save" }

            override fun connect(id: ProfileId) { calls += "connect" }

            override fun create(id: ProfileId) { calls += "create" }

            override fun remove(id: ProfileId) { calls += "remove" }
        }

    @Test
    fun `B-545 empty servers screen renders review picture at 360 dp`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val calls = mutableListOf<String>()
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    CompositionLocalProvider(
                        LocalDensity provides Density(1f),
                        LocalKitSecretSink provides KitSecretSink {},
                    ) {
                        KitTheme(
                            appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            CompositionLocalProvider(LocalKitWindow provides KitWindowInfo(360f, 800f)) {
                                Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                    ServersScreen(ServersUi(), actions(calls), onThisPhone = { calls += "phone" })
                                }
                            }
                        }
                    }
                }
                waitForIdle()
                onAllNodesWithTag("kit-choice-card-check", useUnmergedTree = true).assertCountEquals(1)
                val img = onRoot().captureToImage().toAwtImage()
                ImageIO.write(img, "png", File(out, "b545-after-servers-empty-dark.png"))
                onNodeWithContentDescription("On my computer. Pair with a code, or type its address").performClick()
                waitForIdle()
                onAllNodesWithTag("kit-choice-card-check", useUnmergedTree = true).assertCountEquals(1)
            }
        } finally {
            Locale.setDefault(prev)
        }
    }
}

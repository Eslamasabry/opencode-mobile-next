package io.github.eslamasabry.oc.feature.integrations.catalog

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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogNotice
import io.github.eslamasabry.oc.engine.integrations.catalog.McpCatalogState
import io.github.eslamasabry.oc.engine.integrations.catalog.orderMcpCatalog
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** The catalogue page shows each listing's needs, the price note, and Stop using the registry. */
@OptIn(ExperimentalTestApi::class)
class McpCatalogScreenTest {
    private val list =
        mcpCatalogUi(
            McpCatalogState(
                loading = false,
                optedIn = true,
                entries = orderMcpCatalog(registryEntries(), setOf("docs")),
                installed = setOf("docs"),
                canAdd = true,
            ),
            forgetting = false,
        )

    @Test
    fun `B-AGENT-212 rows say what each listing needs and why some cannot be added`() =
        runComposeUiTest {
            setContent { KitTheme(platform = DesktopKitPlatform()) { McpCatalogScreen(list, RecordingActions()) } }
            onNodeWithText("Hosted by mcp.example.com · Needs an API key", substring = true).assertExists()
            onNodeWithText("Needs Node on the server · Needs an API key", substring = true).assertExists()
            onNodeWithText("Needs Python with uv on the server · Needs extra settings", substring = true)
                .assertExists()
            onNodeWithText("Runs in Docker. To add it anyway, use Enter manually.").assertExists()
            onNodeWithText("Lists nothing the app can start. To add it anyway, use Enter manually.")
                .assertExists()
            onNodeWithText("The registry lists no prices", substring = true).assertExists()
        }

    @Test
    fun `B-AGENT-133 the bar's menu offers Stop using the registry`() = runComposeUiTest {
        val actions = RecordingActions()
        setContent { KitTheme(platform = DesktopKitPlatform()) { McpCatalogScreen(list, actions) } }
        onNodeWithContentDescription("More actions").performClick()
        onNodeWithText("Stop using the registry").performClick()
        assertEquals(listOf("forget"), actions.calls)
    }

    @Test
    fun `B-AGENT-133 the consent page has no menu and a failed forget says so`() = runComposeUiTest {
        val failed =
            mcpCatalogUi(
                McpCatalogState(loading = false, notice = McpCatalogNotice.SaveFailed),
                forgetting = true,
            )
        setContent { KitTheme(platform = DesktopKitPlatform()) { McpCatalogScreen(failed, RecordingActions()) } }
        onAllNodesWithContentDescription("More actions").assertCountEquals(0)
        onNodeWithText("Couldn't forget the saved registry list. Try again.").assertExists()
    }

    /** Capture only, when `-Poc.mcpCatalogShots=<dir>` is set: the list in light and dark at 412 x 915 dp. */
    @Test
    fun `B-AGENT-212 catalogue list capture`() {
        val dir = System.getProperty("oc.mcpCatalogShots").orEmpty()
        if (dir.isEmpty()) return
        val out = File(dir).also { it.mkdirs() }
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(1081, 2402) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2.625f)) {
                        KitTheme(
                            appearance =
                                KitAppearance(
                                    mode = if (dark) AppearanceMode.Dark else AppearanceMode.Light,
                                    motion = MotionLevel.Calm,
                                ),
                            platform = DesktopKitPlatform(),
                            localeTag = "en",
                            stillFrame = true,
                        ) {
                            Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                                McpCatalogScreen(list, RecordingActions())
                            }
                        }
                    }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                val image = onRoot().captureToImage().toAwtImage()
                val name = "mcp-catalog-native-${if (dark) "dark" else "light"}.png"
                ImageIO.write(image, "png", File(out, name))
            }
        }
    }
}

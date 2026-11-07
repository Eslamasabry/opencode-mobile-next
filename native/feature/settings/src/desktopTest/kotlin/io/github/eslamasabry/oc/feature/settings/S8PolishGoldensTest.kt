package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceGuideRecord
import io.github.eslamasabry.oc.engine.settings.walkthrough.AppearanceQuestion
import io.github.eslamasabry.oc.feature.settings.presentation.AppearanceUi
import io.github.eslamasabry.oc.feature.settings.presentation.GroupUi
import io.github.eslamasabry.oc.feature.settings.presentation.HubUi
import io.github.eslamasabry.oc.feature.settings.presentation.ReportUi
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLine
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLocalFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsSavedServer
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.ServerSettingsScreen
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.Proxy
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * S8 settings polish (auditor sweep of a2e7d3fa5): each fixed page asserts the measured fault is gone, then is compared
 * with its golden at text size 1.0 and 1.3 (light, English) and 1.0 dark Arabic. Record: `./gradlew
 * :feature:settings:desktopTest -Poc.recordGoldens=true --tests '*S8PolishGoldensTest*'`, then look at each picture.
 */
@OptIn(ExperimentalTestApi::class)
class S8PolishGoldensTest {
    private data class Variant(val name: String, val dark: Boolean, val locale: String, val scale: Float)

    private val variants =
        listOf(
            Variant("light-en", false, "en", 1f),
            Variant("light-en-text13", false, "en", 1.3f),
            Variant("dark-ar", true, "ar", 1f),
        )
    private val savedLocale: Locale = Locale.getDefault()
    private val dir = File(System.getProperty("oc.goldens.dir") ?: "src/desktopTest/goldens")
    private val record = System.getProperty("oc.goldens.record") == "true"

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val serverActions =
        Proxy.newProxyInstance(
            ServerSettingsActions::class.java.classLoader,
            arrayOf(ServerSettingsActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ServerSettingsCalls"
                else -> null
            }
        } as ServerSettingsActions

    private val thisPhone =
        ServerSettingsUi(
            page = ServerSettingsPage.Home,
            loading = false,
            facts =
                ServerSettingsFacts(
                    name = "This phone",
                    address = "http://127.0.0.1:4096",
                    connected = true,
                    hasSecret = true,
                    lastVersion = "2.2.5",
                    canCheck = true,
                    capabilities = ServerCapabilities(),
                    managedOnPhone = true,
                ),
            health = Health(healthy = true, version = "2.2.5"),
            local =
                ServerLocalFacts(
                    model = ModelRef("opencode", "crouton-alpha-free"),
                    watching = true,
                    notifications = false,
                    monitorSupported = true,
                    sharingRead = false,
                    sharingDefault = false,
                    sharingSaving = false,
                ),
        )

    private fun each(scene: String, check: ComposeUiTest.(Variant) -> Unit = {}, content: @androidx.compose.runtime.Composable () -> Unit) {
        for (variant in variants) {
            Locale.setDefault(Locale.forLanguageTag(variant.locale))
            runComposeUiTest {
                setContent {
                    SettingsThemed(dark = variant.dark, locale = variant.locale, fontScale = variant.scale) { content() }
                }
                waitForIdle()
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                check(variant)
                waitForIdle()
                compare("s8-$scene-${variant.name}")
            }
        }
    }

    @Test
    fun `P-007 P-020 This phone starts every row on the one page edge and names the model`() =
        each(
            "this-phone",
            check = { v ->
                if (v.locale == "en") {
                    // P-007: 16 dp from the start, not 32 (a second gutter around groups that carry their own).
                    val left = onNodeWithText("Sign-in", useUnmergedTree = true).getBoundsInRoot().left
                    assertTrue(abs(left.value - 16f) <= 1f, "Sign-in starts at $left, not on the 16 dp edge")
                    // P-020: the model's words, never "provider / id"; the version says it is one.
                    onAllNodesWithText("opencode / crouton-alpha-free").assertCountEquals(0)
                    onNodeWithText("Crouton Alpha Free · OpenCode").assertExists()
                    onNodeWithText("Version 2.2.5").assertExists()
                    onAllNodesWithText("2.2.5").assertCountEquals(0)
                }
            },
        ) {
            ServerSettingsScreen(thisPhone, serverActions)
        }

    private val hub =
        SettingsUi(
            hub =
                HubUi(
                    groups = SettingsGroupId.entries.toImmutableList(),
                    server = ServerLine("This phone", onPhone = true),
                    searchable = SettingsRowId.entries.toImmutableList(),
                )
        )

    @Test
    fun `P-028 the hub's one-line groups are 56 dp and the phone server is This phone`() =
        each(
            "hub",
            check = { v ->
                if (v.locale == "en") {
                    onAllNodesWithText("On this phone", substring = true).assertCountEquals(0)
                    onNodeWithText("This phone · Connected").assertExists()
                    if (v.scale == 1f) onNodeWithTag("settings-group-Agent").assertHeightIsEqualTo(56.dp)
                }
            },
        ) {
            SettingsScreen(hub, RecordingActions())
        }

    private val serverGroup =
        hub.copy(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Group(SettingsGroupId.Server)),
            group =
                GroupUi(
                    SettingsGroupId.Server,
                    persistentListOf(
                        SettingsRowUi(SettingsRowId.Servers, SettingsDoor.Outside(SettingsOutside.Servers)),
                        SettingsRowUi(SettingsRowId.ThisPhone, SettingsDoor.Outside(SettingsOutside.ThisPhone)),
                        SettingsRowUi(
                            SettingsRowId.ServerCapabilities,
                            SettingsDoor.Page(SettingsPage.ServerCapabilities),
                        ),
                        SettingsRowUi(
                            SettingsRowId.ServerConfiguration,
                            SettingsDoor.Page(SettingsPage.ServerConfiguration),
                        ),
                    ),
                ),
            hub =
                hub.hub.copy(
                    savedServers =
                        persistentListOf(
                            SettingsSavedServer(ProfileId("phone"), "This phone", connected = true, onPhone = true),
                            SettingsSavedServer(ProfileId("laptop"), "Laptop", connected = false),
                        )
                ),
        )

    @Test
    fun `P-031 Settings Server lists the servers once, under no sentence label`() =
        each(
            "server-group",
            check = { v ->
                if (v.locale == "en") {
                    onAllNodesWithText("You can change some of these for one server", substring = true)
                        .assertCountEquals(0)
                    onAllNodesWithText("Laptop").assertCountEquals(0)
                    onNodeWithText("2 servers").assertExists()
                }
            },
        ) {
            SettingsScreen(serverGroup, RecordingActions())
        }

    private val report =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.ReportProblem),
            report =
                ReportUi.Ready(
                    "The app closed unexpectedly\n\n(not described)\n\nEnvironment\nApp: 3.0.0+10002\n" +
                        "Platform: Android 16 (API 36)\n\nDiagnostics\n" +
                        "[2026-11-07 10:00:00Z] androidExit · android.exit\nCrash reason=4 subReason=-1 status=0"
                ),
        )

    @Test
    fun `P-022 Report a problem keeps the raw report in a closed Details fold`() =
        each(
            "report",
            check = { v ->
                if (v.locale == "en") {
                    onNodeWithTag("report-text", useUnmergedTree = true).assertDoesNotExist()
                    onNodeWithText("The app closed unexpectedly").assertExists()
                    // The hint wraps under the field instead of being cut in it.
                    onNodeWithText("What you did, what you expected, what you got instead").assertExists()
                }
            },
        ) {
            SettingsScreen(report, RecordingActions())
        }

    @Test
    fun `P-022 the Details fold opens to the exact report`() {
        Locale.setDefault(Locale.ENGLISH)
        runComposeUiTest {
            setContent { SettingsThemed { SettingsScreen(report, RecordingActions()) } }
            onNodeWithTag("report-details").performClick()
            waitForIdle()
            onNodeWithTag("report-text", useUnmergedTree = true).assertExists()
        }
    }

    private val colours =
        SettingsUi(
            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Appearance),
            appearance =
                AppearanceUi(
                    guide = AppearanceGuideRecord(seen = listOf(AppearanceQuestion.Mode)),
                    question = AppearanceQuestion.Theme,
                    questions =
                        listOf(
                            AppearanceQuestion.Mode,
                            AppearanceQuestion.Theme,
                            AppearanceQuestion.Text,
                            AppearanceQuestion.Motion,
                        ),
                ),
        )

    @Test
    fun `P-027 a Colours question has one way back, the bar's`() =
        each(
            "colours",
            check = { v -> if (v.locale == "en") onAllNodesWithText("All").assertCountEquals(0) },
        ) {
            SettingsScreen(colours, RecordingActions())
        }

    private fun ComposeUiTest.compare(name: String) {
        val image = onRoot().captureToImage().toAwtImage()
        val file = File(dir, "$name.png")
        if (record || !file.isFile) {
            file.parentFile.mkdirs()
            ImageIO.write(image, "png", file)
            if (!record) fail("$name: no golden yet; recorded ${file.name}. Look at it, then run again.")
            return
        }
        val golden = ImageIO.read(file)
        assertTrue(
            golden.width == image.width && golden.height == image.height,
            "$name: size ${image.width}x${image.height} vs ${golden.width}x${golden.height}",
        )
        val share = differingPixels(golden, image).toDouble() / (image.width * image.height)
        if (share > MAX_SHARE) {
            val actual = File("build/golden-actual/$name.png").also { it.parentFile.mkdirs() }
            ImageIO.write(image, "png", actual)
            fail("$name: ${"%.3f".format(share * 100)} % of pixels differ (limit ${MAX_SHARE * 100} %); actual: $actual")
        }
    }

    private fun differingPixels(a: BufferedImage, b: BufferedImage): Int {
        var count = 0
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getRGB(x, y)
            val q = b.getRGB(x, y)
            if (p == q) continue
            if ((0..24 step 8).any { s -> abs((p shr s and 0xFF) - (q shr s and 0xFF)) > CHANNEL_SLACK }) count++
        }
        return count
    }

    private companion object {
        const val MAX_SHARE = 0.002
        const val CHANNEL_SLACK = 8
    }
}

package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.discovery.LanAccess
import io.github.eslamasabry.oc.feature.servers.discovery.LanFinding
import io.github.eslamasabry.oc.feature.servers.pairing.PairingFill
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorInput
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorProbe
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorRecord
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorStep
import io.github.eslamasabry.oc.feature.servers.presentation.ServerEditorUi
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowFacts
import io.github.eslamasabry.oc.feature.servers.presentation.ServerRowsState
import io.github.eslamasabry.oc.feature.servers.presentation.ServersActions
import io.github.eslamasabry.oc.feature.servers.presentation.ServersUi
import io.github.eslamasabry.oc.feature.servers.ui.ServerEditorScreen
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.ui.design.effects.AppearanceMode
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.input.KitSecretSink
import io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitColors
import java.util.Locale
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Wide-window behavior tests for ServersScreen and ServerEditorScreen (B-SRV-300..303). */
@OptIn(ExperimentalTestApi::class)
class ServersWideTest {
    private var previousLocale: Locale = Locale.getDefault()

    @BeforeTest
    fun localeBefore() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @AfterTest
    fun localeAfter() {
        Locale.setDefault(previousLocale)
    }

    private class RecordingServersActions : ServersActions {
        var connected: ProfileId? = null
        var openedSettings: ProfileId? = null
        var edited: ProfileId? = null
        var removed: ProfileId? = null

        override fun back() {}
        override fun allowCleartext(allowed: Boolean, address: String) {}
        override fun save(name: String, address: String, username: String, password: String) {}
        override fun connect(id: ProfileId) {
            connected = id
        }
        override fun create(id: ProfileId) {}
        override fun remove(id: ProfileId) {
            removed = id
        }
        override fun settings(id: ProfileId) {
            openedSettings = id
        }
        override fun edit(id: ProfileId?) {
            edited = id
        }
    }

    private class EditorActions(val draft: ServerEditorInput? = null) : ServerEditorActions {
        override fun keepDraft(input: ServerEditorInput) {}
        override fun takeDraft(): ServerEditorInput? = draft
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
        override fun findAccess(access: LanAccess, extraPorts: Set<Int>) {}
        override fun stopFind() {}
        override fun openAccessSettings() {}
        override fun pickFound(finding: LanFinding) {}
    }

    private val home = SavedServer(ProfileId("home"), "Home laptop", "https://laptop.example.net:4096", connected = true)
    private val desk = SavedServer(ProfileId("desk"), "Office desktop", "https://desk.example.net:4096", connected = false)
    private val vps = SavedServer(ProfileId("vps"), "Build server", "https://build.example.net:4096", connected = false)

    private fun listUi() =
        ServersUi(
            servers = persistentListOf(home, desk, vps),
            rows =
                ServerRowsState(
                    persistentMapOf(
                        home.id to ServerRowFacts(BackendKind.OpenCode2, "2.1.1", "/home/dev/projects/oc_app", false, 0, 2, 0),
                        desk.id to ServerRowFacts(BackendKind.OpenCode2, "2.1.0", "/home/office", false, 3, 0, 1),
                        vps.id to ServerRowFacts(BackendKind.OpenCode2, "2.1.1", "/srv/build", false, 0, 0, 0),
                    ),
                ),
        )

    private val record =
        ServerEditorRecord(
            id = ProfileId("home"),
            name = "Home laptop",
            address = "https://laptop.example.net:4096",
            username = "opencode",
            backend = BackendKind.OpenCode2,
            directory = "/home/dev/projects/oc_app",
            hasSecret = true,
            needsSecret = false,
            tailscale = true,
            cleartextOrigin = null,
            certificatePin = null,
            certificateOrigin = null,
            serverVersion = "2.2.6",
        )

    private fun draftOf(r: ServerEditorRecord) =
        ServerEditorInput(r.id, r.name, r.address, r.username, r.backend, r.directory, null, true, r.tailscale, null, null, null)

    private fun showAt(widthDp: Int, heightDp: Int, content: @Composable () -> Unit, block: ComposeUiTest.() -> Unit) {
        runDesktopComposeUiTest(widthDp, heightDp) {
            setContent {
                CompositionLocalProvider(LocalKitSecretSink provides KitSecretSink {}) {
                    KitTheme(
                        appearance = KitAppearance(mode = AppearanceMode.Dark, motion = MotionLevel.Off),
                        platform = DesktopKitPlatform(),
                        localeTag = "en",
                        stillFrame = true,
                    ) {
                        Box(Modifier.fillMaxSize().background(LocalKitColors.current.ground)) {
                            content()
                        }
                    }
                }
            }
            waitForIdle()
            block()
        }
    }

    @Test
    fun `B-SRV-300 servers list shows the chosen server beside it at 1280`() {
        val actions = RecordingServersActions()
        showAt(1280, 800, { ServersScreen(listUi(), actions, onThisPhone = {}) }) {
            val rowBounds = onNodeWithText("Office desktop").getBoundsInRoot()
            val actionBounds = onNodeWithText("Open Home laptop").getBoundsInRoot()
            onNodeWithText("Edit Home laptop").assertExists()
            onNodeWithText("Conversations").assertExists()
            val delta = actionBounds.left.value - rowBounds.left.value
            assertTrue(
                delta > 300f,
                "Expected chosen server action ($actionBounds) beside server row ($rowBounds), delta=$delta",
            )
            onNodeWithText("Office desktop").performClick()
            waitForIdle()
            val deskAction = onNodeWithText("Connect to Office desktop").getBoundsInRoot()
            onNodeWithText("Edit Office desktop").assertExists()
            assertTrue(
                deskAction.left.value - rowBounds.left.value > 300f,
                "Expected newly chosen server action ($deskAction) beside list row ($rowBounds)",
            )
            onNodeWithText("Connect to Office desktop").performClick()
            assertTrue(actions.connected == desk.id, "Expected Connect action to connect to Office desktop")
        }
    }

    @Test
    fun `B-SRV-301 servers pages unchanged under 840`() {
        for (width in listOf(600, 839)) {
            showAt(width, 800, { ServersScreen(listUi(), RecordingServersActions(), onThisPhone = {}) }) {
                onNodeWithText("Open Home laptop").assertDoesNotExist()
                val row = onNodeWithText("Office desktop").getBoundsInRoot()
                val add = onNodeWithText("Add server").getBoundsInRoot()
                assertTrue(
                    add.top.value > row.bottom.value,
                    "Expected Add server ($add) below list rows ($row) at ${width}dp",
                )
            }
            showAt(
                width,
                800,
                {
                    ServerEditorScreen(
                        ServerEditorUi(
                            step = ServerEditorStep.Ready,
                            newServer = false,
                            record = record,
                            checked = ServerEditorProbe("2.2.6", listOf("opencode", "claude", "codex")),
                        ),
                        EditorActions(),
                    )
                },
            ) {
                onNodeWithText("Home laptop details").assertDoesNotExist()
                val headline = onNodeWithText("Connected to Home laptop").getBoundsInRoot()
                val open = onNodeWithText("Open conversations").getBoundsInRoot()
                assertTrue(
                    open.top.value > headline.bottom.value,
                    "Expected single column at ${width}dp: headline=$headline, open=$open",
                )
            }
            showAt(
                width,
                800,
                {
                    ServerEditorScreen(
                        ServerEditorUi(
                            step = ServerEditorStep.Connect,
                            newServer = false,
                            connectOnSave = false,
                            record = record,
                            canScan = true,
                            canTailscale = true,
                        ),
                        EditorActions(draftOf(record)),
                    )
                },
            ) {
                val url = onNodeWithText("Server URL").getBoundsInRoot()
                val check = onNodeWithText("Check connection").getBoundsInRoot()
                assertTrue(
                    check.top.value > url.bottom.value && abs(check.left.value - url.left.value) < 64f,
                    "Expected single column form at ${width}dp: url=$url, check=$check",
                )
            }
        }
    }

    @Test
    fun `B-SRV-302 a server's page uses two columns at 1280`() {
        showAt(
            1280,
            800,
            {
                ServerEditorScreen(
                    ServerEditorUi(
                        step = ServerEditorStep.Ready,
                        newServer = false,
                        record = record,
                        checked = ServerEditorProbe("2.2.6", listOf("opencode", "claude", "codex")),
                    ),
                    EditorActions(),
                )
            },
        ) {
            val open = onNodeWithText("Open conversations").getBoundsInRoot()
            val version = onNodeWithText("/home/dev/projects/oc_app", substring = true).getBoundsInRoot()
            val delta = version.left.value - open.left.value
            assertTrue(
                delta > 300f,
                "Expected Ready page details ($version) beside primary action ($open) at 1280dp, delta=$delta",
            )
        }
        showAt(
            1280,
            800,
            {
                ServerEditorScreen(
                    ServerEditorUi(
                        step = ServerEditorStep.Connect,
                        newServer = false,
                        connectOnSave = false,
                        record = record,
                        canScan = true,
                        canTailscale = true,
                    ),
                    EditorActions(draftOf(record)),
                )
            },
        ) {
            val url = onNodeWithText("Server URL").getBoundsInRoot()
            val check = onNodeWithText("Check connection").getBoundsInRoot()
            // The phone form sits unchanged in the start column; the help panel takes the other column.
            assertTrue(
                check.top.value > url.bottom.value && kotlin.math.abs(check.left.value - url.left.value) < 64f,
                "Expected the unchanged form in one column: url=$url, check=$check",
            )
            val help = onNodeWithText("Tailscale setup and recovery").getBoundsInRoot()
            assertTrue(help.left.value - url.left.value > 300f, "Expected help beside the form: help=$help, url=$url")
        }
    }

    @Test
    fun `B-SRV-303 the chosen row shows the selected state`() {
        showAt(1280, 800, { ServersScreen(listUi(), RecordingServersActions(), onThisPhone = {}) }) {
            onNode(hasText("Home laptop", substring = true) and isSelected()).assertExists()
            onNodeWithText("Office desktop").performClick()
            waitForIdle()
            onNode(hasText("Office desktop", substring = true) and isSelected()).assertExists()
            onNode(hasText("Home laptop", substring = true) and isSelected()).assertDoesNotExist()
        }
    }

    @Test
    fun `B-SRV-304 servers wide fills every width from 840 to 1700 with no gap wider than gutter`() {
        val widths = (840..1700 step 40).toList() + 1700
        val gutter = 16f
        val tolerance = 1f
        for (width in widths.distinct()) {
            showAt(width, 800, { ServersScreen(listUi(), RecordingServersActions(), onThisPhone = {}) }) {
                val start = onNodeWithTag("servers-wide-start").getBoundsInRoot()
                val end = onNodeWithTag("servers-wide-end").getBoundsInRoot()
                val leftMargin = start.left.value
                val rightMargin = width - end.right.value
                val centerGap = end.left.value - start.right.value
                val startWidth = start.right.value - start.left.value
                val endWidth = end.right.value - end.left.value
                assertTrue(
                    leftMargin <= gutter + tolerance,
                    "At ${width}dp left margin $leftMargin exceeded gutter $gutter (start=$start)",
                )
                assertTrue(
                    rightMargin <= gutter + tolerance,
                    "At ${width}dp right margin $rightMargin exceeded gutter $gutter (end=$end)",
                )
                assertTrue(
                    centerGap in 0f..(gutter + tolerance),
                    "At ${width}dp center gap $centerGap exceeded gutter $gutter (start=$start, end=$end)",
                )
                assertTrue(
                    startWidth in 359f..561f,
                    "At ${width}dp list column width $startWidth outside [360, 560]",
                )
                assertTrue(
                    endWidth >= 359f,
                    "At ${width}dp detail column width $endWidth below 360",
                )
            }
        }
    }
}

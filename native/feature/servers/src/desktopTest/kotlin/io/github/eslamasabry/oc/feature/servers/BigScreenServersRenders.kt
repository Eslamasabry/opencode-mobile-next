package io.github.eslamasabry.oc.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.NetworkWhy
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RedactorApi
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
import io.github.eslamasabry.oc.feature.servers.presentation.ServerIssue
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
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

/** Picture only, asserts nothing: the servers list, a server's page, the edit form and the connection-failed state. */
@OptIn(ExperimentalTestApi::class, RedactorApi::class)
class BigScreenServersRenders {
    private val actions =
        object : ServersActions {
            override fun back() {}
            override fun allowCleartext(allowed: Boolean, address: String) {}
            override fun save(name: String, address: String, username: String, password: String) {}
            override fun connect(id: ProfileId) {}
            override fun create(id: ProfileId) {}
            override fun remove(id: ProfileId) {}
        }

    private class Editor(val draft: ServerEditorInput? = null) : ServerEditorActions {
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

    private class Shot(val key: String, val w: Int, val h: Int, val scale: Float, val suffix: String)

    private val shots =
        listOf(
            Shot("phone", 360, 780, 1f, ""),
            Shot("phone", 360, 780, 1.3f, "-t13"),
            Shot("phone412", 412, 915, 1f, ""),
            Shot("split", 600, 800, 1f, ""),
            Shot("tabp", 800, 1280, 1f, ""),
            Shot("exp840", 840, 800, 1f, ""),
            Shot("tab1024", 1024, 768, 1f, ""),
            Shot("tabl", 1280, 800, 1f, ""),
            Shot("tabl", 1280, 800, 1.3f, "-t13"),
        )

    private val out = File("build/bigscreen").also { it.mkdirs() }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        Locale.setDefault(Locale.forLanguageTag("en"))
        for (s in shots) {
            runCatching {
                runDesktopComposeUiTest(s.w * 2, s.h * 2) {
                    setContent {
                        CompositionLocalProvider(LocalDensity provides Density(2f, s.scale)) {
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
                    mainClock.advanceTimeBy(1_000)
                    waitForIdle()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(out, "$name-${s.key}${s.suffix}.png"))
                }
            }.onFailure { println("RENDERFAIL $name ${s.key}${s.suffix} ${it::class.simpleName}: ${it.message?.take(200)}") }
        }
    }

    private val a = SavedServer(ProfileId("home"), "Home laptop", "https://laptop.example.net:4096", connected = true)
    private val b = SavedServer(ProfileId("vps"), "Build server in the data centre for the nightly release jobs", "https://build-runner-eu-west-02.example.net:4096", connected = false)
    private val c = SavedServer(ProfileId("phone"), "This phone", "http://127.0.0.1:4097", connected = false)
    private val d = SavedServer(ProfileId("desk"), "Office desktop", "https://desk.example.net:4096", connected = false)
    private val kind = BackendKind.entries.first()

    private fun listUi(issue: ServerIssue? = null, failure: Failure? = null) =
        ServersUi(
            servers = persistentListOf(a, b, c, d),
            rows =
                ServerRowsState(
                    persistentMapOf(
                        a.id to ServerRowFacts(kind, "2.1.1", "/home/dev/projects/oc_app", false, 1, 2, 0),
                        b.id to ServerRowFacts(kind, "2.1.1", "/srv/build/releases/native", false, null, null, 0),
                        c.id to ServerRowFacts(kind, "2.0.28", "/root/work", false, 0, 0, 0),
                        d.id to ServerRowFacts(kind, "2.1.0", "/home/office", false, 3, 0, 1),
                    ),
                ),
            issue = issue,
            failure = failure,
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

    @Test
    fun render() {
        val sink = KitSecretSink {}
        shoot("servers-list") {
            CompositionLocalProvider(LocalKitSecretSink provides sink) { ServersScreen(listUi(), actions, onThisPhone = {}) }
        }
        shoot("servers-page") {
            CompositionLocalProvider(LocalKitSecretSink provides sink) {
                ServerEditorScreen(
                    ServerEditorUi(step = ServerEditorStep.Ready, newServer = false, record = record, checked = ServerEditorProbe("2.2.6", listOf("opencode", "claude", "codex"))),
                    Editor(),
                )
            }
        }
        shoot("servers-form") {
            CompositionLocalProvider(LocalKitSecretSink provides sink) {
                ServerEditorScreen(
                    ServerEditorUi(step = ServerEditorStep.Connect, newServer = false, connectOnSave = false, record = record, failedCheck = true, canScan = true, canTailscale = true),
                    Editor(draftOf(record)),
                )
            }
        }
        shoot("servers-failed") {
            CompositionLocalProvider(LocalKitSecretSink provides sink) {
                ServersScreen(listUi(ServerIssue.ConnectFailed, Failure.Network(NetworkWhy.Dns, Redacted("OpenGateway - Network - Dns"))), actions, onThisPhone = {})
            }
        }
    }
}

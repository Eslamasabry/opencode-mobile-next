@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.servers.rb.o45

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.connection.SavedServer
import io.github.eslamasabry.oc.feature.servers.pairing.*
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.feature.servers.ui.ServersScreen
import io.github.eslamasabry.oc.feature.servers.ui.plainServerVersion
import io.github.eslamasabry.oc.feature.servers.ui.savedServersByUrgency
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.*

class ServersGateTest {
    private class Actions(var pending: PairingFill? = null) : ServersActions {
        var savedAddress: String? = null
        var passwordWasPresent = false

        override fun takePairing(): PairingFill? = pending.also { pending = null }

        var opened: ProfileId? = null
        var removed: ProfileId? = null

        override fun back() {}

        override fun allowCleartext(allowed: Boolean, address: String) {}

        override fun save(name: String, address: String, username: String, password: String) {
            savedAddress = address
            passwordWasPresent = password.isNotEmpty()
        }

        override fun connect(id: ProfileId) {}

        override fun create(id: ProfileId) {}

        override fun remove(id: ProfileId) {
            removed = id
        }

        override fun settings(id: ProfileId) {
            opened = id
        }
    }

    private val servers =
        persistentListOf(
            SavedServer(ProfileId("laptop"), "Laptop", "https://laptop.example", false),
            SavedServer(ProfileId("studio"), "Studio Mac", "https://studio.example", false),
            SavedServer(ProfileId("codex"), "Codex box", "wss://codex.example", false),
        )
    private val state =
        ServersUi(
            servers = servers,
            rows =
                ServerRowsState(
                    facts =
                        servers
                            .associate {
                                it.id to
                                    ServerRowFacts(
                                        if (it.id.raw == "codex") BackendKind.Codex
                                        else BackendKind.OpenCode2,
                                        null,
                                        "",
                                        false,
                                        0,
                                        0,
                                        0,
                                    )
                            }
                            .toPersistentMap()
                ),
        )

    @Test
    fun `equal urgency keeps registry order even when a row is also running or needs credentials`() {
        val facts = state.rows.facts.toMutableMap()
        facts[servers[0].id] = facts.getValue(servers[0].id).copy(waiting = 1)
        facts[servers[1].id] =
            facts.getValue(servers[1].id).copy(waiting = 1, running = 2, needsSecret = true)
        assertEquals(
            servers.map { it.id },
            savedServersByUrgency(
                    state.copy(rows = state.rows.copy(facts = facts.toPersistentMap()))
                )
                .map { it.id },
        )
        facts[servers[0].id] = facts.getValue(servers[0].id).copy(waiting = 0)
        assertEquals(
            listOf(servers[1].id, servers[0].id, servers[2].id),
            savedServersByUrgency(
                    state.copy(rows = state.rows.copy(facts = facts.toPersistentMap()))
                )
                .map { it.id },
        )
    }

    @Test
    fun `Details retains the version alone or the whole unrecognized reported value`() {
        assertEquals("0.9.2", plainServerVersion("Paseo daemon 0.9.2 (experimental)"))
        assertEquals("2.0.0-beta.3+build.1", plainServerVersion("OpenCode 2.0.0-beta.3+build.1"))
        assertEquals("development", plainServerVersion("  development  "))
    }

    @Test
    fun `a waiting server leads with its attention words`() = runComposeUiTest {
        val facts =
            state.rows.facts.put(
                servers[0].id,
                state.rows.facts.getValue(servers[0].id).copy(waiting = 1),
            )
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                ServersScreen(state.copy(rows = state.rows.copy(facts = facts)), Actions())
            }
        }
        onNodeWithText("Needs you · OpenCode 2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `saved socket credentials ask for a token while OpenCode asks for a password`() =
        runComposeUiTest {
            val facts =
                state.rows.facts
                    .mapValues { (_, facts) -> facts.copy(needsSecret = true, queued = 1) }
                    .toPersistentMap()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    ServersScreen(state.copy(rows = state.rows.copy(facts = facts)), Actions())
                }
            }
            onNodeWithText(
                    "Can't read the saved token · 1 prompt waiting to send · Not connected · Codex",
                    useUnmergedTree = true,
                )
                .assertExists()
            onAllNodesWithText(
                    "Can't read the saved password · 1 prompt waiting to send · Not connected · OpenCode 2",
                    useUnmergedTree = true,
                )
                .assertCountEquals(2)
        }

    @Test
    fun saved_servers() {
        FlutterGoldenGate.check("servers_servers_loaded", frame = FlutterGoldenGate.Frame.Screen) {
            ServersScreen(state, Actions())
        }
    }

    @Test
    fun saved_servers_wide() {
        FlutterGoldenGate.check(
            "servers_servers_loaded_1280x800",
            frame = FlutterGoldenGate.Frame.Screen,
        ) {
            ServersScreen(state, Actions())
        }
    }

    @Test
    fun `server tap opens its settings while removal stays a named menu action`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    ServersScreen(state, actions)
                }
            }
            onNodeWithText("Laptop").performClick()
            assertEquals(ProfileId("laptop"), actions.opened)
            assertEquals(null, actions.removed)
            onNodeWithContentDescription("Laptop").performClick()
            onNodeWithText("Remove").performClick()
            assertEquals(ProfileId("laptop"), actions.removed)
        }

    @Test
    fun `an incoming pairing fill is reviewed and saved without losing its memory-only credential`() =
        runComposeUiTest {
            val fill =
                PairingFill.of(
                    PairingText.read(
                        """{"urls":["https://paired.example"],"password":"fixture-only"}"""
                    )
                )!!
            val actions = Actions(fill)
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    ServersScreen(state.copy(pairingArrival = 1), actions)
                }
            }
            onNodeWithText("Save").performClick()
            assertEquals("https://paired.example", actions.savedAddress)
            assertEquals(true, actions.passwordWasPresent)
            assertEquals(null, fill.takePassword())
        }
}

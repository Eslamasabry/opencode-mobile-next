@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.servers.rb.o45

import io.github.eslamasabry.oc.feature.servers.ServersTestTheme
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
        var reported = 0
        var backs = 0

        override fun back() {
            backs++
        }

        override fun reportProblem() {
            reported++
        }

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
            state.rows.facts.toPersistentMap().put(
                servers[0].id,
                state.rows.facts.getValue(servers[0].id).copy(waiting = 1),
            )
        setContent {
            ServersTestTheme {
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
                ServersTestTheme {
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

    /** The emulator's list (P-019): four OpenCode 2 servers, two saved as "My computer". */
    private fun sameKindState(): ServersUi {
        val list =
            persistentListOf(
                SavedServer(ProfileId("one"), "My computer", "http://10.0.2.2:4123", true),
                SavedServer(ProfileId("two"), "My computer", "http://10.0.2.2:4124", false),
                SavedServer(ProfileId("vps"), "Build VPS", "https://build.example:4096", false),
                SavedServer(ProfileId("lab"), "Lab", "https://lab.example", false),
            )
        return ServersUi(
            servers = list,
            rows =
                ServerRowsState(
                    facts =
                        list
                            .associate {
                                it.id to
                                    ServerRowFacts(
                                        BackendKind.OpenCode2,
                                        null,
                                        "",
                                        false,
                                        0,
                                        0,
                                        if (it.id.raw == "two") 1 else 0,
                                    )
                            }
                            .toPersistentMap()
                ),
        )
    }

    @Test
    fun `P-019 a list of one kind says each fact once, with no addresses and distinct names`() =
        runComposeUiTest {
            setContent { ServersTestTheme { ServersScreen(sameKindState(), Actions()) } }
            onNodeWithText("My computer (1)").assertExists()
            onNodeWithText("My computer (2)").assertExists()
            onAllNodesWithText("OpenCode 2", substring = true, useUnmergedTree = true)
                .assertCountEquals(0)
            onAllNodesWithText("10.0.2.2", substring = true, useUnmergedTree = true)
                .assertCountEquals(0)
            onNodeWithText("1 prompt waiting to send · Not connected", useUnmergedTree = true)
                .assertExists()
            onAllNodesWithText("Not connected", useUnmergedTree = true).assertCountEquals(2)
        }

    @Test
    fun `P-019 the address is still one tap away in Details`() = runComposeUiTest {
        setContent { ServersTestTheme { ServersScreen(sameKindState(), Actions()) } }
        onNodeWithContentDescription("My computer (2)").performClick()
        onNodeWithText("Details").performClick()
        onNodeWithText("http://10.0.2.2:4124").assertExists()
    }

    @Test
    fun saved_servers_same_kind_looks() {
        for ((suffix, scale) in listOf("" to 1f, "-text13" to 1.3f))
            io.github.eslamasabry.oc.feature.servers.Looks.shoot(
                "p019-servers-same-kind$suffix",
                fontScale = scale,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    io.github.eslamasabry.oc.ui.kit.input.LocalKitSecretSink provides
                        io.github.eslamasabry.oc.ui.kit.input.KitSecretSink {}
                ) {
                    ServersScreen(sameKindState(), Actions())
                }
            }
    }

    // Flutter mounts ServersScreen as the app's home (screen_servers_1_test.dart:149): nothing to pop, so the root bar.
    @Test
    fun saved_servers() {
        FlutterGoldenGate.check("servers_servers_loaded", frame = FlutterGoldenGate.Frame.Screen) {
            ServersScreen(state, Actions(), root = true)
        }
    }

    @Test
    fun saved_servers_wide() {
        FlutterGoldenGate.check(
            "servers_servers_loaded_1280x800",
            frame = FlutterGoldenGate.Frame.Screen,
        ) {
            ServersScreen(state, Actions(), root = true)
        }
    }

    @Test
    fun `B-SERVERS-045 the root page carries the product mark and name, with Report a problem behind More`() =
        runComposeUiTest {
            val actions = Actions()
            setContent { ServersTestTheme { ServersScreen(state, actions, root = true) } }
            onNodeWithTag("servers-brand-bar").assertExists()
            onNodeWithText("OpenCode").assertExists()
            onAllNodesWithText("Servers").assertCountEquals(0)
            onAllNodesWithContentDescription("Back").assertCountEquals(0)
            onNodeWithContentDescription("More actions").performClick()
            onNodeWithText("Report a problem").performClick()
            assertEquals(1, actions.reported)
        }

    @Test
    fun `B-SERVERS-046 a pushed servers page keeps Back and its name, without the brand`() =
        runComposeUiTest {
            val actions = Actions()
            setContent { ServersTestTheme { ServersScreen(state, actions) } }
            onNodeWithTag("servers-brand-bar").assertDoesNotExist()
            onNodeWithText("Servers").assertExists()
            onNodeWithContentDescription("Back").performClick()
            assertEquals(1, actions.backs)
        }

    @Test
    fun `server tap opens its settings while removal stays a named menu action`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                ServersTestTheme {
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
                ServersTestTheme {
                    ServersScreen(state.copy(pairingArrival = 1), actions)
                }
            }
            onNodeWithText("Save and connect").performClick()
            assertEquals("https://paired.example", actions.savedAddress)
            assertEquals(true, actions.passwordWasPresent)
            assertEquals(null, fill.takePassword())
        }
}

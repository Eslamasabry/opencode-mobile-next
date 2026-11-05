@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.agents.rb.o49

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.agents.account.*
import io.github.eslamasabry.oc.feature.agents.hub.*
import io.github.eslamasabry.oc.feature.agents.ui.AgentAccountPanel
import io.github.eslamasabry.oc.feature.agents.ui.AgentsScreen
import io.github.eslamasabry.oc.ui.gate.FlutterGoldenGate
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

class AgentsGateTest {
    private class Actions : AgentsActions {
        var installed: String? = null
        var cancelled = false
        var opened: ProfileId? = null
        var refreshed = 0
        var reset = 0
        var restored = 0
        var removed = 0

        override fun confirmPreviousVersion() {
            restored++
        }

        override fun confirmSignInReset() {
            reset++
        }

        override fun openAccount(id: ProfileId) {
            opened = id
        }

        override fun refreshAccount() {
            refreshed++
        }

        override fun cancelAccountSignIn() {
            cancelled = true
        }

        override fun back() {}

        override fun startAdding() {}

        override fun cancelAdding() {}

        override fun edited() {}

        override fun check(address: String, password: String, folder: String) {}

        override fun add(name: String, address: String, password: String, folder: String) {}

        override fun readAgents(id: ProfileId) {}

        override fun newConversation(id: ProfileId) {}

        override fun refreshPhone() {}

        override fun requestRemoval(id: String) {}

        override fun cancelRemoval() {}

        override fun confirmRemoval() {
            removed++
        }

        override fun installPhone(id: String) {
            installed = id
        }
    }

    private val fixtureNow = Instant.parse("2026-09-05T12:00:00Z")
    private val target = AgentAccountTarget(ProfileId("codex"), "My Codex host")

    @Test
    fun `earlier version review renders in its own host and dispatches only restore`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentsScreen(
                        AgentsUi(
                            revertingAgent =
                                PhoneAgentRow(
                                    "codex",
                                    "Codex",
                                    PhoneAgentStatus.Ready,
                                    previousVersionAvailable = true,
                                )
                        ),
                        actions,
                    )
                }
            }
            onNode(hasText("Use earlier Codex version") and hasClickAction()).performClick()
            assertEquals(1, actions.restored)
            assertEquals(0, actions.reset)
            assertEquals(0, actions.removed)
        }

    @Test
    fun `saved sign-in reset requires the named review action`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                AgentsScreen(AgentsUi(resettingSignIns = true), actions)
            }
        }
        assertEquals(0, actions.reset)
        onNode(hasText("Reset saved sign-ins") and hasClickAction()).performClick()
        assertEquals(1, actions.reset)
    }

    @Test
    fun `busy maintenance review cannot send another command or dismiss it`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                AgentsScreen(AgentsUi(resettingSignIns = true, busy = true), actions)
            }
        }
        onNode(hasText("Reset saved sign-ins") and hasClickAction()).assertIsNotEnabled()
        onNodeWithText("Cancel").assertIsNotEnabled()
        assertEquals(0, actions.reset)
    }

    @Test
    fun `a failed account open retries the same target instead of a missing controller`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentAccountPanel(
                        AgentsUi(accountTarget = target, accountFailed = true),
                        actions,
                        now = fixtureNow,
                    )
                }
            }
            onNodeWithText("Could not read the account").assertExists()
            onNodeWithText("Try again").performClick()
            assertEquals(target.id, actions.opened)
            assertEquals(0, actions.refreshed)
            onNodeWithText("Rate limits").assertDoesNotExist()
        }

    @Test
    fun `an opening account shows one loading answer and cannot refresh twice`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentAccountPanel(
                        AgentsUi(accountTarget = target, accountOpening = true),
                        actions,
                        now = fixtureNow,
                    )
                }
            }
            onNodeWithContentDescription("Refresh account").assertIsNotEnabled()
            onNodeWithText("Sign in with ChatGPT").assertDoesNotExist()
            assertEquals(null, actions.opened)
            assertEquals(0, actions.refreshed)
        }

    @Test
    fun `device sign-in names its host and keeps copy and cancel visible`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                AgentsScreen(
                    AgentsUi(
                        accountTarget = target,
                        accountPanel =
                            AccountPanelState(
                                status = AccountPanelStatus.Ready,
                                loginStatus = AccountLoginStatus.Waiting,
                                account = AgentRuntimeAccount(requiresSignIn = true),
                                canCancel = true,
                                deviceCode =
                                    AccountDeviceCode("https://auth.example/device", "DEMO-CODE"),
                            ),
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("\u2066auth.example\u2069").assertExists()
        onNodeWithContentDescription("Copy sign-in code").assertHasClickAction()
        onNodeWithText("Open official sign-in").assertExists()
        onNodeWithText("Cancel sign-in").performClick()
        assertEquals(true, actions.cancelled)
    }

    @Test
    fun `buckets with no windows say metrics are unavailable instead of drawing an empty group`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentAccountPanel(
                        AgentsUi(
                            accountTarget = target,
                            accountPanel =
                                AccountPanelState(
                                    status = AccountPanelStatus.Ready,
                                    account =
                                        AgentRuntimeAccount(
                                            type = "chatgpt",
                                            requiresSignIn = true,
                                        ),
                                    limits =
                                        persistentListOf(AccountRateBucket("Codex", null, null)),
                                ),
                        ),
                        Actions(),
                        now = fixtureNow,
                    )
                }
            }
            onNodeWithText("Rate limits are unavailable for this account or host.").assertExists()
        }

    @Test
    fun `cancelling keeps a visible disabled Cancel and cannot start a second cancel`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentAccountPanel(
                        AgentsUi(
                            accountTarget = target,
                            accountPanel =
                                AccountPanelState(
                                    status = AccountPanelStatus.Ready,
                                    loginStatus = AccountLoginStatus.Cancelling,
                                    account = AgentRuntimeAccount(requiresSignIn = true),
                                    canCancel = false,
                                ),
                        ),
                        actions,
                        now = fixtureNow,
                    )
                }
            }
            onNodeWithText("Cancel sign-in").assertIsNotEnabled()
            assertEquals(false, actions.cancelled)
        }

    @Test
    fun `a signed-in API-key account shows its reported metrics too`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                AgentAccountPanel(
                    AgentsUi(
                        accountTarget = target,
                        accountPanel =
                            AccountPanelState(
                                status = AccountPanelStatus.Ready,
                                account =
                                    AgentRuntimeAccount(type = "apiKey", requiresSignIn = false),
                                usage = AccountTokenUsage(lifetimeTokens = 1234),
                            ),
                    ),
                    Actions(),
                    now = fixtureNow,
                )
            }
        }
        onNodeWithText("Rate limits").assertExists()
        onNodeWithText("Lifetime tokens").assertExists()
        onNodeWithText("1,234").assertExists()
    }

    @Test
    fun `disconnection does not present stale reported metrics as a signed-in account`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentAccountPanel(
                        AgentsUi(
                            accountTarget = target,
                            accountPanel =
                                AccountPanelState(
                                    status = AccountPanelStatus.Disconnected,
                                    account =
                                        AgentRuntimeAccount(
                                            type = "chatgpt",
                                            requiresSignIn = true,
                                        ),
                                    usage = AccountTokenUsage(lifetimeTokens = 1234),
                                ),
                        ),
                        Actions(),
                        now = fixtureNow,
                    )
                }
            }
            onNodeWithText("Rate limits").assertDoesNotExist()
            onNodeWithText("Lifetime tokens").assertDoesNotExist()
        }

    @Test
    fun `a failed device login cannot retain the code panel or its official-page action`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentAccountPanel(
                        AgentsUi(
                            accountTarget = target,
                            accountPanel =
                                AccountPanelState(
                                    status = AccountPanelStatus.Ready,
                                    loginStatus = AccountLoginStatus.Failed,
                                    account = AgentRuntimeAccount(requiresSignIn = true),
                                    deviceCode =
                                        AccountDeviceCode(
                                            "https://auth.example/device",
                                            "STALE-CODE",
                                        ),
                                ),
                        ),
                        Actions(),
                        now = fixtureNow,
                    )
                }
            }
            onNodeWithText("Open official sign-in").assertDoesNotExist()
            onNodeWithContentDescription("Copy sign-in code").assertDoesNotExist()
            onNodeWithText("STALE-CODE").assertDoesNotExist()
        }

    @Test
    fun `Last checked and reached-limit dates use the full date formatter`() = runComposeUiTest {
        val resetAt = Instant.parse("2026-09-05T18:00:00Z").toEpochMilliseconds()
        val platform =
            object : KitPlatform by DesktopKitPlatform() {
                override fun dateTime(atMs: Long, localeTag: String): String =
                    if (atMs == resetAt) "Sep 5, 2026 6:00 PM" else "Sep 5, 2026 12:00 PM"

                override fun messageTime(createdMs: Long, nowMs: Long): String =
                    error("An account fact must retain its full date")
            }
        setContent {
            KitTheme(platform = platform, localeTag = "en", stillFrame = true) {
                AgentAccountPanel(
                    AgentsUi(
                        accountTarget = target,
                        accountPanel =
                            AccountPanelState(
                                status = AccountPanelStatus.Ready,
                                account =
                                    AgentRuntimeAccount(type = "chatgpt", requiresSignIn = true),
                                limits =
                                    persistentListOf(
                                        AccountRateBucket(
                                            "Codex",
                                            AccountRateWindow(100, 300, resetAt),
                                            null,
                                        )
                                    ),
                                updatedAt = fixtureNow,
                            ),
                    ),
                    Actions(),
                    now = fixtureNow,
                )
            }
        }
        onNodeWithText("Last checked Sep 5, 2026 12:00 PM").assertExists()
        onNodeWithText("You’ve reached a Codex limit. Resets in 6 h (Sep 5, 2026 6:00 PM)")
            .assertExists()
    }

    @Test
    fun signed_out() {
        FlutterGoldenGate.check(
            "agent_account_signed_out",
            frame = FlutterGoldenGate.Frame.Screen,
        ) {
            AgentAccountPanel(
                AgentsUi(
                    accountTarget = target,
                    accountPanel =
                        AccountPanelState(
                            status = AccountPanelStatus.Ready,
                            account = AgentRuntimeAccount(requiresSignIn = true),
                            canSignIn = true,
                            updatedAt = fixtureNow,
                        ),
                ),
                Actions(),
                now = fixtureNow,
            )
        }
    }

    @Test
    fun signed_in() {
        FlutterGoldenGate.check("agent_account_signed_in", frame = FlutterGoldenGate.Frame.Screen) {
            AgentAccountPanel(
                AgentsUi(
                    accountTarget = target,
                    accountPanel =
                        AccountPanelState(
                            status = AccountPanelStatus.Ready,
                            account =
                                AgentRuntimeAccount(
                                    type = "chatgpt",
                                    email = "person@example.com",
                                    plan = "plus",
                                    requiresSignIn = true,
                                ),
                            limits =
                                persistentListOf(
                                    AccountRateBucket(
                                        "Codex",
                                        AccountRateWindow(
                                            32,
                                            300,
                                            Instant.parse("2026-09-05T18:00:00Z")
                                                .toEpochMilliseconds(),
                                        ),
                                        AccountRateWindow(68, 10080, null),
                                    )
                                ),
                            usage = AccountTokenUsage(184250, 12640),
                            updatedAt = fixtureNow,
                        ),
                ),
                Actions(),
                now = fixtureNow,
            )
        }
    }

    @Test
    fun `unavailable account methods explain their own state without claiming an unsupported server`() =
        runComposeUiTest {
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    AgentAccountPanel(
                        AgentsUi(
                            accountTarget = target,
                            accountPanel =
                                AccountPanelState(status = AccountPanelStatus.Unavailable),
                        ),
                        Actions(),
                        now = fixtureNow,
                    )
                }
            }
            onNodeWithText("Account panel unavailable").assertExists()
            onNodeWithText("Sign in with ChatGPT").assertDoesNotExist()
        }

    @Test
    fun `the install chip targets the exact phone agent`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                AgentsScreen(
                    AgentsUi(
                        phone =
                            PhoneAgentsState(
                                available = true,
                                agents =
                                    persistentListOf(
                                        PhoneAgentRow(
                                            "claude",
                                            "Claude Code",
                                            PhoneAgentStatus.Ready,
                                        ),
                                        PhoneAgentRow(
                                            "gemini",
                                            "Gemini CLI",
                                            PhoneAgentStatus.NotInstalled,
                                            installable = true,
                                        ),
                                    ),
                            )
                    ),
                    actions,
                )
            }
        }
        onNodeWithContentDescription("Install or update Gemini CLI").performClick()
        assertEquals("gemini", actions.installed)
    }
}

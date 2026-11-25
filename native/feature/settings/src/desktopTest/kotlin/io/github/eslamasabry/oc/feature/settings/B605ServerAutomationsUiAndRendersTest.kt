package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.feature.settings.presentation.AutomationsNotice
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLocalFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.ServerSettingsScreen
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import java.io.File
import java.lang.reflect.Proxy
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class B605ServerAutomationsUiAndRendersTest {
    private val out = File("build/b605-renders").also { it.mkdirs() }
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private val actions =
        Proxy.newProxyInstance(
            ServerSettingsActions::class.java.classLoader,
            arrayOf(ServerSettingsActions::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "B605UiNoopActions"
                else -> null
            }
        } as ServerSettingsActions

    // Fixed timestamps: 2026-11-26T08:00:00Z
    private val nowMs = 1_795_680_000_000L
    private val inOneHour = nowMs + 3_600_000L
    private val oneHourAgo = nowMs - 3_600_000L
    private val yesterday = nowMs - 86_400_000L

    private val sampleAutomations =
        persistentListOf(
            ServerAutomation(
                id = "aut_1",
                name = "CI check",
                status = AutomationStatus.Active,
                trigger = AutomationTrigger.Every("1h"),
                action = AutomationAction.Prompt("Check CI and summarize any failures"),
                nextDueAtMs = inOneHour,
                runCount = 4,
                lastRunAtMs = oneHourAgo,
                lastOutcome = AutomationOutcome.Fired,
            ),
            ServerAutomation(
                id = "aut_2",
                name = "Nightly cleanup",
                status = AutomationStatus.Active,
                trigger = AutomationTrigger.Cron("0 3 * * *"),
                action = AutomationAction.Shell("git gc --prune=now"),
                nextDueAtMs = inOneHour + 7_200_000L,
                runCount = 12,
                lastRunAtMs = yesterday,
                lastOutcome = AutomationOutcome.Failed,
                lastOutput = Redacted("exit code 1: lock file exists"),
            ),
            ServerAutomation(
                id = "aut_3",
                name = "Release summary",
                status = AutomationStatus.Paused,
                trigger = AutomationTrigger.Event("github", "release.published"),
                action = AutomationAction.Prompt("Write release notes", agent = "writer"),
                runCount = 2,
                lastRunAtMs = yesterday,
                lastOutcome = AutomationOutcome.Skipped,
            ),
        )

    private fun laptopUi(
        supportsAutomations: Boolean = true,
        automations: kotlinx.collections.immutable.ImmutableList<ServerAutomation> =
            sampleAutomations,
    ) =
        ServerSettingsUi(
            page = ServerSettingsPage.Home,
            loading = false,
            facts =
                ServerSettingsFacts(
                    name = "Laptop",
                    address = "https://laptop.example:4096",
                    connected = true,
                    hasSecret = true,
                    lastVersion = "2.4.1",
                    canCheck = true,
                    capabilities =
                        ServerCapabilities(
                            scheduledPrompts = true,
                            serverAutomations = supportsAutomations,
                        ),
                    managedOnPhone = false,
                ),
            health = Health(healthy = true, version = "2.4.1"),
            local =
                ServerLocalFacts(
                    model = ModelRef("opencode", "claude-sonnet-4"),
                    watching = true,
                    notifications = true,
                    monitorSupported = true,
                    sharingRead = false,
                    sharingDefault = false,
                    sharingSaving = false,
                ),
            automations = automations,
            nowMs = nowMs,
        )

    @Test
    fun `B-AUTO-014 server page lists Scheduled prompts and Automations with stop all and captures after pictures`() {
        Locale.setDefault(Locale.ENGLISH)
        val variants =
            listOf(
                Triple("light-10", false, 1.0f),
                Triple("dark-10", true, 1.0f),
                Triple("light-13", false, 1.3f),
                Triple("dark-13", true, 1.3f),
            )
        for ((name, dark, scale) in variants) {
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    SettingsThemed(dark = dark, locale = "en", fontScale = scale, width = 360f) {
                        ServerSettingsScreen(laptopUi(), actions)
                    }
                }
                waitForIdle()
                onNodeWithText("Scheduled prompts").performScrollTo().assertIsDisplayed()
                onNodeWithText("Automations").performScrollTo().assertIsDisplayed()
                onNodeWithText("CI check").performScrollTo().assertIsDisplayed()
                onNodeWithText("Nightly cleanup").performScrollTo().assertIsDisplayed()
                onNodeWithText("Release summary").performScrollTo().assertIsDisplayed()
                onNodeWithTag("server-automations-stop-all").performScrollTo().assertIsDisplayed()
                onNodeWithText("Stop all automations on Laptop").assertIsDisplayed()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "after-server-$name.png"),
                )
            }
        }
    }

    @Test
    fun `B-AUTO-015 automations section is hidden when server does not support automations or has none`() {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    ServerSettingsScreen(laptopUi(supportsAutomations = false), actions)
                }
            }
            waitForIdle()
            onNodeWithText("Automations").assertDoesNotExist()
            onNodeWithTag("server-automations-stop-all").assertDoesNotExist()
        }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    ServerSettingsScreen(
                        laptopUi(supportsAutomations = true, automations = persistentListOf()),
                        actions,
                    )
                }
            }
            waitForIdle()
            onNodeWithText("Automations").assertDoesNotExist()
            onNodeWithTag("server-automations-stop-all").assertDoesNotExist()
        }
    }

    @Test
    fun `B-AUTO-016 detail sheet shows controls and captures after pictures`() {
        Locale.setDefault(Locale.ENGLISH)
        val sheetVariants =
            listOf(
                Triple("light-10", false, 1.0f),
                Triple("dark-10", true, 1.0f),
                Triple("light-13", false, 1.3f),
                Triple("dark-13", true, 1.3f),
            )
        for ((name, dark, scale) in sheetVariants) {
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    SettingsThemed(dark = dark, locale = "en", fontScale = scale, width = 360f) {
                        ServerSettingsScreen(
                            laptopUi().copy(selectedAutomationId = "aut_2"),
                            actions,
                        )
                    }
                }
                waitForIdle()
                onNodeWithText("Pause").assertIsDisplayed()
                onNodeWithText("Delete Nightly cleanup from Laptop").assertIsDisplayed()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "after-server-sheet-$name.png"),
                )
            }
        }
    }

    @Test
    fun `B-AUTO-016 delete confirm, stop all confirm and stopped count notice`() {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    ServerSettingsScreen(
                        laptopUi().copy(deleteAutomationConfirmId = "aut_2"),
                        actions,
                    )
                }
            }
            waitForIdle()
            onNodeWithText("Delete Nightly cleanup from Laptop?").assertIsDisplayed()
            onNodeWithText("Delete Nightly cleanup from Laptop").assertIsDisplayed()
        }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    ServerSettingsScreen(
                        laptopUi().copy(stopAllAutomationsConfirm = true),
                        actions,
                    )
                }
            }
            waitForIdle()
            onNodeWithText("Stop all automations on Laptop?").assertIsDisplayed()
            onNodeWithText("This pauses 2 active automations on Laptop. You can resume them later.")
                .assertIsDisplayed()
        }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    ServerSettingsScreen(
                        laptopUi().copy(automationsNotice = AutomationsNotice.StoppedAll(2)),
                        actions,
                    )
                }
            }
            waitForIdle()
            onNodeWithText("Stopped 2 automations on Laptop.").performScrollTo().assertIsDisplayed()
        }
    }
}

package io.github.eslamasabry.oc.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.eslamasabry.oc.core.common.text.NumberSymbols
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.core.model.AutomationAction
import io.github.eslamasabry.oc.core.model.AutomationOutcome
import io.github.eslamasabry.oc.core.model.AutomationStatus
import io.github.eslamasabry.oc.core.model.AutomationTrigger
import io.github.eslamasabry.oc.core.model.Health
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.core.model.ServerAutomation
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.feature.settings.presentation.AutomationsNotice
import io.github.eslamasabry.oc.feature.settings.presentation.ServerLocalFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsActions
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.ServerSettingsUi
import io.github.eslamasabry.oc.feature.settings.ui.ServerSettingsScreen
import io.github.eslamasabry.oc.ui.kit.modal.KitSheetHost
import io.github.eslamasabry.oc.ui.routes.ServerSettingsPage
import java.io.File
import java.lang.reflect.Proxy
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalTestApi::class)
class B605ServerAutomationsUiAndRendersTest {
    private val out = File("build/b605-renders").also { it.mkdirs() }
    private val savedLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(savedLocale)
        Numbers.pinned = null
    }

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

    // Fixed timezone: Europe/Berlin (UTC+1 on 2026-11-26).
    // nowMs = 2026-11-26T08:00:00+01:00 = 2026-11-26T07:00:00Z.
    private val zoneId = "Europe/Berlin"
    private val nowMs = 1_795_676_400_000L
    private val today0900 = nowMs + 3_600_000L // 2026-11-26 09:00 Berlin
    private val today0700 = nowMs - 3_600_000L // 2026-11-26 07:00 Berlin
    private val today0300 = nowMs - 18_000_000L // 2026-11-26 03:00 Berlin
    private val tomorrow0300 = nowMs + 68_400_000L // 2026-11-27 03:00 Berlin
    private val yesterday1600 = nowMs - 57_600_000L // 2026-11-25 16:00 Berlin

    private val sampleAutomations =
        persistentListOf(
            ServerAutomation(
                id = "aut_1",
                name = "CI check",
                status = AutomationStatus.Active,
                trigger = AutomationTrigger.Every("1h"),
                action = AutomationAction.Prompt("Check CI and summarize any failures"),
                timeZone = zoneId,
                nextDueAtMs = today0900,
                runCount = 4,
                lastRunAtMs = today0700,
                lastOutcome = AutomationOutcome.Fired,
            ),
            ServerAutomation(
                id = "aut_2",
                name = "Nightly cleanup",
                status = AutomationStatus.Active,
                trigger = AutomationTrigger.Cron("0 3 * * *", timeZone = zoneId),
                action = AutomationAction.Shell("git gc --prune=now"),
                timeZone = zoneId,
                nextDueAtMs = tomorrow0300,
                runCount = 12,
                lastRunAtMs = today0300,
                lastOutcome = AutomationOutcome.Failed,
                lastOutput = Redactor().details("exit code 1: lock file exists"),
            ),
            ServerAutomation(
                id = "aut_3",
                name = "Release summary",
                status = AutomationStatus.Paused,
                trigger = AutomationTrigger.Event(server = "github", event = "release.published"),
                action = AutomationAction.Prompt("Write release notes", subagent = "writer"),
                timeZone = zoneId,
                runCount = 2,
                lastRunAtMs = yesterday1600,
                lastOutcome = AutomationOutcome.Skipped,
            ),
        )

    private fun laptopUi(
        supportsAutomations: Boolean = true,
        automations: ImmutableList<ServerAutomation> = sampleAutomations,
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
            timeZoneId = zoneId,
        )

    @Test
    fun `B-AUTO-014 server page lists Automations with coherent times and pause all and captures after pictures`() {
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
                        KitSheetHost(Modifier.fillMaxSize()) {
                            ServerSettingsScreen(laptopUi(), actions)
                        }
                    }
                }
                waitForIdle()
                onNodeWithText("Scheduled prompts").assertDoesNotExist()
                onNodeWithText("Automations").performScrollTo().assertIsDisplayed()
                onNodeWithText("CI check").performScrollTo().assertIsDisplayed()
                onNodeWithText("Next: Today 09:00 · Ran Today 07:00").assertIsDisplayed()
                onNodeWithText("Nightly cleanup").performScrollTo().assertIsDisplayed()
                onNodeWithText("Next: Tomorrow 03:00 · Last run failed Today 03:00")
                    .assertIsDisplayed()
                onNodeWithText("Release summary").performScrollTo().assertIsDisplayed()
                onNodeWithTag("server-automations-stop-all").performScrollTo().assertIsDisplayed()
                onNodeWithText("Pause all automations on Laptop").assertIsDisplayed()
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
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(laptopUi(supportsAutomations = false), actions)
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Automations").assertDoesNotExist()
            onNodeWithTag("server-automations-stop-all").assertDoesNotExist()
        }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi(supportsAutomations = true, automations = persistentListOf()),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Automations").assertDoesNotExist()
            onNodeWithTag("server-automations-stop-all").assertDoesNotExist()
        }
    }

    @Test
    fun `B-AUTO-016 detail sheet shows named actions, human schedule with tz, and captures after pictures`() {
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
                        KitSheetHost(Modifier.fillMaxSize()) {
                            ServerSettingsScreen(
                                laptopUi().copy(selectedAutomationId = "aut_2"),
                                actions,
                            )
                        }
                    }
                }
                waitForIdle()
                onNodeWithText("Pause").assertDoesNotExist()
                onNodeWithText("Pause Nightly cleanup on Laptop").assertIsDisplayed()
                onNodeWithText("Delete Nightly cleanup from Laptop").assertIsDisplayed()
                onNodeWithText("Every day at 03:00 (Europe/Berlin) · Next: Tomorrow 03:00")
                    .assertIsDisplayed()
                onNodeWithText("Cron schedule: 0 3 * * *").assertDoesNotExist()
                onNodeWithText("Schedule expression").assertIsDisplayed()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(out, "after-server-sheet-$name.png"),
                )
            }
        }
    }

    @Test
    fun `B-AUTO-016 paused sheet and in-sheet failure`() {
        Locale.setDefault(Locale.ENGLISH)
        // Paused event + subagent automation sheet
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi().copy(selectedAutomationId = "aut_3"),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Resume").assertDoesNotExist()
            onNodeWithText("Resume Release summary on Laptop").assertIsDisplayed()
            onNodeWithText("When github sends release.published").assertIsDisplayed()
            onNodeWithText("Prompt (writer)").assertIsDisplayed()
        }
        // B5: Failure while sheet is open is rendered inside the open sheet
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi().copy(
                                selectedAutomationId = "aut_2",
                                automationSheetFailed = true,
                            ),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("server-automation-sheet-error").assertIsDisplayed()
            onNodeWithText("Could not update automations on Laptop. Try again.").assertIsDisplayed()
        }
    }

    @Test
    fun `B-AUTO-016 real ISO at in phone zone and unknown status`() {
        Locale.setDefault(Locale.ENGLISH)
        // R4 + R1: real v2.4.1 stored ISO Z `at` payload formats in phone zone (Berlin 10:00, not NY 04:00)
        val at0900Z = 1_795_683_600_000L
        val edgeCases =
            persistentListOf(
                ServerAutomation(
                    id = "aut_iso_at",
                    name = "Deploy check",
                    status = AutomationStatus.Unknown,
                    trigger =
                        AutomationTrigger.At(
                            atMs = at0900Z,
                            raw = "2026-11-26T09:00:00.000Z",
                            timeZone = "America/New_York",
                        ),
                    action = AutomationAction.Prompt("Check deploy"),
                    timeZone = "America/New_York",
                )
            )
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi(automations = edgeCases).copy(selectedAutomationId = "aut_iso_at"),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Status unknown").assertIsDisplayed()
            onNodeWithText("Active").assertDoesNotExist()
            onNodeWithText("Once, Today 10:00").assertIsDisplayed()
            onNodeWithText("04:00", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `B-AUTO-016 Arabic UI localizes duration units and Arabic-Indic digits`() {
        val fiveMin =
            persistentListOf(
                ServerAutomation(
                    id = "aut_5m",
                    name = "CI check",
                    status = AutomationStatus.Active,
                    trigger = AutomationTrigger.Every("5 minutes"),
                    action = AutomationAction.Prompt("Check CI"),
                    timeZone = zoneId,
                )
            )
        Locale.setDefault(Locale.forLanguageTag("ar"))
        Numbers.pinned = NumberSymbols(zeroDigit = '٠', grouping = '٬', decimal = '٫', minus = '-')
        try {
            runDesktopComposeUiTest(360, 800) {
                setContent {
                    SettingsThemed(dark = false, locale = "ar", fontScale = 1.0f, width = 360f) {
                        KitSheetHost(Modifier.fillMaxSize()) {
                            ServerSettingsScreen(
                                laptopUi(automations = fiveMin).copy(selectedAutomationId = "aut_5m"),
                                actions,
                            )
                        }
                    }
                }
                waitForIdle()
                onNodeWithText("Every 5 minutes").assertDoesNotExist()
                onNodeWithText("كل 5 دقائق").assertDoesNotExist()
                onNodeWithText("كل ٥ دقائق").assertIsDisplayed()
            }
        } finally {
            Numbers.pinned = null
        }
    }

    @Test
    fun `B-AUTO-025 absolute instants render in phone timezone when server timezone differs`() {
        Locale.setDefault(Locale.ENGLISH)
        val nextAt0900Z = 1_795_683_600_000L // 2026-11-26T09:00:00Z -> 10:00 Berlin, 04:00 NY
        val lastAt0600Z = 1_795_672_800_000L // 2026-11-26T06:00:00Z -> 07:00 Berlin, 01:00 NY
        val crossZone =
            persistentListOf(
                ServerAutomation(
                    id = "aut_tz",
                    name = "New York nightly",
                    status = AutomationStatus.Active,
                    trigger = AutomationTrigger.Cron("0 3 * * *", timeZone = "America/New_York"),
                    action = AutomationAction.Shell("git gc"),
                    timeZone = "America/New_York",
                    nextDueAtMs = nextAt0900Z,
                    runCount = 3,
                    lastRunAtMs = lastAt0600Z,
                    lastOutcome = AutomationOutcome.Fired,
                )
            )
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(laptopUi(automations = crossZone), actions)
                    }
                }
            }
            waitForIdle()
            onNodeWithText("New York nightly").performScrollTo().assertIsDisplayed()
            onNodeWithText("Next: Today 10:00 · Ran Today 07:00").assertIsDisplayed()
            onNodeWithText("04:00", substring = true).assertDoesNotExist()
            onNodeWithText("01:00", substring = true).assertDoesNotExist()
        }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi(automations = crossZone).copy(selectedAutomationId = "aut_tz"),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Every day at 03:00 (America/New_York) · Next: Today 10:00")
                .assertIsDisplayed()
            onNodeWithText("Ran Today 07:00").assertIsDisplayed()
            onNodeWithText("04:00", substring = true).assertDoesNotExist()
            onNodeWithText("01:00", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `B-AUTO-016 delete confirm, pause all confirm and paused count notice`() {
        Locale.setDefault(Locale.ENGLISH)
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi().copy(deleteAutomationConfirmId = "aut_2"),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Delete Nightly cleanup from Laptop?").assertIsDisplayed()
            onNodeWithText("Delete Nightly cleanup from Laptop").assertIsDisplayed()
        }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi().copy(stopAllAutomationsConfirm = true),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Pause all automations on Laptop?").assertIsDisplayed()
            onNodeWithText("This pauses 2 active automations on Laptop. You can resume them later.")
                .assertIsDisplayed()
        }
        runDesktopComposeUiTest(360, 800) {
            setContent {
                SettingsThemed(dark = false, locale = "en", fontScale = 1.0f, width = 360f) {
                    KitSheetHost(Modifier.fillMaxSize()) {
                        ServerSettingsScreen(
                            laptopUi().copy(automationsNotice = AutomationsNotice.StoppedAll(2)),
                            actions,
                        )
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Paused 2 automations on Laptop.").performScrollTo().assertIsDisplayed()
        }
    }
}

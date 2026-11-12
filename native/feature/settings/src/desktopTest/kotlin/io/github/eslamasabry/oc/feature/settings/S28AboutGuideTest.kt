package io.github.eslamasabry.oc.feature.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.feature.settings.presentation.AboutFacts
import io.github.eslamasabry.oc.feature.settings.presentation.GuideUi
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsDoor
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsFacts
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsGroupId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsOutside
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsPage
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsRowId
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsStructure
import io.github.eslamasabry.oc.feature.settings.presentation.SettingsUi
import io.github.eslamasabry.oc.feature.settings.presentation.ShortcutDoes
import io.github.eslamasabry.oc.feature.settings.presentation.ShortcutHelp
import io.github.eslamasabry.oc.feature.settings.ui.SettingsScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf

/** s28-app: Help › Guide, About › Details and About › Keyboard shortcuts, as Flutter draws them. */
@OptIn(ExperimentalTestApi::class)
class S28AboutGuideTest {
    private val android = PlatformCapabilities.android()

    @Test
    fun `B-NAV-090 Help Guide opens the setup guide page, not a hidden outside place`() {
        val rows =
            SettingsStructure.rows(
                SettingsGroupId.Help,
                SettingsFacts(platform = android, opens = emptySet()),
                reasoning = false,
                timestamps = false,
            )
        val guide = rows.single { it.id == SettingsRowId.Guide }
        assertEquals(SettingsDoor.Page(SettingsPage.Guide), guide.door)
    }

    @Test
    fun `B-NAV-091 Guide shows the three steps, the pair command and opens Add server itself`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        SettingsUi(
                            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Guide),
                            guide =
                                GuideUi(
                                    canScan = true,
                                    addServer = true,
                                    phonePath = true,
                                    demo = true,
                                    androidKeystore = true,
                                ),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Setup guide").assertExists()
            onNodeWithText("Three steps to your first conversation", substring = true).assertExists()
            onNodeWithText("opencode2 pair", substring = true).assertExists()
            onNodeWithText("Scan the QR or paste the code in this app").assertExists()
            onNodeWithTag("guide-add-server").performScrollTo().performClick()
            onNodeWithTag("guide-phone-path").performScrollTo().performClick()
            onNodeWithTag("settings-try-demo").performScrollTo().performClick()
            assertEquals(
                listOf(
                    "outside:${SettingsOutside.AddServer}",
                    "outside:${SettingsOutside.PhoneSetup}",
                    "outside:${SettingsOutside.Demo}",
                ),
                actions.calls,
            )
            onNodeWithTag("guide-advanced").performScrollTo().performClick()
            onNodeWithText("opencode2 serve --hostname 127.0.0.1 --port 4096", substring = true, useUnmergedTree = true)
                .assertExists()
            onNodeWithText("Android Keystore", substring = true, useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-NAV-092 Guide without a scanner says paste and leaves out doors that lead nowhere`() =
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        SettingsUi(
                            stack = persistentListOf(SettingsPage.Hub, SettingsPage.Guide),
                            guide = GuideUi(),
                        ),
                        RecordingActions(),
                    )
                }
            }
            onNodeWithText("Paste the code in this app").assertExists()
            onNodeWithTag("guide-add-server").assertDoesNotExist()
            onNodeWithTag("guide-phone-path").assertDoesNotExist()
            onNodeWithTag("settings-try-demo").assertDoesNotExist()
        }

    @Test
    fun `B-NAV-093 About folds the package id and signing certificate under Details`() =
        runComposeUiTest {
            val signer = "1DE5BF08146F269BCD9EB5C2FFC94469CE4617D37806285955F978A62494D60C"
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        SettingsUi(
                            stack = persistentListOf(SettingsPage.About),
                            about =
                                AboutFacts(
                                    "2.1.5",
                                    "60",
                                    packageId = "io.github.eslamasabry.opencode_mobile",
                                    signer = signer,
                                ),
                        ),
                        RecordingActions(),
                    )
                }
            }
            onNodeWithTag("about-details").assertExists()
            onNodeWithText("Details").performClick()
            onNodeWithText("Package id", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText("io.github.eslamasabry.opencode_mobile", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText("Signing certificate SHA-256", substring = true, useUnmergedTree = true).assertExists()
            onNodeWithText(signer, substring = true, useUnmergedTree = true).assertExists()
        }

    @Test
    fun `B-NAV-094 About without a package id draws no Details and no shortcuts without a keyboard`() =
        runComposeUiTest {
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        SettingsUi(stack = persistentListOf(SettingsPage.About), about = AboutFacts("2.1.5", "60")),
                        RecordingActions(),
                    )
                }
            }
            onNodeWithTag("about-details").assertDoesNotExist()
            onNodeWithTag("library-keyboard-shortcuts").assertDoesNotExist()
        }

    @Test
    fun `B-NAV-095 with a keyboard About lists the keyboard shortcuts the shell binds`() =
        runComposeUiTest {
            val actions = RecordingActions()
            setContent {
                SettingsThemed {
                    SettingsScreen(
                        SettingsUi(
                            stack = persistentListOf(SettingsPage.About),
                            about = AboutFacts("2.1.5", "60", keyboardShortcuts = true),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Tips and shortcuts").performScrollTo().assertExists()
            onNodeWithTag("library-keyboard-shortcuts").performScrollTo().performClick()
            assertEquals(listOf("open:${SettingsPage.KeyboardShortcuts}"), actions.calls)
        }

    @Test
    fun `B-NAV-096 the shortcuts page shows each shortcut with its keys`() = runComposeUiTest {
        setContent {
            SettingsThemed {
                SettingsScreen(
                    SettingsUi(
                        stack = persistentListOf(SettingsPage.About, SettingsPage.KeyboardShortcuts),
                        about = AboutFacts("2.1.5", "60", keyboardShortcuts = true),
                    ),
                    RecordingActions(),
                )
            }
        }
        onNodeWithText("Keyboard shortcuts").assertExists()
        onNodeWithText("New conversation").assertExists()
        onNodeWithText("Ctrl + N").assertExists()
        onNodeWithText("Ctrl + ,").assertExists()
        onNodeWithText("Close this screen").assertExists()
        onNodeWithText("Ctrl + /").assertExists()
    }

    @Test
    fun `B-NAV-097 the shortcut list names every key with the platform modifier`() {
        val mac = ShortcutHelp.entries("⌘")
        val plain = setOf(ShortcutDoes.CloseOverlay, ShortcutDoes.RecentModel, ShortcutDoes.FindMatch, ShortcutDoes.PromptHistory)
        assertTrue(mac.filter { it.does !in plain }.all { it.keys.startsWith("⌘ + ") })
        assertEquals(ShortcutDoes.entries.toSet(), mac.map { it.does }.toSet())
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.linux.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.runtime.backup.*
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.manager.LinuxCleanupId
import io.github.eslamasabry.oc.feature.linux.presentation.*
import io.github.eslamasabry.oc.feature.linux.ui.*
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

/** No shell host: every page must register its own question and dispatch only on confirmation. */
class LinuxModalHostTest {
    private class Actions :
        LinuxHomeActions,
        LinuxStorageActions,
        LinuxPackagesActions,
        LinuxMigrationActions,
        LinuxBackupsActions {
        var confirmed = 0
        var dismissed = 0

        override fun confirm() {
            confirmed++
        }

        override fun dismiss() {
            dismissed++
        }

        override fun back() {}

        override fun shown(visible: Boolean) {}

        override fun open(page: LinuxPage) {}

        override fun askRepair() {}

        override fun askReset() {}

        override fun startSetup() {}

        override fun addPython() {}

        override fun continueSetup() {}

        override fun refreshComponents() {}

        override fun askRemoveComponent(id: String) {}

        override fun openOwnershipSettings() {}

        override fun askStopSetup() {}

        override fun setIdleEnabled(enabled: Boolean) {}

        override fun askIdleMinutes() {}

        override fun finishIdleMinutes(value: String?) {}

        override fun resumeIdle() {}

        override fun refreshLifetimes() {}

        override fun askCreateProject() {}

        override fun finishCreateProject(path: String?) {}

        override fun browse(path: String) {}

        override fun closeFolders() {}

        override fun measureRemoval() {}

        override fun askRemove(projects: Boolean) {}

        override fun askStopJob() {}

        override fun dismissJob() {}

        override fun retry() {}

        override fun askCleanup(id: LinuxCleanupId) {}

        override fun search(text: String) {}

        override fun checkInstall(typed: String) {}

        override fun askRemove(name: String) {}

        override fun askUpdate() {}

        override fun toggle(id: String, selected: Boolean) {}

        override fun command(command: LinuxMigrationCommand) {}

        override fun choose(choice: LinuxBackupChoice) {}

        override fun go() {}

        override fun chooseBackup() {}

        override fun askStop() {}
    }

    @Test
    fun `repair question is hosted by the Linux manager without a shell`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LinuxHomeScreen(
                    LinuxHomeUi(presence = LinuxPresence.Ready, confirm = LinuxConfirm.Repair),
                    actions,
                )
            }
        }
        onNodeWithText("Repair Linux?").assertIsDisplayed()
        assertEquals(0, actions.confirmed)
        onNodeWithText("Repair Linux").performClick()
        assertEquals(1, actions.confirmed)
    }

    @Test
    fun `storage cleanup shows the kept data before dispatching the confirmation`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    LinuxStorageScreen(
                        LinuxStorageUi(
                            loading = false,
                            confirm = CleanupAsk(LinuxCleanupId.AptCache, 2048),
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Clear package downloads?").assertIsDisplayed()
            onNodeWithText("Projects, sign-ins and settings stay").assertExists()
            assertEquals(0, actions.confirmed)
            onNodeWithText("Clear package downloads").performClick()
            assertEquals(1, actions.confirmed)
        }

    @Test
    fun `package removal question is visible without an enclosing shell host`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LinuxPackagesScreen(
                    LinuxPackagesUi(
                        loading = false,
                        confirm = PackagesConfirm.Remove("htop", emptyList()),
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Remove htop?").assertIsDisplayed()
        assertEquals(0, actions.confirmed)
        onNodeWithText("Remove htop").performClick()
        assertEquals(1, actions.confirmed)
    }

    @Test
    fun `checked backup opens its restore question without the shell`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                LinuxBackupsScreen(
                    LinuxBackupsUi(
                        backup =
                            LinuxBackupState(
                                phase = LinuxBackupPhase.Ready,
                                manifest =
                                    LinuxBackupManifest("checked-backup", "Nov 6, 2026", true),
                            )
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Restore this backup?").assertIsDisplayed()
        onNodeWithText(
                "Your projects stay. One with the same name comes back as “name (restored)”."
            )
            .assertExists()
        assertEquals(0, actions.confirmed)
        onNodeWithText("Restore").performClick()
        assertEquals(1, actions.confirmed)
    }
}

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.managespace.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.engine.lifecycle.*
import io.github.eslamasabry.oc.feature.managespace.presentation.ManageSpaceActions
import io.github.eslamasabry.oc.feature.managespace.ui.ManageSpaceScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.Test
import kotlin.test.assertEquals

class ManageSpaceParityTest {
    private class Actions : ManageSpaceActions {
        var cleared = 0
        var deleted = 0
        var stopped = 0

        override fun back() {}

        override fun refresh() {}

        override fun includePrivate(value: Boolean) {}

        override fun export() {}

        override fun stopExport() {
            stopped++
        }

        override fun clearCache() {
            cleared++
        }

        override fun deleteAll() {
            deleted++
        }

        override fun dismiss() {}
    }

    private val facts =
        AppStorageFacts(
            listOf(ProjectFacts("shop", 2097152, 1, 0), ProjectFacts("blog", 1048576, 1, 0)),
            true,
            943718400,
            0,
            2,
        )

    @Test
    fun `cache is direct but deletion first names measured losses and retained data`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    ManageSpaceScreen(
                        ManageSpaceState(loading = false, facts = facts, cacheBytes = 5242880),
                        actions,
                    )
                }
            }
            onNodeWithText("Clear the app's cache only").performScrollTo().performClick()
            assertEquals(1, actions.cleared)
            onNodeWithText("Delete everything").performScrollTo().performClick()
            assertEquals(0, actions.deleted)
            onNodeWithText("2 projects (\u20663\u00a0MB\u2069)").assertExists()
            onNodeWithText("2 saved servers and all settings").assertExists()
            // The kept line names computers and git; Termux is no longer part of the app (2026-11-07).
            onNodeWithText(
                    "your computers and anything pushed to git stay",
                    substring = true,
                    ignoreCase = true,
                )
                .assertExists()
        }

    @Test
    fun `a busy export keeps its stop path while destructive actions stay disabled`() =
        runComposeUiTest {
            val actions = Actions()
            setContent {
                KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                    ManageSpaceScreen(
                        ManageSpaceState(
                            loading = false,
                            facts = facts,
                            busy = true,
                            exporting = true,
                        ),
                        actions,
                    )
                }
            }
            onNodeWithText("Clear the app's cache only").performScrollTo().assertIsNotEnabled()
            onNodeWithText("Delete everything").performScrollTo().assertIsNotEnabled()
            onNodeWithText("Stop the export").performScrollTo().performClick()
            assertEquals(1, actions.stopped)
            assertEquals(0, actions.deleted)
        }
}

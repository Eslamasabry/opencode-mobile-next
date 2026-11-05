@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.eslamasabry.oc.feature.linux.rb.o48

import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.engine.runtime.services.kept.*
import io.github.eslamasabry.oc.feature.linux.presentation.*
import io.github.eslamasabry.oc.feature.linux.ui.KeptServicesScreen
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf

class KeptServicesScreenTest {
    private class Actions : KeptServicesActions {
        var previewed: Int? = null
        val acts = mutableListOf<Pair<String, KeptAct>>()

        override fun shown(visible: Boolean) {}

        override fun refresh() {}

        override fun select(name: String) {}

        override fun add() {}

        override fun save(
            name: String,
            command: String,
            directory: String,
            port: String,
            restart: Boolean,
        ) {}

        override fun act(name: String, action: KeptAct) {
            acts += name to action
        }

        override fun confirm() {}

        override fun searchLog(query: String) {}

        override fun copyLog() {}

        override fun preview(port: Int) {
            previewed = port
        }

        override fun dismiss() {}

        override fun back() {}
    }

    private val service =
        KeptService(
            KeptServiceDef("web", "npm run dev", title = "Web", port = 5173),
            wanted = true,
            phase = KeptPhase.Running,
            since = Instant.fromEpochMilliseconds(0),
        )

    @Test
    fun `standalone selected service hosts its preview and inline failure`() = runComposeUiTest {
        val actions = Actions()
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KeptServicesScreen(
                    KeptServicesUi(
                        loading = false,
                        services = persistentListOf(service),
                        selected = "web",
                        failure = Failure.Device(),
                        nowMs = 200_000,
                    ),
                    actions,
                )
            }
        }
        onNodeWithText("Its page on port 5173").assertIsDisplayed()
        onAllNodesWithText(
                "Check that Linux is set up, then try again. Your saved service choices are kept."
            )
            .onLast()
            .assertIsDisplayed()
        onNodeWithText("Open Web").performClick()
        assertEquals(5173, actions.previewed)
        assertTrue(actions.acts.isEmpty())
    }

    @Test
    fun `a backoff countdown rounds up and does not repeat the port`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KeptServicesScreen(
                    KeptServicesUi(
                        loading = false,
                        services =
                            persistentListOf(
                                service.copy(
                                    phase = KeptPhase.Backoff,
                                    lastExit = 3,
                                    nextTry = Instant.fromEpochMilliseconds(2500),
                                )
                            ),
                        nowMs = 1000,
                    ),
                    Actions(),
                )
            }
        }
        onNodeWithText("Crashed (exit 3). Trying again in 2 s").assertExists()
        onNodeWithText("port 5173", substring = true).assertDoesNotExist()
    }

    @Test
    fun `service log distinguishes a clean exit from a crash`() = runComposeUiTest {
        setContent {
            KitTheme(platform = DesktopKitPlatform(), localeTag = "en", stillFrame = true) {
                KeptServicesScreen(
                    KeptServicesUi(
                        loading = false,
                        services = persistentListOf(service.copy(phase = KeptPhase.Exited)),
                        selected = "web",
                        log = "::svc exit 0\n::svc    exit 2",
                    ),
                    Actions(),
                )
            }
        }
        onNodeWithText("Ended (exit 0)").assertExists()
        onNodeWithText("Crashed (exit 2)").assertExists()
    }
}

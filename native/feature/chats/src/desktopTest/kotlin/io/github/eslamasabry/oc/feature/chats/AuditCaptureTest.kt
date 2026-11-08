package io.github.eslamasabry.oc.feature.chats

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.performTouchInput
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsFilters
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeExtraState
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsHomeSheet
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsOpeningUi
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsPhase
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsUi
import io.github.eslamasabry.oc.feature.chats.ui.ChatsScreen
import kotlin.test.Test

/** Audit capture only: the chats home in its states, on the fixtures of the existing home tests. */
@OptIn(ExperimentalTestApi::class)
class AuditCaptureTest {
    private fun home(state: String, ui: () -> ChatsUi, setup: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {}) =
        Audit.guard("chats__home__${state}__build") {
            val built = ui()
            Audit.shoot("chats", "home", state, setup = setup) {
                ChatEnvironment { ChatsScreen(built, RecordingChatsActions()) }
            }
        }

    @Test fun populated() = home("populated", { mixed() })
    @Test fun populatedTwoAgents() = home("two-agents", { mixed(claude = true) })
    @Test fun populatedLong() = home("many-rows", { mixed(extra = 14) })
    @Test fun opening() = home("loading", { ChatsUi(phase = ChatsPhase.Opening, settingsAvailable = true) })
    @Test fun empty() = home("empty", { ChatsUi(phase = ChatsPhase.Empty, settingsAvailable = true) })
    @Test fun noMatch() = home("no-match", { mixed().copy(phase = ChatsPhase.NoMatch, filters = ChatsFilters(running = true)) })
    @Test fun connecting() = home("connecting", {
        ChatsUi(phase = ChatsPhase.Opening, settingsAvailable = true, home = ChatsHomeExtraState(opening = ChatsOpeningUi(ChatsOpeningPhase.Connecting, "Laptop")))
    })
    @Test fun starting() = home("starting-phone-server", {
        ChatsUi(phase = ChatsPhase.Opening, settingsAvailable = true, home = ChatsHomeExtraState(opening = ChatsOpeningUi(ChatsOpeningPhase.Starting, "This phone")))
    })
    @Test fun offline() = home("offline-failed", {
        ChatsUi(phase = ChatsPhase.Opening, settingsAvailable = true, home = ChatsHomeExtraState(opening = ChatsOpeningUi(ChatsOpeningPhase.Failed, "Laptop", failure = Failure.Network())))
    })
    @Test fun incomplete() = home("incomplete-sources", { mixed().copy(incomplete = kotlinx.collections.immutable.persistentListOf("Build VPS", "Laptop")) })
    @Test fun updateReady() = home("update-ready", { mixed().copy(updateReady = true) })

    @Test fun sheets() {
        for (s in ChatsHomeSheet.entries) home("sheet-${s.name.lowercase()}", { mixed().copy(sheet = s) })
    }

    @Test fun rowMenu() = home("row-long-press", { mixed() }, setup = {
        onAllNodes(SemanticsMatcher("row") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { "Fix the reconnect loop" in it } == true })
            .onFirst().performTouchInput { longClick() }
        waitForIdle()
        mainClock.advanceTimeBy(800)
    })
}

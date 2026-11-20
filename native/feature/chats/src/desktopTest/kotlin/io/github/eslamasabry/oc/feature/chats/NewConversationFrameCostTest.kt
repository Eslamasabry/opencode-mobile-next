package io.github.eslamasabry.oc.feature.chats

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.Density
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.ImportableConversation
import io.github.eslamasabry.oc.core.domain.ImportableConversations
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationAttempt
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationHost
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationServer
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationSetup
import io.github.eslamasabry.oc.feature.chats.presentation.NewConversationViewModel
import io.github.eslamasabry.oc.feature.chats.ui.NewConversationScreen
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.gate.FrameCost
import io.github.eslamasabry.oc.ui.gate.FrameMeter
import io.github.eslamasabry.oc.ui.gate.flicker.kitUiTest
import io.github.eslamasabry.oc.ui.gate.summary
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.DesktopKitPlatform
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * c2-performance: what opening New conversation costs a new person (the default appearance), at a phone's size and
 * density: the frames until the page is still, and the frames after it with nothing changing. Counted work ([FrameMeter]).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class NewConversationFrameCostTest {
    private val phone = ProfileId("phone")
    private val dir = "/work/checkout"

    private class Nav : AppNavigator {
        override val top: StateFlow<AppRoute?> = MutableStateFlow(null)

        override fun open(route: AppRoute) {}

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private inner class ReadyHost : NewConversationHost {
        override val servers: StateFlow<List<NewConversationServer>> =
            MutableStateFlow(listOf(NewConversationServer(phone, "This phone", connected = true)))

        override suspend fun setup(profile: ProfileId): Outcome<NewConversationSetup> =
            Outcome.Ok(NewConversationSetup(dir, emptyList(), emptyList(), emptyList()))

        override suspend fun folderExists(profile: ProfileId, directory: String): Boolean? = true

        override suspend fun draft(profile: ProfileId, directory: String?): Outcome<String> = Outcome.Ok("")

        override suspend fun saveDraft(profile: ProfileId, directory: String?, text: String): Outcome<Unit> = Outcome.Ok(Unit)

        override suspend fun start(
            profile: ProfileId,
            directory: String,
            text: String,
            model: ModelRef?,
            agent: String?,
            separateCopy: Boolean,
            previous: NewConversationAttempt?,
            onStarted: (ConversationRef) -> Unit,
        ): NewConversationAttempt = NewConversationAttempt(directory, failure = Failure.Unavailable(FeatureId("test")))

        override suspend fun imports(profile: ProfileId, directory: String): Outcome<ImportableConversations> =
            Outcome.Failed(Failure.Unavailable(FeatureId("test")))

        override suspend fun import(
            profile: ProfileId,
            directory: String,
            conversation: ImportableConversation,
        ): Outcome<ConversationRef> = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
    }

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private fun measure(
        appearance: KitAppearance,
        platform: io.github.eslamasabry.oc.ui.kit.platform.KitPlatform = DesktopKitPlatform(),
        /** Divides the window and density, for a quicker survey. */
        shrink: Int = 1,
    ): Triple<Int, List<FrameCost>, List<FrameCost>> {
        Locale.setDefault(Locale.forLanguageTag("en"))
        lateinit var out: Triple<Int, List<FrameCost>, List<FrameCost>>
        kitUiTest(W / shrink, H / shrink) {
            mainClock.autoAdvance = false
            val meter = FrameMeter.attach(this)
            val vm = NewConversationViewModel(ChatsRoute.New(phone), ReadyHost(), Nav(), Clock.System, ListenedReports())
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(DENSITY / shrink)) {
                    ChatEnvironment {
                        KitTheme(appearance = appearance, platform = platform, localeTag = "en") {
                            val state by vm.state.collectAsState()
                            NewConversationScreen(state, vm)
                        }
                    }
                }
            }
            val opening = meter.frames(30)
            val settled = meter.settle()
            println("PERF-NEW open scopes ${meter.scopesSoFar}")
            val still = meter.frames(120 / shrink)
            meter.close()
            out = Triple(settled, opening, still)
        }
        return out
    }

    @Test
    fun `PERF-NEW-1 New conversation at the default look composites no page-sized layer and rests still`() {
        val (settled, opening, still) = measure(KitAppearance())
        val numbers = "settle+$settled " + opening.summary("open") + " | " + still.summary("still")
        println("PERF-NEW-1 $numbers")
        assertEquals(0, still.count { it.busy }, "a still New conversation asks for no frames: $numbers")
        // Before 2026-11-15 the page's edges composited its whole body offscreen (2.07 M px here) on every frame.
        val most = (opening + still).maxOf { it.offscreenPx }
        assertTrue(most <= W.toLong() * H / 8, "offscreen pixels per frame $most over an eighth of the window: $numbers")
    }

    private companion object {
        const val W = 1080
        const val H = 2340
        const val DENSITY = 2.625f
    }
}

package io.github.eslamasabry.oc.feature.onboarding

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeSource
import io.github.eslamasabry.oc.feature.onboarding.presentation.PhoneWelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeActions
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomePlace
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeSetupLaunch
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeSetupPhase
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeUi
import io.github.eslamasabry.oc.feature.onboarding.presentation.WelcomeViewModel
import io.github.eslamasabry.oc.feature.onboarding.ui.WelcomeScreen
import androidx.compose.foundation.Indication
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.AppRoute
import io.github.eslamasabry.oc.ui.routes.DemoRoute
import io.github.eslamasabry.oc.ui.routes.OnboardingRoute
import io.github.eslamasabry.oc.ui.routes.PhoneSetupRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.oc.ui.routes.SettingsRoute
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * S31 story 5: first run asked "Where should your agent run?" on the welcome, then again on the empty Servers page,
 * where the first answer was gone and the button only said "On this phone". Flutter asks once: the welcome's
 * "Connect to my computer" opens the server editor (`onComputer: _edit`).
 */
@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class S31FirstRunTest {
    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    private class Nav : AppNavigator {
        val opened = mutableListOf<AppRoute>()
        private val mutableTop = MutableStateFlow<AppRoute?>(OnboardingRoute.Welcome)
        override val top: StateFlow<AppRoute?> = mutableTop

        override fun open(route: AppRoute) {
            opened += route
            mutableTop.value = route
        }

        override fun openChat(ref: ConversationRef, focusComposer: Boolean) {}

        override fun land() {}

        override fun replaceRoot(root: AppRoute) {}

        override fun back() {}
    }

    private class TestPlatform : KitPlatform {
        override val name: String = "test"
        override val family: KitPlatformFamily = KitPlatformFamily.Desktop
        override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())
        override val isDebug: Boolean = true

        override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

        override fun haptic(kind: KitHaptic) {}

        override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
    }

    private class Phone(ui: PhoneWelcomeUi) : PhoneWelcomeSource {
        override val state = MutableStateFlow(ui)

        override suspend fun restore() {}

        override suspend fun start(): Outcome<WelcomeSetupLaunch> = Outcome.Ok(WelcomeSetupLaunch.Setup)
    }

    @Test
    fun `B-NAV-110 Connect to my computer opens the server editor, never a second question`() {
        val nav = Nav()
        val vm = WelcomeViewModel(nav)
        vm.connect()
        assertEquals(listOf<AppRoute>(ServersRoute.Edit(null)), nav.opened)
    }

    @Test
    fun `B-NAV-111 the computer answer is kept and its button goes to the editor`() {
        val nav = Nav()
        val vm = WelcomeViewModel(nav, setOf(Phone(PhoneWelcomeUi(available = true))))
        assertEquals(WelcomePlace.Phone, vm.state.value.place)
        vm.select(WelcomePlace.Computer)
        assertEquals(WelcomePlace.Computer, vm.state.value.place)
        vm.begin()
        assertEquals(WelcomePlace.Computer, vm.state.value.place)
        assertEquals(listOf<AppRoute>(ServersRoute.Edit(null)), nav.opened)
    }

    @Test
    fun `B-NAV-112 the quiet ways in open the demo, phone setup's own page and Report a problem`() {
        val nav = Nav()
        val vm = WelcomeViewModel(nav)
        vm.showDemo()
        vm.moreWays()
        vm.reportProblem()
        assertEquals(listOf(DemoRoute, PhoneSetupRoute.Setup, SettingsRoute.ReportProblem), nav.opened)
    }

    private fun recording(): Pair<WelcomeActions, MutableList<String>> {
        val calls = mutableListOf<String>()
        return object : WelcomeActions {
            override fun connect() {
                calls += "connect"
            }

            override fun openSettings() {
                calls += "settings"
            }

            override fun begin() {
                calls += "begin"
            }

            override fun showDemo() {
                calls += "demo"
            }

            override fun moreWays() {
                calls += "more"
            }
        } to calls
    }

    @Test
    fun `B-NAV-113 the welcome asks once in Flutter's words and the button names the phone answer`() =
        runComposeUiTest {
            val (actions, calls) = recording()
            setContent {
                KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = TestPlatform(), localeTag = "en", stillFrame = true) {
                    WelcomeScreen(
                        WelcomeUi(
                            phone = PhoneWelcomeUi(available = true, seconds = 360, bytes = 165_000_000),
                            place = WelcomePlace.Phone,
                        ),
                        actions,
                    )
                }
            }
            onAllNodes(hasText("Where should your agent run?"), useUnmergedTree = true).assertCountEquals(1)
            onNode(hasText("On this phone"), useUnmergedTree = true).assertExists()
            onNode(hasText("About 6 minutes · 165 MB download"), useUnmergedTree = true).assertExists()
            onNode(hasText("On my computer"), useUnmergedTree = true).assertExists()
            onNode(hasText("Pair with a code, or type its address"), useUnmergedTree = true).assertExists()
            onNodeWithText("Just show me").assertExists()
            onNodeWithText("More ways to set up").assertExists()
            onNodeWithText("Set up on this phone").performClick()
            assertEquals(listOf("begin"), calls)
        }

    @Test
    fun `B-NAV-114 with the computer chosen the button says Connect to my computer`() = runComposeUiTest {
        val (actions, calls) = recording()
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = TestPlatform(), localeTag = "en", stillFrame = true) {
                WelcomeScreen(
                    WelcomeUi(phone = PhoneWelcomeUi(available = true), place = WelcomePlace.Computer),
                    actions,
                )
            }
        }
        onNodeWithText("Connect to my computer").performClick()
        assertEquals(listOf("connect"), calls)
    }

    @Test
    fun `B-NAV-115 a started setup says where it stands on the card and the button`() = runComposeUiTest {
        val (actions, _) = recording()
        setContent {
            KitTheme(appearance = KitAppearance(motion = MotionLevel.Off), platform = TestPlatform(), localeTag = "en", stillFrame = true) {
                WelcomeScreen(
                    WelcomeUi(
                        phone =
                            PhoneWelcomeUi(available = true, phase = WelcomeSetupPhase.Stopped, percent = 42),
                        place = WelcomePlace.Phone,
                    ),
                    actions,
                )
            }
        }
        onNode(hasText("Setup is 42% done"), useUnmergedTree = true).assertExists()
        onNodeWithText("Continue setup").assertExists()
    }
}

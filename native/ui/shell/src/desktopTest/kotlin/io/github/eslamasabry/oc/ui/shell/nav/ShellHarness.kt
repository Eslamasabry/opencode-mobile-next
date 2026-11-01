package io.github.eslamasabry.oc.ui.shell.nav

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitRow
import io.github.eslamasabry.oc.ui.kit.KitScreen
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.motion.KitGrowPage
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.shell.NavShell
import io.github.eslamasabry.oc.ui.shell.ShellDependencies
import io.github.eslamasabry.oc.ui.shell.ShellNavigator
import kotlinx.coroutines.flow.MutableStateFlow

/** Drives the dispatcher the way Android's OnBackInvokedCallback input does (started → progressed → completed). */
class FakeBackInput : NavigationEventInput() {
    fun start(edge: Int, progress: Float = 0f) = dispatchOnBackStarted(NavigationEvent(edge, progress))

    fun progress(edge: Int, progress: Float) = dispatchOnBackProgressed(NavigationEvent(edge, progress))

    fun complete() = dispatchOnBackCompleted()

    fun cancel() = dispatchOnBackCancelled()
}

class FakeSystemMotion(scale: Float = 1f) : SystemMotion {
    override val animatorScale: MutableStateFlow<Float> = MutableStateFlow(scale)
}

private object TestPlatform : KitPlatform {
    override val name: String = "test"

    override fun haptic(kind: KitHaptic) = Unit
}

fun ref(i: Int) = ConversationRef(ProfileId("t"), SessionId("s$i"))

/** A list of rows that register grow keys, and a chat page that grows out of them (what :feature:chats installs). */
private val testInstaller = EntryInstaller {
    entry<ChatsRoute.Home> {
        val nav = LocalHarness.navigator
        KitScreen(title = "List") {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items((0 until 30).toList(), key = { it }) { i ->
                    KitRow("Row $i", "preview $i", onClick = { nav.openChat(ref(i)) }, growKey = ref(i))
                }
            }
        }
    }
    entry<ChatRoute> { route ->
        val nav = LocalHarness.navigator
        KitGrowPage(growKey = route.ref) {
            KitScreen(title = "Chat ${route.ref.sessionId.raw}", backLabel = "Back", onBack = nav::back) {
                KitText("transcript line")
                KitButton("Send", onClick = {})
            }
        }
    }
}

/** Test-only holder; the real features reach the navigator through their ViewModels. */
object LocalHarness {
    lateinit var navigator: ShellNavigator
}

class Harness(scale: Float = 1f) {
    val navigator = ShellNavigator().also { LocalHarness.navigator = it }
    val motion = FakeSystemMotion(scale)
    val gate = RowTransformGate()
    val input = FakeBackInput()
    private val dispatcher = NavigationEventDispatcher().also { it.addInput(input) }
    private val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher: NavigationEventDispatcher = dispatcher
    }
    val deps = ShellDependencies(navigator, setOf(testInstaller), motion)

    @Composable
    fun Content(rtl: Boolean = false) {
        CompositionLocalProvider(
            LocalKitPlatform provides TestPlatform,
            LocalNavigationEventDispatcherOwner provides owner,
            LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            KitTheme(dark = false) { NavShell(start = ChatsRoute.Home, deps = deps, gate = gate) }
        }
    }
}

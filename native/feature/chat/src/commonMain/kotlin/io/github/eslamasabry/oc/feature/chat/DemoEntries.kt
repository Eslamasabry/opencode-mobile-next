package io.github.eslamasabry.oc.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.chat.composer.BasicComposer
import io.github.eslamasabry.oc.feature.chat.composer.ComposerChrome
import io.github.eslamasabry.oc.feature.chat.composer.ComposerHost
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoViewModel
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.DemoRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.stateDemoAfter
import io.github.eslamasabry.oc.ui.words.res.stateDemoAllowed
import io.github.eslamasabry.oc.ui.words.res.stateDemoBefore
import io.github.eslamasabry.oc.ui.words.res.stateDemoDenied
import io.github.eslamasabry.oc.ui.words.res.stateDemoLeave
import io.github.eslamasabry.oc.ui.words.res.stateDemoModel
import io.github.eslamasabry.oc.ui.words.res.stateDemoMore
import io.github.eslamasabry.oc.ui.words.res.stateDemoPrompt
import io.github.eslamasabry.oc.ui.words.res.stateDemoReply
import io.github.eslamasabry.oc.ui.words.res.stateDemoReset
import io.github.eslamasabry.oc.ui.words.res.stateDemoTitle
import org.jetbrains.compose.resources.stringResource

/** C39 uses the production chat and composer with a completely route-owned simulated world. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<EntryInstaller>())
public class DemoEntries(private val environment: KitChatEnvironment) : EntryInstaller {
    override fun EntryProviderScope<NavKey>.install() {
        entry<DemoRoute> {
            val vm = metroViewModel<DemoViewModel>()
            val state by vm.state.collectAsStateWithLifecycle()
            val before = stringResource(Res.string.stateDemoBefore)
            val after = stringResource(Res.string.stateDemoAfter)
            val words =
                DemoWords(
                    stringResource(Res.string.stateDemoTitle),
                    stringResource(Res.string.stateDemoPrompt),
                    stringResource(Res.string.stateDemoReply, before, after),
                    stringResource(Res.string.stateDemoAllowed, after),
                    stringResource(Res.string.stateDemoDenied),
                    before = before,
                    after = after,
                    more = stringResource(Res.string.stateDemoMore),
                    model = stringResource(Res.string.stateDemoModel),
                )
            LaunchedEffect(words) { vm.configure(words) }
            val composer = remember(vm) { DemoComposerHost(vm) }
            // The sample agent's ask is the app's own request card over the message box, as in a real conversation
            // (owner 2026-11-10: two "simulated edit" buttons in the message box's place "feels slop").
            val requests by vm.requests.collectAsStateWithLifecycle()
            environment.Provide {
                ChatScreen(
                    state,
                    vm.actions,
                    composer,
                    requests = requests,
                    requestActions = vm.requestActions,
                    demoMode = true,
                    extraActions =
                        listOf(
                            KitLineAction(stringResource(Res.string.stateDemoReset), vm::reset),
                            KitLineAction(
                                stringResource(Res.string.stateDemoLeave),
                                vm.actions::leaveRequested,
                            ),
                        ),
                )
            }
        }
    }
}

/** The demo keeps the conversation's own message box in every stage; Send waits while the sample request does. */
private class DemoComposerHost(private val vm: DemoViewModel) : ComposerHost {
    @Composable
    override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
        val state by vm.composer.collectAsStateWithLifecycle()
        BasicComposer(state, vm, chrome, modifier)
    }
}

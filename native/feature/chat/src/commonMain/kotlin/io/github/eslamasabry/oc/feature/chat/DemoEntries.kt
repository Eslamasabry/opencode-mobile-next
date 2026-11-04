package io.github.eslamasabry.oc.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoStage
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoViewModel
import io.github.eslamasabry.oc.feature.chat.demo.presentation.DemoWords
import io.github.eslamasabry.oc.feature.chat.ui.ChatScreen
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kitchat.KitChatEnvironment
import io.github.eslamasabry.oc.ui.routes.DemoRoute
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.stateDemoAllow
import io.github.eslamasabry.oc.ui.words.res.stateDemoAllowed
import io.github.eslamasabry.oc.ui.words.res.stateDemoDenied
import io.github.eslamasabry.oc.ui.words.res.stateDemoDeny
import io.github.eslamasabry.oc.ui.words.res.stateDemoLeave
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
            val words =
                DemoWords(
                    stringResource(Res.string.stateDemoTitle),
                    stringResource(Res.string.stateDemoPrompt),
                    stringResource(Res.string.stateDemoReply),
                    stringResource(Res.string.stateDemoAllowed),
                    stringResource(Res.string.stateDemoDenied),
                )
            LaunchedEffect(words) { vm.configure(words) }
            val composer = remember(vm) { DemoComposerHost(vm) }
            environment.Provide {
                ChatScreen(
                    state,
                    vm.actions,
                    composer,
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

private class DemoComposerHost(private val vm: DemoViewModel) : ComposerHost {
    @Composable
    override fun Composer(target: ComposerTarget, chrome: ComposerChrome, modifier: Modifier) {
        val state by vm.composer.collectAsStateWithLifecycle()
        val stage by vm.stage.collectAsStateWithLifecycle()
        when (stage) {
            DemoStage.Ready -> BasicComposer(state, vm, chrome, modifier)
            DemoStage.Review ->
                Row(modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitButton(stringResource(Res.string.stateDemoAllow), vm::allow)
                    KitButton(
                        stringResource(Res.string.stateDemoDeny),
                        vm::deny,
                        role = KitButtonRole.Secondary,
                    )
                }
            DemoStage.Finished ->
                Row(modifier.padding(12.dp)) {
                    KitButton(stringResource(Res.string.stateDemoReset), vm::reset)
                }
        }
    }
}

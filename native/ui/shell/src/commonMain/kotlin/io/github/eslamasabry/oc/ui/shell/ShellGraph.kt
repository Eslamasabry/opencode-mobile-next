package io.github.eslamasabry.oc.ui.shell

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import io.github.eslamasabry.oc.ui.routes.EntryInstaller
import kotlin.reflect.KClass

/** Zero features is legal (the desktop gallery); the consumer declares the multibinding (plan 03 §13.5). */
@ContributesTo(AppScope::class)
public interface ShellMultibindings {
    @Multibinds(allowEmpty = true) public val entryInstallers: Set<EntryInstaller>
}

/** What the App() root needs (plan 03 §15.4). Bootstrap, preferences and the status line join in slices 1–2. */
@SingleIn(AppScope::class)
@Inject
public class ShellDependencies(
    public val navigator: ShellNavigator,
    public val installers: Set<EntryInstaller>,
)

/** One factory for both apps (plan 03 §13.7): every @ViewModelKey / assisted factory contributed on the classpath. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
public class ShellViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders: Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()

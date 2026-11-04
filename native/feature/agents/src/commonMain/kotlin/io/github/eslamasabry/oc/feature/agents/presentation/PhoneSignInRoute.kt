package io.github.eslamasabry.oc.feature.agents.presentation

import androidx.lifecycle.ViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AgentsRoute
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public interface PhoneSignInController : SignInTerminalActions {
    public val state: StateFlow<SignInTerminalUi>
    public val pageHost: StateFlow<String?>

    public fun approvePage()

    public fun dismissPage()

    /** The head's sign-in page confirm/dismiss map onto this controller's page approval. */
    override fun confirmSignInPage(): Unit = approvePage()

    override fun dismissSignInPage(): Unit = dismissPage()

    /**
     * Schedules the exact sign-in's drain in the app owner; never abandoned with a screen scope.
     */
    public fun dispose()
}

public interface PhoneSignInFactory {
    public fun create(owner: ProfileId, agent: String, onLeave: () -> Unit): PhoneSignInController
}

/** Desktop product is deferred; the Android app replaces this capability-off binding. */
@Inject
@ContributesBinding(AppScope::class)
public class UnavailablePhoneSignIn : PhoneSignInFactory {
    override fun create(
        owner: ProfileId,
        agent: String,
        onLeave: () -> Unit,
    ): PhoneSignInController =
        object : PhoneSignInController {
            override val state: StateFlow<SignInTerminalUi> =
                MutableStateFlow(SignInTerminalUi(step = SignInStep.Unavailable))
            override val pageHost: StateFlow<String?> = MutableStateFlow(null)

            override fun approvePage() = Unit

            override fun dismissPage() = Unit

            override fun dispose() = Unit

            override fun openSignInPage() = Unit

            override fun copyCode() = Unit

            override fun send(line: String) = Unit

            override fun setAccessibleMode(on: Boolean) = Unit

            override fun finish() = Unit

            override fun startAgain() = Unit

            override fun leave() = onLeave()
        }
}

@AssistedInject
public class PhoneSignInRouteViewModel(
    @Assisted route: AgentsRoute.SignInTerminal,
    factory: PhoneSignInFactory,
    nav: AppNavigator,
) : ViewModel(), PhoneSignInController {
    private val controller = factory.create(route.owner, route.agentId, nav::back)
    override val state: StateFlow<SignInTerminalUi> = controller.state
    override val pageHost: StateFlow<String?> = controller.pageHost

    override fun approvePage(): Unit = controller.approvePage()

    override fun dismissPage(): Unit = controller.dismissPage()

    override fun dispose(): Unit = controller.dispose()

    override fun openSignInPage(): Unit = controller.openSignInPage()

    override fun copyCode(): Unit = controller.copyCode()

    override fun send(line: String): Unit = controller.send(line)

    override fun setAccessibleMode(on: Boolean): Unit = controller.setAccessibleMode(on)

    override fun finish(): Unit = controller.finish()

    override fun startAgain(): Unit = controller.startAgain()

    override fun leave(): Unit = controller.leave()

    override fun onCleared() {
        controller.dispose()
        super.onCleared()
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: AgentsRoute.SignInTerminal): PhoneSignInRouteViewModel
    }
}

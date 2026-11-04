package io.github.eslamasabry.oc.feature.projects.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.*
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ProjectsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Read before constructing a project page: a denied folder never masquerades as an empty project.
 */
public sealed interface ProjectStorageStatus {
    public data object Allowed : ProjectStorageStatus

    public data object NeedsAccess : ProjectStorageStatus

    public data class NeedsRestart(val workRunning: Boolean) : ProjectStorageStatus
}

public interface ProjectStorageAccess {
    public suspend fun inspect(profile: ProfileId, path: String): Outcome<ProjectStorageStatus>

    public suspend fun requestAccess(): Outcome<Unit>

    public suspend fun restart(profile: ProfileId, path: String): Outcome<Unit>
}

/** Desktop has no app-owned phone runtime; its remote project paths require no local permission. */
@Inject
@ContributesBinding(AppScope::class)
public class RemoteProjectStorageAccess : ProjectStorageAccess {
    override suspend fun inspect(profile: ProfileId, path: String): Outcome<ProjectStorageStatus> =
        Outcome.Ok(ProjectStorageStatus.Allowed)

    override suspend fun requestAccess(): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("builtinLinux")))

    override suspend fun restart(profile: ProfileId, path: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("builtinLinux")))
}

public data class ProjectStorageUi(
    val status: ProjectStorageStatus? = null,
    val checking: Boolean = true,
    val failure: Failure? = null,
    val refused: Boolean = false,
)

public interface ProjectStorageActions {
    public fun shown()

    public fun allow()

    public fun restart()

    public fun retry()

    public fun back()

    public fun useAppSpace()
}

@AssistedInject
public class ProjectStorageViewModel(
    @Assisted private val route: ProjectsRoute.Project,
    private val access: ProjectStorageAccess,
    private val nav: AppNavigator,
) : ViewModel(), ProjectStorageActions {
    private val mutable = MutableStateFlow(ProjectStorageUi())
    public val state: StateFlow<ProjectStorageUi> = mutable.asStateFlow()
    private var awaitingAccess = false
    private var reading = false

    init {
        retry()
    }

    override fun shown() {
        retry()
    }

    override fun retry() {
        if (reading) return
        reading = true
        mutable.update { it.copy(checking = true, failure = null) }
        viewModelScope.launch {
            try {
                when (val result = access.inspect(route.profile, route.directory)) {
                    is Outcome.Failed ->
                        mutable.update { it.copy(checking = false, failure = result.failure) }
                    is Outcome.Ok ->
                        mutable.update {
                            it.copy(
                                checking = false,
                                status = result.value,
                                refused =
                                    awaitingAccess &&
                                        result.value == ProjectStorageStatus.NeedsAccess,
                            )
                        }
                }
            } finally {
                reading = false
                awaitingAccess = false
            }
        }
    }

    override fun allow() {
        if (mutable.value.checking) return
        awaitingAccess = true
        viewModelScope.launch {
            when (val result = access.requestAccess()) {
                is Outcome.Failed -> {
                    awaitingAccess = false
                    mutable.update { it.copy(failure = result.failure) }
                }
                is Outcome.Ok -> Unit // The lifecycle return performs a fresh permission read.
            }
        }
    }

    override fun restart() {
        if (mutable.value.checking || mutable.value.status !is ProjectStorageStatus.NeedsRestart)
            return
        mutable.update { it.copy(checking = true, failure = null) }
        viewModelScope.launch {
            when (val result = access.restart(route.profile, route.directory)) {
                is Outcome.Failed ->
                    mutable.update { it.copy(checking = false, failure = result.failure) }
                is Outcome.Ok -> {
                    mutable.update { it.copy(checking = false) }
                    retry()
                }
            }
        }
    }

    override fun back(): Unit = nav.back()

    override fun useAppSpace() {
        nav.back()
        nav.open(ProjectsRoute.Project(route.profile, "/root/projects"))
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted route: ProjectsRoute.Project): ProjectStorageViewModel
    }
}

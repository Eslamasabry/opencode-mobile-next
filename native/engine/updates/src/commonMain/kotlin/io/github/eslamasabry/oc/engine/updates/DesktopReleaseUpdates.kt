package io.github.eslamasabry.oc.engine.updates

import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

public data class DesktopReleaseState(
    val checking: Boolean = false,
    val checked: Boolean = false,
    val release: ReleaseInfo? = null,
    val failure: Failure? = null,
)

/**
 * A desktop check only opens a release page. It never downloads an Android package or changes this
 * installation.
 */
public interface DesktopReleaseUpdates {
    public val state: StateFlow<DesktopReleaseState>

    public suspend fun checkNow(): Outcome<Unit>
}

public class DefaultDesktopReleaseUpdates(
    private val checker: ReleaseChecker,
    private val currentBuild: Int,
    dispatchers: AppDispatchers,
) : DesktopReleaseUpdates {
    private val lane = dispatchers.serial("desktop-release-updates")
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(DesktopReleaseState())
    override val state: StateFlow<DesktopReleaseState> = mutable.asStateFlow()

    override suspend fun checkNow(): Outcome<Unit> =
        withContext(lane) {
            mutex.withLock {
                mutable.value = mutable.value.copy(checking = true, failure = null)
                try {
                    when (val result = checker.fetchLatest()) {
                        is Outcome.Failed -> {
                            mutable.value = mutable.value.copy(failure = result.failure)
                            result
                        }
                        is Outcome.Ok -> {
                            mutable.value =
                                DesktopReleaseState(
                                    checked = true,
                                    release =
                                        result.value?.takeIf {
                                            ReleaseTags.isNewer(currentBuild, it.tag)
                                        },
                                )
                            Unit.ok()
                        }
                    }
                } finally {
                    mutable.value = mutable.value.copy(checking = false)
                }
            }
        }
}

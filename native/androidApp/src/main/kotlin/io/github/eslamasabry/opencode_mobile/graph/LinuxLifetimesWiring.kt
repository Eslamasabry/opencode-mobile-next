package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.domain.capabilities.PlatformCapabilities
import io.github.eslamasabry.oc.engine.runtime.diagnostics.ServiceLifetime
import io.github.eslamasabry.oc.engine.runtime.diagnostics.ServiceLifetimes
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxLifetimesSource
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * B-LINUX-026: read the real native runtime once, off the UI thread, behind the platform
 * capability.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NativeLinuxLifetimes(
    private val app: Application,
    private val platform: PlatformCapabilities,
    dispatchers: AppDispatchers,
) : LinuxLifetimesSource {
    private val io = dispatchers.io
    private val lane = Mutex()

    override suspend fun read(): Outcome<List<ServiceLifetime>> {
        if (!platform.builtinLinux) return Outcome.Ok(emptyList())
        return lane.withLock {
            withContext(io) {
                attempt({ Failure.Runtime(RuntimeProblem.Generic) }) {
                    ServiceLifetimes.read(BuiltinLinux.get(app).performance())
                }
            }
        }
    }
}

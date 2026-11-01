package io.github.eslamasabry.oc.core.common

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock

/** ADR §9.5. Dispatchers.* and Clock.System appear ONLY in this module (Konsist K4). */
public interface AppDispatchers {
    public val main: CoroutineDispatcher
    public val default: CoroutineDispatcher
    public val io: CoroutineDispatcher

    /** One serial lane per engine or per-profile store. Not a mutex (audit #6): it serialises between suspensions. */
    public fun serial(name: String): CoroutineDispatcher
}

/** The ADR's one allowed kind of `expect`: Dispatchers.IO on JVM/Android/Native. */
internal expect val platformIoDispatcher: CoroutineDispatcher

internal object PlatformDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher get() = Dispatchers.Main.immediate
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = platformIoDispatcher

    override fun serial(name: String): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1, name)
}

@BindingContainer
@ContributesTo(AppScope::class)
public object CommonBindings {
    @Provides @SingleIn(AppScope::class) public fun dispatchers(): AppDispatchers = PlatformDispatchers

    @Provides public fun clock(): Clock = Clock.System

    @Provides
    @SingleIn(AppScope::class)
    public fun appScope(d: AppDispatchers): AppCoroutineScope = AppCoroutineScope(supervisedContext(null, d.default, "app"))
}

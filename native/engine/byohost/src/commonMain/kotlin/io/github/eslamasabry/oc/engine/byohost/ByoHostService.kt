package io.github.eslamasabry.oc.engine.byohost

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok

/**
 * Port of lib/state/byo_host_service.dart. One shared runner and store, one controller per phone profile. Releasing
 * one machine's controller never closes the shared runner or any other machine. The feature is off unless [enabled]
 * (Dart `byoHostBuildEnabled`, default false), and without a reviewed [bundle] (see [ByoHostReleasePins]) it is never
 * reported available.
 */
public class ByoHostService(
    private val store: ByoHostStore,
    private val runner: ByoHostSshRunner,
    private val random: ByoHostRandom,
    private val bundle: ByoHostBundle? = null,
    private val clearLocalData: (suspend (String) -> Unit)? = null,
    private val enabled: Boolean = false,
) {
    private val controllers = mutableMapOf<String, ByoHostController>()
    private var disposed = false

    public suspend fun available(): Boolean = enabled && !disposed && bundle != null && runner.available()

    private fun refusal(): Outcome.Failed? =
        if (!enabled || disposed) Outcome.Failed(ByoHostFailureCode.Disabled.asFailure()) else null

    public suspend fun machines(): Outcome<List<ByoHostRecord>> = refusal() ?: store.listRecords().ok()

    /** A controller for a new machine with a fresh random `host-<32 hex>` profile id. */
    public fun newMachine(): Outcome<ByoHostController> {
        refusal()?.let { return it }
        val id = "host-" + random.bytes(16).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return machine(id)
    }

    /** The one controller for [profileId]; the same instance until it is released. */
    public fun machine(profileId: String): Outcome<ByoHostController> {
        refusal()?.let { return it }
        if (!byoHostSafeId(profileId)) return ByoHostFailureCode.InvalidTarget.asFailure().failed()
        return controllers.getOrPut(profileId) {
            ByoHostController(profileId, runner, store, random, bundle, clearLocalData, enabled)
        }.ok()
    }

    /**
     * Call only after the controller finished removal and the connection layer disposed its gateway and swept shared
     * local data. Busy while that controller is running a command.
     */
    public suspend fun release(profileId: String): Outcome<Unit> {
        val controller = controllers[profileId] ?: return Unit.ok()
        val disposedOne = controller.dispose()
        if (disposedOne is Outcome.Failed) return disposedOne
        controllers.remove(profileId)
        return Unit.ok()
    }

    public suspend fun dispose() {
        for (controller in controllers.values) controller.dispose()
        controllers.clear()
        runner.dispose()
        disposed = true
    }
}

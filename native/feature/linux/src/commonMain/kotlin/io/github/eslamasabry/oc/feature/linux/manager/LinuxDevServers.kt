package io.github.eslamasabry.oc.feature.linux.manager

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.engine.runtime.procs.DetectedPhoneServers
import io.github.eslamasabry.oc.engine.runtime.procs.detectedPhoneServers

/**
 * The Linux manager is explicitly the phone's Linux; a connected remote profile never supplies this
 * runner.
 */
public fun LinuxManager.developmentServers(): DetectedPhoneServers =
    detectedPhoneServers { script ->
        when (val result = run(script)) {
            is LinuxAnswer.Ok -> Outcome.Ok(result.value)
            is LinuxAnswer.Problem ->
                Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic, result.details))
        }
    }

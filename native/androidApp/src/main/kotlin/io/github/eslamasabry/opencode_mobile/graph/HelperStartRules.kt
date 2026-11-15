package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.RuntimeProblem

/** Issue 147: which failures of the agents' helper start a later try may fix by itself. */
internal object HelperStartRules {
    /** Not a missing phone Linux or a helper that must be installed or updated first: those wait for the person. */
    fun keepTrying(failure: Failure): Boolean =
        !(failure is Failure.Runtime &&
            (failure.problem == RuntimeProblem.NotInstalled || failure.problem == RuntimeProblem.HelperUpdateRequired))
}

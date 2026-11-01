package io.github.eslamasabry.oc.engine.runtime.host

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.failed

/**
 * The phone's refusal of one runtime port call (plan 11 §5.5, CR11-1): `Outcome.Failed(Failure.Runtime(problem))`,
 * with the Details-only text. The ports answer `Outcome<T>`; `RuntimeProblem` is `:core:common`'s.
 */
public fun RuntimeProblem.refused(details: Redacted? = null): Outcome<Nothing> = Failure.Runtime(this, details).failed()

/** The runtime problem of a failed port call; null when the failure is not a runtime refusal. */
public val Outcome.Failed.runtimeProblem: RuntimeProblem?
    get() = (failure as? Failure.Runtime)?.problem

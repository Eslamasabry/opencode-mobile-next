package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.core.model.TerminalProcess

/** Optional facet; retained rows cannot retarget a request after the project changes. */
public interface TerminalCommandGateway {
    public suspend fun rerunTerminalCommand(
        ended: TerminalProcess,
        location: ServerLocation,
    ): Outcome<TerminalProcess>
}

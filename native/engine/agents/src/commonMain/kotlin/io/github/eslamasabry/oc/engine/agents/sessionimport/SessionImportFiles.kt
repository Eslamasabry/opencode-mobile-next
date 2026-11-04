package io.github.eslamasabry.oc.engine.agents.sessionimport

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * A user-chosen local file, read with a bounded source; null is cancellation. Never persisted or
 * logged.
 */
public interface SessionImportFiles {
    public suspend fun choose(): Outcome<SessionImportDocument?>
}

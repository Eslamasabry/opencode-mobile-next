package io.github.eslamasabry.oc.engine.connection

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * A person's explicit disconnect; distinct from a suspended transport or deleting a saved server.
 */
public interface ServerDisconnects {
    public suspend fun disconnect(id: ProfileId): Outcome<Unit>
}

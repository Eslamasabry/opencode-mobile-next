package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.model.ServerLocation

/**
 * Project operations pinned to a folder without retargeting another conversation's live gateway.
 */
public interface LocatedProjectGateway {
    public fun operationsAt(location: ServerLocation): ServerOperationsGateway
}

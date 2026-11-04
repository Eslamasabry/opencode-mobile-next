package io.github.eslamasabry.oc.core.domain

import io.github.eslamasabry.oc.core.model.ServerLocation

/** A project-bound shell client never mutates the live chat gateway's current directory. */
public interface LocatedManagedShellGateway {
    public fun managedShellsAt(location: ServerLocation): ManagedShellGateway
}

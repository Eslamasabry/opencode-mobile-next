package io.github.eslamasabry.oc.android.notifications

/** Bind one instance for all holders of row 4747 in the app graph (plan 11 §9.2). */
public enum class StatusOwner { Live, Server, Reply, SignIn }

/** Owner changes and the accompanying OS row update are one transaction. */
public class StatusOwners {
    private val owners = linkedMapOf<StatusOwner, StatusContent>()

    @Synchronized
    public fun begin(owner: StatusOwner, content: StatusContent) {
        owners[owner] = content
    }

    @Synchronized
    public fun has(owner: StatusOwner): Boolean = owner in owners

    @Synchronized
    public fun isOnly(owner: StatusOwner): Boolean = owners.keys == setOf(owner)

    /** The caller removes only when [remaining] is null, otherwise detaches and renders that content. */
    @Synchronized
    public fun end(owner: StatusOwner, afterRelease: (remaining: StatusContent?) -> Unit) {
        if (owners.remove(owner) != null) afterRelease(owners.values.lastOrNull())
    }
}

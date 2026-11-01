package io.github.eslamasabry.oc.engine.composer.store

/** What the composer stores need to know about server profiles (answered by the profiles engine). */
public interface ComposerProfiles {
    /** The profile exists and is readable (not removed, not being deleted). */
    public fun exists(profileId: String): Boolean

    /** How many profiles exist: with one, an older (unattributed) draft is unambiguous. */
    public fun count(): Int
}

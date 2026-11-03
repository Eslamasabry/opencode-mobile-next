package io.github.eslamasabry.oc.engine.entry.links

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Durable string storage for the binding blob, one key per profile (`oc.sessionLinkBinding.<profileId>`). The app
 * binds it to the preference store; reads and writes go to disk, never to an optimistic cache.
 */
public interface SessionLinkStorage {
    /** The stored text, Ok(null) when absent, Failed when storage cannot be read. */
    public suspend fun read(key: String): Outcome<String?>

    /** Failed when the platform refused or threw. A failed write may still have left the proposed value behind. */
    public suspend fun write(key: String, value: String): Outcome<Unit>
}

/** Whether a profile is saved (`oc.profiles`): [isListed] is the current view, [confirm] re-reads durable storage. */
public interface SessionLinkProfiles {
    public fun isListed(profileId: String): Boolean

    /** Failed when the profile list cannot be read: an uncertain membership never opens a writer. */
    public suspend fun confirm(profileId: String): Outcome<Boolean>
}

/**
 * Recognizes credentials and registered secrets (the app binds `:core:security` Redactor; `KitRedact.containsSecret`
 * in Flutter). Asked again right before every durable write, since secrets can become known while a write waits.
 */
public fun interface SecretRecognizer {
    public fun containsSecret(text: String): Boolean
}

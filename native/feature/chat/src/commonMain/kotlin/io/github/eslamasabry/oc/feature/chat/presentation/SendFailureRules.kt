package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure

/** Which plain sentence a not-sent message gets beyond the generic failure words. */
public object SendFailureRules {
    /** OpenCode 2's tag for "Directory not found": the project's folder is gone from its server. */
    public const val LOCATION_NOT_FOUND: String = "LocationNotFoundError"

    /**
     * B-063: the server refused the message because the conversation's project folder is no longer
     * there (it still lists the project); the words say so instead of "couldn't find it".
     */
    public fun folderMissing(failure: Failure): Boolean =
        failure is Failure.NotFound && failure.tag?.raw == LOCATION_NOT_FOUND
}

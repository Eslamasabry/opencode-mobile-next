package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.model.transcript.AgentSignInWords

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

    /**
     * The helper answered with the agent's own words for a sign-in that ran out ("OAuth refresh token is no longer
     * valid; run /login"): read first, before any "could not reach" reading of the same failure. The raw text is matched,
     * never shown.
     */
    public fun signInRanOut(failure: Failure): Boolean =
        failure.details?.text?.let(AgentSignInWords::expired) == true

    /**
     * The failed-send banner under the header says what the bubble under the message already says, so it is only for a
     * failed message that has scrolled out of sight: [visibleKeys] are the keys the transcript list lays out now, and
     * [bubbleKey] the failed bubble's. Nothing laid out yet reads as in view, so the banner never flashes at first draw.
     */
    public fun bannerNeeded(bubbleKey: String, visibleKeys: Collection<Any?>): Boolean =
        true
}

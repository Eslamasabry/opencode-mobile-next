package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ServerOrigin

/**
 * Whose Linux a conversation's project lives in, for the Git page. A conversation on This phone's own OpenCode server
 * lives in the phone's Linux; so does a conversation of a phone agent (Claude Code, Codex, Antigravity), which is kept
 * on the phone agents' helper connection, a second saved profile that belongs to the same phone (issue 147 walk F-017:
 * the Git page asked that helper's server to run commands, which it cannot, and said it could not reach the project).
 *
 * Returns the phone's profile id, or null when the project does not live on this phone.
 */
internal fun phoneGitOwner(
    profile: ProfileId,
    origin: ServerOrigin,
    phonePort: Int,
    builtinOwner: String?,
    helperOf: ProfileId?,
): ProfileId? {
    if (builtinOwner.isNullOrEmpty()) return null
    if (helperOf != null) return helperOf.takeIf { it.raw == builtinOwner }
    return profile.takeIf { it.raw == builtinOwner && origin == ServerOrigin("http", "127.0.0.1", phonePort) }
}

package io.github.eslamasabry.oc.feature.handoff.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId

/**
 * Why "Continue with another agent" cannot start from this conversation. Each reason has plain words and a way
 * forward on the screen; none is shown as a failure code. The tag travels in [Failure.Unavailable] from the host.
 */
public enum class HandoffBlock(public val tag: String) {
    /** The conversation's server is another machine; the agents here run on this phone and cannot open its folder. */
    SourceOnComputer("handoffSourceOnComputer"),

    /** The folder is not one of the projects the phone's Linux (where the agents run) can open. */
    FolderNotOnPhone("handoffFolderNotOnPhone"),

    /** The conversation reports no project folder, so there is nothing to open. */
    FolderUnknown("handoffFolderUnknown"),

    /** The app could not prove the agents on this phone share the conversation's folder. Spelling kept from the first build. */
    NotConfirmed("sharedProject"),
    ;

    public fun failure(): Failure = Failure.Unavailable(FeatureId(tag))

    public companion object {
        public fun of(failure: Failure?): HandoffBlock? {
            val name = (failure as? Failure.Unavailable)?.feature?.name ?: return null
            return entries.firstOrNull { it.tag == name }
        }
    }
}

/**
 * The folder check of "Continue with another agent": the receiving agent must be able to open the conversation's
 * project folder. The only agents the app can start run in the phone's Linux, whose projects live under
 * [PHONE_PROJECTS] (the agent helper's shared workspace, not an accident of the first build). A receiving agent on a
 * computer cannot be checked yet: no computer gateway offers a record writer (`agentHandoffFiles` is false there).
 */
public object HandoffFolderRule {
    public const val PHONE_PROJECTS: String = "/root/projects"

    /** A project folder inside the phone's Linux, clean of `.`/`..` parts and control characters. */
    public fun isPhoneProject(directory: String): Boolean =
        directory.length <= 4096 &&
            (directory == PHONE_PROJECTS || directory.startsWith("$PHONE_PROJECTS/")) &&
            directory.split('/').none { it == "." || it == ".." } &&
            directory.none { it.code < 32 || it.code == 127 }

    /**
     * The folder the conversation lives in. The session's own directory is the truth the record is built from; the
     * connection's project (`location.directory`) is only a fallback, because an OpenCode 2 connection is not scoped to
     * one project and reports none while its sessions each carry theirs.
     */
    public fun folderOf(sessionDirectory: String?, connectionDirectory: String?): String? =
        sessionDirectory?.takeIf { it.isNotBlank() } ?: connectionDirectory?.takeIf { it.isNotBlank() }

    /** True when the conversation's server answers on this phone itself. */
    public fun isPhoneEndpoint(scheme: String, host: String): Boolean =
        scheme in setOf("http", "https") && host in setOf("127.0.0.1", "localhost", "::1")

    /**
     * Null when the agents on this phone can open the folder (as far as this rule can tell; the host still proves the
     * shared filesystem), otherwise the reason. [sourceOnPhone]: the conversation runs on this phone.
     */
    public fun judge(sourceOnPhone: Boolean, directory: String?): HandoffBlock? =
        when {
            directory.isNullOrBlank() -> HandoffBlock.FolderUnknown
            !sourceOnPhone -> HandoffBlock.SourceOnComputer
            !isPhoneProject(directory) -> HandoffBlock.FolderNotOnPhone
            else -> null
        }
}

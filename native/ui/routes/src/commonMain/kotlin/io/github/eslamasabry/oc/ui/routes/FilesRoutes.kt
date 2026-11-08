package io.github.eslamasabry.oc.ui.routes

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import kotlinx.serialization.Serializable

/**
 * Whose project Files shows: a conversation's own folder, or one project's folder with no conversation (the project
 * page's Files row, like Flutter's project hub).
 */
@Serializable
public sealed interface FilesPlace {
    public val profileId: ProfileId

    @Serializable
    public data class Conversation(val ref: ConversationRef) : FilesPlace {
        override val profileId: ProfileId
            get() = ref.profileId
    }

    @Serializable
    public data class Project(val profile: ProfileId, val directory: String) : FilesPlace {
        override val profileId: ProfileId
            get() = profile
    }
}

/**
 * Project Files (07 §5 D02/D03, P12-FILES): a conversation's own server and project, or one project without a
 * conversation. Folders are routes, so predictive back walks up one level at a time. Owned by `:feature:files`.
 */
@Serializable
public sealed interface FilesRoute : AppRoute {
    public val place: FilesPlace

    /** "" is the project root. */
    public val path: String

    /** A folder route of either place. */
    @Serializable
    public sealed interface AnyFolder : FilesRoute

    /** A file route of either place; [line] opens the code view there. */
    @Serializable
    public sealed interface AnyFile : FilesRoute {
        public val line: Int?
    }

    /** One folder of the conversation's project; "" is the project root. */
    @Serializable
    public data class Folder(val from: ConversationRef, override val path: String = "") : AnyFolder {
        override val place: FilesPlace
            get() = FilesPlace.Conversation(from)
    }

    /** One file in the viewer; [line] opens the code view there (a path chip with `:42`). */
    @Serializable
    public data class File(val from: ConversationRef, override val path: String, override val line: Int? = null) :
        AnyFile {
        override val place: FilesPlace
            get() = FilesPlace.Conversation(from)
    }

    /** One folder of a project opened without a conversation. */
    @Serializable
    public data class ProjectFolder(val profile: ProfileId, val directory: String, override val path: String = "") :
        AnyFolder {
        override val place: FilesPlace
            get() = FilesPlace.Project(profile, directory)
    }

    /** One file of a project opened without a conversation. */
    @Serializable
    public data class ProjectFile(
        val profile: ProfileId,
        val directory: String,
        override val path: String,
        override val line: Int? = null,
    ) : AnyFile {
        override val place: FilesPlace
            get() = FilesPlace.Project(profile, directory)
    }

    public companion object {
        /** The folder route at [path] in [place]. */
        public fun folder(place: FilesPlace, path: String = ""): AnyFolder =
            when (place) {
                is FilesPlace.Conversation -> Folder(place.ref, path)
                is FilesPlace.Project -> ProjectFolder(place.profile, place.directory, path)
            }

        /** The file route at [path] in [place]. */
        public fun file(place: FilesPlace, path: String, line: Int? = null): AnyFile =
            when (place) {
                is FilesPlace.Conversation -> File(place.ref, path, line)
                is FilesPlace.Project -> ProjectFile(place.profile, place.directory, path, line)
            }
    }
}

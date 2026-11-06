package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome

/**
 * Creates one safe folder under the app-managed phone runtime's /root/projects. Remote servers
 * cannot create.
 */
public interface ProjectCreation {
    public fun available(server: ProjectServer): Boolean

    public suspend fun create(server: ProjectServer, name: String): Outcome<String>

    /**
     * Whether [path] exists as a folder on [server]. Null when this server cannot be asked, in
     * which case a typed path is opened as is (the server reports a missing folder itself).
     */
    public suspend fun exists(server: ProjectServer, path: String): Boolean? = null

    /** Makes the folder at the exact [path] (the "Create this folder?" answer). */
    public suspend fun createAt(server: ProjectServer, path: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))
}

/**
 * One reusable "make a project and remember it" step, so every New project entry (the Projects
 * picker, the New-conversation project sheet) shares the same checks and the same result.
 */
public class NewProjectRunner(
    private val creator: ProjectCreation,
    private val opened: OpenedFolders,
) {
    public fun available(server: ProjectServer): Boolean = creator.available(server)

    /** Creates `/root/projects/<name>` and remembers it; the folder path on success. */
    public suspend fun create(server: ProjectServer, rawName: String): Outcome<String> {
        val name =
            io.github.eslamasabry.oc.engine.agents.workspace.ProjectFolderName.validated(rawName)
                ?: return Outcome.Failed(Failure.StorageRefused())
        return remember(server, creator.create(server, name))
    }

    /** Creates the folder at exactly [path] and remembers it. */
    public suspend fun createAt(server: ProjectServer, path: String): Outcome<String> =
        remember(server, creator.createAt(server, path))

    private suspend fun remember(server: ProjectServer, made: Outcome<String>): Outcome<String> =
        when (made) {
            is Outcome.Failed -> made
            is Outcome.Ok ->
                when (val saved = opened.remember(server.profile, made.value)) {
                    is Outcome.Ok -> made
                    is Outcome.Failed -> saved
                }
        }
}

@ContributesBinding(AppScope::class)
@Inject
public class NoProjectCreation : ProjectCreation {
    override fun available(server: ProjectServer): Boolean = false

    override suspend fun create(server: ProjectServer, name: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("projectFolderCreation")))
}

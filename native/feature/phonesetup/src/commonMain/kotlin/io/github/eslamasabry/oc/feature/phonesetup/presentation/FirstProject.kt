package io.github.eslamasabry.oc.feature.phonesetup.presentation

import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.ui.routes.AppRoute

/**
 * The setup's Ready step makes the first project (Flutter phone_setup_ready_screen.dart): one name becomes a folder
 * in the phone's own Linux (with git, B-AGENT-126), opened on the phone's server, landing in its first conversation.
 * The app binds it over the projects' own creation; without a binding the step keeps its plain Open button.
 */
public interface FirstProject {
    /** A first project can be made here (the phone's server is set up and its creation is wired). */
    public fun available(): Boolean

    /**
     * Creates the folder [name]; answers its path inside Linux. A failure here reads "could not be created".
     */
    public suspend fun create(name: String): Outcome<String>

    /**
     * Opens the created [path] on the phone's server (reconnecting to it when the connection dropped) and answers
     * where to go: its first conversation, or the project page. A failure reads "could not be opened".
     */
    public suspend fun open(path: String): Outcome<AppRoute>
}

/** The step a first project failed at, so the words match Flutter's "could not be created / opened". */
public enum class FirstProjectStep { Create, Open }

public class NoFirstProject : FirstProject {
    override fun available(): Boolean = false

    override suspend fun create(name: String): Outcome<String> =
        Outcome.Failed(Failure.Unavailable(FeatureId("firstProject")))

    override suspend fun open(path: String): Outcome<AppRoute> =
        Outcome.Failed(Failure.Unavailable(FeatureId("firstProject")))
}

/** Why a first project name cannot be used (Flutter `projectFolderNameProblem`, B-STATE-158; same rule). */
public enum class FirstProjectNameProblem { Empty, OneFolder, Invalid }

public object FirstProjectName {
    /** Flutter `PhoneSetupReadyScreen.suggestedName`: short, valid, obviously a placeholder. */
    public const val SUGGESTED: String = "my-app"

    private val SAFE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

    public fun problem(raw: String): FirstProjectNameProblem? {
        val value = raw.trim()
        return when {
            value.isEmpty() -> FirstProjectNameProblem.Empty
            value == "." || value == ".." || '/' in value || '\\' in value -> FirstProjectNameProblem.OneFolder
            !SAFE.matches(value) -> FirstProjectNameProblem.Invalid
            else -> null
        }
    }
}

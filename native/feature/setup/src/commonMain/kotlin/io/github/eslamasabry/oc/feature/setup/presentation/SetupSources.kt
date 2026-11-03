package io.github.eslamasabry.oc.feature.setup.presentation

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupProgress
import kotlinx.coroutines.flow.StateFlow

/**
 * The phone setup engine as the page sees it (bound by androidApp's SetupWiring over :engine:runtime's SetupEngine and
 * :android:linux's SetupRunner). Every command publishes into [progress]; none throws.
 */
public interface SetupSource {
    /** Prepares the engine (its words come from `:ui:words`) and hands what the page reads. */
    public suspend fun open(): SetupSession

    /** Reads the persisted job (after an app restart) into the session's progress. */
    public suspend fun restore(): Outcome<Unit>

    /** Starts or continues a job for [ids] (`SetupEngine.run`). */
    public suspend fun run(ids: Set<String>, params: Map<String, Map<String, String>>)

    /** Stops the running job; finished components stay (`SetupEngine.cancel`). */
    public suspend fun cancel()
}

/** The engine as one page reads it. */
public class SetupSession(
    /** The live progress of the current or last job; Idle when there is none. Collecting it keeps the engine reading. */
    public val progress: StateFlow<SetupProgress>,
    /** Every component the app knows, in dependency order (`SetupEngine.registry`). */
    public val components: List<SetupComponent>,
    /** The stage labels the native Linux base reports (`phoneSetupStageDownloadingLinux`, `...UnpackingLinux`). */
    public val downloadingLinuxStage: String,
    public val unpackingLinuxStage: String,
)

/** Where the page goes. */
public interface SetupNavigator {
    public fun back()

    /** Android's page for this app (Force stop, for the ownership recovery). False when it did not open. */
    public fun openAppSettings(): Boolean
}

/** The Redactor's Details form of a log (`Redactor.details`): the only way technical text reaches the page. */
public fun interface SetupDetails {
    public fun of(text: String): Redacted
}

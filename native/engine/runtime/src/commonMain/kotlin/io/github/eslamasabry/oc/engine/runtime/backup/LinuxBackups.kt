package io.github.eslamasabry.oc.engine.runtime.backup

import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

public enum class LinuxBackupKind {
    Setup,
    SetupAndProjects,
}

public enum class LinuxBackupPhase {
    Idle,
    Preparing,
    Saving,
    Checking,
    Ready,
    Restoring,
    Saved,
    Restored,
    Failed,
}

public enum class LinuxBackupProblem {
    Busy,
    Missing,
    Job,
    Save,
    NotBackup,
    Newer,
    Restore,
}

public data class LinuxBackupManifest(
    val token: String,
    val created: String,
    val hasProjects: Boolean,
)

public data class LinuxRestoreSummary(
    val counts: Map<String, Int> = emptyMap(),
    val skipped: List<String> = emptyList(),
)

public data class LinuxBackupProgress(
    val phase: LinuxBackupPhase,
    val bytes: Long = 0,
    val total: Long = 0,
    val percent: Int? = null,
)

public sealed interface LinuxBackupResult {
    public data object Cancelled : LinuxBackupResult

    public data class Saved(val name: String, val bytes: Long) : LinuxBackupResult

    public data class Checked(val manifest: LinuxBackupManifest) : LinuxBackupResult

    public data class Restored(val summary: LinuxRestoreSummary) : LinuxBackupResult

    public data class Problem(val kind: LinuxBackupProblem, val failure: Failure? = null) :
        LinuxBackupResult
}

/**
 * Device-wide Linux backup, over app-authored fixed archive names. The host never exposes an
 * archive path to UI.
 */
public interface LinuxBackupPlatform {
    public suspend fun backup(
        kind: LinuxBackupKind,
        progress: (LinuxBackupProgress) -> Unit,
    ): LinuxBackupResult

    public suspend fun choose(): LinuxBackupResult

    public suspend fun restore(
        token: String,
        progress: (LinuxBackupProgress) -> Unit,
    ): LinuxBackupResult

    public suspend fun stop(): Outcome<Unit>

    public suspend fun forgetChosen(): Unit
}

/**
 * Desktop gallery has no built-in Linux; this binding reports capability absence and performs no
 * file operation.
 */
public class UnavailableLinuxBackupPlatform : LinuxBackupPlatform {
    override suspend fun backup(
        kind: LinuxBackupKind,
        progress: (LinuxBackupProgress) -> Unit,
    ): LinuxBackupResult = missing()

    override suspend fun choose(): LinuxBackupResult = missing()

    override suspend fun restore(
        token: String,
        progress: (LinuxBackupProgress) -> Unit,
    ): LinuxBackupResult = missing()

    override suspend fun stop(): Outcome<Unit> = Outcome.Failed(Failure.Device())

    override suspend fun forgetChosen(): Unit = Unit

    private fun missing(): LinuxBackupResult = LinuxBackupResult.Problem(LinuxBackupProblem.Missing)
}

public data class LinuxBackupState(
    val phase: LinuxBackupPhase = LinuxBackupPhase.Idle,
    val bytes: Long = 0,
    val total: Long = 0,
    val percent: Int? = null,
    val name: String = "",
    val manifest: LinuxBackupManifest? = null,
    val summary: LinuxRestoreSummary? = null,
    val problem: LinuxBackupProblem? = null,
    val failure: Failure? = null,
) {
    val busy: Boolean
        get() =
            phase in
                setOf(
                    LinuxBackupPhase.Preparing,
                    LinuxBackupPhase.Saving,
                    LinuxBackupPhase.Checking,
                    LinuxBackupPhase.Restoring,
                )
}

/** App-owned serial lane: leaving the screen does not drop a running backup, save, or restore. */
public class LinuxBackups(
    platform: LinuxBackupPlatform,
    app: AppCoroutineScope,
    dispatchers: AppDispatchers,
) {
    private val host = platform
    private val scope = CoroutineScope(app.coroutineContext + dispatchers.serial("linux.backups"))
    private val lane = Mutex()
    private val mutable = MutableStateFlow(LinuxBackupState())
    public val state: StateFlow<LinuxBackupState> = mutable.asStateFlow()

    public fun backup(kind: LinuxBackupKind): Unit =
        run(LinuxBackupPhase.Preparing) { host.backup(kind, ::progress) }

    public fun choose(): Unit = run(LinuxBackupPhase.Checking) { host.choose() }

    public fun restore() {
        val token = state.value.manifest?.token ?: return
        run(LinuxBackupPhase.Restoring, keepManifest = true) { host.restore(token, ::progress) }
    }

    public fun stop() {
        if (
            state.value.phase != LinuxBackupPhase.Preparing &&
                state.value.phase != LinuxBackupPhase.Restoring
        )
            return
        scope.launch {
            when (val result = host.stop()) {
                is Outcome.Ok -> Unit
                is Outcome.Failed ->
                    mutable.update {
                        it.copy(problem = LinuxBackupProblem.Busy, failure = result.failure)
                    }
            }
        }
    }

    public fun reset() {
        if (state.value.busy || !lane.tryLock()) return
        mutable.value = LinuxBackupState()
        scope.launch {
            try {
                host.forgetChosen()
            } finally {
                lane.unlock()
            }
        }
    }

    private fun progress(p: LinuxBackupProgress) {
        mutable.update {
            it.copy(phase = p.phase, bytes = p.bytes, total = p.total, percent = p.percent)
        }
    }

    private fun run(
        phase: LinuxBackupPhase,
        keepManifest: Boolean = false,
        action: suspend () -> LinuxBackupResult,
    ) {
        if (!lane.tryLock()) return
        mutable.value =
            LinuxBackupState(phase = phase, manifest = state.value.manifest.takeIf { keepManifest })
        scope.launch {
            try {
                when (val result = action()) {
                    LinuxBackupResult.Cancelled -> mutable.value = LinuxBackupState()
                    is LinuxBackupResult.Saved ->
                        mutable.value =
                            LinuxBackupState(
                                phase = LinuxBackupPhase.Saved,
                                name = result.name,
                                bytes = result.bytes,
                                total = result.bytes,
                            )
                    is LinuxBackupResult.Checked ->
                        mutable.value =
                            LinuxBackupState(
                                phase = LinuxBackupPhase.Ready,
                                manifest = result.manifest,
                            )
                    is LinuxBackupResult.Restored ->
                        mutable.value =
                            LinuxBackupState(
                                phase = LinuxBackupPhase.Restored,
                                summary = result.summary,
                            )
                    is LinuxBackupResult.Problem ->
                        mutable.update {
                            it.copy(
                                phase = LinuxBackupPhase.Failed,
                                problem = result.kind,
                                failure = result.failure,
                            )
                        }
                }
            } finally {
                lane.unlock()
            }
        }
    }
}

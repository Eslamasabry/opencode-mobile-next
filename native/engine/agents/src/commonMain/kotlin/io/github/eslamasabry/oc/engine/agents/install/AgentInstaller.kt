package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.AgentKeys
import io.github.eslamasabry.oc.engine.agents.OwnerId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentArchitecture
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.guard.AgentInstallGuard
import io.github.eslamasabry.oc.engine.agents.guard.requiredSetupFreeBytes
import io.github.eslamasabry.oc.engine.agents.store.AgentPrefs
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One setup component of an agent install (`phoneAgentComponents`). Scripts are the runtime owner's
 * resources.
 */
public data class AgentSetupComponent(
    val id: String,
    val dependsOn: ImmutableList<String>,
    /** Runs as uid 1000 `oc` and refuses root. Every agent component sets it. */
    val agentUser: Boolean,
)

/** The durable native job (11 §5.4 `SetupJobHost`), as this engine needs it. */
public interface AgentSetupJobs {
    /**
     * True while any Linux/OpenCode/agent setup runs on this phone (`agentSetupBusy`). Unknown
     * counts as busy.
     */
    public suspend fun anySetupRunning(): Boolean?

    /** Free bytes where the payloads go; null when unknown. */
    public suspend fun freeBytes(): Long?

    public suspend fun start(
        owner: OwnerId,
        agent: AgentId,
        components: List<AgentSetupComponent>,
    ): Outcome<Unit>

    /** The job [owner] started, or null when there is none. */
    public suspend fun job(owner: OwnerId): Outcome<AgentSetupJob?>

    /** Cancels only the exact durable owner's job. */
    public suspend fun cancel(owner: OwnerId, agent: AgentId): Outcome<Unit>
}

public enum class AgentSetupJobState {
    Running,
    Done,
    Failed,
    Interrupted,
}

public data class AgentSetupJob(
    val agent: AgentId,
    val state: AgentSetupJobState,
    val componentId: String?,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val failure: AgentSetupFailure?,
)

/** Fixed failure kinds (raw npm or shell output never reaches UI or logs; B-AGENT-124). */
public enum class AgentSetupFailure {
    Download,
    Checksum,
    Storage,
    Interrupted,
    Version,
    Unknown,
}

public enum class AgentSetupPhase {
    Idle,
    Installing,
    Done,
    Interrupted,
    Failed,
}

public data class AgentSetupProgress(
    val agentId: AgentId?,
    val phase: AgentSetupPhase,
    /** Determinate only from measured bytes; null means an indeterminate bar. */
    val fraction: Double?,
    val componentId: String?,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val failure: AgentSetupFailure?,
) {
    public companion object {
        public val Idle: AgentSetupProgress =
            AgentSetupProgress(null, AgentSetupPhase.Idle, null, null, null, null, null)
    }
}

/** Why an install did not start (each has one sentence in words). */
public enum class InstallRefusal {
    /** "Another setup is running". */
    SetupBusy,
    /** This phone cannot run it, or it has no verified download. */
    NotInstallable,
    /** Download plus extracted size does not fit (`SetupDiskSpace`). */
    NotEnoughSpace,
    /** An install for this owner is already running. */
    AlreadyInstalling,
}

public sealed interface InstallStart {
    public data object Started : InstallStart

    public data class Refused(val reason: InstallRefusal) : InstallStart
}

/**
 * The install pipeline (AGENTS-BACKENDS-08, plan 17 §1.5): builds the component list and runs it on
 * the durable setup job, writing the `oc.agentInstall.<owner>` marker first so [restore] can
 * re-attach after a kill. One serial lane.
 */
public class AgentInstaller(
    private val prefs: AgentPrefs,
    private val jobs: AgentSetupJobs,
    private val catalog: AgentCatalog = AgentCatalog.builtIn,
    private val guard: AgentInstallGuard = AgentInstallGuard(),
) {
    private val lane = Mutex()
    private val state = MutableStateFlow(AgentSetupProgress.Idle)
    public val progress: StateFlow<AgentSetupProgress> = state.asStateFlow()

    public suspend fun install(
        owner: OwnerId,
        agentId: AgentId,
        architecture: AgentArchitecture,
    ): Outcome<InstallStart> = lane.withLock {
        val agent = catalog.byId(agentId)
        if (agent == null || !agent.runsOnPhoneHost || !agent.installableOn(architecture)) {
            return@withLock InstallStart.Refused(InstallRefusal.NotInstallable).ok()
        }
        if (
            prefs.string(AgentKeys.agentInstall(owner)) != null &&
                state.value.phase == AgentSetupPhase.Installing
        ) {
            return@withLock InstallStart.Refused(InstallRefusal.AlreadyInstalling).ok()
        }
        if (jobs.anySetupRunning() != false)
            return@withLock InstallStart.Refused(InstallRefusal.SetupBusy).ok()
        val needed = requiredBytes(agentId, architecture)
        val free = jobs.freeBytes()
        if (needed != null && free != null && free < needed) {
            return@withLock InstallStart.Refused(InstallRefusal.NotEnoughSpace).ok()
        }
        when (val marked = prefs.commit { putString(AgentKeys.agentInstall(owner), agentId.raw) }) {
            is Outcome.Failed -> return@withLock marked
            is Outcome.Ok -> Unit
        }
        when (val started = jobs.start(owner, agentId, components(agentId))) {
            is Outcome.Failed -> {
                prefs.commit { remove(AgentKeys.agentInstall(owner)) }
                state.value =
                    AgentSetupProgress(
                        agentId,
                        AgentSetupPhase.Failed,
                        null,
                        null,
                        null,
                        null,
                        AgentSetupFailure.Unknown,
                    )
                return@withLock started
            }
            is Outcome.Ok -> Unit
        }
        state.value =
            AgentSetupProgress(agentId, AgentSetupPhase.Installing, null, null, null, null, null)
        InstallStart.Started.ok()
    }

    /**
     * Startup task: re-attach to the native job the marker names, or clear the marker when the job
     * record shows done, failed or no job, so a marker never points at nothing for more than one
     * start.
     */
    public suspend fun restore(owner: OwnerId): Outcome<AgentSetupProgress> = lane.withLock {
        val marker =
            prefs.string(AgentKeys.agentInstall(owner))?.let(::AgentId)
                ?: return@withLock AgentSetupProgress.Idle.also { state.value = it }.ok()
        val job =
            when (val read = jobs.job(owner)) {
                is Outcome.Failed -> return@withLock read
                is Outcome.Ok -> read.value
            }
        if (job == null || job.agent != marker || job.state != AgentSetupJobState.Running) {
            when (val cleared = prefs.commit { remove(AgentKeys.agentInstall(owner)) }) {
                is Outcome.Failed -> return@withLock cleared
                is Outcome.Ok -> Unit
            }
        }
        project(marker, job).also { state.value = it }.ok()
    }

    /**
     * Applies a fresh job record (polled or pushed by the runtime). Clears the marker when the job
     * ended.
     */
    public suspend fun observe(owner: OwnerId, job: AgentSetupJob?): Outcome<AgentSetupProgress> =
        lane.withLock {
            val marker =
                prefs.string(AgentKeys.agentInstall(owner))?.let(::AgentId)
                    ?: return@withLock state.value.ok()
            if (job == null || job.state != AgentSetupJobState.Running) {
                when (val cleared = prefs.commit { remove(AgentKeys.agentInstall(owner)) }) {
                    is Outcome.Failed -> return@withLock cleared
                    is Outcome.Ok -> Unit
                }
            }
            project(marker, job).also { state.value = it }.ok()
        }

    /**
     * Cancel stops only the exact durable owner; with no marker there is nothing of ours to stop.
     */
    public suspend fun cancel(owner: OwnerId): Outcome<Unit> = lane.withLock {
        val marker =
            prefs.string(AgentKeys.agentInstall(owner))?.let(::AgentId) ?: return@withLock Unit.ok()
        when (val r = jobs.cancel(owner, marker)) {
            is Outcome.Failed -> return@withLock r
            is Outcome.Ok -> Unit
        }
        prefs
            .commit { remove(AgentKeys.agentInstall(owner)) }
            .also {
                state.value =
                    AgentSetupProgress(
                        marker,
                        AgentSetupPhase.Interrupted,
                        null,
                        null,
                        null,
                        null,
                        AgentSetupFailure.Interrupted,
                    )
            }
    }

    /**
     * `phoneAgentComponents`: linux, essentials, python, agent-user, agent-node, agent-paseo,
     * agent-<id>.
     */
    public fun components(agentId: AgentId): ImmutableList<AgentSetupComponent> =
        listOf(
                AgentSetupComponent(
                    LINUX,
                    emptyList<String>().toImmutableList(),
                    agentUser = false,
                ),
                AgentSetupComponent(ESSENTIALS, listOf(LINUX).toImmutableList(), agentUser = false),
                AgentSetupComponent(
                    PYTHON,
                    listOf(ESSENTIALS).toImmutableList(),
                    agentUser = false,
                ),
                AgentSetupComponent(
                    AGENT_USER,
                    listOf(ESSENTIALS).toImmutableList(),
                    agentUser = false,
                ),
                AgentSetupComponent(
                    AGENT_NODE,
                    listOf(AGENT_USER).toImmutableList(),
                    agentUser = true,
                ),
                AgentSetupComponent(
                    AGENT_PASEO,
                    listOf(AGENT_NODE).toImmutableList(),
                    agentUser = true,
                ),
                AgentSetupComponent(
                    "agent-${agentId.raw}",
                    listOf(AGENT_PASEO, PYTHON).toImmutableList(),
                    agentUser = true,
                ),
            )
            .toImmutableList()

    /**
     * The storage guard: the agent's download PLUS its extracted size (commit d6936e324); null when
     * unknown.
     */
    public fun requiredBytes(agentId: AgentId, architecture: AgentArchitecture): Long? {
        val agent = catalog.byId(agentId) ?: return null
        val artifact = agent.artifactFor(architecture) ?: return null
        val download = guard.downloadBytesFor(agent) ?: return null
        return requiredSetupFreeBytes(download, artifact.installedBytes)
    }

    private fun project(agent: AgentId, job: AgentSetupJob?): AgentSetupProgress {
        val phase =
            when (job?.state) {
                null -> AgentSetupPhase.Interrupted
                AgentSetupJobState.Running -> AgentSetupPhase.Installing
                AgentSetupJobState.Done -> AgentSetupPhase.Done
                AgentSetupJobState.Failed -> AgentSetupPhase.Failed
                AgentSetupJobState.Interrupted -> AgentSetupPhase.Interrupted
            }
        val total = job?.totalBytes?.takeIf { it > 0 }
        val done = job?.downloadedBytes?.takeIf { it >= 0 }
        val fraction =
            if (total != null && done != null) (done.toDouble() / total).coerceIn(0.0, 1.0)
            else null
        return AgentSetupProgress(
            agent,
            phase,
            fraction,
            job?.componentId,
            done,
            total,
            job?.failure,
        )
    }

    public companion object {
        public const val LINUX: String = "linux"
        public const val ESSENTIALS: String = "essentials"
        public const val PYTHON: String = "python"
        public const val AGENT_USER: String = "agent-user"
        public const val AGENT_NODE: String = "agent-node"
        public const val AGENT_PASEO: String = "agent-paseo"
    }
}

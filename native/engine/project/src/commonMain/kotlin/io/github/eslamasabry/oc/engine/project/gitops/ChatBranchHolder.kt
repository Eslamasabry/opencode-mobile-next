package io.github.eslamasabry.oc.engine.project.gitops

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What one read of a project's branch found. */
public sealed interface BranchRead {
    /** [name] is null when detached or unborn. */
    public data class Read(val name: String?) : BranchRead

    /** The read failed; the chip keeps what it had. */
    public data object Failed : BranchRead
}

/**
 * The branch last read per project (`profile|directory`) for this run of the app. One instance lives in the app graph
 * and is shared by every chat, so a chat opened again names its branch from the first frame (jitter J8). Never stored.
 */
public class BranchNameCache {
    private val names = HashMap<String, String?>()

    public fun get(project: String): String? = names[project]

    public fun put(project: String, name: String?) {
        names[project] = name
    }

    public fun clear(): Unit = names.clear()
}

/**
 * The header chip's branch name for one open chat (port of ChatGitHolder's naming rules, B-NAV-023): the project's
 * branch is read once per binding; binding the same project again does nothing; a new project reads again and says
 * nothing until its read lands, unless this run already read that project.
 */
public class ChatBranchHolder(
    private val scope: CoroutineScope,
    private val cache: BranchNameCache,
    /** Reads the branch of [directory] for [profileId]. */
    private val read: suspend (profileId: String?, directory: String) -> BranchRead,
) {
    private val _branchName = MutableStateFlow<String?>(null)
    private var key: String? = null
    private var project: String? = null
    private var job: Job? = null
    private var landed = false

    /** The branch to name on the chip: null when detached or unknown. */
    public val branchName: StateFlow<String?> = _branchName.asStateFlow()

    /** Points the holder at this project. Does nothing when nothing changed. [connection] tells gateways apart. */
    public fun bind(profileId: String?, directory: String?, connection: Any? = null) {
        val trimmed = directory?.trim()
        val newKey = "$profileId|$trimmed|${connection?.hashCode() ?: 0}"
        if (newKey == key) return
        key = newKey
        val newProject = "$profileId|$trimmed"
        project = newProject
        job?.cancel()
        landed = false
        _branchName.value = cache.get(newProject)
        if (trimmed.isNullOrEmpty()) return
        job =
            scope.launch {
                when (val result = read(profileId, trimmed)) {
                    is BranchRead.Read -> {
                        if (project != newProject) return@launch
                        landed = true
                        cache.put(newProject, result.name)
                        _branchName.value = result.name
                    }
                    BranchRead.Failed -> Unit
                }
            }
    }

    /** True once this binding's own read landed (the chip then names what it read, not the cached name). */
    public val hasRead: Boolean get() = landed

    public fun close() {
        job?.cancel()
        job = null
    }
}

package io.github.eslamasabry.oc.feature.review

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.model.VcsDiffMode
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.core.model.gitProjectState
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.feature.review.presentation.ReviewProjectGitInit
import io.github.eslamasabry.oc.feature.review.presentation.ReviewProjectSetup
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.domain.LocatedFileGateway
import io.github.eslamasabry.oc.core.domain.LocatedVcsGateway
import io.github.eslamasabry.oc.core.domain.WorktreeInspectionGateway
import io.github.eslamasabry.oc.core.model.ServerLocation
import io.github.eslamasabry.oc.feature.review.presentation.ReviewProjectRead
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.core.domain.ServerOperationsGateway
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.delivery.ConnectionDelivery
import io.github.eslamasabry.oc.engine.review.ConversationReview
import io.github.eslamasabry.oc.engine.review.ReviewGateways
import io.github.eslamasabry.oc.engine.review.ReviewLifetime
import io.github.eslamasabry.oc.feature.review.presentation.ReviewBranchRead
import io.github.eslamasabry.oc.feature.review.presentation.ReviewCommentBook
import io.github.eslamasabry.oc.feature.review.presentation.ReviewGuide
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The review reads its conversation's gateways from that conversation's own graph (ADR §9.1). */
@ContributesTo(ConnectionScope::class)
public interface ReviewGraphAccessors {
    public val reviewGateway: ServerGateway
    public val reviewOperations: ServerOperationsGateway

    /** The conversation's send pipeline: line comments and the guide question reach its agent (B-155/156). */
    public val reviewDelivery: ConnectionDelivery
}

/** A conversation's review comments and guides, kept as long as its review is (in memory only). */
public class ReviewNotes {
    public val comments: ReviewCommentBook = ReviewCommentBook()
    public val guides: MutableMap<String, ReviewGuide> = HashMap()
}

/**
 * One [ConversationReview] per conversation, shared by its review pages, so a page opened from another sees the same
 * staged state. Each follows the registry: a reconnect swaps its gateways, a closed profile leaves none (and the
 * engine dispatches to neither). The few most recent stay open; older ones stop. Main thread only.
 */
@SingleIn(AppScope::class)
@Inject
public class ReviewHub(
    private val registry: ConnectionRegistry,
    private val gitBranch: io.github.eslamasabry.oc.feature.review.presentation.ReviewGitBranch =
        io.github.eslamasabry.oc.feature.review.presentation.NoReviewGitBranch(),
) {
    private val open = LinkedHashMap<ConversationRef, Pair<ConversationReview, ReviewLifetime>>()
    private val notes = HashMap<ConversationRef, ReviewNotes>()

    /** The conversation's comments and guides; they go when its review is closed. */
    public fun notes(ref: ConversationRef): ReviewNotes {
        review(ref) // keeps the review (and so these notes) among the open ones
        return notes.getOrPut(ref) { ReviewNotes() }
    }

    public fun review(ref: ConversationRef): ConversationReview {
        open.remove(ref)?.let { kept ->
            open[ref] = kept // most recent last
            return kept.first
        }
        val lifetime = ReviewLifetime(registry.appScope, registry.mainContext)
        val scope = lifetime.scope
        val gateways =
            registry.live
                .map { graphs -> (graphs[ref.profileId] as? ReviewGraphAccessors)?.let { ReviewGateways(it.reviewGateway, it.reviewOperations) } }
                .stateIn(scope, SharingStarted.Eagerly, gatewaysOf(ref))
        val review = ConversationReview(ref.sessionId, gateways, scope)
        open[ref] = review to lifetime
        while (open.size > KEEP) {
            val oldest = open.keys.first()
            open.remove(oldest)?.second?.close()
            notes.remove(oldest)
        }
        return review
    }

    /**
     * The whole branch's changes for this conversation, read through its current connection at read time (a
     * reconnect swaps the graph). A server whose gateway has no branch diff answers Unavailable, so the scope hides.
     */
    public fun branch(ref: ConversationRef): ReviewBranchRead =
        ReviewBranchRead {
            val ops = (registry.graphFor(ref) as? ReviewGraphAccessors)?.reviewOperations
            val served = ops?.listVcsDiffs(VcsDiffMode.Branch)
            if (served is Outcome.Ok && served.value.isNotEmpty()) return@ReviewBranchRead served
            // A server with no branch diff, or one that answers with nothing (the phone agents' helper), is asked
            // through the project's own Git (the same shell and ownership as the Git page).
            val git = gitBranch.patch(ref)
            if (git is Outcome.Ok) {
                val files = io.github.eslamasabry.oc.feature.review.presentation.BranchPatches.split(git.value)
                if (files.isNotEmpty()) return@ReviewBranchRead Outcome.Ok(files)
            }
            served ?: Failure.Unavailable(FeatureId("vcsBranchDiff")).failed()
        }

    /**
     * The uncommitted changes of [directory] on [profile], read through its current connection at read time. A server
     * that cannot diff a chosen folder nor the session's own answers Unavailable; a closed profile is refused.
     */
    public fun projectTree(profile: ProfileId, directory: String): ReviewProjectRead =
        ReviewProjectRead {
            val held = registry.live.value[profile]
            val graph = held as? ReviewGraphAccessors
                ?: return@ReviewProjectRead Failure.Refused(RefusalReason.ProfileClosing).failed()
            val gateway = graph.reviewGateway
            val located = gateway as? LocatedVcsGateway
            // Without a located read the server diffs only the folder it is on now: any other folder's answer would be
            // a different project's changes, so that is Unavailable (as Files' own project changes are).
            fun onFolder() = gateway.location.value.directory?.trimEnd('/') == directory.trimEnd('/')
            when {
                located != null -> located.workingTreeAt(ServerLocation(directory, null))
                gateway.capabilities.value.sessionDiff && onFolder() -> {
                    val read = graph.reviewOperations.listVcsDiffs(VcsDiffMode.WorkingTree)
                    if (onFolder() && registry.live.value[profile] === held) read
                    else Failure.Unavailable(FeatureId("sessionDiff")).failed()
                }
                else -> Failure.Unavailable(FeatureId("sessionDiff")).failed()
            }
        }

    /**
     * Whether [directory] on [profile] is under Git, so an empty answer can say why it is empty. Unknown when the
     * server cannot tell (the page then says the tree is clean, never "no Git").
     */
    public fun projectSetup(profile: ProfileId, directory: String): ReviewProjectSetup =
        ReviewProjectSetup {
            val graph = registry.live.value[profile] as? ReviewGraphAccessors
            if (graph == null) VersionControlSetupState.Unknown else gitSetup(graph, directory)
        }

    /** The same answer for the folder this conversation lives in (the session's own folder, else the connection's). */
    public fun conversationSetup(ref: ConversationRef): ReviewProjectSetup =
        ReviewProjectSetup {
            val graph = registry.graphFor(ref) as? ReviewGraphAccessors
            val gateway = graph?.reviewGateway
            val directory =
                (gateway?.session(ref.sessionId) as? Outcome.Ok)?.value?.directory?.takeIf { it.isNotBlank() }
                    ?: gateway?.location?.value?.directory?.takeIf { it.isNotBlank() }
            if (graph == null || directory == null) VersionControlSetupState.Unknown else gitSetup(graph, directory)
        }

    /**
     * One-tap Git initialization for [ref], gated to the server's active folder (`onFolder`) because
     * `initializeGitRepository` acts on the server's current folder.
     */
    public fun conversationGitInit(ref: ConversationRef): ReviewProjectGitInit =
        object : ReviewProjectGitInit {
            override suspend fun canInit(): Boolean = activeFolderGraph(ref) != null

            override suspend fun initialize(): Outcome<Unit> {
                val graph =
                    activeFolderGraph(ref) ?: return Failure.Unavailable(FeatureId("gitInit")).failed()
                return graph.reviewOperations.initializeGitRepository()
            }
        }

    private suspend fun activeFolderGraph(ref: ConversationRef): ReviewGraphAccessors? {
        val graph =
            (registry.graphFor(ref) as? ReviewGraphAccessors)
                ?.takeIf { it.reviewGateway.capabilities.value.gitInit }
                ?: return null
        val gateway = graph.reviewGateway
        val sessionDir =
            (gateway.session(ref.sessionId) as? Outcome.Ok)?.value?.directory?.takeIf { it.isNotBlank() }
        val activeDir = gateway.location.value.directory?.takeIf { it.isNotBlank() }
        val onFolder =
            when {
                sessionDir == null -> activeDir != null
                activeDir == null -> false
                else -> sessionDir.trimEnd('/') == activeDir.trimEnd('/')
            }
        return graph.takeIf { onFolder }
    }

    /**
     * Git, Absent or Unknown, by the one rule the chat header and the new-chat start use ([gitProjectState]): the
     * server's version-control answer for this folder (located when the server can, else only for the folder the
     * connection is on, which is the header's own read), then the project record, then a listing of the project's ROOT
     * folder (a subfolder of a repository has no `.git` of its own and is still in Git).
     */
    private suspend fun gitSetup(graph: ReviewGraphAccessors, directory: String): VersionControlSetupState {
        val gateway = graph.reviewGateway
        val location = ServerLocation(directory, null)
        val capabilities = gateway.capabilities.value
        fun same(a: String?, b: String?) = a != null && b != null && a.trimEnd('/') == b.trimEnd('/')
        val onFolder = same(gateway.location.value.directory, directory)
        val health =
            ((gateway as? LocatedVcsGateway)?.versionControlHealthAt(location) as? Outcome.Ok)?.value
                ?: if (onFolder) (graph.reviewOperations.versionControlHealth() as? Outcome.Ok)?.value else null
        val record =
            if (capabilities.projectManagement)
                ((gateway as? WorktreeInspectionGateway)?.inspectionProject(location) as? Outcome.Ok)?.value
            else null
        val rootHasDotGit =
            if (gitProjectState(health, record?.isGit) == null && record != null && capabilities.fileBrowsing) {
                val root = record.directory.takeIf { it.isNotBlank() } ?: directory
                val files =
                    (gateway as? LocatedFileGateway)?.filesAt(ServerLocation(root, null))
                        ?: gateway.takeIf { same(gateway.location.value.directory, root) }
                ((files?.listFiles("") as? Outcome.Ok)?.value)?.any { it.name == ".git" }
            } else null
        return when (gitProjectState(health, record?.isGit, rootHasDotGit)) {
            true -> VersionControlSetupState.Git
            false -> VersionControlSetupState.Absent
            null -> VersionControlSetupState.Unknown
        }
    }

    private fun gatewaysOf(ref: ConversationRef): ReviewGateways? =
        (registry.graphFor(ref) as? ReviewGraphAccessors)?.let { ReviewGateways(it.reviewGateway, it.reviewOperations) }

    private companion object {
        const val KEEP = 6
    }
}

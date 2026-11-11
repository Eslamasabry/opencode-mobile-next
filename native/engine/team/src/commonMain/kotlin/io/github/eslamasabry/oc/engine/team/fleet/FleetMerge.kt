package io.github.eslamasabry.oc.engine.team.fleet

// ---- the host front's merge documents (Dart `models/merge.dart`)
// Lenient like Dart: a missing field is its zero value, nothing here throws on odd JSON.

/** Dart `MergeReadinessLine`: one check the host ran (`work`, `tests`, `build`, `review`, `conflicts`, `acceptance` or a configured key). */
public data class MergeLine(val key: String, val ok: Boolean, val detail: String? = null, val pending: Boolean = false)

/** Dart `MergeBoundary`: a host rule that applies to merging; [satisfied] false blocks a merge. */
public data class MergeBoundary(val key: String, val satisfied: Boolean, val text: String)

/** Dart `MergeChange`: one changed file with its line counts. */
public data class MergeChange(val path: String, val additions: Int = 0, val deletions: Int = 0)

/** Dart `MergeRequestInfo`: what Approve acts on (a merge-request bead, else the run's own bead). */
public data class MergeRequestInfo(val id: String, val title: String? = null, val approvedBy: String? = null, val approvedAtMs: Long? = null) {
    public val isApproved: Boolean
        get() = !approvedBy.isNullOrEmpty()
}

/** Dart `MergeReadiness`: the front's readiness document for one run. */
public data class MergeReadiness(
    val runId: String,
    /** Every line is ok: the host would accept a merge, boundaries aside. */
    val ready: Boolean,
    val rig: String? = null,
    /** The branch a merge lands on. */
    val targetBranch: String? = null,
    val lines: List<MergeLine> = emptyList(),
    val files: Int = 0,
    val additions: Int = 0,
    val deletions: Int = 0,
    val changes: List<MergeChange> = emptyList(),
    val boundaries: List<MergeBoundary> = emptyList(),
    val mergeRequest: MergeRequestInfo? = null,
    /** The commit already on the target when nothing is left to merge. */
    val mergeCommit: String? = null,
    /** Branches the merge would bring in. */
    val branches: List<String> = emptyList(),
) {
    /** Nothing is left to merge: every branch is already on the target. */
    public val alreadyMerged: Boolean
        get() = ready && branches.isEmpty() && mergeCommit != null

    /** The first line that is not ok, or null. */
    public val firstMissing: MergeLine?
        get() = lines.firstOrNull { !it.ok }

    /** The first boundary the host would refuse on, or null. */
    public val firstBlocking: MergeBoundary?
        get() = boundaries.firstOrNull { !it.satisfied }

    /** Ready and every boundary satisfied: Merge is enabled. */
    public val canMerge: Boolean
        get() = ready && firstBlocking == null
}

/** What a merge or an approval came back with: the receipt, and the parts of the host's answer the page words itself. */
public data class FleetMergeResult(
    val receipt: FleetReceipt,
    /** The merge commit after a merge. */
    val mergeCommit: String? = null,
    val branch: String? = null,
    /** The boundary text when the host refused on a boundary (a `boundary` problem): already words. */
    val boundary: String? = null,
)

internal object FleetMergeWire {
    /** Dart `MergeReadiness.fromJson`: null when the document has neither `ready` nor `lines`, or no run id. */
    fun readiness(json: Json, runId: String): MergeReadiness? {
        if (!json.containsKey("ready") && !json.containsKey("lines")) return null
        return MergeReadiness(
            runId = J.text(json, "runId") ?: runId,
            ready = J.bool(json, "ready") == true,
            rig = J.text(json, "rig"),
            targetBranch = J.text(json, "targetBranch"),
            lines =
                J.list(json, "lines") { l -> J.text(l, "key")?.let { MergeLine(it, J.bool(l, "ok") == true, J.text(l, "detail"), J.bool(l, "pending") == true) } }
                    .filterNotNull(),
            files = J.int(json, "files") ?: 0,
            additions = J.int(json, "additions") ?: 0,
            deletions = J.int(json, "deletions") ?: 0,
            changes =
                J.list(json, "changes") { c -> J.text(c, "path")?.let { MergeChange(it, J.int(c, "additions") ?: 0, J.int(c, "deletions") ?: 0) } }.filterNotNull(),
            boundaries =
                J.list(json, "boundaries") { b -> J.text(b, "key")?.let { MergeBoundary(it, J.bool(b, "satisfied") == true, J.text(b, "text") ?: it) } }
                    .filterNotNull(),
            mergeRequest =
                J.field(json, "mergeRequest").let { m ->
                    J.text(m, "id")?.let { MergeRequestInfo(it, J.text(m, "title"), J.text(m, "approvedBy"), J.timeMs(m, "approvedAt")) }
                },
            mergeCommit = J.text(json, "mergeCommit"),
            branches = (json["branches"] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.takeIf(String::isNotEmpty) } ?: emptyList(),
        )
    }
}

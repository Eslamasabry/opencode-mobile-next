package io.github.eslamasabry.oc.engine.project.coverage

/** What the native project area decided for one call that changes something. */
public enum class ActionDecision {
    /** A command of this engine performs it; [ProjectAction.proof] names the test that proves it. */
    Reachable,

    /** Another module performs it; [ProjectAction.note] says where. */
    CoveredElsewhere,

    /** Deliberately not offered; [ProjectAction.note] says why. */
    NotOffered,
}

/** One mutating action of the project area (port of the Flutter action-coverage ledgers, B-BUILD-004/005). */
public data class ProjectAction(
    /** `<package>.<action>`. */
    val id: String,
    val decision: ActionDecision,
    /** The control and the port call, or where/why. */
    val note: String,
    /** The exact name of the test function that proves a [ActionDecision.Reachable] action. */
    val proof: String? = null,
)

/** Every call of this engine that changes something, with its decision. A new mutating command adds a row here. */
public object ProjectActionLedger {
    public val actions: List<ProjectAction> =
        listOf(
            ProjectAction(
                "gitops.addRemote", ActionDecision.Reachable,
                "Git page › Add remote -> GitRemoteOps.addRemote -> GitOpsShell `git remote add <name> <url>`",
                "B-BUILD-005 add remote sends exactly git remote add in the project folder",
            ),
            ProjectAction(
                "gitops.clone", ActionDecision.Reachable,
                "Git page › Clone -> GitRemoteOps.clone -> GitOpsShell `git clone -- <url> <folder>` in the parent folder",
                "B-BUILD-005 clone sends exactly git clone in the parent folder",
            ),
            ProjectAction(
                "gitops.push", ActionDecision.Reachable,
                "Git page › Push -> GitPushPage.push -> GitRemoteOps.push -> GitOpsShell `git -c credential.helper=… push`",
                "B-BUILD-005 push with a token sends exactly the credential options and the push",
            ),
            ProjectAction(
                "gitops.secretDrop", ActionDecision.Reachable,
                "Push on built-in Ubuntu -> PrivateSecretDrop.place/remove -> SecretDropFiles chmod 700, write, chmod 600, write, wipe",
                "B-BUILD-005 the secret drop makes the folder and file private before the token is written",
            ),
            ProjectAction(
                "gitops.signOutRejectedToken", ActionDecision.Reachable,
                "A push GitHub refused -> GitPushPage -> GitHubTokenSource.tokenRejected (the account signs out)",
                "B-SEC-082 a rejected token signs the account out",
            ),
            ProjectAction(
                "repair.restoreLinks", ActionDecision.Reachable,
                "Repair sheet › Repair -> GitLinkRepair.repair -> RepairFileSystem copy, delete link, rename into place",
                "B-BUILD-005 repair replaces each link through a temporary file beside it",
            ),
            ProjectAction(
                "repair.fetchFromTermux", ActionDecision.Reachable,
                "Repair sheet › Repair -> TermuxGitFiles.fetch(store paths only) into one staging job folder, removed after",
                "B-BUILD-005 repair asks Termux only for store paths and removes its staging folder",
            ),
            ProjectAction(
                "tasks.createWorktree", ActionDecision.Reachable,
                "New task › In a fresh copy -> IsolatedTaskLaunch.start -> create() once",
                "B-BUILD-005 an isolated task creates once and opens exactly the created directory",
            ),
            ProjectAction(
                "tasks.openSession", ActionDecision.Reachable,
                "Launch sheet › Open / Open anyway / Start anyway -> IsolatedTaskLaunch.open -> open(<created directory>)",
                "B-BUILD-005 an isolated task creates once and opens exactly the created directory",
            ),
            ProjectAction(
                "tasks.deleteWorktree", ActionDecision.NotOffered,
                "Cancelling or timing out a launch never deletes the server's copy; removal is the Worktrees screen's own action.",
            ),
            ProjectAction(
                "worktree.mergeBack", ActionDecision.NotOffered,
                "Neither OpenCode contract offers a merge-copy-back operation (WorktreeCapabilities.worktreeMerge is false).",
            ),
            ProjectAction(
                "reports.shareFailedJob", ActionDecision.CoveredElsewhere,
                "A FailedJobReport is only a redacted snapshot; Copy and Share belong to the Report a problem feature.",
            ),
            ProjectAction(
                "devserver.stopProgram", ActionDecision.CoveredElsewhere,
                "The dev server line only reads; stopping a listening program is the Linux › Ports screen's action.",
            ),
        )
}

package io.github.eslamasabry.oc.feature.gate

import io.github.eslamasabry.oc.engine.migration.MigrationBlock
import io.github.eslamasabry.oc.engine.migration.MigrationEngine
import io.github.eslamasabry.oc.engine.migration.MigrationProgress
import io.github.eslamasabry.oc.engine.migration.MigrationView
import io.github.eslamasabry.oc.engine.migration.ReentryNeed

/**
 * The gate's platform ways out (P12-MIGRATION): sharing a report, the system storage page and the place where a
 * server's password is typed again. Bound by `:androidApp` (graph/MigrationWiring.kt); nothing here is Android.
 */
public interface GatePlatform {
    /** Shares a category-only report ([detail] is the step id and reason, never a value). Writes nothing. */
    public fun reportProblem(detail: String?)

    public fun openStorageSettings()

    /** Opens where [item] is typed again (the saved server's sign-in, the Git page, …). */
    public fun openReentry(item: GateReentry)

    /** Stores [secret] as [item]'s saved server's password or token (same server, same id). False when refused. */
    public suspend fun saveSecret(item: GateReentry, secret: String): Boolean = false

    public companion object {
        public val None: GatePlatform = object : GatePlatform {
            override fun reportProblem(detail: String?) = Unit

            override fun openStorageSettings() = Unit

            override fun openReentry(item: GateReentry) = Unit
        }
    }
}

/** The one mapping from what the engine knows to what the gate draws. */
public fun gateInputOf(view: MigrationView): GateInput =
    when (val p = view.progress) {
        // The app's words are known (the system list, or the in-app language once :engine:settings applies it).
        MigrationProgress.Waiting -> GateInput.Closed(localeKnown = true)
        is MigrationProgress.Running -> GateInput.Running(p.step)
        is MigrationProgress.Open ->
            GateInput.Open(
                firstNativeLaunch = p.firstNativeLaunch,
                reenter =
                    p.reenter.map { item ->
                        GateReentry(
                            profileId = item.profileId,
                            targetName = item.profileId?.let { view.names[it] } ?: GITHUB.takeIf { item.profileId == null },
                            kind = kindOf(item.need),
                        )
                    },
                deferredDataFound = p.deferred.isNotEmpty(),
            )
        is MigrationProgress.Blocked ->
            GateInput.Blocked(
                reason = reasonOf(p.reason),
                neededBytes = p.neededBytes,
                restoreAvailable = p.restoreAvailable,
                detail = listOfNotNull(p.step, p.reason.name).joinToString(" · "),
                startFreshAvailable = p.startFreshAvailable,
            )
    }

/** [GateActions] over the engine and the platform. */
public class EngineGateActions(private val engine: MigrationEngine, private val platform: GatePlatform) : GateActions {
    override suspend fun retry(): Unit = engine.retry()

    override suspend fun restorePreUpdateData(): Unit = engine.restorePreUpdateData()

    override suspend fun startFresh(): Unit = engine.startFresh()

    override suspend fun reportProblem() {
        platform.reportProblem((gateInputOf(engine.view.value) as? GateInput.Blocked)?.detail)
    }

    override suspend fun openStorageSettings(): Unit = platform.openStorageSettings()

    override suspend fun acknowledgeNotices(): Unit = engine.acknowledgeNotices()

    override suspend fun openReentry(item: GateReentry): Unit = platform.openReentry(item)

    override suspend fun acknowledgeDeferredNotice(): Unit = engine.acknowledgeDeferredNotice()

    override suspend fun saveReentry(item: GateReentry, secret: String): Boolean = platform.saveSecret(item, secret)
}

/** GitHub is a product name: the same in every language. */
private const val GITHUB = "GitHub"

private fun kindOf(need: ReentryNeed): GateReentryKind =
    when (need) {
        ReentryNeed.Password -> GateReentryKind.Password
        ReentryNeed.Token -> GateReentryKind.Token
        ReentryNeed.GitHubSignIn -> GateReentryKind.GitHubSignIn
        ReentryNeed.ByoSetup -> GateReentryKind.ByoSetup
    }

private fun reasonOf(block: MigrationBlock): GateBlockReason =
    when (block) {
        MigrationBlock.LowSpace -> GateBlockReason.LowSpace
        MigrationBlock.PrefsUnreadable -> GateBlockReason.PrefsUnreadable
        MigrationBlock.WriteRefused -> GateBlockReason.WriteRefused
        MigrationBlock.AuthorityChanged -> GateBlockReason.AuthorityChanged
        MigrationBlock.StepCrashed -> GateBlockReason.StepCrashed
        MigrationBlock.BackupInvalid -> GateBlockReason.BackupInvalid
    }

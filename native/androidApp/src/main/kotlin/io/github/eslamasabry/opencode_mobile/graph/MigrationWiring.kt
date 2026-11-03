package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.migration.BlockReason
import io.github.eslamasabry.oc.android.migration.DeferredFeature as RunnerDeferred
import io.github.eslamasabry.oc.android.migration.MigrationDoneEvent
import io.github.eslamasabry.oc.android.migration.MigrationRunner
import io.github.eslamasabry.oc.android.migration.MigrationSecretSink
import io.github.eslamasabry.oc.android.migration.MigrationSignals
import io.github.eslamasabry.oc.android.migration.ReentryKind
import io.github.eslamasabry.oc.android.migration.RunnerState
import io.github.eslamasabry.oc.android.migration.android.AndroidMigration
import io.github.eslamasabry.oc.core.common.AppBuildInfo
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.android.AndroidDataStores
import io.github.eslamasabry.oc.core.data.migration.MigrationGate
import io.github.eslamasabry.oc.core.data.migration.MigrationOnly
import io.github.eslamasabry.oc.core.data.secret.SecretStore
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.migration.DeferredFeature
import io.github.eslamasabry.oc.engine.migration.MigrationBlock
import io.github.eslamasabry.oc.engine.migration.MigrationEngine
import io.github.eslamasabry.oc.engine.migration.MigrationProgress
import io.github.eslamasabry.oc.engine.migration.MigrationRunnerPort
import io.github.eslamasabry.oc.engine.migration.ReentryItem
import io.github.eslamasabry.oc.engine.migration.ReentryNames
import io.github.eslamasabry.oc.engine.migration.ReentryNeed
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.feature.gate.GatePlatform
import io.github.eslamasabry.oc.feature.gate.GateReentry
import io.github.eslamasabry.oc.feature.gate.GateReentryKind
import io.github.eslamasabry.oc.feature.gate.ui.MigrationGateHost
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.LocalKitPlatform
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import io.github.eslamasabry.oc.ui.routes.ServersRoute
import io.github.eslamasabry.opencode_mobile.MainActivity
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P12-MIGRATION: the in-place update over the Flutter app, Android side. The ONE file of this unit in `:androidApp`.
 *
 * - On the real package (`io.github.eslamasabry.opencode_mobile`, the `switch` flavour) the day-1 runner from
 *   `:android:migration` adopts the Flutter app's data in place: S0 byte backup → M1 census → M2 secrets
 *   (flutter_secure_storage → the native Keystore store, same `pw.<id>` names) → M3 legacy transforms → M4/M5 audit
 *   → M6. The prefs file (`FlutterSharedPreferences.xml`) is the native app's own store, so servers, drafts, queue,
 *   schedules, settings and widget choices are read where they are; files (Linux rootfs, voice models, app_flutter)
 *   stay in place and are only inventoried.
 * - On the side-by-side preview package (`.native`) there is no Flutter data: the gate is open from the start.
 *
 * Every store gets [MigrationGate] from here, so nothing reads or writes the prefs file or the secret store before
 * the runner has finished (plan 05 §7.3).
 */
@SingleIn(AppScope::class)
@Inject
class MigrationWiring(
    private val application: Application,
    buildInfo: AppBuildInfo,
    redactor: Redactor,
    appScope: AppCoroutineScope,
    dispatchers: AppDispatchers,
    clock: Clock,
) {
    /** True when this build replaces the Flutter app (same package): only then is there anything to adopt. */
    val adoptsFlutterData: Boolean = application.packageName == FLUTTER_PACKAGE

    private val scope = CoroutineScope(appScope.coroutineContext + dispatchers.io)

    init {
        // Start fresh, phase 2 (plan 05 §9.4): before the runner or any store opens a prefs file. A few renames only.
        if (adoptsFlutterData && AndroidMigration.recoverBeforeOpen(application, clock)) {
            AppLog.tagged("migration").i { "start fresh finished; old data kept" }
        }
    }

    @OptIn(MigrationOnly::class)
    private val migrationRunner: MigrationRunner? =
        if (adoptsFlutterData) {
            AndroidMigration.create(
                context = application,
                secrets = SecretSink(AndroidDataStores.secretsForMigration(application, redactor, dispatchers.io)),
                redactor = redactor,
                signals = LoggedSignals,
                nativeVersionCode = buildInfo.versionCode.toLong(),
                clock = clock,
                io = dispatchers.io,
            ).also {
                // StartupFence (plan 05 §7.1, Codex review p12/migration #3): the S0 byte backup is taken here, before
                // this wiring hands out the gate, so no store can open (and Android's loader rename) the Flutter
                // prefs file while it is copied. A few small files, once; it never throws.
                it.backupBeforeOpen()
            }
        } else {
            null
        }

    private val runner: MigrationRunnerPort =
        migrationRunner?.let { RunnerPort(application, it, scope) } ?: MigrationRunnerPort.Nothing

    /** Started at graph creation (ADR §9.7 step 5, off the main thread); the stores wait on its gate. */
    val engine: MigrationEngine =
        // Names are read through the graph at call time (after the gate opened): no ProfileStore ↔ gate cycle in DI.
        MigrationEngine(runner, ReentryNames { id -> nameOf(application.appGraph.previewRuntime.profiles, id) }, scope)
            .also { it.start() }

    /**
     * The app's secret store with the credential ledger in front (plan 05 §4.4 rule 1, Codex review p12/migration #1):
     * every native write or delete is recorded first, so the M2 retry and the R1 rollback build never bring back a
     * sign-in the person changed or removed. On the preview package there is nothing to guard.
     */
    fun ledgered(store: SecretStore): SecretStore = migrationRunner?.let { LedgeredSecretStore(store, it, engine.gate) } ?: store

    /** The gate's platform ways out; [navigator] opens the chats (GitHub) or the Servers page for a re-enter row. */
    fun gatePlatform(navigator: AppNavigator): GatePlatform = AndroidGatePlatform(application, navigator)

    private suspend fun nameOf(store: ProfileStore, id: String): String? {
        // The gate has just opened; the app's startup loads the profiles right after it. Wait briefly, never forever.
        withTimeoutOrNull(NAME_WAIT_MS) { store.profiles.first { list -> list.isNotEmpty() } }
        return store.profile(ProfileId(id))?.profile?.name?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val FLUTTER_PACKAGE = "io.github.eslamasabry.opencode_mobile"
        const val NAME_WAIT_MS = 5_000L
    }
}

/** The app root: the update gate over [content] (`App(...)`), inside the kit platform and theme. */
@Composable
fun MigrationRoot(wiring: MigrationWiring, navigator: AppNavigator, platform: KitPlatform, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalKitPlatform provides platform) {
        KitTheme { MigrationGateHost(wiring.engine, wiring.gatePlatform(navigator), content) }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object MigrationBindings {
    /** The one gate every store waits on (plan 05 §7.3). */
    @Provides fun gate(wiring: MigrationWiring): MigrationGate = wiring.engine.gate
}

/** How `MainActivity` reaches the wiring without a new accessor on [AndroidAppGraph]. */
@ContributesTo(AppScope::class)
interface MigrationGraphAccessors {
    val migrationWiring: MigrationWiring
}

internal val AndroidAppGraph.migration: MigrationWiring
    get() = (this as MigrationGraphAccessors).migrationWiring

/** `:android:migration`'s runner as the engine drives it; its states mapped in one place. */
private class RunnerPort(
    private val application: Application,
    private val runner: MigrationRunner,
    scope: CoroutineScope,
) : MigrationRunnerPort {
    override val progress: StateFlow<MigrationProgress> =
        runner.state.map(::progressOf).stateIn(scope, SharingStarted.Eagerly, progressOf(runner.state.value))

    override suspend fun run() {
        runner.run()
    }

    override suspend fun retry() {
        // A Start fresh whose second phase failed (Codex review p12/migration #4) finishes only in a new process,
        // before anything opens the prefs file: Try again restarts the app, as Start fresh itself does.
        if (runner.startFreshPending()) restart() else runner.retry()
    }

    override suspend fun acknowledgeNotices() = runner.acknowledgeNotices()

    /**
     * Start fresh, phase 1: journal the request, then restart the app (plan 05 §9.4: this process holds the loaded
     * prefs file, so nothing moves here). Phase 2 runs in [MigrationWiring]'s init on the next start.
     */
    override suspend fun startFresh(): Boolean {
        if (!runner.requestStartFresh()) return false
        restart()
        return true
    }

    private fun restart() {
        val restart = Intent.makeRestartActivityTask(ComponentName(application, MainActivity::class.java))
        application.startActivity(restart)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    /** R2 needs the RestoreTransaction (plan 05 §9.4, staged copy + outbound hold), not built yet: not offered. */
    override suspend fun restorePreUpdateData(): Boolean = false

    private fun progressOf(state: RunnerState): MigrationProgress =
        when (state) {
            RunnerState.Closed -> MigrationProgress.Waiting
            is RunnerState.Running -> MigrationProgress.Running(state.step)
            is RunnerState.Open ->
                MigrationProgress.Open(
                    firstNativeLaunch = state.firstNativeLaunch,
                    reenter = state.notices.reenter.map { ReentryItem(it.profileId, needOf(it.kind)) },
                    deferred = state.notices.deferredFeatures.map(::deferredOf).toSet(),
                )
            is RunnerState.Blocked ->
                MigrationProgress.Blocked(
                    reason = blockOf(state.reason),
                    step = state.step,
                    neededBytes = state.neededBytes,
                    restoreAvailable = false,
                    startFreshAvailable = state.startFreshAvailable,
                )
        }

    private fun needOf(kind: ReentryKind): ReentryNeed =
        when (kind) {
            ReentryKind.Password -> ReentryNeed.Password
            ReentryKind.Token -> ReentryNeed.Token
            ReentryKind.GitHubSignIn -> ReentryNeed.GitHubSignIn
            ReentryKind.ByoSetup -> ReentryNeed.ByoSetup
        }

    private fun deferredOf(feature: RunnerDeferred): DeferredFeature =
        when (feature) {
            RunnerDeferred.AiTeam -> DeferredFeature.AiTeam
            RunnerDeferred.Memory -> DeferredFeature.Memory
            RunnerDeferred.Gemma -> DeferredFeature.Gemma
        }

    private fun blockOf(reason: BlockReason): MigrationBlock =
        when (reason) {
            BlockReason.LowSpace -> MigrationBlock.LowSpace
            BlockReason.PrefsUnreadable -> MigrationBlock.PrefsUnreadable
            BlockReason.WriteRefused -> MigrationBlock.WriteRefused
            BlockReason.AuthorityChanged -> MigrationBlock.AuthorityChanged
            BlockReason.StepCrashed -> MigrationBlock.StepCrashed
            BlockReason.BackupInvalid -> MigrationBlock.BackupInvalid
        }
}

/** The raw (ungated) native secret store as the migrator's sink. Values never leave this adapter except into the store. */
private class SecretSink(private val store: SecretStore) : MigrationSecretSink {
    override suspend fun read(name: String): String? =
        when (val read = store.read(name)) {
            is Outcome.Ok -> read.value
            is Outcome.Failed -> throw IllegalStateException("secret store unavailable")
        }

    override suspend fun write(name: String, value: String): Boolean {
        if (store.write(name, value) !is Outcome.Ok) return false
        return (store.read(name) as? Outcome.Ok)?.value == value
    }

    override suspend fun delete(name: String): Boolean = store.delete(name) is Outcome.Ok
}

/**
 * [store] with the runner's ledger step before every write and delete. The gate is awaited first, so a record never
 * races M2's own; a ledger that cannot be saved refuses the change (one "Enter password" later, never a resurrected
 * credential). Reads and listings pass through.
 */
private class LedgeredSecretStore(
    private val store: SecretStore,
    private val runner: MigrationRunner,
    private val gate: MigrationGate,
) : SecretStore {
    override suspend fun read(name: String): Outcome<String?> = store.read(name)

    override suspend fun names(prefix: String): Outcome<Set<String>> = store.names(prefix)

    override suspend fun write(name: String, value: String): Outcome<Unit> =
        if (recorded(name, deleted = false)) store.write(name, value) else Failure.StorageRefused().failed()

    override suspend fun delete(name: String): Outcome<Unit> =
        if (recorded(name, deleted = true)) store.delete(name) else Failure.StorageRefused().failed()

    private suspend fun recorded(name: String, deleted: Boolean): Boolean {
        gate.awaitOpen()
        val ok = attempt({ Failure.StorageRefused() }) { runner.beforeNativeChange(name, deleted) }
        return (ok as? Outcome.Ok)?.value == true
    }
}

/**
 * The runner's events (plan 16 §5.3): enums and buckets only. They go to the redacted app log until the analytics
 * engine is in the app graph (the integrator binds it there; see the P12 report).
 */
private object LoggedSignals : MigrationSignals {
    private val log = AppLog.tagged("migration")

    override fun appUpdated(fromCode: Long?) = log.i { "app_updated from=${fromCode ?: "flutter"}" }

    override fun migrationDone(event: MigrationDoneEvent) =
        log.i {
            "migration_done outcome=${event.outcome.wire} failed_step=${event.failedStep.wire} " +
                "reentry=${event.secretsReentry.wire} duration=${event.duration.wire}"
        }

    override fun diagnosticsLine(text: String) = log.i { text }
}

/** The gate's Android ways out. Every intent is app-authored; nothing from the server or the data is launched. */
private class AndroidGatePlatform(private val application: Application, private val navigator: AppNavigator) :
    GatePlatform {
    override fun reportProblem(detail: String?) {
        // Category-only: the app, the step id and the failure class. No path, no value, no address (B-MIGR-011).
        val text = listOfNotNull("OpenCode Mobile update", detail).joinToString(" · ")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(Intent.createChooser(send, null))
    }

    override fun openStorageSettings() {
        start(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
    }

    override fun openReentry(item: GateReentry) {
        // GitHub is signed in again from a chat's Git page, so its row opens the chats (Codex review p12/migration #2).
        // Passwords and tokens are typed in the sheet itself (saveSecret below); BYO set-up opens nothing (not in this
        // version). Only a password row with no saved server behind it reaches here otherwise: the servers.
        when (item.kind) {
            GateReentryKind.GitHubSignIn -> navigator.open(ChatsRoute.Home)
            GateReentryKind.ByoSetup -> Unit
            GateReentryKind.Password, GateReentryKind.Token -> navigator.open(ServersRoute.Home)
        }
    }

    /**
     * Migration proof 2026-11-03: the typed password or token goes onto the SAME saved server (same id, so its
     * schedules, drafts and choices stay its own; trust kept; the re-entry flag cleared by the store). Removing and
     * adding the server again would give it a new id.
     */
    override suspend fun saveSecret(item: GateReentry, secret: String): Boolean {
        val id = ProfileId(item.profileId ?: return false)
        val store = application.appGraph.previewRuntime.profiles
        val loaded =
            store.profile(id)
                ?: withTimeoutOrNull(PROFILE_WAIT_MS) { store.profiles.first { list -> list.any { it.id == id } } }
                    ?.let { store.profile(id) }
                ?: return false
        return store.upsert(loaded.profile, SecretEdit.Set(secret)) is Outcome.Ok
    }

    private fun start(intent: Intent) {
        val launch = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch.resolveActivity(application.packageManager) != null) application.startActivity(launch)
    }

    private companion object {
        const val PROFILE_WAIT_MS = 5_000L
    }
}

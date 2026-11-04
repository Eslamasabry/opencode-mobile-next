package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.profiles.LoadedProfile
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.components.SetupPrelude
import io.github.eslamasabry.oc.engine.runtime.setup.SetupCheckRun
import io.github.eslamasabry.oc.engine.runtime.setup.SetupEngine
import io.github.eslamasabry.oc.engine.runtime.setup.SetupFinishRequest
import io.github.eslamasabry.oc.engine.runtime.setup.SetupFinisher
import io.github.eslamasabry.oc.engine.runtime.setup.SetupHostKind
import io.github.eslamasabry.oc.engine.runtime.setup.SetupJobHost
import io.github.eslamasabry.oc.engine.runtime.setup.SetupJobStart
import io.github.eslamasabry.oc.engine.runtime.setup.SetupStepSpec
import io.github.eslamasabry.oc.engine.runtime.setup.SetupWords
import io.github.eslamasabry.oc.engine.runtime.setup.registry.PhoneSetupRegistry
import io.github.eslamasabry.oc.engine.runtime.setup.registry.PhoneSetupRegistryWords
import io.github.eslamasabry.oc.feature.setup.presentation.SetupDetails
import io.github.eslamasabry.oc.feature.setup.presentation.SetupNavigator
import io.github.eslamasabry.oc.feature.setup.presentation.SetupSession
import io.github.eslamasabry.oc.feature.setup.presentation.SetupSource
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsShort
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupEssentialsWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupLinuxTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupLinuxWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNodeWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationChannel
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationDone
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationProgress
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationStopped
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupOpenCodeWhy
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProfileName
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageDownloadingLinux
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageRetrying
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageStarting
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageUnpackingLinux
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStartWhy
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupPersistenceException
import io.github.eslamasabry.opencode_mobile.SetupRunner
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.json.JSONObject

/*
 * BUILD-RUNTIME-B: "Set up OpenCode on this phone", real. :engine:runtime's SetupEngine (the port of
 * lib/builtin/setup/setup_engine.dart) over :android:linux's SetupRunner and BuiltinLinux — the twin of the Flutter
 * app's native side, which writes the same setup.json and runs the same SetupService notification — with the ported
 * first-setup registry (PhoneSetupRegistry) and the setup prelude in front of every install script.
 *
 * BuiltinLinux and SetupRunner calls block (proot, process I/O, setup.json): every one runs on AppDispatchers.io.
 * Exception text only classifies (classifyRuntime); it never reaches the page. The engine is confined to one serial
 * lane, so every call into it goes through that lane.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object SetupWiring {
    @Provides fun setupSource(setup: PhoneSetup): SetupSource = setup

    @Provides fun setupDetails(redactor: Redactor): SetupDetails = SetupDetails(redactor::details)

    @Provides
    fun setupNavigator(app: Application, nav: AppNavigator): SetupNavigator =
        object : SetupNavigator {
            override fun back() = nav.back()

            override fun openAppSettings(): Boolean =
                try {
                    app.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                } catch (e: SecurityException) {
                    false
                }
        }
}

/** The one SetupEngine of the app, made on first use (its words are read from `:ui:words`, which suspends). */
@SingleIn(AppScope::class)
@Inject
class PhoneSetup(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
    private val clock: Clock,
    private val redactor: Redactor,
    private val finisher: BuiltinSetupFinisher,
) : SetupSource {
    private val lane = dispatchers.serial("phone-setup")
    private val scope = CoroutineScope(appScope.coroutineContext + lane)
    private val making = Mutex()
    private var made: Pair<SetupEngine, SetupSession>? = null

    private suspend fun made(): Pair<SetupEngine, SetupSession> =
        making.withLock {
            made ?: create().also { made = it }
        }

    private suspend fun create(): Pair<SetupEngine, SetupSession> {
        val downloading = getString(Res.string.phoneSetupStageDownloadingLinux)
        val unpacking = getString(Res.string.phoneSetupStageUnpackingLinux)
        val title = getString(Res.string.phoneSetupNotificationTitle)
        val words =
            SetupWords(
                notificationChannel = getString(Res.string.phoneSetupNotificationChannel),
                notificationTitle = title,
                notificationProgress = getString(Res.string.phoneSetupNotificationProgress, "{percent}"),
                notificationDone = getString(Res.string.phoneSetupNotificationDone),
                notificationStopped = getString(Res.string.phoneSetupNotificationStopped),
                stageStarting = getString(Res.string.phoneSetupStageStarting),
                stageDownloadingLinux = downloading,
                stageUnpackingLinux = unpacking,
                stageRetrying = getString(Res.string.phoneSetupStageRetrying),
                // Adding tools to an existing setup is not offered here (non-goal): the plain title stands.
                addingTitle = { title },
            )
        val registry =
            PhoneSetupRegistry(
                PhoneSetupRegistryWords(
                    linuxTitle = getString(Res.string.phoneSetupLinuxTitle),
                    linuxWhy = getString(Res.string.phoneSetupLinuxWhy),
                    essentialsTitle = getString(Res.string.phoneSetupEssentialsTitle),
                    essentialsShort = getString(Res.string.phoneSetupEssentialsShort),
                    essentialsWhy = getString(Res.string.phoneSetupEssentialsWhy),
                    nodeWhy = getString(Res.string.phoneSetupNodeWhy),
                    openCodeWhy = getString(Res.string.phoneSetupOpenCodeWhy),
                    startTitle = getString(Res.string.phoneSetupStartTitle),
                    startWhy = getString(Res.string.phoneSetupStartWhy),
                ),
            )
        val engine =
            withContext(lane) {
                SetupEngine(
                    host = NativeSetupHost(app, dispatchers),
                    registryPort = registry,
                    words = words,
                    scope = scope,
                    clock = clock,
                    scriptPrelude = SetupPrelude.withSetupPrelude(""),
                    redact = redactor::redact,
                    finisher = finisher,
                )
            }
        return engine to SetupSession(engine.progress, engine.registry, downloading, unpacking)
    }

    override suspend fun open(): SetupSession = made().second

    override suspend fun restore(): Outcome<Unit> {
        val engine = made().first
        return withContext(lane) { engine.restore() }
    }

    override suspend fun run(ids: Set<String>, params: Map<String, Map<String, String>>) {
        val engine = made().first
        withContext(lane) { engine.run(ids, params) }
    }

    override suspend fun cancel() {
        val engine = made().first
        withContext(lane) { engine.cancel() }
    }
}

/** SetupJobHost over :android:linux (the Flutter MainActivity's `startSetup`/`setupStatus`/`run` channel calls). */
internal class NativeSetupHost(private val app: Application, private val dispatchers: AppDispatchers) : SetupJobHost {
    private val linux: BuiltinLinux get() = BuiltinLinux.get(app)
    private val runner: SetupRunner get() = SetupRunner.get(app)

    private suspend fun <T> blocking(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) {
            try {
                Outcome.Ok(block())
            } catch (e: SetupPersistenceException) {
                Outcome.Failed(Failure.Runtime(RuntimeProblem.SetupPersistence, Redacted.shape(e)))
            } catch (e: Exception) {
                Outcome.Failed(classifyRuntime(e))
            }
        }

    override suspend fun linuxInstalled(): Outcome<Boolean> = blocking { linux.installed }

    override suspend fun setupStatus(): Outcome<String?> = blocking { runner.status() }

    override suspend fun runCheck(script: String, agentUser: Boolean): Outcome<SetupCheckRun> =
        blocking { linux.run(script, CHECK_TIMEOUT_SECONDS, agentUser = agentUser).let { SetupCheckRun(it.exitCode, it.output) } }

    override suspend fun startSetup(job: SetupJobStart): Outcome<Unit> =
        blocking {
            runner.start(
                job.jobId,
                job.components.map(::spec),
                JSONObject(job.params),
                SetupRunner.Texts(
                    channel = job.texts.channel,
                    title = job.texts.title,
                    progress = job.texts.progress,
                    done = job.texts.done,
                    stopped = job.texts.stopped,
                ),
            )
        }

    override suspend fun cancelSetup(): Outcome<Unit> = blocking { runner.cancel() }

    override suspend fun completeStep(jobId: String, id: String, ok: Boolean, error: String?, version: String?): Outcome<Unit> =
        blocking { runner.completeStep(jobId, id, ok, error, version) }

    internal companion object {
        /** The engine's combined check: two minutes, as the Flutter channel call. */
        const val CHECK_TIMEOUT_SECONDS = 120L

        /** One engine step as SetupRunner takes it (Flutter `MainActivityBuiltinSupport.setupSpec`). */
        fun spec(spec: SetupStepSpec): SetupRunner.Spec =
            SetupRunner.Spec(
                id = spec.id,
                script = spec.script,
                native = spec.native,
                step = spec.step,
                weight = spec.weight.toDouble(),
                skipped = spec.skipped,
                version = spec.version,
                stage = spec.stage,
                labels = spec.labels,
                data = spec.data,
                agentUser = spec.agentUser,
            )
    }
}

/**
 * The job's last step (port of lib/builtin/setup/setup_finish.dart `BuiltinSetupFinisher`): the phone-server profile
 * (found or made, with a fresh app-made password), `oc.builtinServerOwner` pointing at it, the server started through
 * the one start path (BuiltinRuntime's PhoneServerStarter), then the app connected to it. Ok only when OpenCode
 * answered. A server already running the same OpenCode is left alone; one the job just replaced is restarted.
 */
@SingleIn(AppScope::class)
@Inject
class BuiltinSetupFinisher(
    private val app: Application,
    private val dispatchers: AppDispatchers,
    private val stores: PreviewStores,
    private val runtime: BuiltinRuntime,
    private val connections: ServerConnections,
    private val clock: Clock,
) : SetupFinisher {
    override suspend fun finish(request: SetupFinishRequest): Outcome<Unit> {
        // A Termux job must never create or start the in-app Ubuntu profile.
        if (request.host != SetupHostKind.Builtin) return Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
        val warmed = stores.warm()
        if (warmed is Outcome.Failed) return warmed
        val profile =
            when (val ensured = ensureProfile(request.version)) {
                is Outcome.Failed -> return ensured
                is Outcome.Ok -> ensured.value
            }
        val owned = stores.prefs.commit(stores.lanes.app) { putString(OWNER_KEY, profile.id.raw) }
        if (owned is Outcome.Failed) return owned
        val linux = BuiltinLinux.get(app)
        val running = withContext(dispatchers.io) {
            try {
                linux.serverRunning
            } catch (e: Exception) {
                false
            }
        }
        if (request.openCodeChanged && running) {
            // The old program still serves: stop it so the start below runs the new one.
            withContext(dispatchers.io) {
                try {
                    linux.stopServer()
                } catch (e: Exception) {
                    // The start below still reports whether a server answers.
                }
            }
        }
        if (request.openCodeChanged || !running) {
            val started = runtime.startServer()
            if (started is Outcome.Failed) return started
        } else {
            runtime.refreshServer()
        }
        return connections.connect(profile.id)
    }

    /** Flutter `ensureBuiltinProfile`: the active (else first) OpenCode 2 profile at the phone's address, or a new one. */
    private suspend fun ensureProfile(version: String?): Outcome<LoadedProfile> {
        val profiles = stores.profiles
        val candidates = profiles.profiles.value.filter { it.profile.backend == BackendKind.OpenCode2 && managesServerUrl(it.profile.baseUrl) }
        val existing = candidates.firstOrNull { it.id == profiles.activeId.value } ?: candidates.firstOrNull()
        val stored =
            existing?.profile?.copy(username = USERNAME, serverVersion = version ?: existing.profile.serverVersion)
                ?: StoredProfile(
                    id = ProfileId(nowMicros().toString()),
                    name = getString(Res.string.phoneSetupProfileName),
                    baseUrl = SERVER_URL,
                    username = USERNAME,
                    serverVersion = version,
                )
        val secret =
            if (existing == null || !existing.hasSecret || existing.reentry.password) {
                SecretEdit.Set(PhoneServerScripts.newPassword(java.security.SecureRandom()))
            } else {
                SecretEdit.Keep
            }
        return profiles.upsert(stored, secret)
    }

    private fun nowMicros(): Long {
        val now = clock.now()
        return now.epochSeconds * 1_000_000 + now.nanosecondsOfSecond / 1_000
    }

    internal companion object {
        const val OWNER_KEY = "oc.builtinServerOwner"
        const val USERNAME = PhoneServerScripts.USERNAME
        const val SERVER_URL = "http://127.0.0.1:4097"

        /** Flutter `BuiltinLinux.managesServerUrl`: plain HTTP to 127.0.0.1 on the phone server's port. */
        fun managesServerUrl(value: String?): Boolean {
            val uri = value?.let { try { java.net.URI(it) } catch (e: java.net.URISyntaxException) { null } } ?: return false
            if (uri.scheme != "http") return false
            val port = if (uri.port == -1) 80 else uri.port
            return uri.host == "127.0.0.1" && port == PhoneServerScripts.PORT
        }
    }
}

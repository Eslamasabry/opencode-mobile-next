package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.AppLog
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.linuxsetup.FinishProfile
import io.github.eslamasabry.oc.engine.linuxsetup.FinishProfiles
import io.github.eslamasabry.oc.engine.linuxsetup.FinishServer
import io.github.eslamasabry.oc.engine.linuxsetup.FinishStartProblem
import io.github.eslamasabry.oc.engine.linuxsetup.HostReply
import io.github.eslamasabry.oc.engine.linuxsetup.SetupComponentIds
import io.github.eslamasabry.oc.engine.linuxsetup.SetupDevice
import io.github.eslamasabry.oc.engine.linuxsetup.SetupEngine
import io.github.eslamasabry.oc.engine.linuxsetup.SetupFinishRequest
import io.github.eslamasabry.oc.engine.linuxsetup.SetupFinisher
import io.github.eslamasabry.oc.engine.linuxsetup.SetupLinuxHost
import io.github.eslamasabry.oc.engine.linuxsetup.SetupLinuxStatus
import io.github.eslamasabry.oc.engine.linuxsetup.SetupNotificationTexts
import io.github.eslamasabry.oc.engine.linuxsetup.SetupRunResult
import io.github.eslamasabry.oc.engine.linuxsetup.SetupRunnerSpec
import io.github.eslamasabry.oc.engine.linuxsetup.SetupScriptWrapper
import io.github.eslamasabry.oc.engine.linuxsetup.SetupToolsChanged
import io.github.eslamasabry.oc.engine.linuxsetup.setupComponentRegistry
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.setupscripts.withSetupPrelude
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupDevice
import io.github.eslamasabry.oc.feature.phonesetup.presentation.PhoneSetupJobs
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationChannel
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationDone
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationProgress
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationStopped
import io.github.eslamasabry.oc.ui.words.res.phoneSetupNotificationTitle
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProfileName
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageDownloadingLinux
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageRetrying
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageStarting
import io.github.eslamasabry.oc.ui.words.res.phoneSetupStageUnpackingLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.security.SecureRandom
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.json.JSONObject

/*
 * P12-BUILD-RUNTIME-C: the Android bindings of :feature:phonesetup. The setup engine (:engine:linuxsetup) plans the job
 * and hands it to android/linux SetupRunner (the same native runner the Flutter app used, under its foreground
 * service); this page reads setup.json back. The job's last step ("Start OpenCode") runs here through the engine's
 * SetupFinisher: the phone's own OpenCode 2 profile (made once, password made by the app), the owner key the phone
 * server reads (`oc.builtinServerOwner`), then the phone server's start. Words come from :ui:words in the person's
 * language; nothing raw is shown (Details are redacted by the engine).
 */
@BindingContainer
@ContributesTo(AppScope::class)
object PhoneSetupWiring {
    private const val OWNER_KEY = "oc.builtinServerOwner"
    private const val RUN_TIMEOUT_SECONDS = 600L
    private const val POLL_MILLIS = 500L

    @Provides
    @SingleIn(AppScope::class)
    fun setupToolsChanged(): SetupToolsChanged = SetupToolsChanged()

    @Provides
    @SingleIn(AppScope::class)
    fun setupLinuxHost(
        app: Application,
        linux: LinuxHost,
        dispatchers: AppDispatchers,
        payloadGuard: PhonePayloadGuard,
    ): SetupLinuxHost = RunnerSetupHost(app, linux, dispatchers, payloadGuard)

    @Provides
    @SingleIn(AppScope::class)
    fun setupEngine(
        host: SetupLinuxHost,
        clock: Clock,
        scope: AppCoroutineScope,
        changed: SetupToolsChanged,
        redactor: Redactor,
    ): SetupEngine =
        SetupEngine(
            host = host,
            clock = clock,
            scope = scope,
            toolsChanged = changed,
            components = { params -> setupComponentRegistry(params = params) },
            log = { message -> AppLog.tagged("PhoneSetup").i { message() } },
            redactor = redactor,
            wrapper = SetupScriptWrapper(::withSetupPrelude),
        )

    @Provides
    fun phoneSetupDevice(app: Application, dispatchers: AppDispatchers): PhoneSetupDevice =
        object : PhoneSetupDevice {
            override suspend fun facts(): SetupDevice =
                withContext(dispatchers.io) {
                    val memory =
                        (app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.let {
                            manager ->
                            ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem
                        }
                    val free =
                        try {
                            StatFs(app.filesDir.path).availableBytes
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    SetupDevice(
                        supportedAbis = Build.SUPPORTED_ABIS.toList(),
                        totalMemoryMb =
                            memory?.takeIf { it > 0 }?.let { (it / (1024 * 1024)).toInt() },
                        availableStorageBytes = free,
                    )
                }

            override fun openStorageSettings(): Boolean =
                listOf(
                        Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${app.packageName}"),
                        ),
                    )
                    .any { intent ->
                        try {
                            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            true
                        } catch (e: ActivityNotFoundException) {
                            false
                        } catch (e: SecurityException) {
                            false
                        }
                    }
        }

    @Provides
    fun phoneSetupJobs(
        app: Application,
        stores: PreviewStores,
        server: PhoneServerSource,
        linux: LinuxHost,
        dispatchers: AppDispatchers,
    ): PhoneSetupJobs =
        object : PhoneSetupJobs {
            override suspend fun finishStep(jobId: String, openCodeChanged: Boolean) {
                val finisher =
                    finisher(stores, server, linux, getString(Res.string.phoneSetupProfileName))
                val failure =
                    try {
                        finisher.finish(SetupFinishRequest(openCodeChanged = openCodeChanged))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AppLog.tagged("PhoneSetup").w { "finish ${e::class.simpleName}" }
                        FinishFailureMarker
                    }
                withContext(dispatchers.io) {
                    SetupRunner.get(app)
                        .completeStep(
                            jobId,
                            SetupComponentIds.START,
                            ok = failure == null,
                            error =
                                failure?.let { "[oc] OpenCode was installed but did not start" },
                            version = null,
                        )
                }
            }

            override suspend fun cancel() {
                withContext(dispatchers.io) { SetupRunner.get(app).cancel() }
            }

            override suspend fun pause() = delay(POLL_MILLIS)
        }

    /**
     * A finish that threw: reported to the runner as a failed start; the reason stays in the device
     * log by type.
     */
    private val FinishFailureMarker =
        io.github.eslamasabry.oc.engine.linuxsetup.SetupFinishFailure.CannotStart

    /**
     * The engine's finisher over the app's ProfileStore and the phone server (B-LINUX-205's rules).
     */
    private fun finisher(
        stores: PreviewStores,
        server: PhoneServerSource,
        linux: LinuxHost,
        name: String,
    ): SetupFinisher {
        val profiles = stores.profiles
        val store =
            object : FinishProfiles {
                private var cached: List<FinishProfile>? = null
                override val profiles: List<FinishProfile>
                    get() = cached ?: snapshot().also { cached = it }

                override val activeId: String?
                    get() = stores.profiles.activeId.value?.raw

                private fun snapshot(): List<FinishProfile> =
                    stores.profiles.profiles.value.map { loaded ->
                        val p = loaded.profile
                        FinishProfile(
                            id = p.id.raw,
                            name = p.name,
                            baseUrl = p.baseUrl,
                            username = p.username,
                            password = if (loaded.hasSecret) SECRET_KEPT else "",
                            requiresPasswordReentry = loaded.reentry.password,
                            requiresOpenCode2Migration = p.requiresOpenCode2Migration,
                            openCode2HistoryPending = p.openCode2HistoryPending,
                            serverVersion = p.serverVersion,
                        )
                    }

                override suspend fun upsert(profile: FinishProfile): Boolean {
                    val id = ProfileId(profile.id)
                    val existing = profiles.profile(id)?.profile
                    val stored =
                        (existing
                                ?: StoredProfile(
                                    id = id,
                                    name = profile.name,
                                    baseUrl = profile.baseUrl,
                                ))
                            .copy(
                                name = profile.name,
                                baseUrl = profile.baseUrl,
                                username = profile.username,
                                requiresOpenCode2Migration = profile.requiresOpenCode2Migration,
                                openCode2HistoryPending = profile.openCode2HistoryPending,
                                serverVersion = profile.serverVersion,
                            )
                    val secret =
                        if (profile.password == SECRET_KEPT) SecretEdit.Keep
                        else SecretEdit.Set(profile.password)
                    val ok = profiles.upsert(stored, secret) is Outcome.Ok
                    cached = null
                    return ok
                }
            }
        val phone =
            object : FinishServer {
                override suspend fun status(): HostReply<SetupLinuxStatus> {
                    server.refresh()
                    val facts = server.facts.first()
                    return HostReply.Ok(
                        SetupLinuxStatus(
                            installed = linux.presence() == LinuxPresence.Ready,
                            serverRunning = facts.status?.serverRunning == true,
                        )
                    )
                }

                override suspend fun stop() {
                    server.stop()
                }

                override suspend fun start(
                    profile: FinishProfile,
                    migrateLegacyHistory: Boolean,
                ): FinishStartProblem? {
                    val owned =
                        stores.prefs.commit(stores.lanes.app) { putString(OWNER_KEY, profile.id) }
                    if (owned is Outcome.Failed) return FinishStartProblem.PasswordNotSaved
                    return when (server.start()) {
                        is Outcome.Ok -> null
                        is Outcome.Failed -> FinishStartProblem.Other
                    }
                }
            }
        val owner = (stores.prefs.get(OWNER_KEY) as? PrefValue.Str)?.value
        return SetupFinisher(
            store = store,
            server = phone,
            connect = { null },
            isConnectedTo = { it.id == owner },
            serverUrl = PhoneOwnServers.IN_APP_URL,
            managesServerUrl = { url -> url.trimEnd('/') == PhoneOwnServers.IN_APP_URL },
            newId = { UUID.randomUUID().toString() },
            newPassword = { PhoneServerScripts.newPassword(SecureRandom()) },
            profileName = name,
        )
    }

    /** A stored secret the finisher must not overwrite (it only checks for emptiness). */
    private const val SECRET_KEPT = "\u0000kept"
}

/** SetupLinuxHost over android/linux: SetupRunner for jobs, the built-in Linux for checks. */
private class RunnerSetupHost(
    private val app: Application,
    private val linux: LinuxHost,
    private val dispatchers: AppDispatchers,
    private val payloadGuard: PhonePayloadGuard,
) : SetupLinuxHost {
    override suspend fun status(): HostReply<SetupLinuxStatus> =
        HostReply.Ok(SetupLinuxStatus(installed = linux.presence() == LinuxPresence.Ready))

    override suspend fun run(script: String): HostReply<SetupRunResult> =
        when (val run = linux.run(script, 600L)) {
            is Outcome.Ok -> HostReply.Ok(SetupRunResult(run.value.exitCode, run.value.output))
            is Outcome.Failed -> HostReply.Refused("check could not run")
        }

    override suspend fun setupStatus(): HostReply<String?> =
        withContext(dispatchers.io) {
            try {
                HostReply.Ok(SetupRunner.get(app).status())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HostReply.Refused(e::class.simpleName ?: "status")
            }
        }

    override suspend fun startSetup(
        jobId: String,
        components: List<SetupRunnerSpec>,
        params: Map<String, Map<String, String>>,
        texts: SetupNotificationTexts,
    ): HostReply<Unit> {
        val labels =
            mapOf(
                "download" to getString(Res.string.phoneSetupStageDownloadingLinux),
                "unpack" to getString(Res.string.phoneSetupStageUnpackingLinux),
                "retry" to getString(Res.string.phoneSetupStageRetrying),
            )
        val starting = getString(Res.string.phoneSetupStageStarting)
        val words =
            SetupRunner.Texts(
                channel = getString(Res.string.phoneSetupNotificationChannel),
                title = getString(Res.string.phoneSetupNotificationTitle),
                progress = getString(Res.string.phoneSetupNotificationProgress, "{percent}"),
                done = getString(Res.string.phoneSetupNotificationDone),
                stopped = getString(Res.string.phoneSetupNotificationStopped),
            )
        val specs = components.map { spec ->
            SetupRunner.Spec(
                id = spec.id,
                script = spec.script,
                native = spec.native,
                step = spec.step,
                weight = spec.weight,
                skipped = spec.skipped,
                version = spec.version,
                stage = if (spec.id == SetupComponentIds.START) starting else spec.stage,
                labels = spec.labels.mapValues { (key, value) -> labels[key] ?: value },
                data = spec.data,
                agentUser = spec.agentUser,
            )
        }
        val json =
            JSONObject().apply { params.forEach { (id, values) -> put(id, JSONObject(values)) } }
        val reservation =
            when (val reserved = payloadGuard.beginMutation(null)) {
                is Outcome.Failed -> return HostReply.Refused("setup is busy")
                is Outcome.Ok -> reserved.value
            }
        return try {
            withContext(dispatchers.io) {
                try {
                    SetupRunner.get(app).start(jobId, specs, json, words)
                    HostReply.Ok(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    HostReply.Refused(e::class.simpleName ?: "start")
                }
            }
        } finally {
            reservation.release()
        }
    }
}

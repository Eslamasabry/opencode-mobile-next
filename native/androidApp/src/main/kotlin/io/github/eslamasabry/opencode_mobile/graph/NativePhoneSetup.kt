package io.github.eslamasabry.opencode_mobile.graph

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.StatFs
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.agents.scripts.PaseoPhoneScripts
import io.github.eslamasabry.oc.engine.connection.ServerConnections
import io.github.eslamasabry.oc.engine.diagnostics.perf.PerfTrace
import io.github.eslamasabry.oc.engine.runtime.install.InstallPrelude
import io.github.eslamasabry.oc.engine.runtime.install.OpenCodeInstallScripts
import io.github.eslamasabry.oc.engine.runtime.install.SetupHost
import io.github.eslamasabry.oc.engine.runtime.setup.*
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxLifecycle
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxSetupSource
import io.github.eslamasabry.oc.ui.words.res.*
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.json.JSONObject

/**
 * One durable job for first setup, Repair, Continue and Add tools. Native owns setup.json and its
 * foreground work.
 */
@SingleIn(AppScope::class)
@Inject
class NativePhoneSetup(
    private val app: Application,
    private val runtime: BuiltinRuntime,
    private val connections: ServerConnections,
    private val stores: PreviewStores,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
    private val clock: Clock,
    private val redactor: Redactor,
    private val payloadGuard: PhonePayloadGuard,
    private val trace: PerfTrace,
) : LinuxSetupSource {
    private val serial = dispatchers.serial("phone-setup")
    private val scope = CoroutineScope(appScope.coroutineContext + serial)
    private val lane = Mutex()
    private var engine: SetupEngine? = null
    private val shown = MutableStateFlow(SetupProgress.Idle)
    private val changed = MutableStateFlow(0)
    private val admissionState = MutableStateFlow<SetupPreflightResult?>(null)
    val admission: StateFlow<SetupPreflightResult?> = admissionState
    override val progress: StateFlow<SetupProgress> = shown
    override val toolsChanged: StateFlow<Int> = changed
    private val linux by lazy { BuiltinLinux.get(app) }
    private val runner by lazy { SetupRunner.get(app) }

    private suspend fun <T> native(block: () -> T): Outcome<T> =
        withContext(dispatchers.io) { attempt(::classifyRuntime) { block() } }

    private val host =
        object : SetupJobHost {
            override suspend fun linuxInstalled(): Outcome<Boolean> = native { linux.installed }

            override suspend fun setupStatus(): Outcome<String?> = native { runner.status() }

            override suspend fun runCheck(
                script: String,
                agentUser: Boolean,
            ): Outcome<SetupCheckRun> = native {
                linux.run(script, 120, agentUser).let { SetupCheckRun(it.exitCode, it.output) }
            }

            override suspend fun startSetup(job: SetupJobStart): Outcome<Unit> {
                val ids = job.components.filterNot { it.skipped }.map { it.id }.toSet()
                val components = engine?.registry.orEmpty().filter { it.id in ids }
                fun total(values: List<Long>): Long =
                    values.fold(0L) { sum, value ->
                        if (value >= Long.MAX_VALUE - sum) Long.MAX_VALUE else sum + value
                    }
                val downloads = total(components.mapNotNull { it.downloadBytes?.coerceAtLeast(0) })
                val extracted = total(components.mapNotNull { it.installedBytes?.coerceAtLeast(0) })
                val abis = Build.SUPPORTED_ABIS.toList()
                val architecture =
                    SetupPreflight.check(SetupDeviceInfo(abis, null, null), downloads)
                if (!architecture.supported) {
                    admissionState.value = architecture
                    return Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                }
                val memory =
                    (native {
                            ActivityManager.MemoryInfo()
                                .also {
                                    app.getSystemService(ActivityManager::class.java)
                                        .getMemoryInfo(it)
                                }
                                .totalMem
                                // A zero reading is unknown, never a phone with no memory.
                                .takeIf { it > 0 }
                                ?.div(1024 * 1024)
                                ?.coerceAtMost(Int.MAX_VALUE.toLong())
                                ?.toInt()
                        }
                            as? Outcome.Ok)
                        ?.value
                val memoryAdmission =
                    SetupPreflight.check(SetupDeviceInfo(abis, memory, null), downloads)
                if (!memoryAdmission.supported) {
                    admissionState.value = memoryAdmission
                    return Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                }
                val available =
                    (native { StatFs(app.filesDir.absolutePath).availableBytes } as? Outcome.Ok)
                        ?.value
                val admission =
                    SetupPreflight.check(
                        SetupDeviceInfo(abis, memory, available),
                        downloads,
                        extracted.takeIf { it > 0 },
                    )
                admissionState.value = admission
                if (!admission.supported)
                    return Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                val reservation =
                    when (val reserved = payloadGuard.beginMutation(null)) {
                        is Outcome.Failed -> return reserved
                        is Outcome.Ok -> reserved.value
                    }
                return try {
                    native {
                        val specs =
                            job.components.map { s ->
                                SetupRunner.Spec(
                                    s.id,
                                    s.script,
                                    s.native,
                                    s.step,
                                    s.weight.toDouble(),
                                    s.skipped,
                                    s.version,
                                    s.stage,
                                    s.labels,
                                    s.data,
                                    s.agentUser,
                                )
                            }
                        runner.start(
                            job.jobId,
                            specs,
                            JSONObject(job.params),
                            SetupRunner.Texts(
                                job.texts.channel,
                                job.texts.title,
                                job.texts.progress,
                                job.texts.done,
                                job.texts.stopped,
                            ),
                        )
                    }
                } finally {
                    reservation.release()
                }
            }

            override suspend fun cancelSetup(): Outcome<Unit> = native { runner.cancel() }

            override suspend fun completeStep(
                jobId: String,
                id: String,
                ok: Boolean,
                error: String?,
                version: String?,
            ): Outcome<Unit> = native { runner.completeStep(jobId, id, ok, error, version) }
        }

    private suspend fun get(): SetupEngine {
        engine?.let {
            return it
        }
        val linuxTitle = getString(Res.string.phoneSetupLinuxTitle)
        val essentials = getString(Res.string.phoneSetupEssentialsTitle)
        val essentialsShort = getString(Res.string.phoneSetupEssentialsShort)
        val start = getString(Res.string.phoneSetupStartTitle)
        val python = getString(Res.string.linuxRuntimePythonTitle)
        val node = getString(Res.string.linuxRuntimeNodeTitle)
        val openCode = getString(Res.string.linuxRuntimeOpenCodeTitle)
        val adding = getString(Res.string.aiteamComponentAddingTitle, "{names}")
        val separator = getString(Res.string.phoneSetupStartListSeparator)
        val words =
            SetupWords(
                notificationChannel = getString(Res.string.phoneSetupNotificationChannel),
                notificationTitle = getString(Res.string.phoneSetupNotificationTitle),
                notificationProgress =
                    getString(Res.string.phoneSetupNotificationProgress, "{percent}"),
                notificationDone = getString(Res.string.phoneSetupNotificationDone),
                notificationStopped = getString(Res.string.phoneSetupNotificationStopped),
                stageStarting = getString(Res.string.phoneSetupStageStarting),
                stageDownloadingLinux = getString(Res.string.phoneSetupStageDownloadingLinux),
                stageUnpackingLinux = getString(Res.string.phoneSetupStageUnpackingLinux),
                stageRetrying = getString(Res.string.phoneSetupStageRetrying),
                addingTitle = { names -> adding.replace("{names}", names.joinToString(separator)) },
            )
        return SetupEngine(
                host,
                SetupRegistry { params ->
                    val code =
                        OpenCodeInstallScripts.component(
                            SetupHost.Builtin,
                            params[SetupComponentIds.OPENCODE]?.get("version"),
                        )
                    listOf(
                        SetupComponent(
                            SetupComponentIds.LINUX,
                            linuxTitle,
                            linuxTitle,
                            "[ -s /etc/os-release ] && . /etc/os-release && echo \"\${VERSION%% *}\"",
                            "",
                            required = true,
                            native = true,
                            estimatedSeconds = 15,
                            downloadBytes = 30_000_000,
                        ),
                        SetupComponent(
                            SetupComponentIds.ESSENTIALS,
                            essentials,
                            essentialsShort,
                            ESSENTIALS_CHECK,
                            ESSENTIALS_INSTALL,
                            dependsOn = listOf(SetupComponentIds.LINUX),
                            required = true,
                            estimatedSeconds = 60,
                            downloadBytes = 45_000_000,
                        ),
                        SetupComponent(
                            SetupComponentIds.PYTHON,
                            python,
                            python,
                            PYTHON_CHECK,
                            PYTHON_INSTALL,
                            dependsOn = listOf(SetupComponentIds.ESSENTIALS),
                            defaultOn = true,
                            estimatedSeconds = 40,
                            downloadBytes = 25_000_000,
                        ),
                        SetupComponent(
                            SetupComponentIds.NODE,
                            node,
                            node,
                            NODE_CHECK,
                            NODE_INSTALL,
                            dependsOn = listOf(SetupComponentIds.ESSENTIALS),
                            required = true,
                            estimatedSeconds = 20,
                            downloadBytes = 58_000_000,
                        ),
                        SetupComponent(
                            SetupComponentIds.OPENCODE,
                            openCode,
                            openCode,
                            code.checkScript,
                            code.installScript,
                            dependsOn = listOf(SetupComponentIds.NODE),
                            required = true,
                            estimatedSeconds = 100,
                            downloadBytes = 50_000_000,
                        ),
                        SetupComponent(
                            SetupComponentIds.START,
                            start,
                            start,
                            "",
                            "",
                            dependsOn = listOf(SetupComponentIds.OPENCODE),
                            required = true,
                            jobStep = true,
                            estimatedSeconds = 10,
                        ),
                    )
                },
                words,
                scope,
                clock,
                InstallPrelude.SETUP,
                { redactor.diagnostics(it) },
                foreground = { runtime.isForeground },
                timing = {
                    trace.recordDuration(
                        it.name,
                        it.durationMillis.milliseconds,
                        it.attributes,
                        error = it.failed,
                    )
                },
                finisher =
                    SetupFinisher { request ->
                        when (val finished = runtime.finishSetup(request)) {
                            is Outcome.Failed -> finished
                            is Outcome.Ok -> connections.connect(finished.value)
                        }
                    },
            )
            .also { value ->
                engine = value
                scope.launch { value.progress.collect { shown.value = it } }
                scope.launch { value.toolsChanged.collect { changed.value = it } }
            }
    }

    private suspend fun command(action: suspend (SetupEngine) -> Unit): Outcome<Unit> =
        withContext(serial) {
            lane.withLock {
                when (val warm = stores.warm()) {
                    is Outcome.Failed -> warm
                    is Outcome.Ok -> {
                        val setup = get()
                        action(setup)
                        shown.value = setup.progress.value
                        if (shown.value.state == SetupState.Failed)
                            Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                        else Outcome.Ok(Unit)
                    }
                }
            }
        }

    override suspend fun refresh(): Outcome<Unit> =
        withContext(serial) {
            lane.withLock {
                when (val warm = stores.warm()) {
                    is Outcome.Failed -> warm
                    is Outcome.Ok -> get().restore().also { shown.value = get().progress.value }
                }
            }
        }

    override suspend fun start(): Outcome<Unit> = command {
        it.run(setOf(SetupComponentIds.PYTHON), SetupJobParams.firstSetup + RUNTIME_PARAMS)
    }

    override suspend fun addPython(): Outcome<Unit> = command {
        it.run(
            setOf(SetupComponentIds.PYTHON),
            SetupJobParams.adding(listOf(SetupComponentIds.PYTHON)) + RUNTIME_PARAMS,
        )
    }

    override suspend fun resume(): Outcome<Unit> = command { it.resume() }

    override suspend fun cancel(): Outcome<Unit> =
        withContext(serial) {
            // A cancel during checks must set cancelRequested before startSetup can run, so it
            // bypasses the lane a running start holds. With no engine yet (a cold app with a
            // runner job), nothing is starting: build it through the lane and cancel normally.
            val current = engine ?: return@withContext command { it.cancel() }
            current.cancel()
            Outcome.Ok(Unit)
        }

    internal suspend fun <T> toolsCommand(block: suspend () -> T): T =
        withContext(serial) {
            lane.withLock {
                get()
                block()
            }
        }

    internal fun toolsInvalidated() {
        engine?.invalidateTools()
    }

    suspend fun update(): Outcome<Unit> {
        if (!runtime.mayUpdate()) return Outcome.Failed(Failure.Runtime(RuntimeProblem.SlotBusy))
        return command { it.run(setOf(SetupComponentIds.OPENCODE), RUNTIME_PARAMS) }
    }

    val lifecycle: LinuxLifecycle =
        object : LinuxLifecycle {
            override suspend fun installed(): Boolean? =
                (native { linux.installed } as? Outcome.Ok)?.value

            override suspend fun erase(): Outcome<Unit> = runtime.eraseRuntime()

            override suspend fun startSetup(): Outcome<Boolean> =
                when (val ran = start()) {
                    is Outcome.Failed -> ran
                    is Outcome.Ok -> Outcome.Ok(true)
                }

            override suspend fun repair(): Outcome<Unit> = command { setup ->
                setup.run(
                    setup.registry.filter { it.required }.map { it.id }.toSet(),
                    RUNTIME_PARAMS,
                )
            }
        }

    internal companion object {
        val RUNTIME_PARAMS = mapOf(SetupComponentIds.OPENCODE to mapOf("runtime" to "opencode2"))
        const val ESSENTIALS_CHECK =
            "set -e\ncommand -v curl >/dev/null\ncommand -v git >/dev/null\ncommand -v ssh >/dev/null\n[ -s /etc/ssl/certs/ca-certificates.crt ]\ngit --version | cut -d ' ' -f 3\n"
        const val ESSENTIALS_INSTALL =
            "set -eu\noc_apt_install curl ca-certificates git openssh-client\noc_version \"\$(git --version | cut -d ' ' -f 3)\"\n"
        const val PYTHON_CHECK =
            "set -e\npython3 -c 'import ensurepip, venv' >/dev/null\npython3 -m pip --version >/dev/null\npython3 --version | cut -d ' ' -f 2\n"
        const val PYTHON_INSTALL =
            "set -eu\noc_apt_install python3 python3-venv python3-pip\noc_version \"\$(python3 --version | cut -d ' ' -f 2)\"\n"
        val NODE_CHECK =
            "set -e\n[ \"\$(node --version)\" = '${PaseoPhoneScripts.NODE_VERSION}' ]\ncommand -v npm >/dev/null\n[ \"\$(npm config get prefix)\" = /usr/local ]\nnode --version | sed 's/^v//'\n"
        val NODE_INSTALL =
            $$"""set -eu
case "$(uname -m)" in
  aarch64|arm64) node_arch=arm64; node_sha=$${PaseoPhoneScripts.NODE_ARM64_SHA256} ;;
  x86_64|amd64) node_arch=x64; node_sha=$${PaseoPhoneScripts.NODE_X64_SHA256} ;;
  *) exit 64 ;;
esac
node_version=$${PaseoPhoneScripts.NODE_VERSION}
node_file=/var/cache/oc-setup/node-$node_version-linux-$node_arch.tar.gz
oc_stage 'Downloading Node.js'
oc_download "https://nodejs.org/dist/$node_version/node-$node_version-linux-$node_arch.tar.gz" "$node_file" "$node_sha"
oc_stage 'Unpacking Node.js'
rm -rf /opt/node.new
mkdir -p /opt/node.new
tar -xzf "$node_file" -C /opt/node.new --strip-components=1
[ "$(/opt/node.new/bin/node --version)" = "$node_version" ]
rm -rf /opt/node
mv /opt/node.new /opt/node
for tool in node npm npx; do ln -sf /opt/node/bin/$tool /usr/local/bin/$tool; done
npm config set prefix /usr/local
rm -f "$node_file"
oc_version "$(node --version | sed 's/^v//')"
"""
    }
}

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.android.media.speech.SherpaOnnx
import io.github.eslamasabry.oc.android.voice.speech.KokoroSink
import io.github.eslamasabry.oc.android.voice.speech.KokoroTtsConfig
import io.github.eslamasabry.oc.android.voice.speech.NativeRecognizer
import io.github.eslamasabry.oc.android.voice.speech.NativeTts
import io.github.eslamasabry.oc.android.voice.speech.SherpaRuntime
import io.github.eslamasabry.oc.android.voice.speech.WhisperRecognizerConfig
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.Redacted
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.agents.host.PaseoConfig
import io.github.eslamasabry.oc.engine.agents.phonecontext.PhoneAgentContext
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.profiles.StoredProfile
import io.github.eslamasabry.oc.engine.runtime.history.HistorySwitchAdmission
import io.github.eslamasabry.oc.engine.runtime.history.HistorySwitchCheck
import io.github.eslamasabry.oc.engine.runtime.history.LinuxRunResult
import io.github.eslamasabry.oc.engine.runtime.host.NativeRuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.AppOpenStart
import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget
import io.github.eslamasabry.oc.engine.runtime.setup.SetupFinishRequest
import io.github.eslamasabry.oc.engine.runtime.setup.SetupHostKind
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.host.ShellRun
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxLifecycle
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxSetupSource
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.oc.feature.phone.presentation.RecoveryFacts
import io.github.eslamasabry.oc.protocol.paseo.transport.PaseoTransport
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.phoneSetupProfileName
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupPersistenceException
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/*
 * P12-FIX-LINUX-VOICE: the REAL built-in Linux, bound over :android:linux's BuiltinLinux (the Flutter app's runtime,
 * copied unchanged). It reads the same app-private folders the Flutter app wrote — files/linux/ubuntu (the rootfs),
 * files/linux/ubuntu.ready, /root/projects inside it — so after the in-place update the phone server, its projects and
 * everything the Linux pages show are the ones the person already has. Nothing is moved or re-downloaded.
 *
 * Replaces LinuxWiring's former AbsentLinuxHost / not-installed bindings (the migration proof's ship blocker #2).
 * BuiltinLinux calls block (process I/O, proot); every call here runs on AppDispatchers.io. Exception text from the
 * runtime only classifies (Failure.Runtime); it is never shown (Redacted.shape keeps the class name only).
 */
@BindingContainer
@ContributesTo(AppScope::class)
object LinuxRuntimeWiring {
    @Provides
    @SingleIn(AppScope::class)
    fun builtinRuntime(
        app: Application,
        dispatchers: AppDispatchers,
        appScope: AppCoroutineScope,
        profiles: ProfileStore,
        stores: PreviewStores,
        clock: Clock,
        acts: NativeRuntimeActs,
        leases: NativeChatLeases,
        policies: AppAutomationPolicies,
    ): BuiltinRuntime =
        BuiltinRuntime(app, dispatchers.io, appScope, profiles, stores, clock, acts, leases, policies).also {
            leases.start()
        }

    @Provides fun linuxHost(runtime: BuiltinRuntime): LinuxHost = runtime.host

    @Provides fun linuxLifecycle(setup: NativePhoneSetup): LinuxLifecycle = setup.lifecycle

    @Provides fun linuxSetupSource(setup: NativePhoneSetup): LinuxSetupSource = setup

    @Provides
    fun phoneServerSource(runtime: BuiltinRuntime, setup: NativePhoneSetup): PhoneServerSource =
        runtime.serverSource(setup::update)
}

/**
 * Started with the app (OcApplication touches it), so the phone server comes back when the app
 * opens.
 */
@ContributesTo(AppScope::class)
interface LinuxRuntimeGraph {
    val builtinRuntime: BuiltinRuntime
}

/**
 * Where the Flutter app (and this one, same package) keeps the built-in Linux. Pure: tested on the
 * JVM.
 */
internal object BuiltinDiscovery {
    fun home(filesDir: File): File = File(filesDir, "linux")

    fun rootfs(filesDir: File): File = File(home(filesDir), "ubuntu")

    /** `BuiltinLinux.installed`: the marker setup writes last. */
    fun readyMarker(filesDir: File): File = File(home(filesDir), "ubuntu.ready")

    /** `/root/projects` inside Ubuntu (BuiltinLinux.projectsDir). */
    fun projects(filesDir: File): File = File(rootfs(filesDir), "root/projects")

    fun presence(filesDir: File): LinuxPresence =
        if (readyMarker(filesDir).isFile && rootfs(filesDir).isDirectory) LinuxPresence.Ready
        else LinuxPresence.NotInstalled

    /** setup.json (SetupRunner) says a job runs right now. */
    fun setupRunning(statusJson: String?): Boolean =
        statusJson != null && Regex("\"state\"\\s*:\\s*\"running\"").containsMatchIn(statusJson)
}

/**
 * The phone server's scripts, verbatim ports of lib/builtin/builtin_linux.dart. Pure: tested on the
 * JVM.
 */
internal object PhoneServerScripts {
    const val PORT = 4097
    const val USERNAME = "opencode"
    const val PROJECTS_DIR = "/root/projects"
    const val PASSWORD_FILE = "/root/.oc-builtin/server.password"

    /** Flutter `renewBuiltinPassword`: 32 random bytes, base64url, padding removed. */
    fun newPassword(random: java.util.Random): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(ByteArray(32).also(random::nextBytes))

    /** Flutter `BuiltinLinux._quote`. */
    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    /**
     * Flutter `BuiltinLinux.writePasswordScript`: the password never reaches a command line or a
     * log.
     */
    fun writePassword(password: String): String {
        require(password.isNotEmpty()) { "Must not be empty." }
        val dir = PASSWORD_FILE.substringBeforeLast('/')
        return "set -eu\n" +
            "umask 077\n" +
            "mkdir -p $dir\n" +
            "printf '%s' ${quote(password)} > $PASSWORD_FILE.tmp\n" +
            "mv $PASSWORD_FILE.tmp $PASSWORD_FILE\n"
    }

    /**
     * Flutter `BuiltinLinux.serverScript(runtime = opencode2)`: started in /root/projects, bound to
     * 127.0.0.1 only, Basic auth `opencode` + the password read from the root-only file, OpenCode
     * 2's isolated XDG folders, and the OpenCode 1 history copy before the first start. Deviation:
     * the phone-context block is refreshed without making its failure a startup blocker.
     */
    fun server(port: Int = PORT): String {
        require(port in 1024..65535) { "Must be between 1024 and 65535." }
        val isolated =
            "mkdir -p /root/.oc-opencode2/data/opencode /root/.oc-opencode2/cache " +
                "/root/.oc-opencode2/state /root/.oc-opencode2/config/opencode\n" +
                "unset OPENCODE_CONFIG OPENCODE_CONFIG_CONTENT\n" +
                "export XDG_DATA_HOME=/root/.oc-opencode2/data\n" +
                "export XDG_CACHE_HOME=/root/.oc-opencode2/cache\n" +
                "export XDG_STATE_HOME=/root/.oc-opencode2/state\n" +
                "export XDG_CONFIG_HOME=/root/.oc-opencode2/config\n" +
                "export OPENCODE_CONFIG_DIR=/root/.oc-opencode2/config/opencode\n" +
                "export OPENCODE_DB=/root/.oc-opencode2/data/opencode/opencode.db\n" +
                "(\n$HISTORY_COPY) || exit 74\n"
        return "set -eu\n" +
            "mkdir -p $PROJECTS_DIR\n" +
            "cd $PROJECTS_DIR\n" +
            "[ -s $PASSWORD_FILE ] || { echo \"[oc] The server password is missing\" >&2; exit 78; }\n" +
            isolated +
            "(\n${PhoneAgentContext.builtinScript}) || true\n" +
            "password=\$(cat $PASSWORD_FILE)\n" +
            "export OPENCODE_SERVER_USERNAME=$USERNAME\n" +
            "export OPENCODE_SERVER_PASSWORD=\"\$password\"\n" +
            "export OPENCODE_PASSWORD=\"\$password\"\n" +
            "unset password\n" +
            "exec opencode2 serve --hostname 127.0.0.1 --port $port\n"
    }

    /**
     * lib/domain/opencode_history_copy.dart `openCode1HistoryCopyScript`, verbatim (provenance +
     * copy).
     */
    const val HISTORY_COPY: String =
        $$"""
history_source_signature() (
  cd "$1" || exit 1
  for history_file in opencode.db opencode.db-wal opencode.db-shm; do
    [ ! -L "$history_file" ] || exit 1
    if [ -e "$history_file" ]; then
      [ -f "$history_file" ] || exit 1
      sha256sum "$history_file" || exit 1
    else
      printf 'absent %s\n' "$history_file"
    fi
  done
)
history_copy_receipt_matches() {
  [ -f "$oc2_dir/.oc1-import-source.sha256" ] &&
  [ ! -L "$oc2_dir/.oc1-import-source.sha256" ] || return 1
  history_signature=$(history_source_signature "$oc1_dir") || return 1
  [ "$(cat "$oc2_dir/.oc1-import-source.sha256")" = "$history_signature" ]
}

oc1_dir="${HOME:-/root}/.local/share/opencode"
oc2_dir=/root/.oc-opencode2/data/opencode
if [ ! -e "$oc2_dir/opencode.db" ] && [ -f "$oc1_dir/opencode.db" ]; then
  history_copy_failed() {
    echo '[oc-history-copy-failed] Your earlier conversations could not be copied. The original data is still safe on this phone. Free some space and try again.' >&2
    exit 74
  }
  [ ! -L "$oc1_dir" ] && [ ! -L "$oc2_dir" ] || history_copy_failed
  original_signature=$(history_source_signature "$oc1_dir") || history_copy_failed
  # Only our own verified interrupted copy may leave target sidecars.
  if [ -e "$oc2_dir/opencode.db-wal" ] || [ -e "$oc2_dir/opencode.db-shm" ]; then
    history_copy_receipt_matches || history_copy_failed
    for history_file in opencode.db-wal opencode.db-shm; do
      if [ -e "$oc2_dir/$history_file" ]; then
        [ ! -L "$oc2_dir/$history_file" ] &&
        cmp -s "$oc1_dir/$history_file" "$oc2_dir/$history_file" || history_copy_failed
      fi
    done
    rm -f "$oc2_dir/opencode.db-wal" "$oc2_dir/opencode.db-shm" || history_copy_failed
  fi
  mkdir -p "$oc2_dir" || history_copy_failed
  stage="$oc2_dir/.oc1-copy.$$"
  # Do not reuse an interrupted staging folder or follow a stale symlink.
  mkdir "$stage" || history_copy_failed
  if ! { cp "$oc1_dir/opencode.db" "$stage/opencode.db" \
    && { [ ! -f "$oc1_dir/opencode.db-wal" ] || cp "$oc1_dir/opencode.db-wal" "$stage/opencode.db-wal"; } \
    && { [ ! -f "$oc1_dir/opencode.db-shm" ] || cp "$oc1_dir/opencode.db-shm" "$stage/opencode.db-shm"; }; }; then
    rm -rf "$stage"
    history_copy_failed
  fi
  # Commit provenance before the database. A crash after the database rename
  # can then safely retry admission, including after OC2 has converted it.
  copied_signature=$(history_source_signature "$stage") || history_copy_failed
  [ "$copied_signature" = "$original_signature" ] || history_copy_failed
  [ "$(history_source_signature "$oc1_dir")" = "$original_signature" ] || history_copy_failed
  printf '%s\n' "$copied_signature" > "$stage/.oc1-import-source.sha256" || history_copy_failed
  mv "$stage/.oc1-import-source.sha256" "$oc2_dir/.oc1-import-source.sha256" || history_copy_failed
  if ! { { [ ! -f "$stage/opencode.db-wal" ] || mv "$stage/opencode.db-wal" "$oc2_dir/opencode.db-wal"; } \
    && { [ ! -f "$stage/opencode.db-shm" ] || mv "$stage/opencode.db-shm" "$oc2_dir/opencode.db-shm"; } \
    && mv "$stage/opencode.db" "$oc2_dir/opencode.db"; }; then
    rm -rf "$stage"
    history_copy_failed
  fi
  rm -rf "$stage"
fi
"""

    /**
     * lib/domain/server_probe.dart: a 2xx answered with our password, a 401 refused it, anything
     * else is no answer.
     */
    fun answer(status: Int?): ProbeAnswer =
        when {
            status == null -> ProbeAnswer.NoAnswer
            status in 200..299 -> ProbeAnswer.Answered
            status == 401 -> ProbeAnswer.NeedsPassword
            else -> ProbeAnswer.NoAnswer
        }

    /**
     * The routes the probe tries in order (beta `/api/health`, stable `/api/info`, OpenCode 1
     * `/global/health`).
     */
    val PROBE_PATHS: List<String> = listOf("/api/health", "/api/info", "/global/health")
}

/**
 * Runtime exception → the flat Failure the features read (P12-LINUX report §5 item 5). Text
 * classifies only.
 */
internal fun classifyRuntime(e: Throwable): Failure {
    val message = e.message.orEmpty()
    val problem =
        when {
            message == "ownershipUnknown" || message == "ownership_unknown" ->
                RuntimeProblem.OwnershipUnknown
            // SetupRunner.start: the durable setup journal could not be written (B-LINUX-208).
            e is SetupPersistenceException -> RuntimeProblem.SetupPersistence
            message.startsWith("Another phone task") -> RuntimeProblem.SlotBusy
            message.startsWith("Ubuntu is not installed") -> RuntimeProblem.NotInstalled
            message.startsWith("Finish or cancel setup") -> RuntimeProblem.Installing
            message.startsWith("The phone server could not restart") ->
                RuntimeProblem.RecoveryUnavailable
            else -> RuntimeProblem.Generic
        }
    return Failure.Runtime(problem, Redacted.shape(e))
}

/** The status the phone pages read, from BuiltinLinux's live values. Pure: tested on the JVM. */
internal fun runtimeStatusOf(
    installed: Boolean,
    installing: Boolean,
    serverRunning: Boolean,
    port: Int?,
    uptimeMs: Long?,
    services: List<String>,
): RuntimeStatus =
    RuntimeStatus(
        installed = installed,
        phase =
            if (installing) RuntimePhase.Installing
            else if (installed) RuntimePhase.Ready else RuntimePhase.Idle,
        serverRunning = serverRunning,
        serverPort = if (serverRunning) port else null,
        serverUptime = if (serverRunning) uptimeMs?.milliseconds else null,
        services = services,
    )

/**
 * The one owner of the built-in Linux in the app. [host], [lifecycle] and [server] are the seams
 * :feature:linux and :feature:phone read. Starts by following the activity
 * (BuiltinLinux.setActivityResumed, as Flutter's MainActivity did) and, once, by bringing the phone
 * server back when it was running and the person had not stopped it.
 */
class BuiltinRuntime
internal constructor(
    private val app: Application,
    private val io: CoroutineDispatcher,
    private val scope: AppCoroutineScope,
    private val profiles: ProfileStore,
    private val stores: PreviewStores,
    private val clock: Clock,
    private val acts: NativeRuntimeActs,
    private val leases: NativeChatLeases,
    private val policies: AppAutomationPolicies,
) {
    private val linux: BuiltinLinux by lazy { BuiltinLinux.get(app) }
    private val filesDir: File
        get() = app.filesDir

    private val lane = Mutex()
    private val recovery by lazy { NativeRuntimeRecovery(stores, linux, io, policies) }
    private val helperLane = io.limitedParallelism(1)
    private val closedProfiles = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var foreground = false
    @Volatile private var actionEpoch = 0L
    private val statusLane = Mutex()
    private val statusRevision = java.util.concurrent.atomic.AtomicLong()
    private var pendingStatus: Deferred<Outcome<RuntimeStatus>>? = null
    private var pendingStatusRevision = -1L
    private var presenceResetThisVisit = false
    private var idleBudgetHeld = false
    internal var onManualReady: (suspend () -> Unit)? = null
    val isForeground: Boolean
        get() = foreground

    /**
     * Concurrent readers share the pending read; a mutation invalidates it before the next read.
     */
    suspend fun runtimeStatus(): Outcome<RuntimeStatus> {
        val read = statusLane.withLock {
            val revision = statusRevision.get()
            pendingStatus?.takeIf { !it.isCompleted && pendingStatusRevision == revision }
                ?: scope
                    .async {
                        blocking { readStatus() }
                    }
                    .also {
                        pendingStatus = it
                        pendingStatusRevision = revision
                    }
        }
        return read.await()
    }

    private suspend fun <T> mutation(block: () -> T): Outcome<T> =
        try {
            blocking(block)
        } finally {
            statusRevision.incrementAndGet()
        }

    private suspend fun <T> blocking(block: () -> T): Outcome<T> =
        withContext(io) { attempt(::classifyRuntime) { block() } }

    // ---- LinuxHost

    val host: LinuxHost =
        object : LinuxHost {
            override suspend fun presence(): LinuxPresence =
                withContext(io) { BuiltinDiscovery.presence(filesDir) }

            override suspend fun run(script: String, timeoutSeconds: Long): Outcome<ShellRun> =
                mutation {
                    linux.run(script, timeoutSeconds).let { ShellRun(it.exitCode, it.output) }
                }

            override suspend fun setupRunning(): Boolean =
                withContext(io) {
                    try {
                        BuiltinDiscovery.setupRunning(SetupRunner.get(app).status())
                    } catch (e: Exception) {
                        false
                    }
                }

            override suspend fun serviceRunning(name: String): Boolean =
                withContext(io) { linux.serviceRunning(name) }

            override suspend fun startService(
                name: String,
                script: String,
                notice: String,
            ): Outcome<Unit> = mutation { linux.startService(name, script, null, notice) }

            override suspend fun stopService(name: String): Outcome<Unit> = blocking {
                linux.stopService(name)
            }

            override suspend fun serviceLog(name: String, tailBytes: Int): String =
                withContext(io) {
                    try {
                        linux.serviceLogTail(name, tailBytes)
                    } catch (e: Exception) {
                        ""
                    }
                }
        }

    /**
     * The same runtime lock as starts: native independently preserves projects and rejects active
     * setup.
     */
    suspend fun eraseRuntime(): Outcome<Unit> = lane.withLock {
        blocking { linux.uninstall(alsoDeleteProjects = false) }
    }

    // ---- The phone server

    private val port =
        object : PhoneServerPort {
            // BUILD-RUNTIME-B (B-LINUX-108, 142): the real read-only admission, not a blanket yes.
            override suspend fun checkHistoryMove(): Outcome<Unit> =
                when (
                    HistorySwitchCheck({ script, timeout ->
                            blocking {
                                linux.run(script, timeout.inWholeSeconds).let {
                                    LinuxRunResult(it.exitCode, it.output)
                                }
                            }
                        })
                        .admission()
                ) {
                    HistorySwitchAdmission.Allowed -> Outcome.Ok(Unit)
                    HistorySwitchAdmission.SeparateHistory ->
                        Outcome.Failed(Failure.Refused(RefusalReason.MigrationPending))
                    HistorySwitchAdmission.Unavailable ->
                        Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
                }

            override suspend fun writePassword(password: String): Outcome<Unit> =
                when (
                    val ran = blocking { linux.run(PhoneServerScripts.writePassword(password), 60) }
                ) {
                    is Outcome.Failed -> ran
                    is Outcome.Ok ->
                        if (ran.value.exitCode == 0) Outcome.Ok(Unit)
                        else Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                }

            override suspend fun launch(profile: ProfileId, mode: LaunchMode): Outcome<Unit> =
                mutation {
                    check(profile.raw !in closedProfiles) { "The phone server could not restart." }
                    val recipe =
                        mapOf("version" to 1, "profileId" to profile.raw, "runtime" to "openCode2")
                    val script = PhoneServerScripts.server()
                    when (mode) {
                        LaunchMode.Manual ->
                            linux.startServer(script, PhoneServerScripts.PORT, recipe)
                        is LaunchMode.ForAppOpen ->
                            linux.startServerForLaunch(
                                script,
                                PhoneServerScripts.PORT,
                                mode.expectedGeneration,
                                recipe,
                            )
                        is LaunchMode.Recovery ->
                            linux.restartServer(
                                script,
                                PhoneServerScripts.PORT,
                                mode.expectedGeneration,
                            )
                        is LaunchMode.AfterUpdate ->
                            linux.restartServer(
                                script,
                                PhoneServerScripts.PORT,
                                mode.expectedGeneration,
                            )
                    }
                }

            override suspend fun probe(password: String): ProbeAnswer =
                withContext(io) { probeLoopback(password) }

            override suspend fun refreshStatus(): Outcome<RuntimeStatus> = runtimeStatus()

            override suspend fun confirmRecovery(expectedGeneration: Long): Outcome<Unit> =
                blocking {
                    linux.confirmServerRecovery(expectedGeneration)
                }
        }

    private val starter = PhoneServerStarter(port, clock, renewPassword = ::renewPassword)
    private val appOpen =
        AppOpenStart(
            port,
            starter,
            clock,
            mayAct = {
                val owner = ownerId()
                foreground &&
                    owner != null &&
                    owner.raw !in closedProfiles &&
                    profiles.profile(owner) != null &&
                    profiles.activeId.value == owner &&
                    recovery.policyAllows(owner.raw)
            },
        )

    /**
     * B-LINUX-025: the phone server's password is made by the app, so when the Keystore lost it (a
     * fresh install, or the migration's `renewedByOwner`) a new one is made — 32 random bytes,
     * base64url without padding, as Flutter's `renewBuiltinPassword` — and saved under `pw.<id>` on
     * the same profile before the start writes it into Ubuntu.
     */
    private suspend fun renewPassword(id: ProfileId): String? {
        val loaded = profiles.profile(id) ?: return null
        val password = PhoneServerScripts.newPassword(java.security.SecureRandom())
        return when (profiles.upsert(loaded.profile, SecretEdit.Set(password))) {
            is Outcome.Ok -> password
            is Outcome.Failed -> null
        }
    }

    private val facts = MutableStateFlow(PhoneServerFacts())

    private fun readStatus(): RuntimeStatus =
        NativeRuntimeStatus.read(
            mapOf(
                "installed" to linux.installed,
                "phase" to linux.phase,
                "message" to null,
                "restorePhase" to linux.restorePhase,
                "restoreReason" to linux.restoreReason,
                "serverPort" to linux.port,
                "serverUptimeMs" to linux.serverUptimeMs,
                "services" to linux.runningServices(),
                "abi" to android.os.Build.SUPPORTED_ABIS.firstOrNull(),
                "bytesUsed" to linux.bytesUsed(),
            ) + linux.serverIdleStatus()
        )

    private inline fun <T> runCatchingBlocking(block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            null
        }

    /**
     * The person's Stop, as BuiltinLinux keeps it (device-wide `builtin_server_recovery`
     * userStopped).
     */
    private fun stoppedByPerson(): Boolean =
        app.getSharedPreferences("builtin_server_recovery", Context.MODE_PRIVATE).let { prefs ->
            prefs.getBoolean("userStopped", !prefs.getBoolean("wanted", false))
        }

    private fun probeLoopback(password: String): ProbeAnswer {
        val auth =
            "Basic " +
                Base64.getEncoder()
                    .encodeToString("${PhoneServerScripts.USERNAME}:$password".toByteArray())
        for (path in PhoneServerScripts.PROBE_PATHS) {
            val status =
                try {
                    val conn =
                        URL("http", "127.0.0.1", PhoneServerScripts.PORT, path).openConnection()
                            as HttpURLConnection
                    conn.connectTimeout = 2_000
                    conn.readTimeout = 4_000
                    conn.setRequestProperty("Authorization", auth)
                    try {
                        conn.responseCode
                    } finally {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    null
                }
            if (status == 404) continue
            return PhoneServerScripts.answer(status)
        }
        return ProbeAnswer.NoAnswer
    }

    /**
     * The phone server's profile: `oc.builtinServerOwner` (written by the Flutter app, kept by the
     * migration).
     */
    private fun ownerId(): ProfileId? = runCatchingBlocking {
        (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value
    }
        ?.takeIf { it.isNotEmpty() }
        ?.let(::ProfileId)

    internal fun healingOwner(): ProfileId? {
        val key = "oc.builtinServerOwner"
        return try {
            if (key in stores.prefs.keys(key)) {
                val raw = (stores.prefs.get(key) as? PrefValue.Str)?.value ?: return null
                if (!Regex("[A-Za-z0-9_-]{1,80}").matches(raw)) return null
                ProfileId(raw).takeIf(::readable)
            } else {
                profiles.profiles.value
                    .filter {
                        readable(it.id) &&
                            !it.profile.usesAgentSocket &&
                            it.profile.baseUrl.trimEnd('/') in
                                listOf(
                                    "http://127.0.0.1:${PhoneServerScripts.PORT}",
                                    "http://localhost:${PhoneServerScripts.PORT}",
                                )
                    }
                    .singleOrNull()
                    ?.id
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun readable(profile: ProfileId): Boolean =
        profile.raw !in closedProfiles && profiles.profile(profile) != null

    private suspend fun target(manual: Boolean = false): StartTarget? {
        val selected =
            if (manual)
                profiles.activeId.value?.takeIf { id ->
                    profiles.profile(id)?.profile?.let {
                        !it.usesAgentSocket &&
                            it.baseUrl.trimEnd('/') in
                                listOf(
                                    "http://127.0.0.1:${PhoneServerScripts.PORT}",
                                    "http://localhost:${PhoneServerScripts.PORT}",
                                )
                    } == true
                }
            else null
        val id = selected ?: healingOwner() ?: return null
        val loaded =
            profiles.profile(id)
                ?: (profiles.load() as? Outcome.Ok)?.value?.firstOrNull { it.id == id }
                ?: return null
        val password = (profiles.secret(loaded.id) as? Outcome.Ok)?.value.orEmpty()
        return StartTarget(
            loaded.id,
            password,
            requiresOpenCode2Migration = loaded.profile.requiresOpenCode2Migration,
            passwordNeedsReentry = loaded.reentry.password,
        )
    }

    private suspend fun publish() {
        val status = (runtimeStatus() as? Outcome.Ok)?.value
        val stopped = withContext(io) { runCatchingBlocking { stoppedByPerson() } ?: true }
        facts.update {
            it.copy(
                status = status,
                stoppedByPerson = stopped,
                replyRunning = ownerId()?.raw?.let { owner -> leases.busy.value[owner] } == true,
            )
        }
    }

    private suspend fun startNow(): Outcome<Unit> = lane.withLock {
        if (withContext(io) { BuiltinDiscovery.presence(filesDir) } != LinuxPresence.Ready) {
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
        }
        val target =
            target(manual = true)
                ?: return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        if (!readable(target.profile))
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        val epoch = actionEpoch
        val claimed =
            stores.prefs.commit(stores.lanes.forProfile(target.profile)) {
                putString("oc.builtinServerOwner", target.profile.raw)
            }
        if (claimed is Outcome.Failed) return@withLock claimed
        val admission = recovery.bind(target.profile)
        if (admission is Outcome.Failed) return@withLock admission
        if (epoch != actionEpoch || healingOwner() != target.profile || !readable(target.profile))
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        facts.update { it.copy(starting = true, startProblem = null) }
        try {
            val failure =
                starter.start(
                    target,
                    stillWanted = {
                        actionEpoch == epoch &&
                            healingOwner() == target.profile &&
                            readable(target.profile)
                    },
                )
            facts.update {
                it.copy(
                    startProblem = failure?.problem,
                    probe = if (failure == null) ProbeAnswer.Answered else it.probe,
                )
            }
            if (failure == null) {
                val confirmed = blocking { linux.confirmManualServerStart(target.profile.raw) }
                if (confirmed is Outcome.Failed) return@withLock confirmed
                val lifted = recovery.confirmManualStart(target.profile)
                if (lifted is Outcome.Failed) return@withLock lifted
                idleBudgetHeld = false
                onManualReady?.invoke()
            }
            publish()
            if (failure == null) Outcome.Ok(Unit)
            else
                Outcome.Failed(
                    Failure.Runtime(RuntimeProblem.ServerStartUnavailable, failure.details)
                )
        } finally {
            facts.update { it.copy(starting = false) }
        }
    }

    /**
     * Finishes only after history admission and an authenticated probe; legacy profile ids and
     * local keys stay.
     */
    suspend fun finishSetup(request: SetupFinishRequest): Outcome<ProfileId> = lane.withLock {
        if (request.host != SetupHostKind.Builtin || request.runtime != "opencode2") {
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        }
        val local =
            profiles.profiles.value.filter { loaded ->
                !loaded.profile.usesAgentSocket &&
                    loaded.profile.baseUrl.trimEnd('/') in
                        listOf(
                            "http://127.0.0.1:${PhoneServerScripts.PORT}",
                            "http://localhost:${PhoneServerScripts.PORT}",
                        )
            }
        val old =
            local.firstOrNull { it.id == ownerId() }
                ?: local.firstOrNull { it.id == profiles.activeId.value }
                ?: local.singleOrNull()
        if (old == null && local.isNotEmpty())
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        val id = old?.id ?: ProfileId(java.util.UUID.randomUUID().toString())
        if (id.raw in closedProfiles)
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        val migrating = old?.profile?.requiresOpenCode2Migration == true
        val activity = mutation { linux.setActivityResumed(foreground) }
        if (activity is Outcome.Failed) return@withLock activity
        if (migrating) {
            val admission = port.checkHistoryMove()
            if (admission is Outcome.Failed) return@withLock admission
        }
        if (old == null) {
            val saved =
                profiles.upsert(
                    StoredProfile(
                        id,
                        getString(Res.string.phoneSetupProfileName),
                        "http://127.0.0.1:${PhoneServerScripts.PORT}",
                        username = PhoneServerScripts.USERNAME,
                    ),
                    SecretEdit.Set(PhoneServerScripts.newPassword(java.security.SecureRandom())),
                )
            if (saved is Outcome.Failed) return@withLock saved
        }
        val claimed =
            stores.prefs.commit(stores.lanes.forProfile(id)) {
                putString("oc.builtinServerOwner", id.raw)
            }
        if (claimed is Outcome.Failed) return@withLock claimed
        val bound = recovery.bind(id)
        if (bound is Outcome.Failed) return@withLock bound
        val loaded =
            profiles.profile(id)
                ?: return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        val password =
            when (val secret = profiles.secret(id)) {
                is Outcome.Failed -> return@withLock secret
                is Outcome.Ok -> secret.value.orEmpty()
            }
        val current = (blocking { readStatus() } as? Outcome.Ok)?.value
        val answered =
            current?.serverRunning == true && port.probe(password) == ProbeAnswer.Answered
        if (!request.openCodeChanged && !migrating && answered) return@withLock Outcome.Ok(id)
        if (current?.serverRunning == true) {
            val stopped = mutation { linux.stopServer() }
            if (stopped is Outcome.Failed) return@withLock stopped
        }
        val epoch = actionEpoch
        val failure =
            starter.start(
                StartTarget(
                    id,
                    password,
                    requiresOpenCode2Migration = migrating,
                    passwordNeedsReentry = loaded.reentry.password,
                ),
                migrateLegacyHistory = migrating,
                stillWanted = { epoch == actionEpoch && id.raw !in closedProfiles },
            )
        if (failure != null)
            return@withLock Outcome.Failed(
                Failure.Runtime(RuntimeProblem.ServerStartUnavailable, failure.details)
            )
        val confirmed = blocking { linux.confirmManualServerStart(id.raw) }
        if (confirmed is Outcome.Failed) return@withLock confirmed
        if (migrating || request.version != null) {
            val saved =
                profiles.upsert(
                    loaded.profile.copy(
                        requiresOpenCode2Migration = false,
                        openCode2HistoryPending =
                            loaded.profile.openCode2HistoryPending || migrating,
                        serverVersion = request.version ?: loaded.profile.serverVersion,
                    )
                )
            if (saved is Outcome.Failed) return@withLock saved
        }
        publish()
        Outcome.Ok(id)
    }

    suspend fun mayUpdate(): Boolean {
        val status = (runtimeStatus() as? Outcome.Ok)?.value ?: return false
        if (!status.serverRunning) return true
        val owner = ownerId()?.raw ?: return false
        return leases.busy.value[owner] == false
    }

    /** An explicit idle-policy edit is bounded and never manufactures a supported receipt. */
    suspend fun idleConfigure(enabled: Boolean, minutes: Int): Outcome<RuntimeStatus> =
        lane.withLock {
            if (minutes !in 1..60)
                return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            when (val changed = mutation { linux.setPhoneServerIdlePolicy(enabled, minutes) }) {
                is Outcome.Failed -> changed
                is Outcome.Ok ->
                    NativeRuntimeStatus.read(changed.value).let { status ->
                        if (!status.serverIdlePolicySupported || !status.serverIdleReceiptValid)
                            Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
                        else Outcome.Ok(status)
                    }
            }
        }

    /**
     * Only the receipt's exact owner and generation may resume. Never substitute a manual Start.
     */
    suspend fun idleResume(): Outcome<RuntimeStatus> = lane.withLock {
        val target =
            target()
                ?: return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        if (!foreground || target.profile.raw in closedProfiles)
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        val bound = recovery.bind(target.profile)
        if (bound is Outcome.Failed) return@withLock bound
        val before =
            (runtimeStatus() as? Outcome.Ok)?.value
                ?: return@withLock Outcome.Failed(
                    Failure.Runtime(RuntimeProblem.RecoveryUnavailable)
                )
        val generation = before.serverIdleGeneration
        if (
            !before.serverIdlePolicySupported ||
                !before.serverIdleReceiptValid ||
                generation == null ||
                generation <= 0 ||
                !before.serverRestartWanted ||
                !recovery.policyAllows(target.profile.raw)
        )
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        val epoch = actionEpoch
        fun current() =
            foreground &&
                epoch == actionEpoch &&
                healingOwner() == target.profile &&
                readable(target.profile)
        idleBudgetHeld = true
        val receipt =
            if (before.serverIdleStopped) {
                when (
                    val resumed = mutation {
                        linux.resumeIdleStoppedPhoneServer(target.profile.raw, generation)
                    }
                ) {
                    is Outcome.Failed -> return@withLock resumed
                    is Outcome.Ok -> NativeRuntimeStatus.read(resumed.value)
                }
            } else before
        if (!current())
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        if (
            !receipt.serverIdleReceiptValid ||
                receipt.serverIdleGeneration != generation ||
                !receipt.serverRunning ||
                port.probe(target.password) != ProbeAnswer.Answered
        )
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        if (before.serverIdleHelperStopped) {
            val helper =
                (stores.prefs.get("oc.phoneAgentOwner.${target.profile.raw}") as? PrefValue.Str)
                    ?.value ?: target.profile.raw
            if (!Regex("[A-Za-z0-9_-]{1,80}").matches(helper))
                return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            val password =
                when (val read = stores.secrets.read("oc.agentHostSecret.$helper")) {
                    is Outcome.Failed -> return@withLock read
                    is Outcome.Ok ->
                        read.value?.takeIf { Regex("[a-f0-9]{64}").matches(it) }
                            ?: return@withLock Outcome.Failed(
                                Failure.Runtime(RuntimeProblem.RecoveryUnavailable)
                            )
                }
            if (!current())
                return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            val restored = mutation {
                linux.resumeIdleStoppedAgentHost(
                    helper,
                    generation,
                    password,
                    PaseoConfig.render().json,
                )
            }
            if (restored is Outcome.Failed) return@withLock restored
            val handshake =
                PaseoTransport(
                    "ws://127.0.0.1:4099/ws",
                    password,
                    OkHttpPaseoSockets(),
                    scope,
                    helperLane,
                    requestTimeoutMs = 8_000,
                )
            val ready =
                try {
                    attempt(::classifyRuntime) { handshake.connect() }
                } finally {
                    handshake.close()
                }
            if (ready is Outcome.Failed) return@withLock ready
        }
        if (!current())
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        val live =
            (runtimeStatus() as? Outcome.Ok)?.value
                ?: return@withLock Outcome.Failed(
                    Failure.Runtime(RuntimeProblem.RecoveryUnavailable)
                )
        if (
            !live.serverIdleReceiptValid ||
                live.serverIdleGeneration != generation ||
                !live.serverRunning ||
                live.serverIdleStopped
        )
            return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        when (
            val completed = mutation {
                linux.completePhoneServerIdleResume(target.profile.raw, generation)
            }
        ) {
            is Outcome.Failed -> completed
            is Outcome.Ok ->
                NativeRuntimeStatus.read(completed.value).let { after ->
                    if (
                        !current() ||
                            !after.serverIdleReceiptValid ||
                            !after.serverRunning ||
                            after.serverIdleStopped ||
                            after.serverIdleHelperStopped
                    )
                        Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
                    else Outcome.Ok(after)
                }
        }
    }

    /**
     * Exit recovery shares the native budget and generation; already-running or native-owned work
     * is left alone.
     */
    suspend fun recoverAfterExit(profileId: String): Outcome<Boolean> = lane.withLock {
        val target = target() ?: return@withLock Outcome.Ok(false)
        if (
            target.profile.raw != profileId ||
                !foreground ||
                profileId in closedProfiles ||
                !recovery.policyAllows(profileId)
        )
            return@withLock Outcome.Ok(false)
        val bound = recovery.bind(target.profile)
        if (bound is Outcome.Failed) return@withLock bound
        val before =
            (runtimeStatus() as? Outcome.Ok)?.value
                ?: return@withLock Outcome.Failed(
                    Failure.Runtime(RuntimeProblem.RecoveryUnavailable)
                )
        val generation = before.serverRecoveryGeneration
        if (
            !before.installed ||
                !before.serverRestartWanted ||
                before.idleBlocks ||
                before.serverRunning ||
                before.serverRecoveryScheduled ||
                generation == null
        )
            return@withLock Outcome.Ok(false)
        val epoch = actionEpoch
        val failure =
            starter.start(
                target,
                LaunchMode.Recovery(generation),
                stillWanted = {
                    foreground &&
                        actionEpoch == epoch &&
                        profileId !in closedProfiles &&
                        recovery.policyAllows(profileId)
                },
            )
        if (failure != null)
            return@withLock Outcome.Failed(
                Failure.Runtime(RuntimeProblem.RecoveryUnavailable, failure.details)
            )
        val recorded = reconcileRecovery(target)
        if (recorded is Outcome.Failed) return@withLock recorded
        publish()
        Outcome.Ok(true)
    }

    /** A clean exit/update restores wanted service without calling it a crash-restart act. */
    suspend fun restoreAfterCleanExit(profileId: String): Outcome<Unit> = lane.withLock {
        val target = target() ?: return@withLock Outcome.Ok(Unit)
        if (
            target.profile.raw != profileId ||
                !foreground ||
                profileId in closedProfiles ||
                !recovery.policyAllows(profileId)
        )
            return@withLock Outcome.Ok(Unit)
        val bound = recovery.bind(target.profile)
        if (bound is Outcome.Failed) return@withLock bound
        val status =
            (runtimeStatus() as? Outcome.Ok)?.value
                ?: return@withLock Outcome.Failed(
                    Failure.Runtime(RuntimeProblem.RecoveryUnavailable)
                )
        val generation = status.serverRecoveryGeneration
        if (
            !status.installed ||
                !status.serverRestartWanted ||
                status.idleBlocksLaunch ||
                status.serverRunning ||
                status.serverRecoveryScheduled ||
                generation == null
        )
            return@withLock Outcome.Ok(Unit)
        val epoch = actionEpoch
        val failure =
            starter.start(
                target,
                LaunchMode.ForAppOpen(generation),
                stillWanted = {
                    foreground &&
                        epoch == actionEpoch &&
                        profileId !in closedProfiles &&
                        recovery.policyAllows(profileId)
                },
            )
        publish()
        if (failure == null) Outcome.Ok(Unit)
        else Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable, failure.details))
    }

    /** One foreground check; the native generation and retry budget own every restart. */
    internal suspend fun foregroundHealth(): Outcome<Boolean> {
        val owner = healingOwner() ?: return Outcome.Ok(false)
        if (!foreground || !readable(owner)) return Outcome.Ok(false)
        var status =
            (runtimeStatus() as? Outcome.Ok)?.value
                ?: return Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        if (status.serverIdlePolicySupported) {
            if (!status.serverIdleReceiptValid)
                return Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            if (status.serverIdleStopped || status.serverIdleHelperStopped) {
                when (val resumed = idleResume()) {
                    is Outcome.Failed -> return resumed
                    is Outcome.Ok -> status = resumed.value
                }
            }
        }
        if (!foreground || !readable(owner) || healingOwner() != owner) return Outcome.Ok(false)
        if (!openStartDone && !idleBudgetHeld) {
            if (openStart()) openStartDone = true
            status = (runtimeStatus() as? Outcome.Ok)?.value ?: return Outcome.Ok(false)
        }
        if (
            !status.serverRunning &&
                status.serverRestartWanted &&
                !status.serverRecoveryScheduled &&
                !status.idleBlocks
        ) {
            when (val recovered = recoverAfterExit(owner.raw)) {
                is Outcome.Failed -> return recovered
                is Outcome.Ok -> Unit
            }
        }
        refreshNow()
        val target = target() ?: return Outcome.Ok(false)
        if (!foreground || !readable(owner) || target.profile != owner) return Outcome.Ok(false)
        status = (runtimeStatus() as? Outcome.Ok)?.value ?: return Outcome.Ok(false)
        return Outcome.Ok(
            status.serverRunning &&
                !status.idleBlocks &&
                port.probe(target.password) == ProbeAnswer.Answered
        )
    }

    private suspend fun refreshNow() = lane.withLock {
        val target = target()
        if (target != null && target.profile.raw !in closedProfiles) reconcileRecovery(target)
        publish()
    }

    /**
     * Historical confirmations drain even after Stop; a fresh health proof never dispatches a
     * second restart.
     */
    private suspend fun reconcileRecovery(target: StartTarget): Outcome<Unit> {
        val id = target.profile.raw
        if (id in closedProfiles)
            return Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
        val bound = recovery.bind(target.profile)
        if (bound is Outcome.Failed) return bound
        val archived =
            when (val read = blocking { linux.serverRecoveryReceipts(id) }) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        for (receipt in archived) {
            val event =
                receipt["eventId"] as? String
                    ?: return Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            val at =
                (receipt["confirmedAt"] as? Number)?.toLong()
                    ?: return Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            val saved = acts.recordRestart(id, event, at)
            if (saved is Outcome.Failed) return saved
            val ack = mutation { linux.ackServerRecoveryReceipt(id, event) }
            if (ack is Outcome.Failed) return ack
        }
        var freshInverse: (suspend () -> Boolean)? = null
        var budget =
            when (val read = blocking { linux.serverRecoveryBudget(id) }) {
                is Outcome.Failed -> return read
                is Outcome.Ok -> read.value
            }
        var status =
            (runtimeStatus() as? Outcome.Ok)?.value
                ?: return Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
        if (
            budget["pending"] == true &&
                budget["confirmedAt"] == null &&
                foreground &&
                recovery.policyAllows(id) &&
                status.serverRunning &&
                status.serverRestartWanted &&
                !status.idleBlocks &&
                id !in closedProfiles &&
                status.serverRecoveryGeneration ==
                    (budget["recoveryGeneration"] as? Number)?.toLong()
        ) {
            if (port.probe(target.password) == ProbeAnswer.Answered) {
                status =
                    (runtimeStatus() as? Outcome.Ok)?.value
                        ?: return Outcome.Failed(
                            Failure.Runtime(RuntimeProblem.RecoveryUnavailable)
                        )
                if (
                    foreground &&
                        recovery.policyAllows(id) &&
                        status.serverRunning &&
                        status.serverRestartWanted &&
                        !status.idleBlocks &&
                        id !in closedProfiles
                ) {
                    val generation =
                        (budget["recoveryGeneration"] as? Number)?.toLong()
                            ?: return Outcome.Failed(
                                Failure.Runtime(RuntimeProblem.RecoveryUnavailable)
                            )
                    val confirmed = mutation { linux.confirmServerRecovery(generation) }
                    if (confirmed is Outcome.Failed) return confirmed
                    // Only this foreground confirmation may grant a process-local inverse.
                    // Archived receipts and confirmations restored after restart never do.
                    if (
                        (blocking { linux.canUndoConfirmedServerRecovery(id, generation) }
                                as? Outcome.Ok)
                            ?.value == true
                    ) {
                        freshInverse = {
                            val stopped =
                                withContext(io) {
                                    linux.undoConfirmedServerRecovery(id, generation)
                                }
                            if (stopped) {
                                actionEpoch++
                                statusRevision.incrementAndGet()
                                scope.launch { refreshNow() }
                            }
                            stopped
                        }
                    }
                    when (
                        val saved = mutation {
                            linux.updateServerRecoveryReceipt(
                                id,
                                budget + ("confirmedAt" to clock.now().toEpochMilliseconds()),
                            )
                        }
                    ) {
                        is Outcome.Failed -> return saved
                        is Outcome.Ok -> budget = saved.value
                    }
                }
            }
        }
        val confirmedAt = (budget["confirmedAt"] as? Number)?.toLong()
        if (confirmedAt != null) {
            val event =
                budget["eventId"] as? String
                    ?: return Outcome.Failed(Failure.Runtime(RuntimeProblem.RecoveryUnavailable))
            val saved = acts.recordRestart(id, event, confirmedAt, undo = freshInverse)
            if (saved is Outcome.Failed) return saved
            val finished =
                budget +
                    mapOf(
                        "pending" to false,
                        "eventId" to null,
                        "recoveryGeneration" to null,
                        "confirmedAt" to null,
                    )
            when (val cleared = mutation { linux.updateServerRecoveryReceipt(id, finished) }) {
                is Outcome.Failed -> return cleared
                is Outcome.Ok -> budget = cleared.value
            }
        }
        if (
            foreground &&
                !presenceResetThisVisit &&
                !idleBudgetHeld &&
                (budget["attempts"] as? Number)?.toInt() != 0 &&
                budget["pending"] == false &&
                status.serverRunning &&
                status.serverRestartWanted &&
                !status.idleBlocks &&
                id !in closedProfiles &&
                recovery.policyAllows(id) &&
                port.probe(target.password) == ProbeAnswer.Answered
        ) {
            val reset = mutation { linux.confirmServerPresence(id) }
            if (reset is Outcome.Ok && (reset.value["attempts"] as? Number)?.toInt() == 0)
                presenceResetThisVisit = true
        }
        facts.update {
            it.copy(
                recovery =
                    when {
                        status.serverRunning -> RecoveryFacts.None
                        (budget["attempts"] as? Number)?.toInt() == 3 -> RecoveryFacts.Exhausted
                        status.serverRecoveryScheduled ->
                            RecoveryFacts.Waiting(
                                ((budget["attempts"] as? Number)?.toInt() ?: 0)
                                    .plus(1)
                                    .coerceAtMost(3),
                                (budget["nextAt"] as? Number)
                                    ?.toLong()
                                    ?.let(kotlin.time.Instant::fromEpochMilliseconds),
                            )
                        else -> RecoveryFacts.None
                    }
            )
        }
        return Outcome.Ok(Unit)
    }

    /** Deletion fences starts before waiting for the runtime lane. */
    fun stopProfileAdmission(profile: String) {
        closedProfiles.add(profile)
        if (ownerId()?.raw == profile) actionEpoch++
        linux.unbindServerRecovery(profile)
    }

    suspend fun drainProfile(profile: String): Outcome<Unit> = lane.withLock {
        // Revocation is reversible: keep the native budget, recipe and saved policy until the row commits.
        blocking { linux.unbindServerRecovery(profile) }
    }

    suspend fun eraseProfileRecovery(profile: String): Outcome<Unit> = lane.withLock {
        blocking { linux.deleteServerRecovery(profile) }
    }

    fun reopenProfile(profile: String) {
        closedProfiles.remove(profile)
        if (ownerId()?.raw == profile) scope.launch {
            lane.withLock {
                if (profile !in closedProfiles && stores.profiles.profile(ProfileId(profile)) != null)
                    recovery.bind(ProfileId(profile))
            }
        }
    }

    private suspend fun stopNow(): Outcome<Unit> {
        // Withdraw a suspended probe before waiting for the lane; persistence and process drain
        // stay on IO.
        actionEpoch++
        val revoked = mutation { linux.requestServerStop() }
        if (revoked is Outcome.Failed) return revoked
        return lane.withLock {
            val stopped = mutation { linux.stopServer() }
            publish()
            stopped
        }
    }

    /**
     * BUILD-RUNTIME-B: the setup job's finish step starts or re-reads the server through the one
     * lane.
     */
    suspend fun startServer(): Outcome<Unit> = startNow()

    suspend fun refreshServer() = refreshNow()

    fun serverSource(update: suspend () -> Outcome<Unit>): PhoneServerSource =
        object : PhoneServerSource {
            override val facts: StateFlow<PhoneServerFacts> =
                this@BuiltinRuntime.facts.asStateFlow()

            override suspend fun refresh() = refreshNow()

            override suspend fun start(): Outcome<Unit> = startNow()

            override suspend fun stop(): Outcome<Unit> = stopNow()

            override suspend fun restart(): Outcome<Unit> =
                when (val stopped = stopNow()) {
                    is Outcome.Failed -> stopped
                    is Outcome.Ok -> startNow()
                }

            override suspend fun updateOpenCode(): Outcome<Unit> = update()
        }

    init {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) = resumed(true)

                override fun onActivityPaused(activity: Activity) = resumed(false)

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) =
                    Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) =
                    Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
        scope.launch { publish() }
    }

    @Volatile private var openStartDone = false

    /**
     * Main thread, never waits for runtime I/O (BuiltinLinux's contract); only when Linux is set
     * up.
     */
    private fun resumed(on: Boolean) {
        if (on && !foreground) presenceResetThisVisit = idleBudgetHeld
        foreground = on
        if (!readyMarkerPresent()) return
        try {
            linux.setActivityResumed(on)
        } catch (e: Exception) {
            // The runtime refused to follow the activity; its own recovery stays off. Nothing to
            // tell the person.
        }
        if (on)
            scope.launch {
                if (!openStartDone) {
                    val state = (runtimeStatus() as? Outcome.Ok)?.value
                    if (
                        state?.serverIdlePolicySupported == true &&
                            (!state.serverIdleReceiptValid ||
                                state.serverIdleStopped ||
                                state.serverIdleHelperStopped)
                    ) {
                        if (state.serverIdleReceiptValid && idleResume() is Outcome.Ok)
                            openStartDone = true
                    } else if (openStart()) openStartDone = true
                } else refreshNow()
            }
    }

    private fun readyMarkerPresent(): Boolean = BuiltinDiscovery.readyMarker(filesDir).isFile

    /**
     * The app-open start (Flutter `AppOpenStart`, simplified): when Linux is set up, a phone-server
     * profile exists, the person did not stop the server and it is not running, start it once
     * through the one start path. True when this app open is decided (started, or nothing to do).
     */
    private suspend fun openStart(): Boolean = lane.withLock {
        publish()
        if (withContext(io) { BuiltinDiscovery.presence(filesDir) } != LinuxPresence.Ready)
            return@withLock true
        val target = target() ?: return@withLock false
        if (target.profile.raw in closedProfiles) return@withLock true
        val bound = recovery.bind(target.profile)
        if (bound is Outcome.Failed) return@withLock true
        if (target.profile.raw in closedProfiles) return@withLock true
        reconcileRecovery(target)
        val result = appOpen.run(target)
        if (result.starts > 0 && result.failure == null && result.leave == null) {
            mutation { linux.confirmManualServerStart(target.profile.raw) }
        }
        if (result.failure != null) facts.update { it.copy(startProblem = result.failure?.problem) }
        publish()
        true
    }
}

/*
 * P12-FIX-LINUX-VOICE: the REAL offline speech engine (the migration proof's ship blocker #3). sherpa-onnx 2.0.1 — the
 * version the Flutter app pins — through :android:media's SherpaOnnx, adapted to :android:voice's SherpaRuntime port.
 * It reads the speech models the Flutter app downloaded, in place (files/voice_models), and the total-RAM gate stays in
 * :engine:voice (ModelPacks / VoiceController read ActivityManager.MemoryInfo.totalMem only). Replaces VoiceWiring's
 * former NoOfflineSpeechEngine.
 */
object OfflineSpeechEngine : SherpaRuntime {
    /**
     * The JNI library loaded on this device (false only on a broken install or an ABI the APK
     * lacks).
     */
    val available: Boolean
        get() = SherpaOnnx.loadable

    override fun newRecognizer(config: WhisperRecognizerConfig): NativeRecognizer {
        val whisper =
            SherpaOnnx.whisper(
                encoder = config.encoder,
                decoder = config.decoder,
                tokens = config.tokens,
                language = config.language,
                task = config.task,
                numThreads = config.numThreads,
                debug = config.debug,
                provider = config.provider,
                modelType = config.modelType,
            )
        return object : NativeRecognizer {
            override fun decode(samples: FloatArray, sampleRate: Int): String =
                whisper.decode(samples, sampleRate)

            override fun release() = whisper.release()
        }
    }

    override fun newTts(config: KokoroTtsConfig): NativeTts {
        val kokoro =
            SherpaOnnx.kokoro(
                model = config.model,
                voices = config.voices,
                tokens = config.tokens,
                lexicon = config.lexicon,
                lang = config.lang,
                numThreads = config.numThreads,
                provider = config.provider,
                maxNumSentences = config.maxNumSentences,
            )
        return object : NativeTts {
            override val sampleRate: Int
                get() = kokoro.sampleRate

            override fun generate(text: String, speakerId: Int, speed: Float, sink: KokoroSink) =
                kokoro.generate(text, speakerId, speed, sink)

            override fun release() = kokoro.release()
        }
    }
}

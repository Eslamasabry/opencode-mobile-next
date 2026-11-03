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
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.engine.profiles.ProfileStore
import io.github.eslamasabry.oc.engine.profiles.SecretEdit
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.server.LaunchMode
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerPort
import io.github.eslamasabry.oc.engine.runtime.server.PhoneServerStarter
import io.github.eslamasabry.oc.engine.runtime.server.ProbeAnswer
import io.github.eslamasabry.oc.engine.runtime.server.StartTarget
import io.github.eslamasabry.oc.feature.linux.host.LinuxHost
import io.github.eslamasabry.oc.feature.linux.host.LinuxPresence
import io.github.eslamasabry.oc.feature.linux.host.ShellRun
import io.github.eslamasabry.oc.feature.linux.presentation.LinuxLifecycle
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerFacts
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneServerSource
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import io.github.eslamasabry.opencode_mobile.SetupRunner
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    fun builtinRuntime(app: Application, dispatchers: AppDispatchers, appScope: AppCoroutineScope, profiles: ProfileStore, stores: PreviewStores, clock: Clock): BuiltinRuntime =
        BuiltinRuntime(app, dispatchers.io, appScope, profiles, stores, clock)

    @Provides fun linuxHost(runtime: BuiltinRuntime): LinuxHost = runtime.host

    @Provides fun linuxLifecycle(runtime: BuiltinRuntime): LinuxLifecycle = runtime.lifecycle

    @Provides fun phoneServerSource(runtime: BuiltinRuntime): PhoneServerSource = runtime.server
}

/** Started with the app (OcApplication touches it), so the phone server comes back when the app opens. */
@ContributesTo(AppScope::class)
interface LinuxRuntimeGraph {
    val builtinRuntime: BuiltinRuntime
}

/** Where the Flutter app (and this one, same package) keeps the built-in Linux. Pure: tested on the JVM. */
internal object BuiltinDiscovery {
    fun home(filesDir: File): File = File(filesDir, "linux")

    fun rootfs(filesDir: File): File = File(home(filesDir), "ubuntu")

    /** `BuiltinLinux.installed`: the marker setup writes last. */
    fun readyMarker(filesDir: File): File = File(home(filesDir), "ubuntu.ready")

    /** `/root/projects` inside Ubuntu (BuiltinLinux.projectsDir). */
    fun projects(filesDir: File): File = File(rootfs(filesDir), "root/projects")

    fun presence(filesDir: File): LinuxPresence =
        if (readyMarker(filesDir).isFile && rootfs(filesDir).isDirectory) LinuxPresence.Ready else LinuxPresence.NotInstalled

    /** setup.json (SetupRunner) says a job runs right now. */
    fun setupRunning(statusJson: String?): Boolean =
        statusJson != null && Regex("\"state\"\\s*:\\s*\"running\"").containsMatchIn(statusJson)
}

/** The phone server's scripts, verbatim ports of lib/builtin/builtin_linux.dart. Pure: tested on the JVM. */
internal object PhoneServerScripts {
    const val PORT = 4097
    const val USERNAME = "opencode"
    const val PROJECTS_DIR = "/root/projects"
    const val PASSWORD_FILE = "/root/.oc-builtin/server.password"

    /** Flutter `renewBuiltinPassword`: 32 random bytes, base64url, padding removed. */
    fun newPassword(random: java.util.Random): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    /** Flutter `BuiltinLinux._quote`. */
    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"

    /** Flutter `BuiltinLinux.writePasswordScript`: the password never reaches a command line or a log. */
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
     * Flutter `BuiltinLinux.serverScript(runtime = opencode2)`: started in /root/projects, bound to 127.0.0.1 only,
     * Basic auth `opencode` + the password read from the root-only file, OpenCode 2's isolated XDG folders, and the
     * OpenCode 1 history copy before the first start. Deviation: the agent-context block (`PhoneAgentContext`), which
     * Flutter runs with `|| true` and which never blocks the start, is left out; the AGENTS.md it wrote stays.
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
            "password=\$(cat $PASSWORD_FILE)\n" +
            "export OPENCODE_SERVER_USERNAME=$USERNAME\n" +
            "export OPENCODE_SERVER_PASSWORD=\"\$password\"\n" +
            "export OPENCODE_PASSWORD=\"\$password\"\n" +
            "unset password\n" +
            "exec opencode2 serve --hostname 127.0.0.1 --port $port\n"
    }

    /** lib/domain/opencode_history_copy.dart `openCode1HistoryCopyScript`, verbatim (provenance + copy). */
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

    /** lib/domain/server_probe.dart: a 2xx answered with our password, a 401 refused it, anything else is no answer. */
    fun answer(status: Int?): ProbeAnswer =
        when {
            status == null -> ProbeAnswer.NoAnswer
            status in 200..299 -> ProbeAnswer.Answered
            status == 401 -> ProbeAnswer.NeedsPassword
            else -> ProbeAnswer.NoAnswer
        }

    /** The routes the probe tries in order (beta `/api/health`, stable `/api/info`, OpenCode 1 `/global/health`). */
    val PROBE_PATHS: List<String> = listOf("/api/health", "/api/info", "/global/health")
}

/** Runtime exception → the flat Failure the features read (P12-LINUX report §5 item 5). Text classifies only. */
internal fun classifyRuntime(e: Throwable): Failure {
    val message = e.message.orEmpty()
    val problem =
        when {
            message == "ownershipUnknown" || message == "ownership_unknown" -> RuntimeProblem.OwnershipUnknown
            message.startsWith("Another phone task") -> RuntimeProblem.SlotBusy
            message.startsWith("Ubuntu is not installed") -> RuntimeProblem.NotInstalled
            message.startsWith("Finish or cancel setup") -> RuntimeProblem.Installing
            message.startsWith("The phone server could not restart") -> RuntimeProblem.RecoveryUnavailable
            else -> RuntimeProblem.Generic
        }
    return Failure.Runtime(problem, Redacted.shape(e))
}

/** The status the phone pages read, from BuiltinLinux's live values. Pure: tested on the JVM. */
internal fun runtimeStatusOf(installed: Boolean, installing: Boolean, serverRunning: Boolean, port: Int?, uptimeMs: Long?, services: List<String>): RuntimeStatus =
    RuntimeStatus(
        installed = installed,
        phase = if (installing) RuntimePhase.Installing else if (installed) RuntimePhase.Ready else RuntimePhase.Idle,
        serverRunning = serverRunning,
        serverPort = if (serverRunning) port else null,
        serverUptime = if (serverRunning) uptimeMs?.milliseconds else null,
        services = services,
    )

/**
 * The one owner of the built-in Linux in the app. [host], [lifecycle] and [server] are the seams :feature:linux and
 * :feature:phone read. Starts by following the activity (BuiltinLinux.setActivityResumed, as Flutter's MainActivity
 * did) and, once, by bringing the phone server back when it was running and the person had not stopped it.
 */
class BuiltinRuntime internal constructor(
    private val app: Application,
    private val io: CoroutineDispatcher,
    private val scope: AppCoroutineScope,
    private val profiles: ProfileStore,
    private val stores: PreviewStores,
    clock: Clock,
) {
    private val linux: BuiltinLinux by lazy { BuiltinLinux.get(app) }
    private val filesDir: File get() = app.filesDir
    private val lane = Mutex()

    private suspend fun <T> blocking(block: () -> T): Outcome<T> =
        withContext(io) {
            try {
                Outcome.Ok(block())
            } catch (e: Exception) {
                Outcome.Failed(classifyRuntime(e))
            }
        }

    // ---- LinuxHost

    val host: LinuxHost =
        object : LinuxHost {
            override suspend fun presence(): LinuxPresence = withContext(io) { BuiltinDiscovery.presence(filesDir) }

            override suspend fun run(script: String, timeoutSeconds: Long): Outcome<ShellRun> =
                blocking { linux.run(script, timeoutSeconds).let { ShellRun(it.exitCode, it.output) } }

            override suspend fun setupRunning(): Boolean =
                withContext(io) {
                    try {
                        BuiltinDiscovery.setupRunning(SetupRunner.get(app).status())
                    } catch (e: Exception) {
                        false
                    }
                }

            override suspend fun serviceRunning(name: String): Boolean = withContext(io) { linux.serviceRunning(name) }

            override suspend fun startService(name: String, script: String, notice: String): Outcome<Unit> =
                blocking { linux.startService(name, script, null, notice) }

            override suspend fun stopService(name: String): Outcome<Unit> = blocking { linux.stopService(name) }

            override suspend fun serviceLog(name: String, tailBytes: Int): String =
                withContext(io) {
                    try {
                        linux.serviceLogTail(name, tailBytes)
                    } catch (e: Exception) {
                        ""
                    }
                }
        }

    // ---- LinuxLifecycle

    val lifecycle: LinuxLifecycle =
        object : LinuxLifecycle {
            override suspend fun installed(): Boolean? = withContext(io) { BuiltinDiscovery.presence(filesDir) == LinuxPresence.Ready }

            /** Projects stay (`uninstall(alsoDeleteProjects = false)`). */
            override suspend fun erase(): Outcome<Unit> = lane.withLock { blocking { linux.uninstall(alsoDeleteProjects = false) } }

            /**
             * Phone setup (download Ubuntu, install OpenCode) is the Flutter setup engine's job, which is not ported to
             * this app yet: an existing install is used as it is, and a phone without one is told plainly.
             */
            override suspend fun startSetup(): Outcome<Boolean> =
                if (installed() == true) Outcome.Ok(true) else Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))

            override suspend fun repair(): Outcome<Unit> = Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable))
        }

    // ---- The phone server

    private val port =
        object : PhoneServerPort {
            override suspend fun checkHistoryMove(): Outcome<Unit> = Outcome.Ok(Unit)

            override suspend fun writePassword(password: String): Outcome<Unit> =
                when (val ran = blocking { linux.run(PhoneServerScripts.writePassword(password), 60) }) {
                    is Outcome.Failed -> ran
                    is Outcome.Ok ->
                        if (ran.value.exitCode == 0) Outcome.Ok(Unit) else Outcome.Failed(Failure.Runtime(RuntimeProblem.Generic))
                }

            override suspend fun launch(profile: ProfileId, mode: LaunchMode): Outcome<Unit> =
                blocking { linux.startServer(PhoneServerScripts.server(), PhoneServerScripts.PORT) }

            override suspend fun probe(password: String): ProbeAnswer = withContext(io) { probeLoopback(password) }

            override suspend fun refreshStatus(): Outcome<RuntimeStatus> = blocking { readStatus() }

            override suspend fun confirmRecovery(expectedGeneration: Long): Outcome<Unit> = Outcome.Ok(Unit)
        }

    private val starter = PhoneServerStarter(port, clock, renewPassword = ::renewPassword)

    /**
     * B-LINUX-025: the phone server's password is made by the app, so when the Keystore lost it (a fresh install, or
     * the migration's `renewedByOwner`) a new one is made — 32 random bytes, base64url without padding, as Flutter's
     * `renewBuiltinPassword` — and saved under `pw.<id>` on the same profile before the start writes it into Ubuntu.
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

    private fun readStatus(): RuntimeStatus {
        val installed = BuiltinDiscovery.presence(filesDir) == LinuxPresence.Ready
        if (!installed) return runtimeStatusOf(false, false, false, null, null, emptyList())
        return runtimeStatusOf(
            installed = true,
            installing = BuiltinDiscovery.setupRunning(runCatchingBlocking { SetupRunner.get(app).status() }),
            serverRunning = linux.serverRunning,
            port = linux.port,
            uptimeMs = linux.serverUptimeMs,
            services = linux.runningServices(),
        )
    }

    private inline fun <T> runCatchingBlocking(block: () -> T): T? =
        try {
            block()
        } catch (e: Exception) {
            null
        }

    /** The person's Stop, as BuiltinLinux keeps it (device-wide `builtin_server_recovery` userStopped). */
    private fun stoppedByPerson(): Boolean =
        app.getSharedPreferences("builtin_server_recovery", Context.MODE_PRIVATE).let { prefs ->
            prefs.getBoolean("userStopped", !prefs.getBoolean("wanted", false))
        }

    private fun probeLoopback(password: String): ProbeAnswer {
        val auth = "Basic " + Base64.getEncoder().encodeToString("${PhoneServerScripts.USERNAME}:$password".toByteArray())
        for (path in PhoneServerScripts.PROBE_PATHS) {
            val status =
                try {
                    val conn = URL("http", "127.0.0.1", PhoneServerScripts.PORT, path).openConnection() as HttpURLConnection
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

    /** The phone server's profile: `oc.builtinServerOwner` (written by the Flutter app, kept by the migration). */
    private fun ownerId(): ProfileId? =
        runCatchingBlocking { (stores.prefs.get("oc.builtinServerOwner") as? PrefValue.Str)?.value }
            ?.takeIf { it.isNotEmpty() }
            ?.let(::ProfileId)

    private suspend fun target(): StartTarget? {
        val id = ownerId() ?: return null
        val loaded = profiles.profile(id) ?: (profiles.load() as? Outcome.Ok)?.value?.firstOrNull { it.id == id } ?: return null
        val password = (profiles.secret(loaded.id) as? Outcome.Ok)?.value.orEmpty()
        return StartTarget(loaded.id, password, passwordNeedsReentry = loaded.reentry.password)
    }

    private suspend fun publish() {
        val status = (blocking { readStatus() } as? Outcome.Ok)?.value
        val stopped = withContext(io) { runCatchingBlocking { stoppedByPerson() } ?: false }
        facts.update { it.copy(status = status, stoppedByPerson = stopped) }
    }

    private suspend fun startNow(): Outcome<Unit> =
        lane.withLock {
            if (BuiltinDiscovery.presence(filesDir) != LinuxPresence.Ready) return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
            val target = target() ?: return@withLock Outcome.Failed(Failure.Runtime(RuntimeProblem.InvalidProfile))
            facts.update { it.copy(starting = true, startProblem = null) }
            val failure = starter.start(target)
            facts.update { it.copy(starting = false, startProblem = failure?.problem, probe = if (failure == null) ProbeAnswer.Answered else it.probe) }
            publish()
            if (failure == null) Outcome.Ok(Unit) else Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable, failure.details))
        }

    private suspend fun stopNow(): Outcome<Unit> =
        lane.withLock {
            val stopped =
                blocking {
                    linux.requestServerStop()
                    linux.stopServer()
                }
            publish()
            stopped
        }

    val server: PhoneServerSource =
        object : PhoneServerSource {
            override val facts: StateFlow<PhoneServerFacts> = this@BuiltinRuntime.facts.asStateFlow()

            override suspend fun refresh() = publish()

            override suspend fun start(): Outcome<Unit> = startNow()

            override suspend fun stop(): Outcome<Unit> = stopNow()

            override suspend fun restart(): Outcome<Unit> =
                when (val stopped = stopNow()) {
                    is Outcome.Failed -> stopped
                    is Outcome.Ok -> startNow()
                }

            /** The setup engine's "Update OpenCode" job is not ported yet (same reason as setup). */
            override suspend fun updateOpenCode(): Outcome<Unit> = Outcome.Failed(Failure.Runtime(RuntimeProblem.ServerStartUnavailable))
        }

    init {
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) = resumed(true)

                override fun onActivityPaused(activity: Activity) = resumed(false)

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
        scope.launch { publish() }
    }

    @Volatile private var openStartDone = false

    /** Main thread, never waits for runtime I/O (BuiltinLinux's contract); only when Linux is set up. */
    private fun resumed(on: Boolean) {
        if (!readyMarkerPresent()) return
        try {
            linux.setActivityResumed(on)
        } catch (e: Exception) {
            // The runtime refused to follow the activity; its own recovery stays off. Nothing to tell the person.
        }
        if (on && !openStartDone) scope.launch { if (openStart()) openStartDone = true }
    }

    private fun readyMarkerPresent(): Boolean = BuiltinDiscovery.readyMarker(filesDir).isFile

    /**
     * The app-open start (Flutter `AppOpenStart`, simplified): when Linux is set up, a phone-server profile exists, the
     * person did not stop the server and it is not running, start it once through the one start path. True when this
     * app open is decided (started, or nothing to do).
     */
    private suspend fun openStart(): Boolean {
        publish()
        if (withContext(io) { BuiltinDiscovery.presence(filesDir) } != LinuxPresence.Ready) return true
        val current = facts.value
        if (current.status?.serverRunning == true || current.stoppedByPerson) return true
        // Before the migration gate opens the profile list cannot be read yet: try again on the next resume.
        if (ownerId() == null) return false
        startNow()
        return true
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
    /** The JNI library loaded on this device (false only on a broken install or an ABI the APK lacks). */
    val available: Boolean get() = SherpaOnnx.loadable

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
            override fun decode(samples: FloatArray, sampleRate: Int): String = whisper.decode(samples, sampleRate)

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
            override val sampleRate: Int get() = kokoro.sampleRate

            override fun generate(text: String, speakerId: Int, speed: Float, sink: KokoroSink) =
                kokoro.generate(text, speakerId, speed, sink)

            override fun release() = kokoro.release()
        }
    }
}

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RuntimeProblem
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.domain.platform.DeviceFacts
import io.github.eslamasabry.oc.core.domain.platform.ThermalLevel
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.runtime.heat.ServicesHeatHold
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalPolicy
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalReading
import io.github.eslamasabry.oc.engine.runtime.heat.ThermalStatus
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptAct
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptPhase
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptService
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServiceDef
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServiceSupervisor
import io.github.eslamasabry.oc.engine.runtime.services.kept.KeptServices
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The same device-wide definitions, wants and supervisor used by the Flutter app. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidKeptServices(
    private val app: Application,
    dispatchers: AppDispatchers,
    private val scope: AppCoroutineScope,
    private val clock: Clock,
    private val redactor: Redactor,
    private val stores: PreviewStores,
    private val facts: DeviceFacts,
) : KeptServices {
    private val lane = dispatchers.serial("linux-kept-services")
    private val commands = Mutex()
    private val linux
        get() = BuiltinLinux.get(app)

    init {
        // Reuse the platform's one thermal observation. A cool transition gets one delayed
        // confirmation; hot readings or a setting change cancel that pending wake.
        scope.launch {
            if (stores.warm() is Outcome.Failed) return@launch
            val guard =
                ServicesHeatHold(
                    readHold = {
                        (command { path("${KeptServiceSupervisor.HOME}/hold").exists() }
                                as? Outcome.Ok)
                            ?.value
                    },
                    setHold = { held ->
                        command {
                            if (held) write("${KeptServiceSupervisor.HOME}/hold", "critical\n")
                            else {
                                delete("${KeptServiceSupervisor.HOME}/hold")
                                ensure()
                            }
                        }
                    },
                    enabled = {
                        (stores.prefs.get("oc.thermalGuard") as? PrefValue.Bool)?.value ?: true
                    },
                    clock = clock,
                )
            combine(facts.thermal, stores.prefs.changes.onStart { emit(emptySet()) }) { thermal, _
                    ->
                    thermal
                }
                .collectLatest { level ->
                    val status =
                        when (level) {
                            ThermalLevel.None -> ThermalStatus.None
                            ThermalLevel.Light -> ThermalStatus.Light
                            ThermalLevel.Moderate -> ThermalStatus.Moderate
                            ThermalLevel.Severe -> ThermalStatus.Severe
                            ThermalLevel.Critical -> ThermalStatus.Critical
                            ThermalLevel.Emergency -> ThermalStatus.Emergency
                            ThermalLevel.Shutdown -> ThermalStatus.Shutdown
                        }
                    val reading = ThermalReading(status)
                    guard.observe(reading)
                    if (guard.held && ThermalPolicy.cool(reading)) {
                        delay(guard.coolFor.inWholeMilliseconds)
                        guard.observe(reading)
                    }
                }
        }
    }

    /** Admitted writes finish independently of the page's lifetime, in one serial lane. */
    private suspend fun <T> command(body: suspend () -> T): Outcome<T> =
        scope
            .async(lane) {
                commands.withLock {
                    try {
                        Outcome.Ok(body())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: MissingLinux) {
                        Outcome.Failed(Failure.Runtime(RuntimeProblem.NotInstalled))
                    } catch (_: Exception) {
                        Outcome.Failed(Failure.StorageRefused())
                    }
                }
            }
            .await()

    private fun root(): File {
        check(!Files.isSymbolicLink(BuiltinDiscovery.home(app.filesDir).toPath()))
        val root = BuiltinDiscovery.rootfs(app.filesDir)
        if (!BuiltinDiscovery.readyMarker(app.filesDir).isFile || !root.isDirectory)
            throw MissingLinux()
        check(!Files.isSymbolicLink(root.toPath()))
        return root
    }

    /** Refuse every guest symlink and any non-directory ancestor, including absent roots. */
    private fun path(relative: String, createParents: Boolean = false): File {
        require(relative.startsWith('/') && relative.split('/').none { it == ".." })
        var file = root()
        val parts = relative.split('/').filter { it.isNotEmpty() }
        parts.forEachIndexed { index, part ->
            file = File(file, part)
            check(!Files.isSymbolicLink(file.toPath()))
            if (index < parts.lastIndex) {
                if (!Files.exists(file.toPath(), NOFOLLOW_LINKS)) {
                    if (createParents) check(file.mkdir())
                    else return File(file, parts.drop(index + 1).joinToString("/"))
                }
                check(Files.isDirectory(file.toPath(), NOFOLLOW_LINKS))
            }
        }
        return file
    }

    private fun readFile(relative: String, cap: Long = 65536): String? {
        val file = path(relative)
        if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) return null
        require(file.length() <= cap)
        return file.readText()
    }

    private fun write(relative: String, value: String) {
        val file = path(relative, true)
        val temp = File.createTempFile(".kept-", ".tmp", file.parentFile)
        try {
            temp.outputStream().use {
                it.write(value.toByteArray())
                it.fd.sync()
            }
            Files.move(temp.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
    }

    private fun delete(relative: String) {
        val file = path(relative)
        if (Files.exists(file.toPath(), NOFOLLOW_LINKS)) check(file.delete())
    }

    private fun definitions(): List<KeptServiceDef> {
        val directory = path("${KeptServiceSupervisor.HOME}/defs/placeholder").parentFile!!
        if (!directory.exists()) return emptyList()
        check(Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS))
        return requireNotNull(directory.listFiles())
            .filter { it.name.endsWith(".svc") && !Files.isSymbolicLink(it.toPath()) }
            .mapNotNull { file ->
                val name = file.name.removeSuffix(".svc")
                if (!KeptServiceDef.validName(name)) null
                else
                    readFile("${KeptServiceSupervisor.HOME}/defs/$name.svc")?.let {
                        KeptServiceDef.decode(name, it)
                    }
            }
    }

    private fun state(name: String): String? = readFile("${KeptServiceSupervisor.HOME}/state/$name")

    private fun wanted(name: String): Boolean =
        readFile("${KeptServiceSupervisor.HOME}/want/$name")?.trim() == "on"

    private fun rows(): List<KeptService> {
        val running = linux.serviceRunning(KeptServiceSupervisor.NATIVE_NAME)
        return definitions()
            .map { KeptService.combine(it, wanted(it.name), state(it.name), running) }
            .sortedWith(
                compareBy<KeptService> { it.urgency }.thenBy { it.definition.title.lowercase() }
            )
    }

    override suspend fun read(): Outcome<List<KeptService>> = command { rows() }

    override suspend fun save(definition: KeptServiceDef): Outcome<Unit> = command {
        val old = definitions()
        require(definition.invalid(old, true) == null)
        write("${KeptServiceSupervisor.HOME}/defs/${definition.name}.svc", definition.encode())
        if (old.none { it.name == definition.name })
            write("${KeptServiceSupervisor.HOME}/want/${definition.name}", "off\n")
    }

    private suspend fun ensure() {
        check(stores.warm() is Outcome.Ok)
        // Installation may finish without a new thermal event. Admit no freshly started
        // supervised process while Android already reports critical heat.
        if (
            facts.thermal.value >= ThermalLevel.Critical &&
                ((stores.prefs.get("oc.thermalGuard") as? PrefValue.Bool)?.value ?: true)
        ) {
            write("${KeptServiceSupervisor.HOME}/hold", "critical\n")
        }
        val scriptPath = "${KeptServiceSupervisor.HOME}/oc-svd"
        val changed = readFile(scriptPath) != KeptServiceSupervisor.script
        if (changed) write(scriptPath, KeptServiceSupervisor.script)
        val running = linux.serviceRunning(KeptServiceSupervisor.NATIVE_NAME)
        val anyOn = definitions().any { wanted(it.name) }
        if (running && !changed) return
        if (!anyOn) {
            if (running) retire()
            return
        }
        if (running) write("${KeptServiceSupervisor.HOME}/keep", "restart\n")
        try {
            linux.startService(
                KeptServiceSupervisor.NATIVE_NAME,
                KeptServiceSupervisor.LAUNCH,
                null,
                null,
            )
        } catch (e: Exception) {
            delete("${KeptServiceSupervisor.HOME}/keep")
            throw e
        }
    }

    override suspend fun ensureSupervisor(): Outcome<Unit> = command { ensure() }

    private suspend fun waitOff(name: String, seconds: Int) {
        repeat((seconds + 2) * 4) {
            if (!linux.serviceRunning(KeptServiceSupervisor.NATIVE_NAME)) return
            val phase = state(name)?.let(KeptServiceDef::fields)?.get("phase")
            if (phase in setOf("off", "failed", "exited", "backoff", "port_busy")) return
            delay(250)
        }
        // Unknown/still live: retain the definition and its ownership data.
        check(!linux.serviceRunning(KeptServiceSupervisor.NATIVE_NAME))
    }

    private suspend fun retire() {
        if (
            !linux.serviceRunning(KeptServiceSupervisor.NATIVE_NAME) ||
                definitions().any { wanted(it.name) }
        )
            return
        for (def in definitions()) waitOff(def.name, def.stopSeconds)
        if (definitions().none { wanted(it.name) })
            linux.stopService(KeptServiceSupervisor.NATIVE_NAME)
    }

    override suspend fun act(name: String, action: KeptAct): Outcome<Unit> = command {
        require(KeptServiceDef.validName(name))
        val def = requireNotNull(definitions().firstOrNull { it.name == name })
        val home = KeptServiceSupervisor.HOME
        when (action) {
            KeptAct.Start -> {
                val row = rows().first { it.definition.name == name }
                write("$home/want/$name", "on\n")
                if (row.phase !in setOf(KeptPhase.Running, KeptPhase.Stopping))
                    write("$home/kick/$name", "${clock.now().toEpochMilliseconds()}\n")
                ensure()
            }
            KeptAct.Restart -> {
                // Restart is shown only for an on service; stale actions cannot turn an off service
                // on.
                check(wanted(name))
                write("$home/kick/$name", "${clock.now().toEpochMilliseconds()}\n")
                ensure()
            }
            KeptAct.Stop,
            KeptAct.Remove -> {
                write("$home/want/$name", "off\n")
                waitOff(name, def.stopSeconds)
                if (action == KeptAct.Remove) {
                    for (relative in
                        listOf(
                            "$home/defs/$name.svc",
                            "$home/want/$name",
                            "$home/kick/$name",
                            "$home/state/$name",
                            "${KeptServiceSupervisor.LOGS}/$name.log",
                            "${KeptServiceSupervisor.LOGS}/$name.log.1",
                        )) delete(relative)
                }
                retire()
            }
        }
    }

    override suspend fun log(name: String): Outcome<String> = command {
        require(KeptServiceDef.validName(name))
        val limit = 65536
        fun tail(relative: String, cap: Int): ByteArray {
            if (cap <= 0) return byteArrayOf()
            val file = path(relative)
            if (!Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) return byteArrayOf()
            return RandomAccessFile(file, "r").use { input ->
                val start = (input.length() - cap).coerceAtLeast(0)
                input.seek(start)
                val bytes = ByteArray((input.length() - start).toInt())
                input.readFully(bytes)
                if (start > 0) bytes.dropWhile { it != '\n'.code.toByte() }.drop(1).toByteArray()
                else bytes
            }
        }
        val current = tail("${KeptServiceSupervisor.LOGS}/$name.log", limit)
        val old = tail("${KeptServiceSupervisor.LOGS}/$name.log.1", limit - current.size)
        redactor.redact((old + current).toString(Charsets.UTF_8))
    }

    override suspend fun copyLog(name: String): Outcome<Unit> =
        when (val read = log(name)) {
            is Outcome.Failed -> read
            is Outcome.Ok ->
                command {
                    val clipboard =
                        requireNotNull(app.getSystemService(ClipboardManager::class.java))
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            app.applicationInfo.loadLabel(app.packageManager),
                            read.value,
                        )
                    )
                }
        }

    private class MissingLinux : Exception()
}

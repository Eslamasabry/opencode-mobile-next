package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.engine.diagnostics.exit.LifecycleSource
import io.github.eslamasabry.oc.engine.runtime.host.RuntimePhase
import io.github.eslamasabry.oc.engine.runtime.host.RuntimeStatus
import io.github.eslamasabry.oc.engine.runtime.lifecycle.AppStorageFacts
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecyclePorts
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleReportController
import io.github.eslamasabry.oc.engine.runtime.lifecycle.LifecycleTarget
import io.github.eslamasabry.oc.engine.runtime.lifecycle.ProjectStorageFacts
import io.github.eslamasabry.oc.engine.runtime.recovery.LaunchFacts
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExit
import io.github.eslamasabry.oc.engine.runtime.recovery.PreviousExitKind
import io.github.eslamasabry.oc.feature.phone.presentation.PhoneStateDetails
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** B-STATE-025/027/028: real read-only observations, reachable on Settings › This phone. */
@SingleIn(AppScope::class)
@ContributesIntoSet(AppScope::class, binding = binding<PhoneStateDetails>())
@Inject
class AndroidPhoneStateDetails(
    private val application: Application,
    private val stores: PreviewStores,
    private val source: LifecycleSource,
    private val dispatchers: AppDispatchers,
    appScope: AppCoroutineScope,
) : PhoneStateDetails {
    private val linux by lazy { BuiltinLinux.get(application) }

    private fun selected() = stores.profiles.activeId.value?.let(stores.profiles::profile)

    private fun eligible(): Boolean {
        val loaded = selected() ?: return false
        if (stores.prefs.string("oc.builtinServerOwner") != loaded.id.raw) return false
        val address =
            try {
                URI(loaded.profile.baseUrl)
            } catch (_: Exception) {
                return false
            }
        return address.scheme == "http" &&
            address.host == "127.0.0.1" &&
            address.port == PhoneServerScripts.PORT
    }

    private fun target(): LifecycleTarget? {
        if (!eligible()) return null
        val loaded = selected() ?: return null
        val config =
            loaded.profile.orchestration?.encoded?.let { DartJson.decodeOrNull(it) as? Map<*, *> }
        val team = config?.get("provider") == "gascity" && config["url"] == "http://127.0.0.1:8472"
        return LifecycleTarget(
            DartJson.sha256Hex(
                loaded.id.raw +
                    ":" +
                    loaded.profile.baseUrl +
                    ":" +
                    loaded.profile.username +
                    ":" +
                    loaded.profile.orchestration?.encoded.orEmpty()
            ),
            team,
        )
    }

    private val controller =
        LifecycleReportController(
            object : LifecyclePorts {
                override fun target(): LifecycleTarget? = this@AndroidPhoneStateDetails.target()

                override suspend fun history(): Outcome<LaunchFacts> =
                    withContext(dispatchers.io) {
                        when (val report = source.launchReport()) {
                            is Outcome.Failed -> report
                            is Outcome.Ok ->
                                Outcome.Ok(
                                    LaunchFacts(
                                        exit =
                                            report.value.exit
                                                ?.takeIf { it.hasTime }
                                                ?.let {
                                                    PreviousExit(
                                                        PreviousExitKind.valueOf(it.kind.name),
                                                        it.at,
                                                    )
                                                },
                                        previousServices =
                                            report.value.previousServices.filterNotNull().toList(),
                                    )
                                )
                        }
                    }

                override suspend fun status(): Outcome<RuntimeStatus> =
                    withContext(dispatchers.io) {
                        attempt({ Failure.Device() }) {
                            val installed =
                                BuiltinDiscovery.presence(application.filesDir) ==
                                    io.github.eslamasabry.oc.feature.linux.host.LinuxPresence.Ready
                            RuntimeStatus(
                                installed = installed,
                                phase = if (installed) RuntimePhase.Ready else RuntimePhase.Idle,
                                serverRunning = installed && linux.serverRunning,
                                services = if (installed) linux.runningServices() else emptyList(),
                            )
                        }
                    }

                override suspend fun authenticatedHealth(
                    target: LifecycleTarget
                ): Outcome<Boolean> {
                    val profile = selected() ?: return Outcome.Ok(false)
                    if (
                        this@AndroidPhoneStateDetails.target() != target || profile.reentry.password
                    )
                        return Outcome.Ok(false)
                    val secret =
                        (stores.profiles.secret(profile.id) as? Outcome.Ok)?.value?.takeIf {
                            it.isNotEmpty()
                        } ?: return Outcome.Ok(false)
                    val username = profile.profile.username.ifEmpty { PhoneServerScripts.USERNAME }
                    return withContext(dispatchers.io) {
                        attempt({ Failure.Device() }) {
                            if (this@AndroidPhoneStateDetails.target() != target) false
                            else {
                                val authorization =
                                    "Basic " +
                                        Base64.getEncoder()
                                            .encodeToString(
                                                "$username:$secret".toByteArray(Charsets.UTF_8)
                                            )
                                var healthy = false
                                for (path in PhoneServerScripts.PROBE_PATHS) {
                                    val status =
                                        request(PhoneServerScripts.PORT, path, authorization)?.first
                                    if (status == 404) continue
                                    healthy = status != null && status in 200..299
                                    break
                                }
                                healthy
                            }
                        }
                    }
                }

                override suspend fun teamSupervisorHealth(): Outcome<Boolean> =
                    teamHealth("/health")

                override suspend fun teamCityHealth(): Outcome<Boolean> =
                    teamHealth("/v0/city/phone/health")
            }
        )
    override val recovery = controller.value

    init {
        appScope.launch {
            combine(stores.profiles.activeId, stores.profiles.profiles) { _, _ -> target() }
                .collect { controller.invalidate() }
        }
    }

    override fun invalidate() = controller.invalidate()

    override fun dismissRecovery() = controller.dismiss()

    override suspend fun refresh() {
        if (stores.warm() !is Outcome.Ok) {
            controller.invalidate()
            return
        }
        controller.refresh()
    }

    override suspend fun measureStorage(): Outcome<AppStorageFacts> =
        withContext(dispatchers.io) {
            attempt({ Failure.StorageRefused() }) {
                AppStorageScanner.scan(application.filesDir, stores.profiles.profiles.value.size)
            }
        }

    private suspend fun teamHealth(path: String): Outcome<Boolean> =
        withContext(dispatchers.io) {
            attempt({ Failure.Device() }) {
                val reply = request(8472, path) ?: return@attempt false
                reply.first in 200..299 &&
                    (DartJson.decodeOrNull(reply.second) as? Map<*, *>)?.get("status") == "ok"
            }
        }

    private fun request(
        port: Int,
        path: String,
        authorization: String? = null,
    ): Pair<Int, String>? {
        val connection = URL("http", "127.0.0.1", port, path).openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 4_000
        connection.instanceFollowRedirects = false
        authorization?.let { connection.setRequestProperty("Authorization", it) }
        return try {
            val status = connection.responseCode
            val body =
                if (authorization == null && status in 200..299)
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(16_385)
                        var count = 0
                        while (count < buffer.size) {
                            val read = input.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        if (count > 16_384) "" else String(buffer, 0, count, Charsets.UTF_8)
                    }
                else ""
            status to body
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * No content reads, links followed or path/credential logging. A scan is a transient measurement
 * only.
 */
internal object AppStorageScanner {
    private val noLinks = arrayOf(LinkOption.NOFOLLOW_LINKS)
    private val privateNames =
        setOf(
            "auth.json",
            ".gitconfig",
            ".git-credentials",
            ".netrc",
            "_netrc",
            ".npmrc",
            ".pypirc",
            ".pgpass",
            ".dockercfg",
            "credentials",
            "credentials.json",
            "id_rsa",
            "id_dsa",
            "id_ecdsa",
            "id_ed25519",
        )
    private val privateFolders = setOf(".ssh", ".gnupg", ".aws", ".docker")
    private val privateExtensions = setOf("pem", "key", "p12", "pfx", "jks", "keystore")
    private val templates = setOf(".env.example", ".env.sample", ".env.template", ".env.dist")

    private fun attributes(path: Path): BasicFileAttributes? =
        try {
            Files.readAttributes(path, BasicFileAttributes::class.java, *noLinks)
        } catch (_: java.nio.file.NoSuchFileException) {
            null
        }

    private fun walk(
        path: Path,
        relative: String,
        skipped: Set<String> = emptySet(),
        gitRoot: Path? = null,
        visit: (String, Long) -> Unit,
    ) {
        val info = attributes(path) ?: return
        if (info.isRegularFile) {
            visit(relative, info.size())
            return
        }
        if (info.isSymbolicLink && gitRoot != null) {
            linkedObject(path, gitRoot)?.let { target -> visit(relative, Files.size(target)) }
            return
        }
        if (!info.isDirectory) return
        Files.newDirectoryStream(path).use { children ->
            for (child in children) if (
                child.fileName.toString() !in skipped &&
                    (gitRoot == null || !child.fileName.toString().startsWith(".l2s."))
            )
                walk(child, relative + "/" + child.fileName, skipped, gitRoot, visit)
        }
    }

    private fun linkedObject(link: Path, root: Path): Path? {
        val top = root.toRealPath()
        var current = link
        repeat(8) {
            val target = Files.readSymbolicLink(current)
            val name = target.fileName?.toString() ?: return null
            if (!name.startsWith(".l2s.")) return null
            var next = if (target.isAbsolute) target else current.parent.resolve(target)
            if (attributes(next) == null) next = current.parent.resolve(name)
            val info = attributes(next) ?: return null
            when {
                info.isSymbolicLink -> current = next
                info.isRegularFile ->
                    return next.toRealPath().takeIf { it.startsWith(top) && it != top }
                else -> return null
            }
        }
        return null
    }

    private fun bytes(path: Path, skipped: Set<String> = emptySet()): Long {
        var result = 0L
        walk(path, "", skipped) { _, size -> result = Math.addExact(result, size) }
        return result
    }

    private fun privateFile(relative: String): Boolean {
        val parts = relative.lowercase().split('/')
        val name = parts.last()
        return parts.any { it in privateFolders } ||
            name in privateNames ||
            (name == ".env" || name.startsWith(".env.") && name !in templates) ||
            name.substringAfterLast('.', "") in privateExtensions ||
            ".git" in parts && name in setOf("config", "config.worktree")
    }

    fun scan(files: File, savedServers: Int): AppStorageFacts {
        val base = files.toPath()
        val root = base.resolve("linux/ubuntu/root")
        // An unsafe ancestor is never traversed, even when the final entry itself is not a link.
        for (relative in listOf("linux", "linux/ubuntu", "linux/ubuntu/root", "projects")) {
            val info = attributes(base.resolve(relative))
            if (info != null && !info.isDirectory) throw java.io.IOException("unavailable")
        }
        val projects = mutableListOf<ProjectStorageFacts>()
        val seen = mutableSetOf<String>()
        for (folder in listOf(base.resolve("projects"), root.resolve("projects"))) {
            val info = attributes(folder)
            if (info == null || !info.isDirectory) continue
            Files.newDirectoryStream(folder).use { tops ->
                for (top in tops) {
                    val topInfo = attributes(top) ?: continue
                    val name = top.fileName.toString()
                    if ((!topInfo.isDirectory && !topInfo.isRegularFile) || !seen.add(name))
                        continue
                    var size = 0L
                    var count = 0
                    var private = 0
                    walk(top, name, gitRoot = base) { path, bytes ->
                        size = Math.addExact(size, bytes)
                        count++
                        if (privateFile(path)) private++
                    }
                    projects += ProjectStorageFacts(name, size, count, private)
                }
            }
        }
        val all = bytes(base.resolve("linux"))
        val legacy = bytes(root.resolve("projects"))
        var privateBytes = 0L
        for (relative in
            listOf(
                ".local/share/opencode",
                ".config/opencode",
                ".oc-opencode2/config",
                ".oc-opencode2/data",
                ".oc-migration-exports",
            )) {
            // Every parent must be a real directory before following a private-data root.
            var cursor = root
            var safe = true
            for (part in relative.split('/')) {
                cursor = cursor.resolve(part)
                val info = attributes(cursor)
                if (info != null && !info.isDirectory) {
                    safe = false
                    break
                }
            }
            if (safe)
                privateBytes =
                    Math.addExact(privateBytes, bytes(root.resolve(relative), setOf("bin", "log")))
        }
        return AppStorageFacts(
            projects.sortedBy { it.name.lowercase() }.toList(),
            attributes(base.resolve("linux/ubuntu.ready"))?.isRegularFile == true,
            (all - legacy).coerceAtLeast(0),
            privateBytes,
            savedServers,
        )
    }
}

package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.system.Os
import android.system.OsConstants
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.data.kv.string
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.core.model.ServerProfile
import io.github.eslamasabry.oc.core.security.Redactor
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.integrations.quota.*
import io.github.eslamasabry.oc.engine.profiles.*
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The optional collector belongs to the exact saved built-in server owner. A matching loopback
 * address alone never grants access to Ubuntu or its provider sign-ins. All values stay in private
 * files; commands contain paths only.
 */
@Inject
@SingleIn(AppScope::class)
class AndroidPlansCollectorHost(
    private val app: Application,
    private val stores: PreviewStores,
    private val registry: ConnectionRegistry,
    private val dispatchers: AppDispatchers,
    private val redactor: Redactor,
) {
    private val lane = Mutex()
    private val closed = ConcurrentHashMap.newKeySet<ProfileId>()
    private val linux by lazy { BuiltinLinux.get(app) }
    private val secureRandom = SecureRandom()

    fun applies(profile: ServerProfile): Boolean =
        profile.id !in closed &&
            profile.backend == BackendKind.OpenCode2 &&
            stores.profiles
                .profile(profile.id)
                ?.profile
                ?.baseUrl
                ?.let(ProfileOrigins::trustOrigin) == profile.origin.toString() &&
            stores.prefs.string("oc.builtinServerOwner") == profile.id.raw &&
            InAppQuotaCollector.looksLikeInAppServer(
                QuotaServerProfile(profile.id, profile.origin.toString(), true)
            )

    private fun current(id: ProfileId): ServerProfile? =
        registry.live.value[id]?.profile?.takeIf(::applies)

    private fun collector(id: ProfileId): InAppQuotaCollector =
        InAppQuotaCollector(
            prefs = stores.prefs,
            lane = stores.lanes.app,
            secrets = stores.secrets,
            linux =
                object : QuotaLinuxHost {
                    override suspend fun status(): QuotaLinuxHost.Status =
                        withContext(dispatchers.io) {
                            QuotaLinuxHost.Status(
                                linux.installed,
                                linux.installed,
                                linux.runningServices(),
                            )
                        }

                    override suspend fun run(
                        script: String,
                        timeoutMs: Long,
                    ): QuotaLinuxHost.RunResult =
                        withContext(dispatchers.io) {
                            require(stores.prefs.string("oc.builtinServerOwner") == id.raw)
                            linux.run(script, ((timeoutMs + 999) / 1000).coerceIn(1, 120)).let {
                                QuotaLinuxHost.RunResult(it.exitCode, it.output)
                            }
                        }

                    override suspend fun startService(name: String, script: String, port: Int?) =
                        withContext(dispatchers.io) {
                            check(
                                current(id) != null &&
                                    linux.serverRunning &&
                                    linux.port == InAppQuotaCollector.IN_APP_SERVER_PORT
                            )
                            require(
                                name == InAppQuotaCollector.SERVICE_NAME &&
                                    port == InAppQuotaCollector.PORT
                            )
                            linux.startService(name, script, port, null)
                        }

                    override suspend fun stopService(name: String) =
                        withContext(dispatchers.io) {
                            require(name == InAppQuotaCollector.SERVICE_NAME)
                            check(stores.prefs.string("oc.builtinServerOwner") == id.raw)
                            linux.stopService(name)
                        }

                    override suspend fun serviceLog(name: String): String =
                        withContext(dispatchers.io) {
                            require(name == InAppQuotaCollector.SERVICE_NAME)
                            redactor.diagnostics(linux.serviceLogTail(name, 8192), 8192)
                        }
                },
            rootfs = PrivateQuotaRootfs(linux.rootfs, dispatchers),
            randomBytes = { size -> ByteArray(size).also(secureRandom::nextBytes) },
            loadCollector = { asset("quota/collector.mjs") },
            loadKeysHelper = { asset("quota/keys.mjs") },
            probe = { token -> probe(token) },
            supported = true,
        )

    private suspend fun asset(name: String): String =
        withContext(dispatchers.io) {
            app.assets.open(name).bufferedReader().use { it.readText() }
        }

    private suspend fun probe(token: String): Boolean =
        withContext(dispatchers.io) {
            val client =
                OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .retryOnConnectionFailure(false)
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .callTimeout(5, TimeUnit.SECONDS)
                    .build()
            try {
                client
                    .newCall(
                        Request.Builder()
                            .url("http://127.0.0.1:${InAppQuotaCollector.PORT}$PROVIDER_QUOTA_PATH")
                            .header("Authorization", "Bearer $token")
                            .get()
                            .build()
                    )
                    .execute()
                    .use { it.code in 200..599 }
            } catch (_: java.io.IOException) {
                false
            } finally {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }

    suspend fun setup(id: ProfileId): Boolean = lane.withLock {
        if (stores.warm() !is Outcome.Ok) return@withLock false
        val profile = current(id) ?: return@withLock false
        val result =
            attempt({ Failure.Device() }) {
                collector(id).setUp(QuotaServerProfile(id, profile.origin.toString(), true))
            }
        result is Outcome.Ok && result.value == null && current(id) == profile
    }

    suspend fun disable(id: ProfileId): Boolean = lane.withLock {
        if (stores.warm() !is Outcome.Ok || current(id) == null) return@withLock false
        val result = attempt({ Failure.Device() }) { collector(id).turnOff(id) }
        result is Outcome.Ok && result.value == null
    }

    fun enabled(id: ProfileId): Boolean = current(id) != null && collector(id).isEnabled(id)

    /**
     * Called only by the quota client's private token lambda immediately before a loopback read.
     */
    suspend fun token(id: ProfileId): String? = lane.withLock {
        val profile = current(id) ?: return@withLock null
        val host = collector(id)
        if (!host.isEnabled(id)) return@withLock null
        if (!host.ensureRunning(QuotaServerProfile(id, profile.origin.toString(), true)))
            return@withLock null
        val token = InAppQuotaCollector.readToken(stores.secrets, id) ?: return@withLock null
        redactor.registerSecret(token)
        token.takeIf { current(id) == profile }
    }

    fun stopAdmission(id: ProfileId) {
        closed += id
    }

    fun reopen(id: ProfileId) {
        closed -= id
    }

    suspend fun drain(id: ProfileId): Outcome<Unit> = lane.withLock {
        if (stores.prefs.string("oc.builtinServerOwner") != id.raw) return@withLock Outcome.Ok(Unit)
        attempt({ Failure.Device() }) {
            withContext(dispatchers.io) { linux.stopService(InAppQuotaCollector.SERVICE_NAME) }
        }
    }

    /**
     * Cleanup owns the closed profile too; it precedes the secure slot sweep and refuses failures.
     */
    suspend fun clear(id: ProfileId): Outcome<Int> = lane.withLock {
        if (stores.prefs.string("oc.builtinServerOwner") != id.raw) return@withLock Outcome.Ok(0)
        when (val result = attempt({ Failure.StorageRefused() }) { collector(id).turnOff(id) }) {
            is Outcome.Failed -> result
            is Outcome.Ok ->
                if (result.value == null) Outcome.Ok(1)
                else Outcome.Failed(Failure.StorageRefused())
        }
    }
}

/**
 * Absolute, fixed quota paths only, with no symlink at any ancestor or leaf and restrictive modes
 * at creation.
 */
private class PrivateQuotaRootfs(private val root: File, private val dispatchers: AppDispatchers) :
    QuotaRootfs {
    private val allowed =
        setOf(
            InAppQuotaCollector.COLLECTOR_PATH,
            InAppQuotaCollector.KEYS_PATH,
            InAppQuotaCollector.TOKEN_PATH,
        )

    private fun target(path: String, create: Boolean): File {
        require(path in allowed)
        val base = root.absoluteFile.toPath()
        var ancestor: java.nio.file.Path? = base
        while (ancestor != null) {
            check(!java.nio.file.Files.isSymbolicLink(ancestor))
            ancestor = ancestor.parent
        }
        check(java.nio.file.Files.isDirectory(base, java.nio.file.LinkOption.NOFOLLOW_LINKS))
        val destination = base.resolve(path.removePrefix("/"))
        var current = base
        for (part in base.relativize(destination.parent)) {
            current = current.resolve(part)
            check(!java.nio.file.Files.isSymbolicLink(current))
            if (
                !java.nio.file.Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS) &&
                    create
            )
                Os.mkdir(current.toString(), 448)
            if (
                !java.nio.file.Files.isDirectory(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)
            ) {
                if (!create) return destination.toFile()
                error("quota storage unavailable")
            }
        }
        check(!java.nio.file.Files.isSymbolicLink(destination))
        return destination.toFile()
    }

    override suspend fun read(path: String): ByteArray? =
        withContext(dispatchers.io) {
            val file = target(path, false)
            if (!file.exists()) return@withContext null
            val fd = Os.open(file.absolutePath, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW, 0)
            FileInputStream(fd).use { input ->
                check(OsConstants.S_ISREG(Os.fstat(fd).st_mode))
                val bytes = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    check(bytes.size() + count <= 512 * 1024)
                    bytes.write(chunk, 0, count)
                }
                bytes.toByteArray()
            }
        }

    override suspend fun write(path: String, bytes: ByteArray) =
        withContext(dispatchers.io) {
            check(bytes.size <= 512 * 1024)
            val file = target(path, true)
            val temporary = File(file.parentFile, ".quota-${java.util.UUID.randomUUID()}.tmp")
            // New inode: existing hardlinks or symlinks cannot receive token bytes.
            val fd = Os.open(
                temporary.absolutePath,
                OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW,
                384,
            )
            try {
                FileOutputStream(fd).use { output ->
                    val stat = Os.fstat(fd)
                    check(OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L)
                    Os.fchmod(fd, 384)
                    output.write(bytes)
                    output.fd.sync()
                }
                check(target(path, false).absolutePath == file.absolutePath)
                // Replaces the entry instead of truncating the file that its old inode referenced.
                Os.rename(temporary.absolutePath, file.absolutePath)
            } finally {
                if (temporary.exists()) Os.remove(temporary.absolutePath)
            }
        }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileCloser>())
class QuotaCollectorCloser(private val host: AndroidPlansCollectorHost) : ProfileCloser {
    override val owner: String = ":engine:integrations-quota"

    override fun stopAdmission(profile: ProfileId) = host.stopAdmission(profile)

    override suspend fun drain(profile: ProfileId): Outcome<Unit> = host.drain(profile)

    override fun reopen(profile: ProfileId) = host.reopen(profile)
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<ProfileResourceCleaner>())
class QuotaCollectorCleaner(private val host: AndroidPlansCollectorHost) : ProfileResourceCleaner {
    override val owner: String = ":engine:integrations-quota"
    override val order: Int = DeletionStep.DEVICE_SURFACES - 1
    override val guardsSweep: Boolean = true

    override suspend fun clear(profileId: ProfileId, options: DeletionOptions): Outcome<Int> =
        host.clear(profileId)
}

package io.github.eslamasabry.oc.android.termux

import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import io.github.eslamasabry.oc.core.common.AppDispatchers
import java.io.BufferedInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class GitRepairTermuxState {
    Ready,
    Missing,
    Permission,
    Unavailable,
}

interface GitRepairTermuxFiles {
    fun available(): GitRepairTermuxState

    suspend fun fetch(paths: List<String>, into: File): Map<Int, File>?

    companion object {
        val STORE =
            Regex(
                "^(?!.*/\\.\\.(?:/|$))/data/(?:data|user/0)/com\\.termux/files/usr/var/lib/proot-distro/(?:containers/[A-Za-z0-9._-]+/rootfs|installed-rootfs/[A-Za-z0-9._-]+)/\\.l2s/\\.l2s\\.[A-Za-z0-9._-]+$"
            )
    }
}

/** The callback is scoped by a UUID and consumed once; its explicit service is not exported. */
internal object GitRepairCallbacks {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Bundle?>>()

    fun register(token: String): CompletableDeferred<Bundle?> =
        CompletableDeferred<Bundle?>().also { check(pending.putIfAbsent(token, it) == null) }

    fun complete(token: String, result: Bundle?) {
        pending.remove(token)?.complete(result)
    }

    fun remove(token: String) {
        pending.remove(token)?.cancel()
    }
}

class GitRepairResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra("oc.gitRepairToken")
        if (token != null && intent?.data?.lastPathSegment == token)
            GitRepairCallbacks.complete(token, intent?.getBundleExtra("result"))
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

/** Pulls only allowlisted link-store files over a fresh authenticated loopback endpoint. */
class AndroidGitRepairTermuxFiles(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : GitRepairTermuxFiles {
    override fun available(): GitRepairTermuxState {
        try {
            context.packageManager.getPackageInfo("com.termux", 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return GitRepairTermuxState.Missing
        } catch (_: Exception) {
            return GitRepairTermuxState.Unavailable
        }
        if (
            context.checkSelfPermission("com.termux.permission.RUN_COMMAND") !=
                PackageManager.PERMISSION_GRANTED
        )
            return GitRepairTermuxState.Permission
        return try {
            val info =
                context.packageManager.getServiceInfo(
                    ComponentName("com.termux", "com.termux.app.RunCommandService"),
                    0,
                )
            if (info.enabled && info.exported) GitRepairTermuxState.Ready
            else GitRepairTermuxState.Unavailable
        } catch (_: Exception) {
            GitRepairTermuxState.Unavailable
        }
    }

    override suspend fun fetch(paths: List<String>, into: File): Map<Int, File>? =
        withContext(dispatchers.io) {
            if (
                available() != GitRepairTermuxState.Ready ||
                    paths.any { !GitRepairTermuxFiles.STORE.matches(it) }
            )
                return@withContext null
            val token =
                Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
            val received = ConcurrentHashMap<Int, File>()
            var transferred = 0L
            val socket = ServerSocket(0, 8, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
            coroutineScope {
                val serving = launch {
                    try {
                        while (!socket.isClosed) {
                            try {
                                socket.accept().use { client ->
                                    client.soTimeout = 15_000
                                    val input = BufferedInputStream(client.getInputStream())
                                    val head = StringBuilder()
                                    while (head.length < 16_384 && !head.endsWith("\r\n\r\n")) {
                                        val value = input.read()
                                        if (value < 0) break
                                        head.append(value.toChar())
                                    }
                                    val lines = head.toString().split("\r\n")
                                    val request = lines.firstOrNull().orEmpty().split(' ')
                                    val headers =
                                        lines
                                            .drop(1)
                                            .filter { ':' in it }
                                            .map {
                                                it.substringBefore(':').trim().lowercase() to
                                                    it.substringAfter(':').trim()
                                            }
                                    val index =
                                        request.getOrNull(1)?.let {
                                            Regex("/f/([0-9]{1,6})")
                                                .matchEntire(it)
                                                ?.groupValues
                                                ?.get(1)
                                                ?.toIntOrNull()
                                        }
                                    val length =
                                        headers
                                            .filter { it.first == "content-length" }
                                            .singleOrNull()
                                            ?.second
                                            ?.toLongOrNull()
                                    val accepted =
                                        head.endsWith("\r\n\r\n") &&
                                            request.size == 3 &&
                                            request[0] == "PUT" &&
                                            request[2] == "HTTP/1.1" &&
                                            index != null &&
                                            index in paths.indices &&
                                            !received.containsKey(index) &&
                                            headers
                                                .filter { it.first == "authorization" }
                                                .map { it.second } == listOf("Bearer $token") &&
                                            length != null &&
                                            length in 1..MAX_FILE &&
                                            length <= MAX_JOB - transferred &&
                                            length <=
                                                (into.usableSpace - RESERVED).coerceAtLeast(0) &&
                                            headers.none { it.first == "transfer-encoding" }
                                    var status = "403 Forbidden"
                                    if (accepted) {
                                        val file = File(into, index.toString())
                                        try {
                                            java.nio.file.Files.newOutputStream(
                                                    file.toPath(),
                                                    java.nio.file.StandardOpenOption.CREATE_NEW,
                                                )
                                                .use { output ->
                                                    var left = requireNotNull(length)
                                                    val buffer = ByteArray(65_536)
                                                    while (left > 0) {
                                                        val n =
                                                            input.read(
                                                                buffer,
                                                                0,
                                                                minOf(left, buffer.size.toLong())
                                                                    .toInt(),
                                                            )
                                                        check(n > 0)
                                                        output.write(buffer, 0, n)
                                                        left -= n
                                                    }
                                                }
                                            transferred += requireNotNull(length)
                                            received[requireNotNull(index)] = file
                                            status = "204 No Content"
                                        } catch (_: Exception) {
                                            file.delete()
                                            status = "400 Bad Request"
                                        }
                                    }
                                    client
                                        .getOutputStream()
                                        .write(
                                            "HTTP/1.1 $status\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                                                .toByteArray()
                                        )
                                }
                            } catch (e: java.net.SocketException) {
                                if (socket.isClosed) break
                            } catch (_: java.io.IOException) {
                                /* One incomplete client does not consume another file's admission. */
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        /* The waiting command or its timeout determines the transfer outcome. */
                    }
                }
                try {
                    for (start in paths.indices step 150) {
                        val selected = paths.indices.drop(start).take(150)
                        val entries = selected.joinToString("\n") { "$it|${paths[it]}" }
                        val script =
                            """# oc-git-repair
export LC_ALL=C
while IFS='|' read -r index path; do
  case "${'$'}path" in
    /data/data/com.termux/files/usr/var/lib/proot-distro/*/.l2s/.l2s.*|/data/user/0/com.termux/files/usr/var/lib/proot-distro/*/.l2s/.l2s.*) ;;
    *) continue ;;
  esac
  case "${'$'}path" in *..*/*|*/..*) continue ;; esac
  [ -f "${'$'}path" ] && [ -r "${'$'}path" ] || continue
  curl --silent --fail --max-time 120 --connect-timeout 3 -T "${'$'}path" -H 'Authorization: Bearer $token' "http://127.0.0.1:${socket.localPort}/f/${'$'}index" >/dev/null 2>&1 || true
done <<'OC_GIT_REPAIR_LIST'
$entries
OC_GIT_REPAIR_LIST
"""
                        if (!run(script)) return@coroutineScope null
                    }
                    received.toMap()
                } finally {
                    socket.close()
                    serving.cancel()
                }
            }
        }

    /** A bounded, read-only probe under Termux's own UID. No output leaves the bridge. */
    public suspend fun sharedStorageReadable(): Boolean? {
        val answer = command("ls -A /storage/emulated/0 >/dev/null 2>&1", 12_000L) ?: return null
        if (!answer.containsKey("exitCode") || answer.getInt("err", -1) != -1) return null
        return answer.getInt("exitCode", -1) == 0
    }

    private suspend fun run(script: String): Boolean {
        val answer = command(script, 300_000L) ?: return false
        return answer.containsKey("exitCode") &&
            answer.getInt("exitCode", -1) == 0 &&
            answer.getInt("err", -1) == -1
    }

    private suspend fun command(script: String, timeoutMs: Long): Bundle? {
        val id = UUID.randomUUID().toString()
        val result = GitRepairCallbacks.register(id)
        val callback =
            PendingIntent.getService(
                context,
                0,
                Intent(context, GitRepairResultService::class.java)
                    .setData(Uri.parse("opencode://git-repair/$id"))
                    .putExtra("oc.gitRepairToken", id),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE,
            )
        try {
            if (available() != GitRepairTermuxState.Ready) return null
            context.startService(
                Intent("com.termux.RUN_COMMAND").apply {
                    component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                    putExtra(
                        "com.termux.RUN_COMMAND_PATH",
                        "/data/data/com.termux/files/usr/bin/bash",
                    )
                    putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-s"))
                    putExtra("com.termux.RUN_COMMAND_STDIN", script)
                    putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                    putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                    putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
                }
            )
            return withTimeoutOrNull(timeoutMs) { result.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        } finally {
            GitRepairCallbacks.remove(id)
            callback.cancel()
        }
    }

    private companion object {
        const val MAX_FILE = 2L * 1024 * 1024 * 1024
        const val MAX_JOB = 8L * 1024 * 1024 * 1024
        const val RESERVED = 64L * 1024 * 1024
    }
}

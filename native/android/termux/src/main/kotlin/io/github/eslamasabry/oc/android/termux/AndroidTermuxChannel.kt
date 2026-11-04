package io.github.eslamasabry.oc.android.termux

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.engine.termuxhost.ChannelReply
import io.github.eslamasabry.oc.engine.termuxhost.TermuxChannel
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Android RUN_COMMAND transport used only for the explicit migrate-out journey (ADR D7). */
public class AndroidTermuxChannel(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    private val permission: suspend () -> String,
) : TermuxChannel {
    public fun state(): GitRepairTermuxState {
        val info =
            try {
                context.packageManager.getPackageInfo("com.termux", 0)
            } catch (_: PackageManager.NameNotFoundException) {
                return GitRepairTermuxState.Missing
            } catch (_: Exception) {
                return GitRepairTermuxState.Unavailable
            }
        val parts =
            Regex("\\d+")
                .findAll(info.versionName.orEmpty())
                .take(2)
                .map { it.value.toIntOrNull() ?: 0 }
                .toList()
        if (parts.size < 2 || (parts[0] == 0 && parts[1] < 109))
            return GitRepairTermuxState.Unavailable
        val service =
            try {
                context.packageManager.getServiceInfo(
                    ComponentName("com.termux", "com.termux.app.RunCommandService"),
                    0,
                )
            } catch (_: Exception) {
                return GitRepairTermuxState.Unavailable
            }
        if (!service.enabled || !service.exported) return GitRepairTermuxState.Unavailable
        return if (
            context.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
                PackageManager.PERMISSION_GRANTED
        )
            GitRepairTermuxState.Ready
        else GitRepairTermuxState.Permission
    }

    override suspend fun invoke(method: String, arguments: Map<String, Any?>?): ChannelReply {
        return when (method) {
            "getCapabilities" -> {
                val state = state()
                ChannelReply.Value(
                    mapOf(
                        "platformSupported" to true,
                        "installed" to (state != GitRepairTermuxState.Missing),
                        "permissionGranted" to (state == GitRepairTermuxState.Ready),
                        "serviceAvailable" to
                            (state == GitRepairTermuxState.Ready ||
                                state == GitRepairTermuxState.Permission),
                        "protocolSupported" to true,
                    )
                )
            }
            "requestRunCommandAccess" -> ChannelReply.Value(permission())
            "requestRunCommandPermission" -> ChannelReply.Value(permission() == "granted")
            "openTermux" -> ChannelReply.Value(openTermux())
            "openAppSettings" -> ChannelReply.Value(openSettings())
            "runInTermux" -> run(arguments)
            else -> ChannelReply.MissingHandler
        }
    }

    public fun openTermux(): Boolean =
        launch(context.packageManager.getLaunchIntentForPackage("com.termux"))

    public fun openSettings(): Boolean =
        launch(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            )
        )

    private fun launch(intent: Intent?): Boolean =
        try {
            if (intent == null || intent.resolveActivity(context.packageManager) == null) false
            else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
        } catch (_: Exception) {
            false
        }

    private suspend fun run(arguments: Map<String, Any?>?): ChannelReply =
        withContext(dispatchers.io) {
            when (state()) {
                GitRepairTermuxState.Missing ->
                    return@withContext ChannelReply.PlatformError("termux_missing", null)
                GitRepairTermuxState.Permission ->
                    return@withContext ChannelReply.PlatformError("permission_denied", null)
                GitRepairTermuxState.Unavailable ->
                    return@withContext ChannelReply.PlatformError("service_unavailable", null)
                GitRepairTermuxState.Ready -> Unit
            }
            val script =
                arguments?.get("script") as? String
                    ?: return@withContext ChannelReply.PlatformError("invalid_command", null)
            val timeout = (arguments["timeoutMs"] as? Long)?.coerceIn(1_000, 900_000) ?: 30_000L
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
                context.startService(
                    Intent("com.termux.RUN_COMMAND").apply {
                        component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                        putExtra(
                            "com.termux.RUN_COMMAND_PATH",
                            "/data/data/com.termux/files/usr/bin/bash",
                        )
                        putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-s"))
                        putExtra("com.termux.RUN_COMMAND_STDIN", script)
                        putExtra(
                            "com.termux.RUN_COMMAND_WORKDIR",
                            "/data/data/com.termux/files/home",
                        )
                        putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                        putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
                    }
                )
                val answer =
                    withTimeoutOrNull(timeout) { result.await() }
                        ?: return@withContext ChannelReply.PlatformError("command_timeout", null)
                ChannelReply.Value(
                    mapOf(
                        "stdout" to answer.getString("stdout", ""),
                        "stderr" to answer.getString("stderr", ""),
                        "exitCode" to answer.getInt("exitCode", -1),
                        "err" to answer.getInt("err", -1),
                        "errorMessage" to answer.getString("errmsg", ""),
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                ChannelReply.PlatformError("permission_denied", null)
            } catch (_: Exception) {
                ChannelReply.PlatformError("service_unavailable", null)
            } finally {
                GitRepairCallbacks.remove(id)
                callback.cancel()
            }
        }
}

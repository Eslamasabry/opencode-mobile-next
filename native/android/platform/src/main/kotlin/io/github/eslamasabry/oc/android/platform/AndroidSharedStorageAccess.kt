package io.github.eslamasabry.oc.android.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Android's actual shared-storage gate; never requested at process startup. */
public class AndroidSharedStorageAccess(private val dispatchers: AppDispatchers) {
    private val lane = Mutex()
    private var requests = 0

    public fun granted(activity: android.content.Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else
            activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED &&
                activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED

    /**
     * Returns after the permission sheet/settings activity finishes, cancellation, or a ten-minute
     * bound.
     */
    public suspend fun request(activity: ComponentActivity): Outcome<Unit> =
        withContext(dispatchers.main) {
            if (!lane.tryLock()) return@withContext Outcome.Failed(Failure.StorageRefused())
            try {
                if (granted(activity)) return@withContext Outcome.Ok(Unit)
                val answer = CompletableDeferred<Unit>()
                val key = "oc.storage.access.${requests++}"
                val gone =
                    object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            answer.complete(Unit)
                        }
                    }
                activity.lifecycle.addObserver(gone)
                try {
                    if (Build.VERSION.SDK_INT >= 30) {
                        val launcher =
                            activity.activityResultRegistry.register(
                                key,
                                ActivityResultContracts.StartActivityForResult(),
                            ) {
                                answer.complete(Unit)
                            }
                        try {
                            try {
                                launcher.launch(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${activity.packageName}"),
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                launcher.launch(
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                )
                            }
                            withTimeoutOrNull(10 * 60_000L) { answer.await() }
                        } finally {
                            launcher.unregister()
                        }
                    } else {
                        val launcher =
                            activity.activityResultRegistry.register(
                                key,
                                ActivityResultContracts.RequestMultiplePermissions(),
                            ) {
                                answer.complete(Unit)
                            }
                        try {
                            launcher.launch(
                                arrayOf(
                                    Manifest.permission.READ_EXTERNAL_STORAGE,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                )
                            )
                            withTimeoutOrNull(10 * 60_000L) { answer.await() }
                        } finally {
                            launcher.unregister()
                        }
                    }
                    Outcome.Ok(Unit)
                } finally {
                    activity.lifecycle.removeObserver(gone)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Outcome.Failed(Failure.StorageRefused())
            } finally {
                lane.unlock()
            }
        }
}

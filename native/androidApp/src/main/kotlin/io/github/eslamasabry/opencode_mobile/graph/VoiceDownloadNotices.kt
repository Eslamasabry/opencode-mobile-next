package io.github.eslamasabry.opencode_mobile.graph

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import io.github.eslamasabry.oc.android.notifications.ChannelRegistry
import io.github.eslamasabry.oc.android.notifications.NotifyChannel
import io.github.eslamasabry.oc.android.notifications.NotifyWords
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.engine.voice.models.SetupNetworkSource
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupNoticePhase
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupNoticeSink
import io.github.eslamasabry.oc.engine.voice.setup.VoiceSetupNotices
import io.github.eslamasabry.oc.engine.voice.state.PackId
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceModels
import io.github.eslamasabry.opencode_mobile.MainActivity
import io.github.eslamasabry.opencode_mobile.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Fixed-copy shade progress for a download explicitly requested from Voice settings. No foreground
 * service is started.
 */
internal class VoiceDownloadNoticeSink(
    private val application: Application,
    private val activities: ResumedActivities,
    private val dispatchers: AppDispatchers,
    preferences: AppPreferences,
) : VoiceSetupNoticeSink {
    private val words = NotifyWords(application) { preferences.localeTag.value }
    private val manager = application.getSystemService(NotificationManager::class.java)
    private var answer: CompletableDeferred<Boolean>? = null
    private var launcher: androidx.activity.result.ActivityResultLauncher<String>? = null

    init {
        activities.onCreated { activity -> if (answer != null) register(activity) }
    }

    private fun register(activity: ComponentActivity) {
        launcher?.unregister()
        val pending = answer
        launcher =
            activity.activityResultRegistry.register(
                "oc.voice.download.notifications",
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                pending?.complete(granted)
            }
    }

    private fun allowed(): Boolean {
        if (
            Build.VERSION.SDK_INT >= 33 &&
                application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return false
        if (manager?.areNotificationsEnabled() != true) return false
        ChannelRegistry(manager, words).ensure()
        return manager.getNotificationChannel(NotifyChannel.VoiceSetup.id)?.importance !=
            NotificationManager.IMPORTANCE_NONE
    }

    override suspend fun requestPermission(): Outcome<Boolean> =
        withContext(dispatchers.main) {
            attempt({ Failure.Device() }) {
                if (
                    Build.VERSION.SDK_INT < 33 ||
                        application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                )
                    return@attempt allowed()
                val activity = activities.current ?: return@attempt false
                answer?.let {
                    return@attempt it.await() && allowed()
                }
                val pending = CompletableDeferred<Boolean>()
                answer = pending
                try {
                    register(activity)
                    launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
                    pending.await() && allowed()
                } finally {
                    if (answer === pending) {
                        answer = null
                        launcher?.unregister()
                        launcher = null
                    }
                }
            }
        }

    override suspend fun show(
        phase: VoiceSetupNoticePhase,
        receivedBytes: Long,
        totalBytes: Long,
    ): Outcome<Boolean> =
        withContext(dispatchers.main) {
            attempt({ Failure.Device() }) {
                if (!allowed()) return@attempt false
                val active =
                    phase == VoiceSetupNoticePhase.Downloading ||
                        phase == VoiceSetupNoticePhase.Verifying
                val body =
                    when (phase) {
                        VoiceSetupNoticePhase.Downloading -> R.string.voice_download_downloading
                        VoiceSetupNoticePhase.Verifying -> R.string.voice_download_verifying
                        VoiceSetupNoticePhase.Complete -> R.string.voice_download_complete
                        VoiceSetupNoticePhase.Failed -> R.string.voice_download_failed
                    }
                val intent =
                    Intent(application, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                val open =
                    PendingIntent.getActivity(
                        application,
                        ID,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                val total = totalBytes.coerceAtLeast(0)
                val received = receivedBytes.coerceIn(0, total)
                val notification =
                    Notification.Builder(words.localized(), NotifyChannel.VoiceSetup.id)
                        .setSmallIcon(android.R.drawable.stat_sys_download)
                        .setContentTitle(words.text(R.string.voice_download_title))
                        .setContentText(words.text(body))
                        .setContentIntent(open)
                        .setOnlyAlertOnce(true)
                        .setOngoing(active)
                        .setAutoCancel(!active)
                        .setVisibility(Notification.VISIBILITY_PRIVATE)
                        .setCategory(Notification.CATEGORY_PROGRESS)
                if (active) {
                    val percent =
                        if (total > 0) ((received.toDouble() / total) * 100).toInt() else 0
                    notification.setProgress(
                        100,
                        percent,
                        phase == VoiceSetupNoticePhase.Verifying || total == 0L,
                    )
                    notification.setTimeoutAfter(15 * 60 * 1000L)
                }
                manager?.notify(ID, notification.build()) ?: return@attempt false
                true
            }
        }

    override suspend fun dismiss(): Outcome<Unit> =
        withContext(dispatchers.main) {
            attempt({ Failure.Device() }) {
                manager?.cancel(ID)
                Unit
            }
        }

    private companion object {
        const val ID = 0x701CE
    }
}

/**
 * Keeps notification mutations ordered through the engine's bounded sink, including terminal phases
 * and cancellation.
 */
internal class NotifyingVoiceModels(
    private val models: VoiceModels,
    private val notices: VoiceSetupNotices,
    private val network: SetupNetworkSource,
) : VoiceModels by models {
    private val downloading = Mutex()

    override suspend fun downloadNetwork(): SetupNetwork = network.current()

    override suspend fun download(pack: PackId, replaceExisting: Boolean): Outcome<Unit> =
        downloadConsented(pack, replaceExisting, allowMetered = false)

    override suspend fun downloadConsented(
        pack: PackId,
        replaceExisting: Boolean,
        allowMetered: Boolean,
    ): Outcome<Unit> {
        if (!downloading.tryLock()) return Failure.Media(MediaProblem.DownloadBusy).failed()
        try {
            // Called only by a person's Download/Download again choice, while that screen is in the
            // foreground.
            notices.requestPermission()
            // Recheck after the permission dialog: a Wi-Fi observation before the dialog authorizes
            // no mobile data.
            val connection = network.current()
            if (
                connection == SetupNetwork.Offline ||
                    (connection != SetupNetwork.Unmetered && !allowMetered)
            ) {
                return Failure.Media(MediaProblem.DownloadOffline).failed()
            }
            return coroutineScope {
                val progress =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        models.state.collect { state ->
                            val job = state.job?.takeIf { it.pack == pack } ?: return@collect
                            when (val phase = job.phase) {
                                is PackJobPhase.Downloading ->
                                    notices.show(
                                        VoiceSetupNoticePhase.Downloading,
                                        phase.received,
                                        phase.total,
                                    )
                                PackJobPhase.Verifying ->
                                    notices.show(VoiceSetupNoticePhase.Verifying, 0, 0)
                            }
                        }
                    }
                val networkWatch = launch {
                    network.changes.collect { current ->
                        if (
                            current == SetupNetwork.Offline ||
                                (current != SetupNetwork.Unmetered && !allowMetered)
                        ) {
                            models.cancelDownload()
                        }
                    }
                }
                var terminal = false
                try {
                    val result = models.download(pack, replaceExisting)
                    progress.cancelAndJoin()
                    val phase =
                        if (result is Outcome.Ok) VoiceSetupNoticePhase.Complete
                        else VoiceSetupNoticePhase.Failed
                    notices.show(phase, 0, 0)
                    terminal = true
                    result
                } finally {
                    withContext(NonCancellable) {
                        progress.cancelAndJoin()
                        networkWatch.cancelAndJoin()
                        if (!terminal) notices.dismiss()
                    }
                }
            }
        } finally {
            downloading.unlock()
        }
    }
}

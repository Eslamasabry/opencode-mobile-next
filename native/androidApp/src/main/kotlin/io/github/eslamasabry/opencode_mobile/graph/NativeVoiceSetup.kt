package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.runtime.setup.SetupAppComponent
import io.github.eslamasabry.oc.engine.runtime.setup.SetupAppProgress
import io.github.eslamasabry.oc.engine.voice.models.AutomaticPack
import io.github.eslamasabry.oc.engine.voice.models.ModelCatalog
import io.github.eslamasabry.oc.engine.voice.models.ModelPackSpec
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceHub
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceModels
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.setupAppStageDownloading
import io.github.eslamasabry.oc.ui.words.res.setupAppStageVerifying
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** Setup and Settings use the same verified, resumable app-private model manager. */
@Inject
@SingleIn(AppScope::class)
class NativeVoiceSetup(private val hub: VoiceHub) : SetupAppComponent {
    private var selected: ModelPackSpec? = null
    private var installing: VoiceModels? = null
    val downloadBytes: Long
        get() = selected?.totalBytes ?: ModelCatalog.tiny.totalBytes

    suspend fun prepare(): Outcome<Unit> {
        val models =
            when (val result = hub.parts()) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value.models
            }
        models.refresh()
        val state = models.state.value
        selected =
            AutomaticPack.choose(
                state.selected,
                models.device(),
                { state.packs[it] == PackStatus.Installed },
                { (state.packs[it] as? PackStatus.Partial)?.bytes ?: 0 },
                models.speechPacks,
            )
        return if (selected == null) Outcome.Failed(Failure.Media(MediaProblem.PackMemoryTooSmall))
        else Outcome.Ok(Unit)
    }

    override suspend fun installed(): Outcome<Boolean> {
        when (val prepared = prepare()) {
            is Outcome.Failed -> return prepared
            is Outcome.Ok -> Unit
        }
        return when (val parts = hub.parts()) {
            is Outcome.Failed -> parts
            is Outcome.Ok ->
                Outcome.Ok(
                    parts.value.models.state.value.let {
                        it.selected == selected?.id && it.packs[it.selected] == PackStatus.Installed
                    }
                )
        }
    }

    override suspend fun install(progress: (SetupAppProgress) -> Unit): Outcome<Unit> =
        coroutineScope {
            when (val prepared = prepare()) {
                is Outcome.Failed -> return@coroutineScope prepared
                is Outcome.Ok -> Unit
            }
            val pack =
                selected
                    ?: return@coroutineScope Outcome.Failed(
                        Failure.Media(MediaProblem.PackMemoryTooSmall)
                    )
            val models =
                when (val result = hub.parts()) {
                    is Outcome.Failed -> return@coroutineScope result
                    is Outcome.Ok -> result.value.models
                }
            if (models.state.value.job != null)
                return@coroutineScope Outcome.Failed(Failure.Media(MediaProblem.DownloadBusy))
            when (val choice = models.select(pack.id)) {
                is Outcome.Failed -> return@coroutineScope choice
                is Outcome.Ok -> Unit
            }
            if (models.state.value.packs[pack.id] == PackStatus.Installed)
                return@coroutineScope Outcome.Ok(Unit)
            val downloading = getString(Res.string.setupAppStageDownloading)
            val verifying = getString(Res.string.setupAppStageVerifying)
            installing = models
            val observer = launch {
                models.state.collect { state ->
                    val job = state.job?.takeIf { it.pack == pack.id } ?: return@collect
                    progress(
                        when (val phase = job.phase) {
                            is PackJobPhase.Downloading ->
                                SetupAppProgress(downloading, phase.received, phase.total)
                            PackJobPhase.Verifying ->
                                SetupAppProgress(verifying, pack.totalBytes, pack.totalBytes)
                        }
                    )
                }
            }
            try {
                // The visible setup action names the model download; consent belongs to this
                // attempt.
                models.downloadConsented(pack.id, replaceExisting = false, allowMetered = true)
            } finally {
                observer.cancel()
                installing = null
            }
        }

    override suspend fun cancel() {
        installing?.cancelDownload()
    }

    override suspend fun remove(): Outcome<Unit> {
        val models =
            when (val result = hub.parts()) {
                is Outcome.Failed -> return result
                is Outcome.Ok -> result.value.models
            }
        if (models.state.value.job != null)
            return Outcome.Failed(Failure.Media(MediaProblem.DownloadBusy))
        for (pack in models.speechPacks) {
            when (val removed = models.remove(pack.id)) {
                is Outcome.Failed -> return removed
                is Outcome.Ok -> Unit
            }
        }
        models.refresh()
        return if (
            models.speechPacks.any { models.state.value.packs[it.id] == PackStatus.Installed }
        )
            Outcome.Failed(Failure.Media(MediaProblem.DownloadVerification))
        else Outcome.Ok(Unit)
    }
}

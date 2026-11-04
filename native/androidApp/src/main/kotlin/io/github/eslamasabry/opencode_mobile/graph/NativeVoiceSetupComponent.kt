package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.MediaProblem
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.RefusalReason
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.engine.runtime.setup.DownloadSize
import io.github.eslamasabry.oc.engine.runtime.setup.SetupAppComponents
import io.github.eslamasabry.oc.engine.runtime.setup.SetupAppOffer
import io.github.eslamasabry.oc.engine.runtime.setup.SetupAppProgress
import io.github.eslamasabry.oc.engine.runtime.setup.SetupCheckResult
import io.github.eslamasabry.oc.engine.runtime.setup.SetupComponentIds
import io.github.eslamasabry.oc.engine.voice.models.AutomaticPack
import io.github.eslamasabry.oc.engine.voice.models.ModelPackSpec
import io.github.eslamasabry.oc.engine.voice.state.PackJobPhase
import io.github.eslamasabry.oc.engine.voice.state.PackStatus
import io.github.eslamasabry.oc.engine.voice.state.VoiceState
import io.github.eslamasabry.oc.feature.voice.presentation.SpeechEngineKind
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceHub
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceModels
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.r40SetupVoiceDownloading
import io.github.eslamasabry.oc.ui.words.res.r40SetupVoiceVerifying
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/** Setup and Voice Settings share the same verified files and the same single model manager. */
@SingleIn(AppScope::class)
@Inject
class NativeVoiceSetupComponent(private val hub: VoiceHub, private val scope: AppCoroutineScope) :
    SetupAppComponents {
    @Volatile
    var lastOffer: SetupAppOffer? = null
        private set

    private data class Offer(
        val models: VoiceModels,
        val pack: ModelPackSpec,
        val installed: Boolean,
    )

    private class Owned(val models: VoiceModels, val token: String) {
        @Volatile var cancelled = false
    }

    @Volatile private var owned: Owned? = null
    private val cancellationEpoch = AtomicLong()

    private suspend fun offered(): Outcome<Offer?> {
        val parts = hub.parts()
        if (parts is Outcome.Failed) return parts
        val voice = (parts as Outcome.Ok).value
        if (voice.engine != SpeechEngineKind.OfflineModel || !voice.micAvailable)
            return Outcome.Ok(null)
        val models = voice.models
        models.refresh()
        val state = models.state.value
        val pack =
            AutomaticPack.choose(
                state.selected,
                models.device(),
                { state.packs[it] == PackStatus.Installed },
                packs = models.speechPacks,
            ) ?: return Outcome.Ok(null)
        val installed = state.selected == pack.id && state.packs[pack.id] == PackStatus.Installed
        return Outcome.Ok(Offer(models, pack, installed))
    }

    suspend fun offer(): Outcome<SetupAppOffer?> {
        val choice = offered()
        if (choice is Outcome.Failed) {
            lastOffer = null
            return choice
        }
        val chosen = (choice as Outcome.Ok).value
        val offer = chosen?.let {
            val installedBytes =
                it.models.speechPacks
                    .filter { p -> it.models.state.value.packs[p.id] == PackStatus.Installed }
                    .sumOf { p -> p.totalBytes }
            SetupAppOffer(
                if (it.installed) installedBytes else it.pack.totalBytes,
                it.installed,
                DownloadSize.exact(if (it.installed) 0 else it.pack.totalBytes),
            )
        }
        lastOffer = offer
        return Outcome.Ok(offer)
    }

    override suspend fun check(id: String): Outcome<SetupCheckResult> {
        if (id != SetupComponentIds.VOICE) return Failure.Unavailable(FeatureId("voice")).failed()
        return when (val offer = offer()) {
            is Outcome.Failed -> offer
            is Outcome.Ok -> Outcome.Ok(SetupCheckResult(offer.value?.installed == true, null))
        }
    }

    override suspend fun install(
        id: String,
        onProgress: (SetupAppProgress) -> Unit,
    ): Outcome<Unit> {
        if (id != SetupComponentIds.VOICE) return Failure.Unavailable(FeatureId("voice")).failed()
        val epoch = cancellationEpoch.get()
        val offered = offered()
        if (offered is Outcome.Failed) return offered
        if (epoch != cancellationEpoch.get())
            return Failure.Refused(RefusalReason.StaleScope).failed()
        val choice =
            (offered as Outcome.Ok).value ?: return Failure.Unavailable(FeatureId("voice")).failed()
        if (choice.models.state.value.job != null || owned != null)
            return Failure.Media(MediaProblem.DownloadBusy).failed()
        when (val selected = choice.models.select(choice.pack.id)) {
            is Outcome.Failed -> return selected
            is Outcome.Ok -> Unit
        }
        if (epoch != cancellationEpoch.get())
            return Failure.Refused(RefusalReason.StaleScope).failed()
        if (choice.installed) return Outcome.Ok(Unit)
        val operation = Owned(choice.models, "setup-${UUID.randomUUID()}")
        owned = operation
        val downloading = getString(Res.string.r40SetupVoiceDownloading)
        val verifying = getString(Res.string.r40SetupVoiceVerifying)
        val reporter = scope.launch {
            choice.models.state.collect { state ->
                if (!choice.models.ownsDownload(operation.token)) return@collect
                if (operation.cancelled) {
                    choice.models.cancelOwned(operation.token)
                    return@collect
                }
                state.job
                    ?.takeIf { it.pack == choice.pack.id }
                    ?.let { job ->
                        when (val phase = job.phase) {
                            is PackJobPhase.Downloading ->
                                onProgress(
                                    SetupAppProgress(downloading, phase.received, phase.total)
                                )
                            PackJobPhase.Verifying ->
                                onProgress(
                                    SetupAppProgress(
                                        verifying,
                                        choice.pack.totalBytes,
                                        choice.pack.totalBytes,
                                    )
                                )
                        }
                    }
            }
        }
        return try {
            if (operation.cancelled || epoch != cancellationEpoch.get())
                Failure.Refused(RefusalReason.StaleScope).failed()
            else choice.models.downloadOwned(choice.pack.id, operation.token)
        } catch (cancelled: CancellationException) {
            if (operation.cancelled || epoch != cancellationEpoch.get())
                Failure.Refused(RefusalReason.StaleScope).failed()
            else {
                currentCoroutineContext().ensureActive()
                Failure.Media(MediaProblem.DownloadClosed).failed()
            }
        } finally {
            reporter.cancel()
            if (owned === operation) owned = null
        }
    }

    override suspend fun cancel(id: String) {
        if (id != SetupComponentIds.VOICE) return
        cancellationEpoch.incrementAndGet()
        val operation = owned ?: return
        operation.cancelled = true
        operation.models.cancelOwned(operation.token)
    }

    suspend fun remove(): Outcome<Unit> {
        val parts = hub.parts()
        if (parts is Outcome.Failed) return parts
        val voice = (parts as Outcome.Ok).value
        if (voice.models.state.value.job != null || owned != null)
            return Failure.Media(MediaProblem.DownloadBusy).failed()
        when (voice.dictation.state.value) {
            VoiceState.Starting,
            is VoiceState.PreparingModel,
            is VoiceState.Listening,
            is VoiceState.Writing,
            VoiceState.Cancelling -> return Failure.Media(MediaProblem.DownloadBusy).failed()
            else -> Unit
        }
        for (pack in voice.models.speechPacks) when (val result = voice.models.remove(pack.id)) {
            is Outcome.Failed -> return result
            is Outcome.Ok -> Unit
        }
        voice.models.refresh()
        if (
            voice.models.speechPacks.any {
                voice.models.state.value.packs[it.id] == PackStatus.Installed
            }
        )
            return Failure.Media(MediaProblem.DownloadVerification).failed()
        return Outcome.Ok(Unit)
    }
}

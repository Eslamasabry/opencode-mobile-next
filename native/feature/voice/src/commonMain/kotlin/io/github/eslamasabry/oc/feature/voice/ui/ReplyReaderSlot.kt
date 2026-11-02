package io.github.eslamasabry.oc.feature.voice.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.metroViewModel
import io.github.eslamasabry.oc.feature.voice.presentation.ReadNote
import io.github.eslamasabry.oc.feature.voice.presentation.ReplyReaderPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.ReplyReaderUi
import io.github.eslamasabry.oc.feature.voice.screen.ReplyReaderViewModel
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirm
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmKind
import io.github.eslamasabry.oc.ui.kit.modal.KitConfirmSpec
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReadRequest
import io.github.eslamasabry.oc.ui.kitchat.composer.KitReplyReader
import io.github.eslamasabry.oc.ui.kitchat.composer.KitVoicePill
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.e7VoiceUiNotNow
import io.github.eslamasabry.oc.ui.words.res.kitVoiceSpeaking
import io.github.eslamasabry.oc.ui.words.res.kitVoiceStopReading
import io.github.eslamasabry.oc.ui.words.res.naturalVoiceFallbackNote
import io.github.eslamasabry.oc.ui.words.res.readAloudConsentDetail
import io.github.eslamasabry.oc.ui.words.res.readAloudConsentEngine
import io.github.eslamasabry.oc.ui.words.res.readAloudConsentHeard
import io.github.eslamasabry.oc.ui.words.res.readAloudConsentTitle
import io.github.eslamasabry.oc.ui.words.res.readAloudContinue
import io.github.eslamasabry.oc.ui.words.res.readAloudNoProse
import org.jetbrains.compose.resources.stringResource

/** The message menu's "Read aloud" (kit-chat `KitReplyReader`): consent once, the reading strip with Stop, notes. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<KitReplyReader>())
public class ReplyReaderSlot : KitReplyReader {
    @Composable
    override fun Reader(request: KitReadRequest?, onHandled: (id: Long) -> Unit, modifier: Modifier) {
        val vm = metroViewModel<ReplyReaderViewModel>()
        val presenter = vm.presenter
        val state by presenter.state.collectAsStateWithLifecycle()
        LaunchedEffect(request?.id) {
            val asked = request ?: return@LaunchedEffect
            presenter.read(asked.rowKey, asked.text)
            onHandled(asked.id)
        }
        LifecycleStartEffect(presenter) {
            presenter.hostChanged(resumed = true)
            onStopOrDispose { presenter.hostChanged(resumed = false) }
        }
        ReplyReaderView(state, presenter, modifier)
    }
}

@Composable
internal fun ReplyReaderView(state: ReplyReaderUi, presenter: ReplyReaderPresenter, modifier: Modifier = Modifier) {
    if (state.reading != null) {
        KitVoicePill(
            title = stringResource(Res.string.kitVoiceSpeaking),
            modifier = modifier,
            primary = KitLineAction(stringResource(Res.string.kitVoiceStopReading), presenter::stop, testTag = "read-aloud-stop"),
        )
    }
    val note = state.note
    if (note != null) {
        val dismiss = KitLineAction(stringResource(Res.string.e7VoiceUiNotNow), presenter::dismissNote)
        when (note) {
            ReadNote.NoProse -> KitNotice(stringResource(Res.string.readAloudNoProse), modifier, dismiss = dismiss)
            ReadNote.NaturalFallback -> KitNotice(stringResource(Res.string.naturalVoiceFallbackNote), modifier, dismiss = dismiss)
            is ReadNote.Failed ->
                KitNotice(problemWords(note.problem), modifier, tone = StatusTone.Failure, dismiss = dismiss)
        }
    }
    if (state.asking) {
        KitConfirm(
            spec =
                KitConfirmSpec(
                    title = stringResource(Res.string.readAloudConsentTitle),
                    body = stringResource(Res.string.readAloudConsentDetail),
                    consequences =
                        listOf(stringResource(Res.string.readAloudConsentHeard), stringResource(Res.string.readAloudConsentEngine)),
                    confirmLabel = stringResource(Res.string.readAloudContinue),
                    cancelLabel = stringResource(Res.string.e7VoiceUiNotNow),
                    kind = KitConfirmKind.Neutral,
                ),
            onConfirm = presenter::consent,
            onCancel = presenter::declineConsent,
        )
    }
}

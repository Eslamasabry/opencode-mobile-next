package io.github.eslamasabry.oc.feature.voice.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.feature.voice.presentation.ComposerVoicePresenter
import io.github.eslamasabry.oc.feature.voice.presentation.ReplyReaderPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceConversations
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceHub
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceSettingsPresenter
import io.github.eslamasabry.oc.feature.voice.presentation.VoiceTeachingHub
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.VoiceRoute
import kotlin.time.Clock

/** One conversation composer's voice ([key] = "profile:session"). */
@AssistedInject
public class ComposerVoiceViewModel(
    @Assisted private val key: String,
    hub: VoiceHub,
    clock: Clock,
    nav: AppNavigator,
    private val app: AppCoroutineScope,
    private val conversations: VoiceConversations,
    teaching: VoiceTeachingHub,
) : ViewModel() {
    public fun attachConversation(ref: io.github.eslamasabry.oc.core.common.ids.ConversationRef?) {
        presenter.attachConversation(ref, conversations)
    }

    public val presenter: ComposerVoicePresenter =
        ComposerVoicePresenter(hub, clock, viewModelScope) { nav.open(VoiceRoute.Settings) }

    init {
        presenter.attachTeaching(teaching)
    }

    override fun onCleared() {
        presenter.close(cleanup = app)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted key: String): ComposerVoiceViewModel
    }
}

/** Settings › Voice typing. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class VoiceSettingsViewModel(hub: VoiceHub, nav: AppNavigator) : ViewModel() {
    public val presenter: VoiceSettingsPresenter =
        VoiceSettingsPresenter(hub, viewModelScope) { nav.back() }
}

/**
 * The reply reader of one chat screen (the engine underneath is app-wide: one reading at a time).
 */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class ReplyReaderViewModel(hub: VoiceHub, private val app: AppCoroutineScope) : ViewModel() {
    public val presenter: ReplyReaderPresenter = ReplyReaderPresenter(hub, viewModelScope)

    override fun onCleared() {
        // Leaving the conversation stops its reading (plan 10 §8: reading stops when you leave the
        // conversation).
        presenter.stopIn(app)
    }
}

package io.github.eslamasabry.oc.feature.chat.composer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.domain.events.InterruptReason
import io.github.eslamasabry.oc.core.domain.events.RunOutcome
import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseChip
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMoment
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMomentRules
import io.github.eslamasabry.oc.feature.chat.composer.ComposerTarget
import io.github.eslamasabry.oc.feature.chat.world.ChatLink
import io.github.eslamasabry.oc.feature.chat.world.ChatWorlds
import io.github.eslamasabry.oc.feature.chat.world.TurnFeed
import io.github.eslamasabry.oc.feature.chat.world.TurnFeedSource
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * One real store adapter is contributed by Android; a host without one has no suggestions feature.
 */
public interface PhraseSuggestions {
    public val changes: StateFlow<Int>

    public suspend fun ready(profileId: String): Boolean

    public suspend fun enabled(): Boolean

    public suspend fun choices(
        profileId: String,
        moment: PhraseMoment,
        starters: List<String>,
        exclude: Set<String>,
    ): List<PhraseChip>

    public suspend fun record(
        profileId: String,
        text: String,
        moment: PhraseMoment,
        hasAttachments: Boolean,
    ): Boolean

    public suspend fun hide(profileId: String, text: String): Boolean
}

@ContributesTo(AppScope::class)
public interface PhraseSuggestionBindings {
    @Multibinds(allowEmpty = true) public val phraseSuggestionSources: Set<PhraseSuggestions>
}

public data class PhraseSuggestionsUi(
    val enabled: Boolean = false,
    val moment: PhraseMoment? = null,
    val continueShown: Boolean = false,
    val errorShown: Boolean = false,
    val phrases: List<PhraseChip> = emptyList(),
    val revision: Int = 0,
    val failed: Boolean = false,
)

/**
 * The actual message ledger supplies the moment; no title, button label or guessed state enters the
 * rules.
 */
@AssistedInject
public class PhraseSuggestionsViewModel(
    @Assisted private val target: ComposerTarget,
    private val worlds: ChatWorlds,
    sources: Set<PhraseSuggestions>,
    private val clock: Clock,
) : ViewModel() {
    private val source = sources.singleOrNull()
    private val world =
        target.sessionId?.let {
            worlds.open(ConversationRef(ProfileId(target.profileId), SessionId(it)))
        }
    private val mutable = MutableStateFlow(PhraseSuggestionsUi())
    public val state: StateFlow<PhraseSuggestionsUi> = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            val data = source ?: return@launch
            if (!data.ready(target.profileId)) return@launch
            val turn = (world as? TurnFeedSource)?.turnFeed ?: flowOf(TurnFeed.Empty)
            combine(turn, data.changes) { feed, revision -> feed to revision }
                .collect { (feed, revision) ->
                    val moment =
                        PhraseMomentRules.derive(
                            feed.ledger.messages,
                            feed.busy,
                            clock.now().toEpochMilliseconds(),
                            stoppedByPerson =
                                (feed.lastOutcome as? RunOutcome.Interrupted)?.reason ==
                                    InterruptReason.User,
                            offline = feed.link != ChatLink.Live,
                        )
                    val ended = feed.lastOutcome as? RunOutcome.Failed
                    val errorShown =
                        moment.errorShown ||
                            (ended != null && ended.kind != MessageErrorKind.OutputLength)
                    val enabled = data.enabled()
                    val currentMoment = moment.moment
                    val chips =
                        if (
                            enabled &&
                                currentMoment != null &&
                                currentMoment != PhraseMoment.Working &&
                                !errorShown
                        )
                            data.choices(target.profileId, currentMoment, emptyList(), emptySet())
                        else emptyList()
                    mutable.value =
                        PhraseSuggestionsUi(
                            enabled,
                            moment.moment,
                            moment.continueShown,
                            errorShown,
                            chips,
                            revision,
                        )
                }
        }
    }

    /** Localized built-ins are supplied by the screen, never placed as copy in a ViewModel. */
    public fun starters(continueWord: String, retryWord: String) {
        val current = mutable.value
        if (!current.enabled || current.moment != PhraseMoment.StoppedPartway || current.errorShown)
            return
        viewModelScope.launch {
            val data = source ?: return@launch
            val before = mutable.value
            val moment = before.moment ?: return@launch
            val choices =
                data.choices(
                    target.profileId,
                    moment,
                    if (moment == PhraseMoment.StoppedPartway) listOf(continueWord, retryWord)
                    else emptyList(),
                    if (before.continueShown) setOf(continueWord, "Continue") else emptySet(),
                )
            if (mutable.value.moment == moment)
                mutable.value = mutable.value.copy(phrases = choices)
        }
    }

    /**
     * The caller captured [moment] before admitting the send; files and disabled learning are
     * refused by the store.
     */
    public fun acknowledged(text: String, hasAttachments: Boolean, moment: PhraseMoment?) {
        viewModelScope.launch {
            val data = source ?: return@launch
            if (data.enabled())
                data.record(target.profileId, text, moment ?: PhraseMoment.Idle, hasAttachments)
        }
    }

    public fun hide(text: String) {
        viewModelScope.launch {
            if (source?.hide(target.profileId, text) != true)
                mutable.value = mutable.value.copy(failed = true)
        }
    }

    override fun onCleared() {
        world?.let(worlds::release)
    }

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    public fun interface Factory : ManualViewModelAssistedFactory {
        public fun create(@Assisted target: ComposerTarget): PhraseSuggestionsViewModel
    }
}

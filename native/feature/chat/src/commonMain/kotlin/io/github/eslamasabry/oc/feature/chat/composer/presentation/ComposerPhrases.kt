package io.github.eslamasabry.oc.feature.chat.composer.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.composer.phrases.PhraseMoment
import kotlinx.coroutines.flow.Flow

/** Device learned short phrases; providers enforce profile deletion and the global switch. */
public interface ComposerPhrases {
    public val changes: Flow<Unit>

    public fun suggestions(profile: ProfileId, moment: PhraseMoment): List<String>

    public suspend fun record(
        profile: ProfileId,
        text: String,
        moment: PhraseMoment,
        attachments: Boolean,
    )

    public suspend fun hide(profile: ProfileId, text: String): Boolean
}

@ContributesTo(AppScope::class)
public interface ComposerPhraseBindings {
    @Multibinds(allowEmpty = true) public val composerPhraseSources: Set<ComposerPhrases>
}

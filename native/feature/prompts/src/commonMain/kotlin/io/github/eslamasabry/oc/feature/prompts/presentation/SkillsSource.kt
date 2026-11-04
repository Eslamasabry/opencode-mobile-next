package io.github.eslamasabry.oc.feature.prompts.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.model.SkillInfo
import io.github.eslamasabry.oc.engine.integrations.catalog.SkillActivation

public interface SkillsSource {
    public fun available(ref: ConversationRef): Boolean

    public suspend fun list(ref: ConversationRef): Outcome<List<SkillInfo>>

    public suspend fun activate(ref: ConversationRef, skill: String): SkillActivation
}

@ContributesTo(AppScope::class)
public interface SkillsBindings {
    @Multibinds(allowEmpty = true) public val skillSources: Set<SkillsSource>
}

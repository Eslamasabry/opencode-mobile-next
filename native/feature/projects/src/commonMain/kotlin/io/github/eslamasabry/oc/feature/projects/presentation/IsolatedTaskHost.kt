package io.github.eslamasabry.oc.feature.projects.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.ModelRef
import io.github.eslamasabry.oc.engine.agents.worktree.IsolatedTaskLaunch
import kotlinx.coroutines.CoroutineScope

public interface IsolatedTaskHost {
    public fun redactTechnical(value: String): io.github.eslamasabry.oc.core.common.Redacted

    public suspend fun draft(profile: ProfileId): Outcome<String>

    public suspend fun saveDraft(profile: ProfileId, text: String): Outcome<Unit>

    public suspend fun open(
        scope: CoroutineScope,
        profile: ProfileId,
        directory: String,
        model: ModelRef?,
        agent: String?,
    ): Outcome<IsolatedTaskLaunch>
}

@ContributesTo(AppScope::class)
public interface IsolatedTaskBindings {
    @Multibinds(allowEmpty = true) public val isolatedTaskHosts: Set<IsolatedTaskHost>
}

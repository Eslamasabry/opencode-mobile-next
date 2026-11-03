package io.github.eslamasabry.oc.feature.about.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome

/** The bundled THIRD_PARTY_NOTICES.md, as the app ships it. Android binds the asset reader. */
public fun interface NoticesSource {
    public suspend fun load(): Outcome<String>
}

/** Desktop and tests: no notices file bundled; the page says so and offers Try again. */
@ContributesBinding(AppScope::class)
@Inject
public class NoNoticesSource : NoticesSource {
    override suspend fun load(): Outcome<String> = Outcome.Failed(Failure.Unavailable(FeatureId("notices")))
}

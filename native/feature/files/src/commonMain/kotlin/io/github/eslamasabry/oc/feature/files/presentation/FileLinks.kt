package io.github.eslamasabry.oc.feature.files.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome

/**
 * The policy-approved host to ask about, followed by a second policy check after the person
 * confirms.
 */
public interface FileLinks {
    public fun host(url: String): String?

    public fun openConfirmed(url: String): Outcome<Unit>
}

/** Platforms without an opener refuse explicitly; a preview never pretends a link was opened. */
@Inject
@ContributesBinding(AppScope::class)
public class NoFileLinks : FileLinks {
    override fun host(url: String): String? = null

    override fun openConfirmed(url: String): Outcome<Unit> =
        Outcome.Failed(Failure.Unavailable(FeatureId("external-links")))
}

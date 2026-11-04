package io.github.eslamasabry.oc.engine.integrations.catalog

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.attempt
import kotlin.time.Clock

/** Public pictures only. The app binding never uses a server or provider authenticated client. */
public interface ProviderLogoHost : ProviderLogoFiles {
    public suspend fun fetch(url: String): ProviderLogoResponse?

    public suspend fun bundled(slug: String): String?
}

/**
 * App-wide public logo cache: provider ids carry no account/profile data and survive profile
 * deletion.
 */
@SingleIn(AppScope::class)
@Inject
public class ProviderLogos(private val host: ProviderLogoHost, clock: Clock) {
    private val store =
        ProviderLogoStore(
            fetch = host::fetch,
            files = host,
            nowMs = { clock.now().toEpochMilliseconds() },
            loadBundled = { provider ->
                ProviderMarks.providerSlug(provider)?.let { host.bundled(it) }
            },
        )

    public suspend fun svgFor(providerId: String): String? = store.svgFor(providerId)

    public suspend fun agentSvg(iconKey: String): String? {
        val slug = ProviderMarks.agentSlug(iconKey) ?: return null
        return (attempt({ Failure.Device() }) { host.bundled(slug) } as? Outcome.Ok)?.value
    }
}

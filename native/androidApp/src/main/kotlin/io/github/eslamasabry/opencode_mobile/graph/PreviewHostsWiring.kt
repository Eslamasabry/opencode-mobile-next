package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.feature.preview.presentation.NoPreviewHosts
import io.github.eslamasabry.oc.feature.preview.presentation.PreviewHosts

/**
 * A phone agent's conversation sits on the phone agents' helper, which is never listed as a server; its previews open
 * on the phone's own address (issue 147 walk F-024, the same ownership as the Git page's).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoPreviewHosts::class])
@Inject
class AndroidPreviewHosts(private val stores: PreviewStores) : PreviewHosts {
    override fun addressFor(profile: ProfileId): String? =
        try {
            stores.phoneOfHelper(profile)?.let { stores.profiles.profile(it)?.profile?.baseUrl }
        } catch (_: Exception) {
            null
        }
}

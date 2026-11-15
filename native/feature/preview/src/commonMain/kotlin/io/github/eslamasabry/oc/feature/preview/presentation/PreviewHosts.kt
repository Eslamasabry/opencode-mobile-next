package io.github.eslamasabry.oc.feature.preview.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * The address a preview points at for a connection that is not a listed server (issue 147 walk F-024): the phone
 * agents' helper is part of This phone and never appears in the server list, so a phone agent's conversation found no
 * server and Preview said "Connect a server first". The app answers with its phone's own address.
 */
public fun interface PreviewHosts {
    public fun addressFor(profile: ProfileId): String?
}

@ContributesBinding(AppScope::class)
@Inject
public class NoPreviewHosts : PreviewHosts {
    override fun addressFor(profile: ProfileId): String? = null
}

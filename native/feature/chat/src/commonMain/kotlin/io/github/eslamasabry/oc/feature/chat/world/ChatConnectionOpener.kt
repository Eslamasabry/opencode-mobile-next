package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * Try again in a chat opens the connection of a computer that went away, by the app's one path for it (the home's
 * own Try again). This phone's server and the agents' helper never come here: the app starts them by itself.
 * An open chat holds its computer's connection ([hold]), so no background watch lets go of it meanwhile.
 */
public interface ChatConnectionOpener {
    /** Opens the connection of [profile]; its failure when that computer stays away. */
    public suspend fun open(profile: ProfileId): Outcome<Unit>

    /** [held] is true while an open chat shows [profile], and false again once it closes. */
    public fun hold(profile: ProfileId, held: Boolean)
}

@ContributesTo(AppScope::class)
public interface ChatConnectionOpenerBindings {
    @Multibinds(allowEmpty = true) public val chatConnectionOpeners: Set<ChatConnectionOpener>
}

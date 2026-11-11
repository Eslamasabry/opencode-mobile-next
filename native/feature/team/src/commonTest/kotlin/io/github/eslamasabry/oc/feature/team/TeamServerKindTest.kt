package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.feature.team.presentation.TeamServerKind
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phone's own server carries managedOnPhone from the profile store (PhoneServerProfile, wave 3 conn 2), so the kind
 * follows the flag alone, as Flutter's `teamServerKindOf` does. 87a1002623's loopback rule was the stopgap for the
 * missing flag; a loopback tunnel to a computer is a computer (PhoneServerProfileTest covers the flag's rule).
 */
class TeamServerKindTest {
    @Test
    fun theFlagMakesItThisPhone() = assertEquals(TeamServerKind.ThisPhone, RegistryTeamServerFacts.kindOf(true))

    @Test
    fun anotherServerIsAComputer() = assertEquals(TeamServerKind.Computer, RegistryTeamServerFacts.kindOf(false))
}

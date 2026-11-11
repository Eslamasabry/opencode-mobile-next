package io.github.eslamasabry.oc.feature.team

import io.github.eslamasabry.oc.feature.team.presentation.TeamServerKind
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamServerKindTest {
    @Test
    fun theFlagMakesItThisPhone() = assertEquals(TeamServerKind.ThisPhone, RegistryTeamServerFacts.kindOf(true, "studio-pc"))

    @Test
    fun aLoopbackServerIsThisPhoneEvenWithoutTheFlag() {
        for (host in listOf("127.0.0.1", "localhost", "::1"))
            assertEquals(TeamServerKind.ThisPhone, RegistryTeamServerFacts.kindOf(false, host))
    }

    @Test
    fun anotherHostIsAComputer() = assertEquals(TeamServerKind.Computer, RegistryTeamServerFacts.kindOf(false, "studio-pc"))
}

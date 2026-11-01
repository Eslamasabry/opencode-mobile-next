package io.github.eslamasabry.oc.android.agenthost

import org.junit.Test

/** B-LINUX-115: cleanup never kills another owner's helper (the other-owners ledger, one test per Flutter scenario). */
class PhoneAgentAuthOtherOwnersTest {
    @Test fun `B-LINUX-115 native other-agent ownership registered-helper`() = otherOwnersScenario("registered-helper")

    @Test fun `B-LINUX-115 native other-agent ownership private-excluded`() = otherOwnersScenario("private-excluded")

    @Test fun `B-LINUX-115 native other-agent ownership unknown-at-launch`() = otherOwnersScenario("unknown-at-launch")

    @Test fun `B-LINUX-115 native other-agent ownership dead-pruned`() = otherOwnersScenario("dead-pruned")

    @Test fun `B-LINUX-115 native other-agent ownership reused-pruned`() = otherOwnersScenario("reused-pruned")

    @Test fun `B-LINUX-115 native other-agent ownership unreadable-excluded`() = otherOwnersScenario("unreadable-excluded")

    @Test fun `B-LINUX-115 native other-agent ownership unreadable-launch`() = otherOwnersScenario("unreadable-launch")

    @Test fun `B-LINUX-115 native other-agent ownership invalid-birth`() = otherOwnersScenario("invalid-birth")
}

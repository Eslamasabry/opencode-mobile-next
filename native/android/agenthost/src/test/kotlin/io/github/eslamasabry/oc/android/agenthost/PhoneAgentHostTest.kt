package io.github.eslamasabry.oc.android.agenthost

import org.junit.Test

/** B-LINUX-117 (native phone host table) plus the rows a scenario also proves; one test per Flutter scenario. */
class PhoneAgentHostTest {
    @Test fun `B-LINUX-117 native phone host low-space-launch`() = phoneAgentHostScenario("low-space-launch")

    @Test fun `B-LINUX-117 native phone host healthy-launch`() = phoneAgentHostScenario("healthy-launch")

    @Test fun `B-LINUX-117 B-LINUX-129 native phone host exited-at-once`() = phoneAgentHostScenario("exited-at-once")

    @Test fun `B-LINUX-117 native phone host stale-paseo-lock`() = phoneAgentHostScenario("stale-paseo-lock")

    @Test fun `B-LINUX-117 native phone host answering-paseo-lock`() = phoneAgentHostScenario("answering-paseo-lock")

    @Test fun `B-LINUX-117 native phone host concurrent-start`() = phoneAgentHostScenario("concurrent-start")

    @Test fun `B-LINUX-117 B-LINUX-129 native phone host partial-inventory`() = phoneAgentHostScenario("partial-inventory")

    @Test fun `B-LINUX-117 B-LINUX-131 native phone host helper-version-restart`() = phoneAgentHostScenario("helper-version-restart")

    @Test fun `B-LINUX-117 B-LINUX-131 native phone host helper-invalid-update`() = phoneAgentHostScenario("helper-invalid-update")

    @Test fun `B-LINUX-117 native phone host helper-busy-update`() = phoneAgentHostScenario("helper-busy-update")

    @Test fun `B-LINUX-117 native phone host helper-unowned-update`() = phoneAgentHostScenario("helper-unowned-update")

    @Test fun `B-LINUX-117 native phone host helper-other-profile`() = phoneAgentHostScenario("helper-other-profile")

    @Test fun `B-LINUX-117 native phone host helper-restart-failure`() = phoneAgentHostScenario("helper-restart-failure")

    @Test fun `B-LINUX-117 native phone host helper-guarded-stop`() = phoneAgentHostScenario("helper-guarded-stop")
}

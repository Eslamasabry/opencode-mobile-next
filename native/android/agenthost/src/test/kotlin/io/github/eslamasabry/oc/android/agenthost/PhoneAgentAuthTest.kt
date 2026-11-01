package io.github.eslamasabry.oc.android.agenthost

import org.junit.Test
import java.io.File

/**
 * B-LINUX-115 (private agent auth through the native bridge, one test per Flutter scenario) and B-LINUX-130 (the probe
 * returns only narrow facts; an auth timeout differs from not signed in). The Dart-authored scripts are fixtures.
 */
class PhoneAgentAuthTest {
    private val fixtures = File(checkNotNull(javaClass.getResource("/auth-scripts/claude-probe.txt")).toURI()).parentFile

    @Test fun `B-LINUX-115 native private agent auth request-admission`() = phoneAgentAuthScenario("request-admission", fixtures)

    @Test fun `B-LINUX-115 native private agent auth request-rejection`() = phoneAgentAuthScenario("request-rejection", fixtures)

    @Test fun `B-LINUX-115 B-LINUX-130 native private agent auth projection-valid`() = phoneAgentAuthScenario("projection-valid", fixtures)

    @Test fun `B-LINUX-115 B-LINUX-130 native private agent auth projection-rejection`() = phoneAgentAuthScenario("projection-rejection", fixtures)

    @Test fun `B-LINUX-115 B-LINUX-130 native private agent auth private-success`() = phoneAgentAuthScenario("private-success", fixtures)

    @Test fun `B-LINUX-115 native private agent auth invalid-no-launch`() = phoneAgentAuthScenario("invalid-no-launch", fixtures)

    @Test fun `B-LINUX-115 native private agent auth prepare-denied`() = phoneAgentAuthScenario("prepare-denied", fixtures)

    @Test fun `B-LINUX-115 native private agent auth start-failure`() = phoneAgentAuthScenario("start-failure", fixtures)

    @Test fun `B-LINUX-115 B-LINUX-130 native private agent auth timeout`() = phoneAgentAuthScenario("timeout", fixtures)

    @Test fun `B-LINUX-115 B-LINUX-130 native private agent auth logout-timeout`() = phoneAgentAuthScenario("logout-timeout", fixtures)

    @Test fun `B-LINUX-115 native private agent auth wait-failure`() = phoneAgentAuthScenario("wait-failure", fixtures)

    @Test fun `B-LINUX-115 native private agent auth reader-failure`() = phoneAgentAuthScenario("reader-failure", fixtures)

    @Test fun `B-LINUX-115 B-LINUX-130 native private agent auth output-overflow`() = phoneAgentAuthScenario("output-overflow", fixtures)

    @Test fun `B-LINUX-115 native private agent auth output-overflow-timeout`() = phoneAgentAuthScenario("output-overflow-timeout", fixtures)

    @Test fun `B-LINUX-115 native private agent auth blocked-profile`() = phoneAgentAuthScenario("blocked-profile", fixtures)

    @Test fun `B-LINUX-115 native private agent auth retained-dead-root`() = phoneAgentAuthScenario("retained-dead-root", fixtures)

    @Test fun `B-LINUX-115 native private agent auth deletion-during-run`() = phoneAgentAuthScenario("deletion-during-run", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-dead-root`() = phoneAgentAuthScenario("tree-dead-root", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-pid-reuse`() = phoneAgentAuthScenario("tree-pid-reuse", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-zombie`() = phoneAgentAuthScenario("tree-zombie", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-unconfirmed-drain`() = phoneAgentAuthScenario("tree-unconfirmed-drain", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-missing-identity`() = phoneAgentAuthScenario("tree-missing-identity", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-inventory-failure`() = phoneAgentAuthScenario("tree-inventory-failure", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-root-only-then-orphan`() = phoneAgentAuthScenario("tree-root-only-then-orphan", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-signal-reuse`() = phoneAgentAuthScenario("tree-signal-reuse", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-failed-start-unknown`() = phoneAgentAuthScenario("tree-failed-start-unknown", fixtures)

    @Test fun `B-LINUX-115 native private agent auth tree-new-task-from-earlier-owner`() = phoneAgentAuthScenario("tree-new-task-from-earlier-owner", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-helper-after-baseline`() = phoneAgentAuthScenario("outside-helper-after-baseline", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-unknown-orphan`() = phoneAgentAuthScenario("outside-unknown-orphan", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-wrong-root-cookie`() = phoneAgentAuthScenario("outside-wrong-root-cookie", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-reused-root-cookie`() = phoneAgentAuthScenario("outside-reused-root-cookie", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-private-identities-win`() = phoneAgentAuthScenario("outside-private-identities-win", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-retained-helper-child`() = phoneAgentAuthScenario("outside-retained-helper-child", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-reused-helper-child`() = phoneAgentAuthScenario("outside-reused-helper-child", fixtures)

    @Test fun `B-LINUX-115 native private agent auth outside-registry-failure`() = phoneAgentAuthScenario("outside-registry-failure", fixtures)

    @Test fun `B-LINUX-115 native private agent auth private-success-helper-concurrent`() = phoneAgentAuthScenario("private-success-helper-concurrent", fixtures)

    @Test fun `B-LINUX-115 native private agent auth cold-registered-owner`() = phoneAgentAuthScenario("cold-registered-owner", fixtures)

    @Test fun `B-LINUX-115 native private agent auth cold-unknown-owner`() = phoneAgentAuthScenario("cold-unknown-owner", fixtures)

    @Test fun `B-LINUX-115 native private agent auth deadline-absolute-probe`() = phoneAgentAuthScenario("deadline-absolute-probe", fixtures)

    @Test fun `B-LINUX-115 native private agent auth deadline-absolute-claude-probe`() = phoneAgentAuthScenario("deadline-absolute-claude-probe", fixtures)

    @Test fun `B-LINUX-115 native private agent auth deadline-absolute-logout`() = phoneAgentAuthScenario("deadline-absolute-logout", fixtures)

    @Test fun `B-LINUX-115 native private agent auth inventory-valid`() = phoneAgentAuthScenario("inventory-valid", fixtures)

    @Test fun `B-LINUX-115 native private agent auth inventory-unreadable`() = phoneAgentAuthScenario("inventory-unreadable", fixtures)

    @Test fun `B-LINUX-115 native private agent auth inventory-malformed`() = phoneAgentAuthScenario("inventory-malformed", fixtures)

    @Test fun `B-LINUX-115 native private agent auth inventory-vanished`() = phoneAgentAuthScenario("inventory-vanished", fixtures)

    @Test fun `B-LINUX-115 native private agent auth inventory-cross-uid`() = phoneAgentAuthScenario("inventory-cross-uid", fixtures)

    @Test fun `B-LINUX-115 native private agent auth lock-empty-exact`() = phoneAgentAuthScenario("lock-empty-exact", fixtures)

    @Test fun `B-LINUX-115 native private agent auth lock-nonempty-retained`() = phoneAgentAuthScenario("lock-nonempty-retained", fixtures)

    @Test fun `B-LINUX-115 native private agent auth lock-foreign-symlink`() = phoneAgentAuthScenario("lock-foreign-symlink", fixtures)
}

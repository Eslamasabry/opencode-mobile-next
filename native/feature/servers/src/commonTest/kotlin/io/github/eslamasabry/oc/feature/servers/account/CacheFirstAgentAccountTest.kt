package io.github.eslamasabry.oc.feature.servers.account

import io.github.eslamasabry.oc.engine.agents.account.AccountLoginStatus
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelState
import io.github.eslamasabry.oc.engine.agents.account.AccountPanelStatus
import io.github.eslamasabry.oc.engine.agents.account.AccountRateBucket
import io.github.eslamasabry.oc.engine.agents.account.AccountRateWindow
import io.github.eslamasabry.oc.engine.agents.account.AccountTokenUsage
import io.github.eslamasabry.oc.engine.agents.account.AgentRuntimeAccount
import io.github.eslamasabry.oc.feature.servers.account.presentation.KeptAgentAccount
import io.github.eslamasabry.oc.feature.servers.account.presentation.shownPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.Json

class CacheFirstAgentAccountTest {
    private val live =
        AccountPanelState(
            status = AccountPanelStatus.Ready,
            account = AgentRuntimeAccount("chatgpt", "me@example.com", "plus", requiresSignIn = true),
            limits = persistentListOf(AccountRateBucket("codex", AccountRateWindow(40, 300, 1000L), null)),
            usage = AccountTokenUsage(5000L, 900L),
            updatedAt = Instant.fromEpochMilliseconds(123_000),
        )

    private fun kept(): KeptAgentAccount =
        Json.decodeFromString(
            KeptAgentAccount.serializer(),
            Json.encodeToString(KeptAgentAccount.serializer(), KeptAgentAccount.of(live)!!),
        )

    @Test
    fun `CF-90 the first frame with a kept account has the live identity, allowance and usage, updating quietly`() {
        val shown = shownPanel(AccountPanelState(), kept())
        assertTrue(shown.updating)
        assertEquals(live.copy(), shown.panel.copy(canSignIn = false, canCancel = false))
        assertFalse(shown.panel.canSignIn)
    }

    @Test
    fun `CF-91 a live answer replaces the kept one, and a login in progress is never covered`() {
        assertFalse(shownPanel(live, kept()).updating)
        val signing = AccountPanelState(loginStatus = AccountLoginStatus.Starting)
        assertEquals(signing, shownPanel(signing, kept()).panel)
    }

    @Test
    fun `CF-92 with nothing kept the first-ever read shows the loading panel`() {
        val loading = AccountPanelState()
        assertEquals(loading, shownPanel(loading, null).panel)
    }

    @Test
    fun `CF-93 only a finished read is kept`() {
        assertNull(KeptAgentAccount.of(live.copy(metricsLoading = true)))
        assertNull(KeptAgentAccount.of(AccountPanelState()))
    }
}

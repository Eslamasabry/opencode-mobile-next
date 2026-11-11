package io.github.eslamasabry.oc.feature.plans

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.IntegrationAuthLaunch
import io.github.eslamasabry.oc.core.model.IntegrationAuthStatus
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.engine.integrations.usage.UsageSnapshot
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInChoice
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInLink
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInPort
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInStep
import io.github.eslamasabry.oc.feature.plans.presentation.PlanSignInViewModel
import io.github.eslamasabry.oc.feature.plans.presentation.PlansSource
import io.github.eslamasabry.oc.feature.plans.presentation.planPromptVisible
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalCoroutinesApi::class)
class PlanSignInMethodsTest {
    private class Source : PlansSource {
        override val snapshot = MutableStateFlow(UsageSnapshot())
        override suspend fun refresh(force: Boolean) {}
        override suspend fun setThreshold(key: String?, percent: Double?) = false
        override suspend fun setResetNotice(key: String?, value: Boolean) = false
        override val defaultThreshold = 80.0
        override val resetNotice = false
    }

    private class Port(val rows: List<IntegrationInfo>) : PlanSignInPort {
        val started = mutableListOf<String>()
        val sent = mutableListOf<Map<String, String>>()
        private fun no(): Outcome.Failed = Outcome.Failed(Failure.Unavailable(FeatureId("test")))
        override suspend fun choices(provider: String) =
            Outcome.Ok(persistentListOf(PlanSignInChoice("conn", "Server")))
        override suspend fun openPhone(provider: String) = Outcome.Ok(Unit)
        override suspend fun integrations(connectionId: String, provider: String) =
            Outcome.Ok(rows.toImmutableList())
        override suspend fun connectKey(connectionId: String, integrationId: String, key: String) = Outcome.Ok(Unit)
        override suspend fun startOAuth(
            connectionId: String,
            integrationId: String,
            methodId: String,
            inputs: ImmutableMap<String, String>,
        ): Outcome<IntegrationAuthLaunch> {
            started += methodId
            sent += inputs
            return no()
        }
        override suspend fun startCommand(connectionId: String, integrationId: String, methodId: String): Outcome<IntegrationAuthLaunch> {
            started += methodId
            return no()
        }
        override suspend fun status(connectionId: String, integrationId: String, attemptId: String, command: Boolean): Outcome<IntegrationAuthStatus> = no()
        override suspend fun complete(connectionId: String, attemptId: String, code: String?) = Outcome.Ok(Unit)
        override suspend fun cancel(connectionId: String, integrationId: String, attemptId: String, command: Boolean) = Outcome.Ok(Unit)
        override fun copyCode(connectionId: String, code: String) = Outcome.Ok(Unit)
        override fun openLink(connectionId: String, url: String, confirmed: Boolean): Outcome<PlanSignInLink> = Outcome.Ok(PlanSignInLink.Opened)
        val keyPages = mutableListOf<String>()
        override fun openKeyPage(integrationId: String): Outcome<Unit> {
            keyPages += integrationId
            return Outcome.Ok(Unit)
        }
    }

    private fun m(type: String, id: String? = null, label: String = type, ask: Boolean = false) =
        IntegrationMethodInfo(
            type,
            id,
            label,
            if (ask) persistentListOf(buildJsonObject { put("key", "deploymentType"); put("required", true) }) else persistentListOf(),
            persistentListOf(),
            persistentMapOf(),
        )

    private fun info(id: String, vararg methods: IntegrationMethodInfo) =
        IntegrationInfo(id, id, methods.toList().toImmutableList(), persistentListOf(), 0)

    @BeforeTest fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun reset() = Dispatchers.resetMain()

    @Test
    fun `W4-20 one usable method goes straight to its step and Back leaves the flow`() = runTest {
        val vm = PlanSignInViewModel(Port(listOf(info("opencode-go", m("key"), m("env")))), Source())
        vm.open("opencode-go")
        assertEquals(PlanSignInStep.Key, vm.state.value.step)
        vm.dismiss()
        assertEquals(false, vm.state.value.active)
    }

    @Test
    fun `W4-21 several methods list sign-in first, then the key, then the browser sign-in`() = runTest {
        val rows =
            info(
                "openai",
                m("key"),
                m("oauth", "codex", "Codex browser (legacy)"),
                m("oauth", "chatgpt", "Sign in with ChatGPT"),
            )
        val vm = PlanSignInViewModel(Port(listOf(rows)), Source())
        vm.open("openai")
        assertEquals(PlanSignInStep.Methods, vm.state.value.step)
        assertEquals(listOf("chatgpt", "key", "codex"), vm.state.value.integrations.single().methods.map { it.id ?: it.type })
    }

    @Test
    fun `W4-22 a key-led provider with a key goes straight to the key`() = runTest {
        val rows = info("anthropic", m("oauth", "claude", "Claude account"), m("key"))
        val vm = PlanSignInViewModel(Port(listOf(rows)), Source())
        vm.open("anthropic")
        assertEquals(PlanSignInStep.Key, vm.state.value.step)
    }

    @Test
    fun `W4-25 the key step opens the provider's own key page, never a server link`() = runTest {
        val port = Port(listOf(info("anthropic", m("key"))))
        val vm = PlanSignInViewModel(port, Source())
        vm.open("anthropic")
        assertEquals(PlanSignInStep.Key, vm.state.value.step)
        vm.openKeyPage()
        assertEquals(listOf("anthropic"), port.keyPages)
    }

    @Test
    fun `W4-23 a sign-in that asks required questions is offered and asks them first`() = runTest {
        val port = Port(listOf(info("github-copilot", m("oauth", "device", "Login with GitHub Copilot", ask = true))))
        val vm = PlanSignInViewModel(port, Source())
        vm.open("github-copilot")
        assertEquals(PlanSignInStep.Form, vm.state.value.step)
        assertEquals(emptyList(), port.started)
    }

    @Test
    fun `W4-24 a command sign-in goes straight to starting on the server`() = runTest {
        val port = Port(listOf(info("acme", m("command", "login", "Server sign-in"))))
        val vm = PlanSignInViewModel(port, Source())
        vm.open("acme")
        assertEquals(listOf("login"), port.started)
    }

    private fun copilot() =
        IntegrationMethodInfo(
            "oauth",
            "device",
            "Login with GitHub Copilot",
            persistentListOf(
                buildJsonObject {
                    put("key", "deploymentType")
                    put("message", "Deployment type")
                    put("type", "select")
                    put("required", true)
                    put("default", "github.com")
                    put(
                        "options",
                        kotlinx.serialization.json.JsonArray(
                            listOf("github.com", "enterprise").map {
                                buildJsonObject { put("value", it); put("label", it) }
                            }
                        ),
                    )
                },
                buildJsonObject {
                    put("key", "enterpriseUrl")
                    put("message", "Enterprise URL")
                    put("type", "text")
                    put("required", true)
                    put(
                        "when",
                        kotlinx.serialization.json.JsonArray(
                            listOf(
                                buildJsonObject {
                                    put("key", "deploymentType")
                                    put("op", "eq")
                                    put("value", "enterprise")
                                }
                            )
                        ),
                    )
                },
            ),
            persistentListOf(),
            persistentMapOf(),
        )

    @Test
    fun `W4-29 Copilot asks its choice first and the address only for the enterprise answer`() = runTest {
        val port = Port(listOf(info("github-copilot", copilot())))
        val vm = PlanSignInViewModel(port, Source())
        vm.open("github-copilot")
        assertEquals(PlanSignInStep.Form, vm.state.value.step)
        val prompts = vm.state.value.method!!.prompts
        assertEquals(false, planPromptVisible(prompts[1], mapOf("deploymentType" to "github.com")))
        assertEquals(true, planPromptVisible(prompts[1], mapOf("deploymentType" to "enterprise")))

        // The default alone is enough: the hidden address is neither required nor sent.
        vm.submitForm(persistentMapOf("deploymentType" to "github.com", "enterpriseUrl" to "stale.example"))
        assertEquals(listOf(mapOf("deploymentType" to "github.com")), port.sent)
    }

    @Test
    fun `W4-29 the enterprise answer needs its address and sends both under their keys`() = runTest {
        val port = Port(listOf(info("github-copilot", copilot())))
        val vm = PlanSignInViewModel(port, Source())
        vm.open("github-copilot")
        vm.submitForm(persistentMapOf("deploymentType" to "enterprise", "enterpriseUrl" to ""))
        assertEquals(emptyList(), port.sent)
        assertEquals(setOf("enterpriseUrl"), vm.state.value.missingFields)
        vm.submitForm(persistentMapOf("deploymentType" to "enterprise", "enterpriseUrl" to "company.ghe.com"))
        assertEquals(
            listOf(mapOf("deploymentType" to "enterprise", "enterpriseUrl" to "company.ghe.com")),
            port.sent,
        )
    }
}

package io.github.eslamasabry.oc.feature.plans.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.SignInWhy
import io.github.eslamasabry.oc.core.model.IntegrationAuthState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One explicit provider sign-in; it never retries a mutation or stores a key/code in UI state. */
@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
public class PlanSignInViewModel(
    private val port: PlanSignInPort,
    private val source: PlansSource,
) : ViewModel(), PlanSignInActions {
    private val local = MutableStateFlow(PlanAuthenticationUi())
    public val state: StateFlow<PlanAuthenticationUi> = local.asStateFlow()
    private var generation = 0

    public fun open(providerId: String, connectionId: String? = null) {
        if (local.value.busy || local.value.active) return
        val mine = ++generation
        local.value =
            PlanAuthenticationUi(
                active = true,
                provider = providerId,
                busy = true,
                inputRevision = mine,
            )
        viewModelScope.launch {
            when (val result = port.choices(providerId)) {
                is Outcome.Failed ->
                    if (mine == generation)
                        local.update {
                            it.copy(
                                busy = false,
                                failure = result.failure,
                                step = PlanSignInStep.Unavailable,
                            )
                        }
                is Outcome.Ok ->
                    if (mine == generation) {
                        local.update {
                            it.copy(
                                choices = result.value,
                                busy = false,
                                step =
                                    if (result.value.isEmpty()) PlanSignInStep.Unavailable
                                    else PlanSignInStep.Places,
                            )
                        }
                        val choice =
                            result.value.firstOrNull { it.id == connectionId }
                                ?: result.value.singleOrNull()
                        if (choice != null) choosePlace(choice.id)
                    }
            }
        }
    }

    override fun dismiss() {
        if (local.value.busy) return
        val old = local.value
        val mine = ++generation
        local.value = PlanAuthenticationUi(inputRevision = mine)
        val attempt = old.launch
        val place = old.place
        val integration = old.integration
        if (
            attempt != null &&
                place != null &&
                integration != null &&
                old.step != PlanSignInStep.Complete
        )
            viewModelScope.launch {
                port.cancel(
                    place.id,
                    integration.id,
                    attempt.attemptId,
                    old.method?.type == "command",
                )
            }
    }

    override fun choosePlace(id: String) {
        val before = local.value
        val place = before.choices.firstOrNull { it.id == id } ?: return
        if (place.phone) {
            operation { mine ->
                when (val opened = port.openPhone(before.provider)) {
                    is Outcome.Failed -> failed(mine, opened.failure)
                    is Outcome.Ok ->
                        if (mine == generation) {
                            generation++
                            local.value = PlanAuthenticationUi(inputRevision = generation)
                        }
                }
            }
            return
        }
        operation { mine ->
            when (val result = port.integrations(place.id, before.provider)) {
                is Outcome.Failed -> failed(mine, result.failure)
                is Outcome.Ok ->
                    if (mine == generation)
                        local.update {
                            it.copy(
                                place = place,
                                integrations = result.value,
                                step =
                                    if (result.value.isEmpty()) PlanSignInStep.Unavailable
                                    else PlanSignInStep.Methods,
                            )
                        }
            }
        }
    }

    override fun chooseMethod(integrationId: String, methodId: String?, type: String) {
        if (local.value.busy) return
        val integration = local.value.integrations.firstOrNull { it.id == integrationId } ?: return
        val method =
            integration.methods.firstOrNull { it.id == methodId && it.type == type } ?: return
        if (type !in setOf("oauth", "key", "command")) return
        local.update {
            it.copy(
                integration = integration,
                method = method,
                failure = null,
                inputRevision = it.inputRevision + 1,
                step = if (type == "key") PlanSignInStep.Key else PlanSignInStep.Form,
            )
        }
        if (type == "command") {
            val place = local.value.place ?: return
            val id = method.id ?: return
            operation { mine ->
                when (val result = port.startCommand(place.id, integration.id, id)) {
                    is Outcome.Failed -> failed(mine, result.failure)
                    is Outcome.Ok ->
                        if (mine == generation)
                            local.update {
                                it.copy(
                                    launch = result.value,
                                    deviceCode =
                                        providerSignInDeviceCode(result.value.instructions),
                                    codeCopied = false,
                                    step = PlanSignInStep.OAuth,
                                    inputRevision = it.inputRevision + 1,
                                )
                            }
                }
            }
        } else if (type == "oauth" && method.prompts.isEmpty()) submitForm(persistentMapOf())
    }

    override fun submitKey(key: String) {
        val s = local.value
        val place = s.place ?: return
        val integration = s.integration ?: return
        if (key.isBlank()) return
        operation { mine ->
            when (val result = port.connectKey(place.id, integration.id, key)) {
                is Outcome.Failed -> failed(mine, result.failure)
                is Outcome.Ok -> completed(mine)
            }
        }
    }

    override fun submitForm(inputs: ImmutableMap<String, String>) {
        val s = local.value
        val place = s.place ?: return
        val integration = s.integration ?: return
        val method = s.method ?: return
        val id = method.id ?: return
        val answers = method.presetAnswers.toPersistentMap().putAll(inputs)
        val visible = method.prompts.filter { planPromptVisible(it, answers) }
        if (visible.any { it.text("key").isNullOrBlank() }) {
            failed(generation, Failure.UnreadableReply())
            return
        }
        val missing =
            visible
                .filter {
                    it["required"] != JsonPrimitive(false) &&
                        inputs[it.text("key").orEmpty()].isNullOrBlank()
                }
                .mapNotNull { it.text("key") }
        if (missing.isNotEmpty()) {
            local.update { it.copy(missingFields = missing.toImmutableSet()) }
            return
        }
        val submitted =
            method.presetAnswers
                .toPersistentMap()
                .putAll(
                    visible
                        .mapNotNull { prompt ->
                            val key = prompt.text("key") ?: return@mapNotNull null
                            inputs[key]?.let { key to it }
                        }
                        .toMap()
                )
        operation { mine ->
            when (val result = port.startOAuth(place.id, integration.id, id, submitted)) {
                is Outcome.Failed -> failed(mine, result.failure)
                is Outcome.Ok ->
                    if (mine == generation)
                        local.update {
                            it.copy(
                                launch = result.value,
                                deviceCode = providerSignInDeviceCode(result.value.instructions),
                                codeCopied = false,
                                step = PlanSignInStep.OAuth,
                                inputRevision = it.inputRevision + 1,
                            )
                        }
            }
        }
    }

    override fun copyCode() {
        val s = local.value
        val code = s.deviceCode ?: return
        val place = s.place ?: return
        when (val copied = port.copyCode(place.id, code)) {
            is Outcome.Failed -> local.update { it.copy(failure = copied.failure) }
            is Outcome.Ok -> local.update { it.copy(codeCopied = true) }
        }
    }

    override fun openPage(): Unit = page(false)

    override fun confirmPage(): Unit = page(true)

    private fun page(confirmed: Boolean) {
        val launch = local.value.launch ?: return
        val place = local.value.place ?: return
        when (val result = port.openLink(place.id, launch.url, confirmed)) {
            is Outcome.Failed ->
                local.update { it.copy(failure = result.failure, askedHost = null) }
            is Outcome.Ok ->
                local.update {
                    it.copy(askedHost = (result.value as? PlanSignInLink.Ask)?.host, failure = null)
                }
        }
    }

    override fun check() {
        val s = local.value
        val place = s.place ?: return
        val launch = s.launch ?: return
        val integration = s.integration ?: return
        operation { mine ->
            when (
                val result =
                    port.status(
                        place.id,
                        integration.id,
                        launch.attemptId,
                        s.method?.type == "command",
                    )
            ) {
                is Outcome.Failed -> failed(mine, result.failure)
                is Outcome.Ok ->
                    when (result.value.state) {
                        IntegrationAuthState.Complete -> completed(mine)
                        IntegrationAuthState.Pending -> Unit
                        IntegrationAuthState.Failed,
                        IntegrationAuthState.Expired ->
                            failed(mine, Failure.SignIn(SignInWhy.TokenRejected))
                    }
            }
        }
    }

    override fun finish(code: String) {
        val s = local.value
        val place = s.place ?: return
        val launch = s.launch ?: return
        val integration = s.integration ?: return
        if (s.method?.type == "command") return
        operation { mine ->
            when (
                val result =
                    port.complete(place.id, launch.attemptId, code.takeIf { it.isNotBlank() })
            ) {
                is Outcome.Failed -> failed(mine, result.failure)
                is Outcome.Ok ->
                    when (
                        val checked = port.status(place.id, integration.id, launch.attemptId, false)
                    ) {
                        is Outcome.Failed -> failed(mine, checked.failure)
                        is Outcome.Ok ->
                            when (checked.value.state) {
                                IntegrationAuthState.Complete -> completed(mine)
                                IntegrationAuthState.Failed,
                                IntegrationAuthState.Expired ->
                                    failed(mine, Failure.SignIn(SignInWhy.TokenRejected))
                                IntegrationAuthState.Pending -> Unit
                            }
                    }
            }
        }
    }

    private suspend fun completed(mine: Int) {
        source.refresh()
        if (mine == generation)
            local.update {
                it.copy(
                    step = PlanSignInStep.Complete,
                    deviceCode = null,
                    codeCopied = false,
                    launch = null,
                    askedHost = null,
                    inputRevision = it.inputRevision + 1,
                )
            }
    }

    private fun failed(mine: Int, failure: Failure) {
        if (mine == generation) local.update { it.copy(failure = failure) }
    }

    private fun operation(action: suspend (Int) -> Unit) {
        if (!local.value.active || local.value.busy) return
        val mine = generation
        local.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                action(mine)
            } finally {
                if (mine == generation) local.update { it.copy(busy = false) }
            }
        }
    }
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.content

/** OC2's form conditions use the same eq/neq answer rules as Flutter. */
public fun planPromptVisible(prompt: JsonObject, answers: Map<String, String>): Boolean {
    val condition = prompt["when"] as? JsonObject ?: return true
    val key = condition.text("key") ?: return true
    val value = condition.text("value") ?: return true
    return when (condition.text("op")) {
        "eq" -> answers[key] == value
        "neq" -> answers[key] != value
        else -> true
    }
}

/**
 * Only a short shouted token explicitly introduced as a code is displayed; raw provider
 * instructions stay private.
 */
public fun providerSignInDeviceCode(instructions: String): String? {
    val code =
        Regex(
                """(?:[Ee]nter\s+(?:the\s+|your\s+)?)?[Cc]ode\s*[:：]\s*([A-Z0-9]{3,}(?:-[A-Z0-9]{2,})*)(?![A-Za-z0-9-])"""
            )
            .find(instructions)
            ?.groupValues
            ?.get(1)
    return code?.takeIf { it.length in 4..24 }
}

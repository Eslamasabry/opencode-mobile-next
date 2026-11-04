package io.github.eslamasabry.oc.feature.plans.presentation

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.IntegrationAuthLaunch
import io.github.eslamasabry.oc.core.model.IntegrationAuthStatus
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf

public data class PlanSignInChoice(val id: String, val name: String, val phone: Boolean = false, val inApp: Boolean = false)
public sealed interface PlanSignInLink {
    public data object Opened : PlanSignInLink
    public data class Ask(val host: String) : PlanSignInLink
}
public interface PlanSignInPort {
    public suspend fun choices(provider: String): Outcome<ImmutableList<PlanSignInChoice>>
    public suspend fun integrations(connectionId: String, provider: String): Outcome<ImmutableList<IntegrationInfo>>
    public suspend fun connectKey(connectionId: String, integrationId: String, key: String): Outcome<Unit>
    public suspend fun startOAuth(connectionId: String, integrationId: String, methodId: String, inputs: ImmutableMap<String,String>): Outcome<IntegrationAuthLaunch>
    public suspend fun status(connectionId: String, attemptId: String): Outcome<IntegrationAuthStatus>
    public suspend fun complete(connectionId: String, attemptId: String, code: String?): Outcome<Unit>
    public suspend fun cancel(connectionId: String, attemptId: String): Outcome<Unit>
    public fun openLink(url: String, confirmed: Boolean): Outcome<PlanSignInLink>
}
public enum class PlanSignInStep { Places, Methods, Key, Form, OAuth, Complete, Unavailable }
public data class PlanAuthenticationUi(
    val active: Boolean = false,
    val provider: String = "",
    val step: PlanSignInStep = PlanSignInStep.Places,
    val choices: ImmutableList<PlanSignInChoice> = persistentListOf(),
    val place: PlanSignInChoice? = null,
    val integrations: ImmutableList<IntegrationInfo> = persistentListOf(),
    val integration: IntegrationInfo? = null,
    val method: IntegrationMethodInfo? = null,
    val launch: IntegrationAuthLaunch? = null,
    val askedHost: String? = null,
    val busy: Boolean = false,
    val failure: Failure? = null,
    /** Increments whenever inputs must be cleared, including after completion or dismissal. */
    val inputRevision: Int = 0,
) {
    override fun toString(): String = "PlanAuthenticationUi(active=$active, step=$step, busy=$busy)"
}
public interface PlanSignInActions {
    public fun dismiss()
    public fun choosePlace(id: String)
    public fun chooseMethod(integrationId: String, methodId: String?, type: String)
    public fun submitKey(key: String)
    public fun submitForm(inputs: ImmutableMap<String,String>)
    public fun openPage()
    public fun confirmPage()
    public fun check()
    public fun finish(code: String)
}

package io.github.eslamasabry.oc.protocol.paseo.requests

import io.github.eslamasabry.oc.protocol.paseo.PaseoFailureKind
import io.github.eslamasabry.oc.protocol.paseo.arr
import io.github.eslamasabry.oc.protocol.paseo.obj
import io.github.eslamasabry.oc.protocol.paseo.paseoFail
import io.github.eslamasabry.oc.protocol.paseo.str
import io.github.eslamasabry.oc.protocol.paseo.stringOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * ACP agents through Paseo (plan 04 §8: "No ACP code exists in the native app"; the app only reads what the phone
 * helper's daemon reports). Ports of lib/paseo/host_agent_providers.dart and the protocol-neutral types of
 * lib/domain/host_agent_providers.dart. TODO(move-to-core): the neutral types belong in :core:model with the
 * HostAgentProvider facet (contract request in the S3-PASEO report). No copy here: words live in :ui:words.
 */

/** Host availability controls new chats. Restoration proof controls reopening old chats, never visibility. */
public enum class HostAgentAvailability { Ready, NeedsHostSignIn, Checking, Hidden }

public enum class HostAgentHiddenReason { ResumeUnverified, ResumeUnsupported, NotPilot, Unavailable, Disabled }

public enum class HostAgentLoginState { Unknown, Ready, NeedsHostSignIn }

public enum class HostAgentResumeSupport { Unknown, Unsupported, LoadSession, ListAndLoad }

/** Protocol-neutral host truth. Missing proof stays unknown (never inferred from an installed executable). */
public data class HostAgentProvider(
    val id: String,
    /** A product name from the agent catalog, never the host's label. */
    val displayName: String,
    val availability: HostAgentAvailability,
    val hiddenReason: HostAgentHiddenReason? = null,
    val loginState: HostAgentLoginState,
    val resumeSupport: HostAgentResumeSupport,
) {
    init {
        require(hiddenReason != HostAgentHiddenReason.ResumeUnverified && hiddenReason != HostAgentHiddenReason.ResumeUnsupported) {
            "Restoration alone cannot hide a host agent."
        }
        if (availability == HostAgentAvailability.Hidden) {
            require(hiddenReason != null) { "A hidden host agent needs a reason." }
        } else {
            require(hiddenReason == null) { "Only a hidden host agent can have a hidden reason." }
        }
        require(!(availability == HostAgentAvailability.Ready && loginState == HostAgentLoginState.NeedsHostSignIn)) {
            "A signed-out host agent cannot be ready."
        }
        require(availability != HostAgentAvailability.NeedsHostSignIn || loginState == HostAgentLoginState.NeedsHostSignIn) {
            "A host sign-in request needs host sign-in state."
        }
    }

    val resumeVerified: Boolean
        get() = resumeSupport == HostAgentResumeSupport.LoadSession || resumeSupport == HostAgentResumeSupport.ListAndLoad

    val selectable: Boolean get() = availability == HostAgentAvailability.Ready && loginState != HostAgentLoginState.NeedsHostSignIn
}

public data class HostAgentCatalog(val providers: ImmutableList<HostAgentProvider>) {
    val selectable: ImmutableList<HostAgentProvider> get() = providers.filter { it.selectable }.toImmutableList()
}

/** Old chats with unknown restoration need an explicit new-chat action; a live chat continues without proof. */
public enum class HostAgentContinuationState { ExistingRoute, LiveSession, ResumeUnverified, MissingHandle }

public data class HostAgentContinuation(val sessionId: String, val providerId: String, val state: HostAgentContinuationState) {
    val requiresNewChat: Boolean
        get() = state == HostAgentContinuationState.ResumeUnverified || state == HostAgentContinuationState.MissingHandle
}

public enum class HostAgentPermissionBehavior { AllowOnce, RejectOnce }

/** The action id is opaque and only ever sent back to the same host. */
public data class HostAgentPermissionChoice(val actionId: String, val behavior: HostAgentPermissionBehavior)

public data class HostAgentPermissionRequest(
    val requestId: String,
    val sessionId: String,
    val choices: ImmutableList<HostAgentPermissionChoice>,
) {
    init {
        val ids = mutableSetOf<String>()
        for (choice in choices) require(choice.actionId.isNotBlank() && ids.add(choice.actionId)) { "Permission choices need unique, nonempty IDs." }
    }
}

// ---- pure projections of the daemon's snapshots

/** The runtimes with their own native route in the app (Copilot included). */
public fun isExistingPaseoProvider(id: String?): Boolean = id in EXISTING

private val EXISTING = setOf("claude", "codex", "pi", "opencode", "copilot")

/** The app's product name for a runtime it names itself; null lets the daemon's label stand. */
public fun paseoAgentProductName(provider: String): String? =
    when (provider) {
        "claude" -> "Claude Code"
        "pi" -> "Pi"
        "codex" -> "Codex"
        else -> null
    }

private val PROVIDER_ID = Regex("^[a-z][a-z0-9_-]{0,63}$")

public fun isPaseoProviderId(id: String?): Boolean = id != null && PROVIDER_ID.matches(id)

private val SIGN_IN_NEEDED = Regex(
    """AuthRequired|Authentication required|\bnot (?:logged|signed) in\b|""" +
        """\b(?:run|use) \S+ (?:login|auth)\b|\b(?:log|sign) ?in\b|""" +
        """\bapi[ _-]?key\b|\bunauthori[sz]ed\b|\b401\b""",
    RegexOption.IGNORE_CASE,
)

/** A provider error that asks the person to sign in (B-AGENT-175). The error text itself never leaves here. */
public fun paseoProviderNeedsSignIn(entry: JsonObject): Boolean {
    val error = entry.str("error") ?: return false
    return SIGN_IN_NEEDED.containsMatchIn(error)
}

/** Ready, enabled, error-free, not signed out, and not a custom override of an existing route (B-AGENT-142/143). */
public fun paseoProviderCanStart(entry: JsonObject): Boolean {
    val id = entry.str("provider")
    val error = entry["error"]
    return isPaseoProviderId(id) &&
        !entry.isFalse("enabled") &&
        entry.str("status") == "ready" &&
        (error == null || error is kotlinx.serialization.json.JsonNull || error.stringOrNull() == "") &&
        !(entry.str("source") == "custom" && isExistingPaseoProvider(id)) &&
        !paseoProviderNeedsSignIn(entry)
}

/** Catalog names by provider id (lib/domain/agent_catalog.dart `AgentCatalog.builtIn`), never the host's label. */
private val CATALOG_NAMES = mapOf(
    "opencode" to "OpenCode 2",
    "claude" to "Claude Code",
    "codex" to "Codex",
    "gemini" to "Gemini CLI",
    "qwen" to "Qwen Code",
    "kilo" to "Kilo",
    "cline" to "Cline",
    "goose" to "Goose",
    "omp-acp" to "Oh My Pi",
    "fx" to "fx",
)

public fun paseoHostAgentName(id: String): String =
    CATALOG_NAMES[id] ?: when (id) {
        "omp" -> "omp"
        "copilot" -> "GitHub Copilot"
        "pi" -> "Pi"
        else -> "Other host agent"
    }

/** Dart `paseoHostAgentCatalog`: one row per valid provider id, first entry wins; resume support stays Unknown. */
public fun paseoHostAgentCatalog(entries: List<JsonObject>): HostAgentCatalog {
    val seen = mutableSetOf<String>()
    val rows = mutableListOf<HostAgentProvider>()
    for (entry in entries) {
        val id = entry.str("provider")
        if (!isPaseoProviderId(id) || !seen.add(id!!)) continue
        val loginRequired = paseoProviderNeedsSignIn(entry)
        val disabled = entry.isFalse("enabled")
        val checking = !disabled && entry.str("status") == "loading"
        val canStart = paseoProviderCanStart(entry)
        rows += HostAgentProvider(
            id = id,
            displayName = paseoHostAgentName(id),
            availability = when {
                checking -> HostAgentAvailability.Checking
                disabled -> HostAgentAvailability.Hidden
                loginRequired -> HostAgentAvailability.NeedsHostSignIn
                canStart -> HostAgentAvailability.Ready
                else -> HostAgentAvailability.Hidden
            },
            hiddenReason = when {
                checking -> null
                disabled -> HostAgentHiddenReason.Disabled
                loginRequired || canStart -> null
                else -> HostAgentHiddenReason.Unavailable
            },
            loginState = if (loginRequired) HostAgentLoginState.NeedsHostSignIn else HostAgentLoginState.Unknown,
            resumeSupport = HostAgentResumeSupport.Unknown,
        )
    }
    return HostAgentCatalog(rows.toImmutableList())
}

/**
 * Restart state of a conversation (B-AGENT-144): live when this connection created or loaded it ([live]) or the
 * daemon holds it; otherwise a missing or foreign persistence handle is MissingHandle, an existing native route is
 * ExistingRoute, and every other runtime is ResumeUnverified (undeclared capability fields never prove resume).
 */
public fun paseoHostAgentContinuation(sessionId: String, agent: JsonObject, live: Boolean): HostAgentContinuation {
    val provider = agent.str("provider")?.takeIf { it.isNotEmpty() && it.length <= 128 } ?: paseoFail(PaseoFailureKind.InvalidResponse)
    val handle = agent.obj("persistence")
    val handleId = handle?.str("sessionId")
    val intact = handle != null && handle.str("provider") == provider && !handleId.isNullOrEmpty() && handleId.length <= 512
    val holds = "activeTurn" in agent && agent.str("status") in setOf("initializing", "running", "idle")
    val state = when {
        live || holds -> HostAgentContinuationState.LiveSession
        !intact -> HostAgentContinuationState.MissingHandle
        isExistingPaseoProvider(provider) -> HostAgentContinuationState.ExistingRoute
        else -> HostAgentContinuationState.ResumeUnverified
    }
    return HostAgentContinuation(sessionId, provider, state)
}

/**
 * Dart `paseoHostPermission`: null for a native runtime's request (claude, codex, pi, opencode without ACP options);
 * else the request with only the offered one-call actions whose option kind and action behaviour agree (B-SEC-109).
 * Any contradiction (duplicate ids, unmatched option, behaviour disagreeing with its kind) drops every choice, so the
 * request can only be denied by cancelling the turn. Standing (`*_always`) and unknown kinds never become choices.
 */
public fun paseoHostPermission(sessionId: String, request: JsonObject, provider: String?): HostAgentPermissionRequest? {
    val metadata = request.obj("metadata")
    val hasOptions = metadata != null && "options" in metadata
    val runtime = provider ?: request.str("provider")
    if (!hasOptions && isExistingPaseoProvider(runtime) && runtime != "copilot") return null
    val requestId = request.str("id")
    if (requestId.isNullOrEmpty() || requestId.length > 512) paseoFail(PaseoFailureKind.InvalidResponse)
    val choices = mutableListOf<HostAgentPermissionChoice>()
    val options = if (hasOptions) metadata!!.arr("options") else null
    val actions = request.arr("actions")
    if (options != null && options.size <= 64 && actions != null && actions.size <= 64) {
        val seen = mutableSetOf<String>()
        val actionIds = mutableSetOf<String>()
        var valid = true
        for (action in actions) {
            val id = (action as? JsonObject)?.str("id")
            if (id == null || !actionIds.add(id)) {
                valid = false
                break
            }
        }
        if (valid) {
            for (option in options) {
                if (option !is JsonObject) {
                    valid = false
                    break
                }
                val id = option.str("optionId")
                if (id.isNullOrEmpty() || id.length > 512 || !seen.add(id) || id !in actionIds) {
                    valid = false
                    break
                }
                val matching = actions.filterIsInstance<JsonObject>().single { it.str("id") == id }
                val behavior = when (option.str("kind")) {
                    "allow_once" -> HostAgentPermissionBehavior.AllowOnce
                    "reject_once" -> HostAgentPermissionBehavior.RejectOnce
                    else -> null
                } ?: continue
                if (matching.str("behavior") != (if (behavior == HostAgentPermissionBehavior.AllowOnce) "allow" else "deny")) {
                    valid = false
                    break
                }
                choices += HostAgentPermissionChoice(id, behavior)
            }
        }
        if (!valid || seen.size != actionIds.size) choices.clear()
    }
    return HostAgentPermissionRequest(requestId, sessionId, choices.toImmutableList())
}

private fun JsonObject.isFalse(key: String): Boolean {
    val value: JsonElement? = this[key]
    return value is JsonPrimitive && !value.isString && value.content == "false"
}

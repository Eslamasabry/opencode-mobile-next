package io.github.eslamasabry.oc.engine.aisetup

import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The server settings page shows the "AI setup" entry (Dart key `ai-setup-entry`) only on a server that shares its
 * configuration: gated on the capability, never on the backend kind.
 */
public fun aiSetupEntryVisible(capabilities: ServerCapabilities): Boolean = capabilities.setupConfigRead

/** A read-only suggestion derived from a redacted snapshot (Dart `SetupSuggestionKind`); it carries no edit. */
public enum class SetupSuggestionKind {
    /** "Sign in to {name}" / "Its tools stay off until someone signs in to it on the server." */
    SignInTool,

    /** "Check {name}'s settings" / "It failed to start. Fix its entry in the server's configuration, then …" */
    FixTool,

    /** "Choose a default model" / "No model is set, so new conversations use the server's own pick. …" */
    ChooseModel,

    /** "Add tool servers" / "No MCP servers are set up. Add one in the server's configuration …" */
    AddTools,
}

public data class SetupSuggestion(val kind: SetupSuggestionKind, val subject: String? = null)

/** What needs the person first, what is fine last. Unknown statuses sort with waiting ones. */
public fun setupToolUrgency(status: SetupToolStatus): Int =
    when (status) {
        SetupToolStatus.NeedsAuth, SetupToolStatus.NeedsClientRegistration -> 0
        SetupToolStatus.Failed -> 1
        SetupToolStatus.Pending, SetupToolStatus.Unknown -> 2
        SetupToolStatus.Disabled -> 3
        SetupToolStatus.Connected -> 4
    }

/** OpenCode 2's ordered source list, shown as it is and never merged into an invented effective view. */
public fun setupConfigIsLayered(config: JsonObject): Boolean = config.containsKey("sources")

public fun setupSuggestions(snapshot: SetupSnapshot): List<SetupSuggestion> {
    val servers = snapshot.servers.sortedBy { setupToolUrgency(it.status) }
    val config = snapshot.config
    val defined = config["mcp"] as? JsonObject
    return buildList {
        for (server in servers) {
            if (setupToolUrgency(server.status) == 0) {
                add(SetupSuggestion(SetupSuggestionKind.SignInTool, server.name))
            } else if (server.status == SetupToolStatus.Failed) {
                add(SetupSuggestion(SetupSuggestionKind.FixTool, server.name))
            }
        }
        if (!setupConfigIsLayered(config) && !config.containsKey("model")) {
            add(SetupSuggestion(SetupSuggestionKind.ChooseModel))
        }
        if (servers.isEmpty() && defined.isNullOrEmpty()) add(SetupSuggestion(SetupSuggestionKind.AddTools))
    }
}

/** The line at the top of a page that shows a read. */
public enum class AiSetupNotice {
    /** "Review only. Changes are made on the server for now." */
    ReviewOnly,

    /** "Offline. This is {server}'s setup as last read; it updates when you reconnect." */
    OfflineStale,
}

/** One configuration source as the server reports it (OpenCode 2), in its reported order. */
public data class AiSetupSource(
    /** 1-based: "{position}. Sets {keys}" or "{position}. Sets nothing". */
    val position: Int,
    /** Null: "Source without a file ({type})". */
    val path: String?,
    val type: String,
    val keys: List<String>,
)

public sealed interface AiSetupConfigView {
    /**
     * OpenCode 1, "Settings in effect": [model] null reads "Not set: the server picks"; [permissionRules] is the
     * "{count} rules" plural; [permissionText] a scalar permission. [allSettings] is the redacted JSON fold.
     */
    public data class Effective(
        val model: String?,
        val smallModel: String?,
        val defaultAgent: String?,
        val providers: List<String>,
        val permissionRules: Int?,
        val permissionText: String?,
        val allSettings: String,
    ) : AiSetupConfigView

    /** OpenCode 2, "Configuration sources"; empty shows "No configuration files". [allSources] is the redacted fold. */
    public data class Sources(val sources: List<AiSetupSource>, val allSources: String?) : AiSetupConfigView
}

/** Which state the review page renders (Dart AiSetupScreen `_body`). */
public sealed interface AiSetupReviewBody {
    /** "Reading this server's setup…" (key `ai-setup-loading`). */
    public data object Loading : AiSetupReviewBody

    /** "AI setup isn't available" / "This server doesn't share its configuration with the app. …" */
    public data object Unsupported : AiSetupReviewBody

    /** "Sign-in needed" / "{server} didn't accept the saved sign-in, …"; action "Change sign-in for {server}". */
    public data object NeedsSignIn : AiSetupReviewBody

    /**
     * "Couldn't read setup" / "The server didn't answer as expected. …"; [details] only under Details; [canRetry]
     * offers "Try again".
     */
    public data class Error(val details: SetupReason?, val canRetry: Boolean) : AiSetupReviewBody

    /** "You're offline" / "Reconnect to {server} to read its setup." (no read yet). */
    public data object Offline : AiSetupReviewBody

    /** "Nothing set up yet" / "This server runs on its defaults, …" (key `ai-setup-empty`). */
    public data object Empty : AiSetupReviewBody

    /** Suggestions, then tools most urgent first, then the configuration. Nothing applies, proposes or undoes. */
    public data class Content(
        val notice: AiSetupNotice,
        val suggestions: List<SetupSuggestion>,
        val tools: List<SetupTool>,
        val configuration: AiSetupConfigView?,
    ) : AiSetupReviewBody
}

/** The review page's whole state; [canRefresh] shows "Read this server's setup again" only over a shown read. */
public data class AiSetupReviewState(val body: AiSetupReviewBody, val canRefresh: Boolean)

/** The page's actions (review only: nothing proposes, applies or undoes). Change sign-in is navigation, in the UI. */
public interface AiSetupReviewActions {
    /** "Read this server's setup again" and "Try again". */
    public fun refresh()
}

private val prettyJson = Json { prettyPrint = true }

public fun aiSetupReviewState(snapshot: SetupSnapshot, online: Boolean): AiSetupReviewState {
    val hasData = snapshot.config.isNotEmpty() || snapshot.servers.isNotEmpty()
    val canRefresh = online && (snapshot.phase == SetupPhase.Ready || snapshot.phase == SetupPhase.Empty)
    val body: AiSetupReviewBody =
        when (snapshot.phase) {
            SetupPhase.Idle, SetupPhase.Loading, SetupPhase.Applying, SetupPhase.Verifying, SetupPhase.Undoing ->
                if (hasData) content(snapshot) else AiSetupReviewBody.Loading
            SetupPhase.Unsupported -> AiSetupReviewBody.Unsupported
            SetupPhase.NeedsSignIn -> AiSetupReviewBody.NeedsSignIn
            SetupPhase.Error, SetupPhase.Uncertain -> AiSetupReviewBody.Error(snapshot.reason, canRetry = online)
            SetupPhase.Offline -> if (hasData) content(snapshot) else AiSetupReviewBody.Offline
            SetupPhase.Empty -> AiSetupReviewBody.Empty
            SetupPhase.Ready -> content(snapshot)
        }
    return AiSetupReviewState(body, canRefresh)
}

private fun content(snapshot: SetupSnapshot): AiSetupReviewBody.Content {
    val config = snapshot.config
    return AiSetupReviewBody.Content(
        notice = if (snapshot.phase == SetupPhase.Offline) AiSetupNotice.OfflineStale else AiSetupNotice.ReviewOnly,
        suggestions = setupSuggestions(snapshot),
        tools = snapshot.servers.sortedBy { setupToolUrgency(it.status) },
        configuration = when {
            setupConfigIsLayered(config) -> sources(config["sources"])
            config.isNotEmpty() -> effective(config)
            else -> null
        },
    )
}

private fun JsonElement?.nonBlank(): String? = textOrNull()?.takeIf { it.isNotBlank() }

private fun effective(config: JsonObject): AiSetupConfigView.Effective {
    val providers = config["provider"] as? JsonObject
    val permission = config["permission"]
    return AiSetupConfigView.Effective(
        model = config["model"].nonBlank(),
        smallModel = config["small_model"].nonBlank(),
        defaultAgent = config["default_agent"].nonBlank(),
        providers = providers?.keys?.toList().orEmpty(),
        permissionRules = (permission as? JsonObject)?.size?.takeIf { it > 0 },
        permissionText = if (permission is JsonObject && permission.isNotEmpty()) null else permission.nonBlank(),
        allSettings = prettyJson.encodeToString(JsonElement.serializer(), config),
    )
}

private fun sources(value: JsonElement?): AiSetupConfigView.Sources {
    val list = value as? JsonArray ?: JsonArray(emptyList())
    return AiSetupConfigView.Sources(
        sources = list.mapIndexed { index, source ->
            val entry = source as? JsonObject ?: JsonObject(emptyMap())
            AiSetupSource(
                position = index + 1,
                path = entry["path"].textOrNull()?.takeIf { it.isNotEmpty() },
                type = entry["type"].textOrNull().orEmpty(),
                keys = (entry["info"] as? JsonObject)?.keys?.toList().orEmpty(),
            )
        },
        allSources = if (list.isEmpty()) null else prettyJson.encodeToString(JsonElement.serializer(), list),
    )
}

package io.github.eslamasabry.oc.engine.usagereaders.computer

import io.github.eslamasabry.oc.core.common.dart.DartJson
import io.github.eslamasabry.oc.engine.usagereaders.ProviderQuotaSnapshot
import io.github.eslamasabry.oc.engine.usagereaders.ProviderQuotaStatus
import io.github.eslamasabry.oc.engine.usagereaders.QuotaProvider
import kotlin.io.encoding.Base64

/*
 * Reading what the one-shot reader (`tool/quota/read_once.mjs`) prints in a terminal (Flutter
 * `lib/state/computer_usage_source.dart`, parsing part). The output itself is never kept.
 */

/** The reader script that ships with the app. */
public const val COMPUTER_USAGE_ASSET: String = "tool/quota/read_once.mjs"

/** The one line the reader prints. */
public const val COMPUTER_USAGE_MARKER: String = "OCMN_USAGE_V1"

/** The title the app gives its usage-check terminal. Terminal lists leave it out: it is not the person's. */
public const val COMPUTER_USAGE_TITLE: String = "OpenCode Mobile usage check"

/** Why a computer could not be read. Plain reasons only; none carries server or process text. */
public enum class ComputerUsageProblem {
    /** Node.js is not on the computer's PATH. */
    NodeMissing,

    /** Node.js is older than 20. */
    NodeTooOld,

    /** This OpenCode has no terminal API the check can use. */
    ServerTooOld,

    /** The server did not answer. */
    Unreachable,

    /** The check started but never printed its answer. */
    NoAnswer,
}

/** The providers the reader looked for and what it found. [auth] is `oauth` or `api` per signed-in provider. */
public class ComputerUsageResult(
    public val snapshots: Map<QuotaProvider, ProviderQuotaSnapshot> = emptyMap(),
    public val auth: Map<QuotaProvider, String> = emptyMap(),
    public val problem: ComputerUsageProblem? = null,
) {
    public val ok: Boolean get() = problem == null

    /** Providers with a plan the check could use (a plan sign-in, or a key that is itself the plan). */
    public val found: List<QuotaProvider>
        get() = QuotaProvider.entries.filter { it.collectionAvailable && auth.containsKey(it) && !payAsYouGo(it) }

    /** An OpenAI API key, or a MiniMax pay-as-you-go key: it has no plan limits. */
    public fun payAsYouGo(provider: QuotaProvider): Boolean =
        auth[provider] == "api" &&
            (
                provider == QuotaProvider.Codex ||
                    (provider == QuotaProvider.Minimax && snapshots[provider]?.status == ProviderQuotaStatus.Unsupported)
                )

    override fun toString(): String = "ComputerUsageResult(${snapshots.size} snapshots, $problem)"
}

private val ANSI = Regex("\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B\\[[0-9;?<=>]*[ -/]*[@-~]|\u001B[@-Z\\\\-_]")
private val MARKER_LINE = Regex("^$COMPUTER_USAGE_MARKER ([A-Za-z0-9+/]+={0,2})$")
private val LINE_BREAKS = Regex("[\r\n]+")
private const val MAX_ENCODED = 96 * 1024

/** Removes terminal control codes. */
public fun stripTerminalCodes(text: String): String = text.replace(ANSI, "")

/**
 * Finds the reader's answer in raw terminal [output]: echo, colour codes and carriage returns are ignored; only a line
 * that is exactly the marker and a base64 JSON document counts. Null when there is none (yet).
 */
public fun parseComputerUsageOutput(output: String): ComputerUsageResult? {
    var found: ComputerUsageResult? = null
    for (raw in stripTerminalCodes(output).split(LINE_BREAKS)) {
        val match = MARKER_LINE.find(raw.trim()) ?: continue
        decode(match.groupValues[1])?.let { found = it }
    }
    return found
}

private fun decode(encoded: String): ComputerUsageResult? {
    if (encoded.length > MAX_ENCODED) return null
    val bytes = try {
        Base64.Default.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(encoded)
    } catch (e: IllegalArgumentException) {
        return null
    }
    val json = DartJson.decodeOrNull(bytes.decodeToString()) as? Map<*, *> ?: return null
    if (json["v"] != 1L) return null
    val error = json["error"]
    if (error != null) {
        return ComputerUsageResult(
            problem = if (error == "nodeTooOld") ComputerUsageProblem.NodeTooOld else ComputerUsageProblem.NoAnswer,
        )
    }
    val snapshots = LinkedHashMap<QuotaProvider, ProviderQuotaSnapshot>()
    val providers = json["providers"] as? Map<*, *>
    if (providers != null) {
        for (provider in QuotaProvider.entries) {
            val raw = providers[provider.wire] ?: continue
            // One provider's bad answer does not hide the others.
            ProviderQuotaSnapshot.parse(raw)?.let { snapshots[provider] = it }
        }
    }
    val auth = LinkedHashMap<QuotaProvider, String>()
    val rawAuth = json["auth"] as? Map<*, *>
    if (rawAuth != null) {
        for (provider in QuotaProvider.entries) {
            val type = rawAuth[provider.wire]
            if (type == "oauth" || type == "api") auth[provider] = type as String
        }
    }
    return ComputerUsageResult(snapshots, auth)
}

/** Words in the output that say why Node itself did not run. Used only to choose a reason; never kept. */
public fun classifyComputerUsageOutput(output: String): ComputerUsageProblem {
    val text = stripTerminalCodes(output).lowercase()
    if (listOf("not found", "not recognized", "enoent", "no such file").any { it in text }) {
        return ComputerUsageProblem.NodeMissing
    }
    if (listOf("syntaxerror", "err_unknown_builtin", "unexpected token", "requires node").any { it in text }) {
        return ComputerUsageProblem.NodeTooOld
    }
    return ComputerUsageProblem.NoAnswer
}

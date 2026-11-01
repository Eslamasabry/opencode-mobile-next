package io.github.eslamasabry.oc.engine.agents.catalog

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** The shared helper pins (lib/builtin/agents/paseo_scripts.dart). */
public data class PaseoPins(
    val version: String,
    val packageLockSha256: String,
    val cliSha256: String,
    val payloadSha256: String,
    val codeTreeSha256: String,
) {
    /** `/home/oc/.local/share/oc-paseo/<version>-<first 12 of the lock hash>`. */
    public val installDirectory: String get() = "/home/oc/.local/share/oc-paseo/$version-${packageLockSha256.take(12)}"
}

public data class NodePins(val version: String, val sha256: ImmutableMap<AgentArchitecture, String>)

/**
 * The verified pins (plan 17 §1.3): one file, `native/engine/agents/pins/agent-pins.json`, read by this engine and by
 * the install scripts. A pin moves only with an app release (S3-PINS). [claudeCodeTestedVersions] must equal the
 * Kotlin `TESTED_CLAUDE_VERSIONS` of `:android:agenthost` (B-AGENT-050, checked there).
 */
public class AgentPins internal constructor(
    public val paseo: PaseoPins,
    public val node: NodePins,
    public val claudeCodeTestedVersions: ImmutableList<String>,
    /** `paseoTestedVersions` (lib/paseo/tested_versions.dart): helper versions certification evidence carries across. */
    public val paseoTestedVersions: ImmutableList<String>,
    private val agents: List<AgentDescriptor>,
) {
    public fun catalog(): AgentCatalog = AgentCatalog(agents)

    public companion object {
        public val builtIn: AgentPins by lazy { parse(AGENT_PINS_JSON) }

        private val json = Json { ignoreUnknownKeys = false }

        /** Throws on a malformed file: the bundled pins are build input, so a bad file is a bug, never a runtime state. */
        public fun parse(text: String): AgentPins {
            val root = json.parseToJsonElement(text).jsonObject
            require(root.int("schema") == 1L) { "Unknown pins schema." }
            val paseo = root.obj("paseo").let {
                PaseoPins(
                    version = it.str("version"),
                    packageLockSha256 = it.sha("packageLockSha256"),
                    cliSha256 = it.sha("cliSha256"),
                    payloadSha256 = it.sha("payloadSha256"),
                    codeTreeSha256 = it.sha("codeTreeSha256"),
                )
            }
            val node = root.obj("node").let { n ->
                NodePins(
                    version = n.str("version"),
                    sha256 = n.obj("sha256").entries.associate { (k, v) ->
                        requireNotNull(AgentArchitecture.fromWire(k)) { "Unknown architecture." } to
                            v.jsonPrimitive.content.also { require(AgentArtifact.SHA256.matches(it)) }
                    }.toImmutableMap(),
                )
            }
            val tested = root.getValue("claudeCodeTestedVersions").jsonArray.map { it.jsonPrimitive.content }
            val paseoTested = root.getValue("paseoTestedVersions").jsonArray.map { it.jsonPrimitive.content }
            val agents = root.getValue("agents").jsonArray.map { descriptor(it.jsonObject) }
            return AgentPins(paseo, node, tested.toImmutableList(), paseoTested.toImmutableList(), agents)
        }

        private fun descriptor(o: JsonObject): AgentDescriptor {
            val id = AgentId(o.str("id"))
            val r = o.obj("recipe")
            val recipe = AgentInstallRecipe(
                version = r.str("version"),
                executable = r.str("executable"),
                artifacts = r.obj("artifacts").entries.associate { (arch, a) ->
                    requireNotNull(AgentArchitecture.fromWire(arch)) { "Unknown architecture." } to artifact(a.jsonObject)
                },
                launchArgs = r.strings("launchArgs"),
                signInArgs = r.strings("signInArgs"),
            )
            return AgentDescriptor(
                id = id,
                name = o.str("name"),
                iconKey = id.raw,
                route = when (o.str("route")) {
                    "opencode2" -> AgentRoute.Opencode2
                    "paseoNative" -> AgentRoute.PaseoNative
                    "acpPaseo" -> AgentRoute.AcpPaseo
                    else -> throw IllegalArgumentException("Unknown route.")
                },
                providerId = o.str("providerId"),
                signInMethod = when (o.str("signInMethod")) {
                    "browserOAuthHost" -> AgentSignInMethod.BrowserOAuthHost
                    "apiKeyHost" -> AgentSignInMethod.ApiKeyHost
                    "none" -> AgentSignInMethod.None
                    else -> throw IllegalArgumentException("Unknown sign-in method.")
                },
                recipe = recipe,
                limitation = builtInLimitation(id),
                resumeReason = AgentResumeReason.ResumeUnverified,
            )
        }

        /** The Dart `entry(…, limitation:)` overrides; every other entry says "Needs a check on your phone before use." */
        internal fun builtInLimitation(id: AgentId): AgentLimitation =
            when (id.raw) {
                "gemini" -> AgentLimitation.GeminiHostSignIn
                "qwen" -> AgentLimitation.QwenProviderKey
                "kilo" -> AgentLimitation.KiloSignInOrKey
                "cline" -> AgentLimitation.ClineSignIn
                "goose" -> AgentLimitation.GooseProvider
                "omp-acp" -> AgentLimitation.OmpLogin
                "fx" -> AgentLimitation.FxProvider
                else -> AgentLimitation.NeedsPhoneCheck
            }

        private fun artifact(o: JsonObject): AgentArtifact =
            AgentArtifact(
                url = o.str("url"),
                sha256 = o.str("sha256"),
                format = when (o.str("format")) {
                    "executable" -> AgentArtifactFormat.Executable
                    "tarGz" -> AgentArtifactFormat.TarGz
                    "tarBz2" -> AgentArtifactFormat.TarBz2
                    "zip" -> AgentArtifactFormat.Zip
                    "npmTarGz" -> AgentArtifactFormat.NpmTarGz
                    else -> throw IllegalArgumentException("Unknown artifact format.")
                },
                archiveMember = (o["member"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content,
                downloadBytes = (o["downloadBytes"] as? JsonPrimitive)?.longOrNull,
                installedBytes = (o["installedBytes"] as? JsonPrimitive)?.longOrNull,
            )

        private fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
        private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.also { require(it.isString) }.content
        private fun JsonObject.int(key: String): Long? = (get(key) as? JsonPrimitive)?.longOrNull
        private fun JsonObject.sha(key: String): String = str(key).also { require(AgentArtifact.SHA256.matches(it)) }
        private fun JsonObject.strings(key: String): List<String> =
            (get(key) as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
    }
}

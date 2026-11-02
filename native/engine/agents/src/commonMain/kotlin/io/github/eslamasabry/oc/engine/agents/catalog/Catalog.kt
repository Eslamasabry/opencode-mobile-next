package io.github.eslamasabry.oc.engine.agents.catalog

import io.github.eslamasabry.oc.engine.agents.AgentId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

// Port of lib/domain/agent_catalog.dart (AGENTS-BACKENDS-01, plan 17 §1.3). App-authored installation metadata,
// separate from runtime qualification: a pinned download never proves sign-in, resume or phone compatibility.
// The validators are invariants of bundled data (a broken catalog is a bug), so they throw IllegalArgumentException
// like the Dart ArgumentError; nothing a person or a server sends reaches them.

public enum class AgentRoute { Opencode2, PaseoNative, AcpPaseo }

public enum class AgentArchitecture(public val wire: String) {
    Arm64("arm64"),
    X64("x64"),
    ;

    public companion object {
        public fun fromWire(value: String): AgentArchitecture? = entries.firstOrNull { it.wire == value }
    }
}

public enum class AgentArtifactFormat { Executable, TarGz, TarBz2, Zip, NpmTarGz }

public enum class AgentSignInMethod { BrowserOAuthHost, ApiKeyHost, None }

public enum class AgentAvailability { Available, Limited, Hidden }

public enum class AgentUnavailableReason { RecipeUnverified, DependencyClosureUnverified, UnsupportedArchitecture }

/**
 * The plain limitation line of a `Limited` agent, as a words key (the English of each is in the S3-AGENTSE report;
 * `:ui:words` owns the copy). Closed: every value has one sentence.
 */
public enum class AgentLimitation {
    /** "Needs a check on your phone before use." */
    NeedsPhoneCheck,
    GeminiHostSignIn,
    QwenProviderKey,
    KiloSignInOrKey,
    ClineSignIn,
    GooseProvider,
    OmpLogin,
    FxProvider,
    /** Test and future catalog entries that only need host setup. */
    NeedsHostSetup,
}

/** Why resume is not verified, as a words key ("Can't reopen old chats"). */
public enum class AgentResumeReason { ResumeUnverified, SavedChatsNeedCheck }

/** Proof obtained for the selected runtime, never inferred from agent identity. Defaults fail closed. */
public data class AgentCapabilities(
    val resumeVerified: Boolean = false,
    val modelList: Boolean = false,
    val permissions: Boolean = false,
    val images: Boolean = false,
    val cancel: Boolean = false,
) {
    public companion object {
        public val None: AgentCapabilities = AgentCapabilities()
    }
}

public class AgentArtifact(
    public val url: String,
    public val sha256: String,
    public val format: AgentArtifactFormat,
    /** Entrypoint for simple archives; for NpmTarGz the whole package tree is kept and this member is run by Node. */
    public val archiveMember: String? = null,
    public val downloadBytes: Long? = null,
    /** Payload size, not total disk use. Null means unknown; never substitute an estimate. */
    public val installedBytes: Long? = null,
) {
    init {
        require(isHttpsWithoutCredentials(url)) { "Artifact URL must be HTTPS without credentials." }
        require(SHA256.matches(sha256)) { "Artifact needs an exact lowercase SHA-256 pin." }
        if (format == AgentArtifactFormat.Executable) {
            require(archiveMember == null) { "Executable artifacts have no archive member." }
        } else {
            require(archiveMember != null && safeMember(archiveMember)) { "Archive artifacts need a safe relative member." }
        }
        require((downloadBytes == null || downloadBytes > 0) && (installedBytes == null || installedBytes > 0)) {
            "Known artifact sizes must be positive."
        }
    }

    override fun equals(other: Any?): Boolean =
        other is AgentArtifact && other.url == url && other.sha256 == sha256 && other.format == format &&
            other.archiveMember == archiveMember && other.downloadBytes == downloadBytes &&
            other.installedBytes == installedBytes

    override fun hashCode(): Int = sha256.hashCode()

    override fun toString(): String = "AgentArtifact($format, sha256=${sha256.take(12)}…)"

    public companion object {
        internal val SHA256 = Regex("^[a-f0-9]{64}$")

        /** Dart `Uri`: scheme https, nonempty host, no user info, no fragment. */
        public fun isHttpsWithoutCredentials(url: String): Boolean {
            if (!url.startsWith("https://")) return false
            if ('#' in url) return false
            val rest = url.substring("https://".length)
            val authority = rest.substring(0, rest.indexOfAny(charArrayOf('/', '?')).let { if (it < 0) rest.length else it })
            if ('@' in authority) return false
            val host = authority.substringBefore(':')
            return host.isNotEmpty() && host.none { it.isWhitespace() }
        }

        public fun safeMember(value: String): Boolean {
            val normalized = if (value.startsWith("./")) value.substring(2) else value
            return normalized.isNotEmpty() && !normalized.startsWith("/") && '\\' !in normalized &&
                '\u0000' !in normalized && normalized.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
        }
    }
}

public class AgentInstallRecipe(
    public val version: String,
    public val executable: String,
    artifacts: Map<AgentArchitecture, AgentArtifact>,
    launchArgs: List<String> = emptyList(),
    signInArgs: List<String> = emptyList(),
) {
    public val artifacts: ImmutableMap<AgentArchitecture, AgentArtifact> = artifacts.toImmutableMap()

    /** Argument arrays are data, never shell fragments. ACP provider config uses executable + launchArgs. */
    public val launchArgs: ImmutableList<String> = launchArgs.toImmutableList()

    /** Empty means interactive host setup; it does not mean no authentication. */
    public val signInArgs: ImmutableList<String> = signInArgs.toImmutableList()

    init {
        require(version.isNotBlank() && EXECUTABLE.matches(executable) && artifacts.isNotEmpty()) {
            "Recipe needs a version, executable, and artifact."
        }
        require((launchArgs + signInArgs).none { '\u0000' in it }) { "Arguments cannot contain NUL." }
    }

    internal companion object {
        val EXECUTABLE = Regex("^[a-z][a-z0-9-]*$")
    }
}

public class AgentDescriptor(
    public val id: AgentId,
    public val name: String,
    public val iconKey: String,
    public val route: AgentRoute,
    public val providerId: String,
    public val signInMethod: AgentSignInMethod,
    public val recipe: AgentInstallRecipe? = null,
    public val capabilities: AgentCapabilities = AgentCapabilities.None,
    public val availability: AgentAvailability = AgentAvailability.Limited,
    public val unavailableReason: AgentUnavailableReason? = null,
    public val limitation: AgentLimitation? = null,
    public val resumeReason: AgentResumeReason? = null,
) {
    init {
        require(ID.matches(id.raw) && providerId.isNotBlank() && name.isNotBlank() && iconKey.isNotBlank()) {
            "Agent identity must be nonempty and stable."
        }
        require(!((recipe == null || availability == AgentAvailability.Hidden) && unavailableReason == null)) {
            "An unavailable agent needs an explicit reason."
        }
        require(!(availability != AgentAvailability.Hidden && unavailableReason != null)) {
            "Unavailable agents must be hidden."
        }
        require(capabilities.resumeVerified || resumeReason != null) { "Unverified resume needs a plain explanation." }
        require(availability != AgentAvailability.Limited || limitation != null) {
            "Limited agents need a plain explanation."
        }
    }

    /** True when the row shows "Can't reopen old chats" and the note "Starts a new chat" (words keys). */
    public val resumeUnverified: Boolean get() = !capabilities.resumeVerified

    public fun artifactFor(architecture: AgentArchitecture): AgentArtifact? = recipe?.artifacts?.get(architecture)

    /** Installation availability does not make this agent selectable for chat. */
    public fun installableOn(architecture: AgentArchitecture): Boolean =
        unavailableReason == null && artifactFor(architecture) != null

    /** Runs on the phone's agent host (Paseo); server types (OpenCode) are not phone agents. */
    public val runsOnPhoneHost: Boolean get() = route == AgentRoute.PaseoNative || route == AgentRoute.AcpPaseo

    override fun toString(): String = "AgentDescriptor(${id.raw})"

    internal companion object {
        val ID = Regex("^[a-z][a-z0-9-]*$")
    }
}

public class AgentCatalog(agents: List<AgentDescriptor>) {
    public val agents: ImmutableList<AgentDescriptor> = agents.toImmutableList()

    init {
        require(agents.map { it.id }.toSet().size == agents.size) { "Agent IDs must be unique." }
    }

    public fun byId(id: AgentId): AgentDescriptor? = agents.firstOrNull { it.id == id }

    public companion object {
        public val Empty: AgentCatalog = AgentCatalog(persistentListOf())

        /** The bundled catalog, parsed once from [AgentPins.builtIn]. */
        public val builtIn: AgentCatalog by lazy { AgentPins.builtIn.catalog() }
    }
}

package io.github.eslamasabry.oc.engine.cards

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What a path is, read without following a final symlink. */
public enum class RootEntry { Directory, File, Link, Missing, Other }

/**
 * The app-owned built-in Linux root as the card helpers need it (the Android binding is java.nio over
 * `filesDir/linux/ubuntu`). Nothing here takes a shell command: a bearer never reaches process arguments.
 */
public interface CardsRootFs {
    /** The app-support directory with symlinks resolved; the root is `<support>/linux/ubuntu`. */
    public suspend fun supportRealPath(): String

    public suspend fun entry(path: String): RootEntry

    /** POSIX permission bits of a directory (0..0x1ff), null when unreadable. */
    public suspend fun mode(path: String): Int?

    /**
     * Publishes [bytes] as `<directory>/<name>` with mode 0600: written to a private temporary file in [directory],
     * then renamed over the target. False on any failure (the temporary file is removed).
     */
    public suspend fun publishPrivate(directory: String, name: String, bytes: ByteArray): Boolean
}

/** Whose Linux user runs an agent (Flutter `AgentRunUser`): the agent user lives under a per-profile home. */
public enum class CardsRunUser { AgentUser, Root }

/** One agent the card helpers are installed for (Flutter `GenUiAgent`). [configured] = it has a helper config. */
public data class CardsHelperAgent(val id: String, val runsAs: CardsRunUser, val configured: Boolean = true) {
    public companion object {
        public val Claude: CardsHelperAgent = CardsHelperAgent("claude", CardsRunUser.AgentUser)
        public val OpenCode2: CardsHelperAgent = CardsHelperAgent("openCode2", CardsRunUser.Root)
    }
}

/**
 * Publishes the connector-search bridge descriptor for the card tool server (Flutter `BuiltinGenUiSearchPublisher`;
 * B-CHAT-046): only inside the existing app-owned root, into the helper's private directory (no group or other bits),
 * never through a symlink, with a strict loopback endpoint and a 64-hex bearer. The helper then reads it on every
 * search ([CardToolServer]).
 */
public class CardSearchPublisher(private val fs: CardsRootFs) {
    private val profilePattern = Regex("^[a-zA-Z0-9_-]{1,96}$")
    private val bearerPattern = Regex("^[a-f0-9]{64}$")
    private val endpointPattern = Regex("^http://127\\.0\\.0\\.1:([1-9][0-9]{0,4})/find-connectors$")

    public suspend fun publish(profileId: String, agent: CardsHelperAgent, endpoint: String, bearer: String): Boolean {
        val port = endpointPattern.matchEntire(endpoint)?.groupValues?.get(1)?.toIntOrNull()
        if (!profilePattern.matches(profileId) || !agent.configured || port == null || port > 65535 || !bearerPattern.matches(bearer)) {
            return false
        }
        return try {
            val relative =
                if (agent.runsAs == CardsRunUser.AgentUser) {
                    listOf("home", "oc", ".oc-profiles", profileId, ".oc-genui")
                } else {
                    listOf("root", ".oc-genui", agent.id)
                }
            var path = fs.supportRealPath()
            for (part in listOf("linux", "ubuntu") + relative) {
                path = "$path/$part"
                if (fs.entry(path) != RootEntry.Directory) return false
            }
            val mode = fs.mode(path) ?: return false
            if (mode and 0x3f != 0) return false // the helper directory must be private
            val target = fs.entry("$path/$TARGET")
            if (target != RootEntry.Missing && target != RootEntry.File) return false
            val body = JsonObject(mapOf("endpoint" to JsonPrimitive(endpoint), "bearer" to JsonPrimitive(bearer))).toString()
            fs.publishPrivate(path, TARGET, body.encodeToByteArray())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    public companion object {
        public const val TARGET: String = "enabled.search.json"
    }
}

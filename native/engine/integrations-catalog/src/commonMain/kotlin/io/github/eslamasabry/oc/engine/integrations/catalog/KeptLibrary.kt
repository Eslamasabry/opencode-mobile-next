package io.github.eslamasabry.oc.engine.integrations.catalog

import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.CommandInfo
import io.github.eslamasabry.oc.core.model.IntegrationConnectionInfo
import io.github.eslamasabry.oc.core.model.IntegrationInfo
import io.github.eslamasabry.oc.core.model.IntegrationMethodInfo
import io.github.eslamasabry.oc.core.model.McpResourceInfo
import io.github.eslamasabry.oc.core.model.McpServerInfo
import io.github.eslamasabry.oc.core.model.ReferenceInfo
import io.github.eslamasabry.oc.core.model.Session
import io.github.eslamasabry.oc.core.model.SkillInfo
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Cache-first (owner rule, 2026-11-09): the Tools library opens on the lists it last read for this connection and
 * folder, then the live read replaces them in place. The app binds this to its last-known store; without a binding
 * the library loads as before. [place] is the folder and workspace the lists were read for.
 */
public interface LibraryLastKnown {
    public fun peek(profile: ProfileId, place: String): KeptLibrary?

    public suspend fun keep(profile: ProfileId, place: String, kept: KeptLibrary)
}

/**
 * The lists the library draws: commands, skills, references, providers (names, sign-in methods and connection labels,
 * never keys), MCP servers and resources. Attempts, credentials and authorization links are never kept.
 */
@Serializable
public data class KeptLibrary(
    val commands: List<KeptCommand> = emptyList(),
    val skills: List<KeptSkill> = emptyList(),
    val references: List<KeptReference> = emptyList(),
    val providers: List<KeptProvider> = emptyList(),
    val servers: List<KeptServer> = emptyList(),
    val resources: List<KeptResource> = emptyList(),
    val conversations: List<KeptConversation> = emptyList(),
) {
    @Serializable
    public data class KeptCommand(val name: String, val description: String?, val agent: String?, val subtask: Boolean)

    @Serializable
    public data class KeptSkill(
        val id: String?,
        val name: String,
        val description: String?,
        val location: String,
        val content: String,
        val slashCommand: Boolean,
    )

    @Serializable public data class KeptReference(val name: String, val path: String, val description: String?)

    @Serializable
    public data class KeptConnection(val type: String, val id: String?, val label: String, val status: String?)

    @Serializable
    public data class KeptMethod(
        val type: String,
        val id: String?,
        val label: String,
        /** Each prompt as its JSON text. */
        val prompts: List<String> = emptyList(),
        val environmentNames: List<String> = emptyList(),
        val presetAnswers: Map<String, String> = emptyMap(),
    )

    @Serializable
    public data class KeptProvider(
        val id: String,
        val name: String,
        val methods: List<KeptMethod>,
        val connections: List<KeptConnection>,
        val connectionCount: Int,
    )

    @Serializable public data class KeptServer(val name: String, val status: String, val error: String?)

    @Serializable
    public data class KeptResource(
        val name: String,
        val server: String,
        val uri: String,
        val description: String?,
        val mimeType: String?,
    )

    @Serializable public data class KeptConversation(val id: String, val title: String?)

    val isEmpty: Boolean
        get() =
            commands.isEmpty() &&
                skills.isEmpty() &&
                references.isEmpty() &&
                providers.isEmpty() &&
                servers.isEmpty() &&
                resources.isEmpty()

    /** The kept lists laid over [base]; loading stays true until the live read answers. */
    public fun over(base: LibraryInventory): LibraryInventory =
        base.copy(
            commands = commands.map { CommandInfo(it.name, it.description, it.agent, it.subtask) }.toImmutableList(),
            skills =
                skills
                    .map { SkillInfo(it.id, it.name, it.description, it.location, it.content, it.slashCommand) }
                    .toImmutableList(),
            references = references.map { ReferenceInfo(it.name, it.path, it.description) }.toImmutableList(),
            providers =
                providers
                    .map { p ->
                        IntegrationInfo(
                            p.id,
                            p.name,
                            p.methods
                                .map { m ->
                                    IntegrationMethodInfo(
                                        m.type,
                                        m.id,
                                        m.label,
                                        m.prompts
                                            .mapNotNull { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
                                            .toImmutableList(),
                                        m.environmentNames.toImmutableList(),
                                        m.presetAnswers.toImmutableMap(),
                                    )
                                }
                                .toImmutableList(),
                            p.connections.map { IntegrationConnectionInfo(it.type, it.id, it.label, it.status) }.toImmutableList(),
                            p.connectionCount,
                        )
                    }
                    .toImmutableList(),
            servers = servers.map { McpServerInfo(it.name, it.status, it.error) }.toImmutableList(),
            resources = resources.map { McpResourceInfo(it.name, it.server, it.uri, it.description, it.mimeType) }.toImmutableList(),
            conversations = conversations.map { Session(SessionId(it.id), it.title) }.toImmutableList(),
            fromKept = true,
        )

    public companion object {
        public const val MAX_ROWS: Int = 200
        public const val MAX_SKILL_BYTES: Int = 200_000
        private const val MAX_ERROR: Int = 300

        public fun of(inventory: LibraryInventory): KeptLibrary {
            var budget = MAX_SKILL_BYTES
            val skills = buildList {
                for (s in inventory.skills.take(MAX_ROWS)) {
                    if (s.content.length > budget) continue
                    budget -= s.content.length
                    add(KeptSkill(s.id, s.name, s.description, s.location, s.content, s.slashCommand))
                }
            }
            return KeptLibrary(
                commands = inventory.commands.take(MAX_ROWS).map { KeptCommand(it.name, it.description, it.agent, it.subtask) },
                skills = skills,
                references = inventory.references.take(MAX_ROWS).map { KeptReference(it.name, it.path, it.description) },
                providers =
                    inventory.providers.take(MAX_ROWS).map { p ->
                        KeptProvider(
                            p.id,
                            p.name,
                            p.methods.map { m ->
                                KeptMethod(m.type, m.id, m.label, m.prompts.map { it.toString() }, m.environmentNames, m.presetAnswers)
                            },
                            p.connections.map { KeptConnection(it.type, it.id, it.label, it.status) },
                            p.connectionCount,
                        )
                    },
                servers = inventory.servers.take(MAX_ROWS).map { KeptServer(it.name, it.status, it.error?.take(MAX_ERROR)) },
                resources = inventory.resources.take(MAX_ROWS).map { KeptResource(it.name, it.server, it.uri, it.description, it.mimeType) },
                conversations = inventory.conversations.take(MAX_ROWS).map { KeptConversation(it.id.raw, it.title) },
            )
        }
    }
}

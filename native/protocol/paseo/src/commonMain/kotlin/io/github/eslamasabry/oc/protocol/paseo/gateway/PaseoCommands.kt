package io.github.eslamasabry.oc.protocol.paseo.gateway

import io.github.eslamasabry.oc.core.model.CommandInfo
import io.github.eslamasabry.oc.protocol.paseo.arrayOrNull
import io.github.eslamasabry.oc.protocol.paseo.boolOrNull
import io.github.eslamasabry.oc.protocol.paseo.jsonOf
import io.github.eslamasabry.oc.protocol.paseo.objectOrNull
import io.github.eslamasabry.oc.protocol.paseo.str
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Discovers commands without loading a stored conversation's provider process. */
internal suspend fun PaseoGateway.readCommands(): ImmutableList<CommandInfo> {
    val scope = scopeDir
    val epoch = locationEpoch
    val providers = readProviders()
    checkLocation(scope, epoch)
    val runtime =
        providers.providers.firstOrNull { it.id == PaseoGateway.DEFAULT_PROVIDER }
            ?: return persistentListOf()
    val model =
        runtime.modelIds.firstOrNull {
            runtime.modelData[it]?.get("isDefault").boolOrNull() == true
        }
            ?: runtime.modelIds.firstOrNull { it != PaseoGateway.DEFAULT_MODEL }
            ?: return persistentListOf()
    val result =
        transport.request(
            "list_commands_request",
            jsonOf(
                "agentId" to "",
                "draftConfig" to
                    jsonOf(
                        "provider" to PaseoGateway.DEFAULT_PROVIDER,
                        "cwd" to scope,
                        "model" to model,
                    ),
            ),
            timeoutMs = 45_000,
            beforeSend = { checkLocation(scope, epoch) },
        )
    checkLocation(scope, epoch)
    return result["commands"]
        .arrayOrNull()
        .orEmpty()
        .take(300)
        .mapNotNull { raw ->
            val command = raw.objectOrNull() ?: return@mapNotNull null
            val name =
                command
                    .str("name")
                    ?.takeIf { it.isNotEmpty() && it.length <= 128 }
                    ?.removePrefix("/") ?: return@mapNotNull null
            if (name.startsWith("__")) return@mapNotNull null
            CommandInfo(name, command.str("description"), agent = null, subtask = false)
        }
        .toImmutableList()
}

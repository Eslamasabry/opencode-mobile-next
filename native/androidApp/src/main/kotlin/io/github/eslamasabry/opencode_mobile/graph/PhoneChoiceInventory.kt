package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.os.Build
import io.github.eslamasabry.oc.android.voice.device.AndroidSetupNetwork
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.catalog.AgentDescriptor
import io.github.eslamasabry.oc.engine.agents.catalog.AgentPins
import io.github.eslamasabry.oc.engine.agents.install.AgentKeepBlock
import io.github.eslamasabry.oc.engine.agents.install.AgentNeedRule
import io.github.eslamasabry.oc.engine.agents.install.AgentPinMove
import io.github.eslamasabry.oc.engine.agents.install.AgentPinRule
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateGroup
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateInventory
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateItem
import io.github.eslamasabry.oc.engine.agents.install.AgentUpdateNetwork
import io.github.eslamasabry.oc.engine.agents.removal.AgentRemoval
import io.github.eslamasabry.oc.engine.agents.scripts.AgentRemovalScripts
import io.github.eslamasabry.oc.engine.runtime.install.ManagedRuntime
import io.github.eslamasabry.oc.engine.voice.models.SetupNetwork
import io.github.eslamasabry.oc.feature.chats.presentation.HomeDownloadSizes
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.runningPhoneName__agentHelper
import io.github.eslamasabry.opencode_mobile.BuiltinLinux
import java.io.File
import org.jetbrains.compose.resources.getString

internal class PhoneChoiceInventory(
    private val app: Application,
    private val phones: AppPhoneAgents,
    private val runningVersion: () -> String? = { null },
) {
    private val linux = BuiltinLinux.get(app)
    private val network = AndroidSetupNetwork(app)
    private val pins = AgentPins.builtIn
    private val arch = PhoneAgentRules.architectureOf(Build.SUPPORTED_ABIS.toList())
    var sharedAnchor: AgentId? = null
        private set

    /** The OpenCode version the last read found at the launch path (the disk program), null when none or unknown. */
    @Volatile var openCodeOnDisk: String? = null
        private set

    suspend fun read(): AgentUpdateInventory {
        val versions = phones.choiceVersions()
        sharedAnchor = versions.keys.firstOrNull()
        val changed = recordBehind(app, versions.keys.toList())
        val items = buildList {
            if (versions.isNotEmpty()) addAll(sharedItems())
            if (linux.installed) openCode()?.let(::add)
            for ((id, from) in versions) {
                val agent = AgentCatalog.builtIn.byId(id) ?: continue
                if (PhoneChoiceRules.agentOffered(agent, from, id in changed)) add(agentRow(agent, from))
            }
        }
        val connection = when (network.current()) {
            SetupNetwork.Unmetered -> AgentUpdateNetwork.Unmetered
            SetupNetwork.Metered -> AgentUpdateNetwork.Metered
            else -> AgentUpdateNetwork.Unknown
        }
        return AgentUpdateInventory(items, connection, app.filesDir.usableSpace)
    }

    private fun agentRow(agent: AgentDescriptor, from: String): AgentUpdateItem {
        val recipe = requireNotNull(agent.recipe)
        val keep = when (agent.id.raw) {
            "claude" -> from in pins.claudeCodeTestedVersions
            "codex" -> KEEP_CODEX
            "antigravity" -> KEEP_ANTIGRAVITY
            else -> true
        }
        val removable = recipe.executable in AgentRemoval.REMOVABLE_EXECUTABLES
        val size = if (removable) readLine(AgentRemovalScripts.measure(agent))?.toLongOrNull()
            else diskBytes("/home/oc/.local/share/oc-agents/${agent.id.raw}")
        return AgentUpdateItem(
            agent.id, agent.name, from, recipe.version, arch?.let(agent::artifactFor)?.downloadBytes,
            size, AgentUpdateGroup.Optional, keep, removable,
            if (keep) null else AgentKeepBlock.NotSupportedAnyMore, null, null,
        )
    }

    private fun openCode(): AgentUpdateItem? {
        val binary = "/opt/opencode2/bin/opencode2"
        val pin = ManagedRuntime.OpenCode2.pinnedVersion
        val installed = File(linux.rootfs, binary.removePrefix("/")).isFile
        val from = if (installed) phones.choiceVersion("opencode2", pin) ?: runningVersion() else null
        openCodeOnDisk = from
        return if (!PhoneChoiceRules.openCodeOffered(from, pin)) null else AgentUpdateItem(
            AgentId.OPENCODE, "OpenCode", from, pin, HomeDownloadSizes.OPEN_CODE_MB * MB,
            diskBytes("/opt/opencode2"), AgentUpdateGroup.Optional, true, false, null, null, null,
        )
    }

    private suspend fun sharedItems(): List<AgentUpdateItem> = buildList {
        val helper = phones.choiceVersion("paseo", pins.paseo.version)
        val node = phones.choiceVersion("node", pins.node.version)?.let { "v$it" }
        val needs =
            PhoneChoiceRules.shared(helper, node, pins.paseo.version, pins.node.version, pins.paseoTestedVersions)
        needs.helper?.let { rule ->
            add(rule.needed(
                "agent-paseo", getString(Res.string.runningPhoneName__agentHelper), helper, pins.paseo.version,
                HomeDownloadSizes.HELPER_MB * MB,
            ))
        }
        needs.node?.let { add(it.needed("agent-node", "Node.js", node, pins.node.version, NODE_BYTES)) }
    }

    private fun AgentNeedRule.needed(id: String, name: String, from: String?, pin: String, bytes: Long) =
        AgentUpdateItem(AgentId(id), name, from, pin, bytes, null, AgentUpdateGroup.Needed, false, false,
            AgentKeepBlock.HelperTooNew, null, this)

    private fun diskBytes(path: String): Long? =
        readLine("du -sk '$path' 2>/dev/null")?.substringBefore('\t')?.toLongOrNull()?.times(KIB)

    private fun readLine(script: String): String? =
        linux.run(script, PROBE_SECONDS, logOutput = false)
            .takeIf { it.exitCode == 0 }?.output?.trim()?.lineSequence()?.lastOrNull()

    private companion object {
        const val KEEP_CODEX = true
        const val KEEP_ANTIGRAVITY = true
        const val MB = 1_000_000L
        const val KIB = 1024L
        const val NODE_BYTES = 58_000_000L
        const val PROBE_SECONDS = 30L
    }
}

/** The shared parts the offer lists, each with the rule that says when it comes; null: the part stays as it is. */
internal data class SharedNeeds(val helper: AgentNeedRule?, val node: AgentNeedRule?)

/**
 * What the phone's inventory lists, by the order of the versions and never by their text: a copy newer than this
 * app's pin was listed as "Version 0.172.0 → 0.171.0 · Update" (it was only "another version"). Only a copy older
 * than the pin is an update; a newer one, or one whose version cannot be read, is left as it is.
 */
internal object PhoneChoiceRules {
    /**
     * An installed agent gets a row when its copy is older than the pin, or ([changed], issue 147) it is the pin's own
     * version from another artifact. A newer copy never does, whatever its install record says.
     */
    fun agentOffered(agent: AgentDescriptor, from: String, changed: Boolean): Boolean =
        !agent.retired && AgentPinRule.move(from, agent.recipe?.version, reinstall = changed) == AgentPinMove.Update

    /** OpenCode at the launch path gets a row only when it is older than the pin. */
    fun openCodeOffered(from: String?, pin: String): Boolean = AgentPinRule.move(from, pin) == AgentPinMove.Update

    /**
     * The agent helper and Node.js. A part that is not there, or older than its pin, comes with the updates. The helper
     * runs only as the pin or a [tested] version, so one outside them must move whatever is chosen
     * ([AgentNeedRule.Always]): that is also the one newer copy that goes back to the pin, because this app cannot run
     * it. A newer tested helper and a newer Node.js run as they are and are not listed.
     */
    fun shared(
        helper: String?,
        node: String?,
        helperPin: String,
        nodePin: String,
        tested: Collection<String>,
    ): SharedNeeds {
        val runs = helper != null && helper in tested
        val helperListed = helper == null || AgentPinRule.move(helper, helperPin, runsAsIs = runs) != AgentPinMove.None
        val nodeListed = node == null || AgentPinRule.move(node, nodePin) == AgentPinMove.Update
        val always = helper != null && helperListed && !runs
        val rule = if (always) AgentNeedRule.Always else AgentNeedRule.WithAnyUpdate
        return SharedNeeds(helper = rule.takeIf { helperListed }, node = rule.takeIf { nodeListed })
    }
}

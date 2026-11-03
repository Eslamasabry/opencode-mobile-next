package io.github.eslamasabry.oc.ui.kit.capability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.KitButtonRole
import io.github.eslamasabry.oc.ui.kit.bidi.KitBidi
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.foundation.kitAssert
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.words.res.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The words of the capability explainer (Flutter `KitCapabilityExplainer.titleOf/whyOf/enableLabelOf/hostsLineOf`,
 * B-KIT-013): what a missing capability is called, why it is missing here, and the words of the flow that turns it on.
 * Host and server names are isolated ([KitBidi.auto]) so they keep their order in Arabic.
 */
public object KitCapabilityWords {
    @Composable
    public fun titleOf(capability: String): String = wordsOf(capability)?.let { stringResource(it.title) }.orEmpty()

    @Composable
    public fun enableLabelOf(capability: String): String? = wordsOf(capability)?.enable?.let { stringResource(it) }

    @Composable
    public fun offerOf(capability: String): String? = wordsOf(capability)?.offer?.let { stringResource(it) }

    /**
     * Why [capability] is missing: on a host that can (or with no host known) the registry's own reason; on a host
     * that cannot, "<title> isn't available on <host>. Works on <the hosts that can>." — naming the server when known.
     */
    @Composable
    public fun whyOf(capability: String, host: KitHost? = null, serverName: String? = null): String {
        val entry = KitCapabilities.byId(capability) ?: return ""
        val words = wordsOf(capability) ?: return ""
        val why = stringResource(words.why)
        if (host == null || host in entry.supported || host in entry.partly) return why
        val hostText = KitBidi.auto(hostWord(host))
        val name = serverName?.trim()
        val place = if (name.isNullOrEmpty()) hostText else stringResource(Res.string.kitCapServerOnHost, KitBidi.auto(name), hostText)
        val count = if (capability in KitCapabilities.pluralTitles) 2 else 1
        val notHere =
            if (count == 1) stringResource(Res.string.kitCapNotOnHost__eq1, count, stringResource(words.title), place)
            else pluralStringResource(Res.plurals.kitCapNotOnHost, count, count, stringResource(words.title), place)
        val hosts = hostList(entry)
        return if (hosts.isEmpty()) stringResource(Res.string.kitCapWhyElsewhere, notHere, why)
        else stringResource(Res.string.kitCapWhyElsewhere, notHere, stringResource(Res.string.kitCapWorksOn, hosts))
    }

    /** "Works on this phone, computers with OpenCode 2, Codex and Paseo"; empty when no host can. */
    @Composable
    public fun hostsLineOf(capability: String): String {
        val entry = KitCapabilities.byId(capability) ?: return ""
        val hosts = hostList(entry)
        return if (hosts.isEmpty()) "" else stringResource(Res.string.kitCapWorksOn, hosts)
    }

    @Composable
    public fun hostWord(host: KitHost): String = stringResource(
        when (host) {
            KitHost.ThisPhone -> Res.string.kitHostThisPhone
            KitHost.Termux -> Res.string.kitHostTermux
            KitHost.OpenCode2 -> Res.string.kitHostOpenCode2
            KitHost.Codex -> Res.string.kitHostCodex
            KitHost.Paseo -> Res.string.kitHostPaseo
            KitHost.Demo -> Res.string.kitHostDemo
        },
    )

    /** The hosts that can, in matrix order; the demo only when no real host can; Termux folds into this phone. */
    @Composable
    private fun hostList(entry: KitCapability): String {
        val can = entry.supported + entry.partly
        val ordered = KitHost.entries.filter { it in can }
        val realHosts = ordered.filter { it != KitHost.Demo }
        val hosts = (realHosts.ifEmpty { ordered }).let { list ->
            if (KitHost.ThisPhone in list) list - KitHost.Termux else list
        }
        val isolated = hosts.map { KitBidi.auto(hostWord(it)) }
        if (isolated.isEmpty()) return ""
        if (isolated.size == 1) return isolated.single()
        var head = isolated.first()
        for (word in isolated.subList(1, isolated.size - 1)) head = stringResource(Res.string.kitCapComma, head, word)
        return stringResource(Res.string.kitCapAnd, head, isolated.last())
    }

    internal class Words(
        val title: StringResource,
        val why: StringResource,
        val enable: StringResource? = null,
        val offer: StringResource? = null,
    )

    private val S = Res.string

    internal fun wordsOf(id: String): Words? = when (id) {
        "server.any" -> Words(S.kitCapServerAnyTitle, S.kitCapServerAnyWhy, S.kitCapServerAnyEnable, S.kitCapServerAnyOffer)
        "server.oc2" -> Words(S.kitCapServerOc2Title, S.kitCapServerOc2Why, S.kitCapServerOc2Enable, S.kitCapServerOc2Offer)
        "server.codex" -> Words(S.kitCapServerCodexTitle, S.kitCapServerCodexWhy, S.kitCapServerCodexEnable, S.kitCapServerCodexOffer)
        "server.paseo" -> Words(S.kitCapServerPaseoTitle, S.kitCapServerPaseoWhy, S.kitCapServerPaseoEnable, S.kitCapServerPaseoOffer)
        "phone.builtin" -> Words(S.kitCapPhoneBuiltinTitle, S.kitCapPhoneBuiltinWhy, S.kitCapPhoneBuiltinEnable, S.kitCapPhoneBuiltinOffer)
        "phone.termux" -> Words(S.kitCapPhoneTermuxTitle, S.kitCapPhoneTermuxWhy, S.kitCapPhoneTermuxEnable, S.kitCapPhoneTermuxOffer)
        "phone.any" -> Words(S.kitCapPhoneAnyTitle, S.kitCapPhoneAnyWhy)
        "model.auth" -> Words(S.kitCapModelAuthTitle, S.kitCapModelAuthWhy, S.kitCapModelAuthEnable, S.kitCapModelAuthOffer)
        "team.on" -> Words(S.kitCapTeamOnTitle, S.kitCapTeamOnWhy, S.kitCapTeamOnEnable, S.kitCapTeamOnOffer)
        "team.phone" -> Words(S.kitCapTeamPhoneTitle, S.kitCapTeamPhoneWhy)
        "team.control" -> Words(S.kitCapTeamControlTitle, S.kitCapTeamControlWhy, S.kitCapTeamControlEnable, S.kitCapTeamControlOffer)
        "claude.local" -> Words(S.kitCapClaudeLocalTitle, S.kitCapClaudeLocalWhy, S.kitCapClaudeLocalEnable, S.kitCapClaudeLocalOffer)
        "voice.model" -> Words(S.kitCapVoiceModelTitle, S.kitCapVoiceModelWhy, S.kitCapVoiceModelEnable, S.kitCapVoiceModelOffer)
        "mcp.any" -> Words(S.kitCapMcpAnyTitle, S.kitCapMcpAnyWhy, S.kitCapMcpAnyEnable, S.kitCapMcpAnyOffer)
        "project.open" -> Words(S.kitCapProjectOpenTitle, S.kitCapProjectOpenWhy, S.kitCapProjectOpenEnable, S.kitCapProjectOpenOffer)
        "project.git" -> Words(S.kitCapProjectGitTitle, S.kitCapProjectGitWhy, S.kitCapProjectGitEnable, S.kitCapProjectGitOffer)
        "perm.notifications" ->
            Words(S.kitCapPermNotificationsTitle, S.kitCapPermNotificationsWhy, S.kitCapPermNotificationsEnable, S.kitCapPermNotificationsOffer)
        "perm.battery" -> Words(S.kitCapPermBatteryTitle, S.kitCapPermBatteryWhy, S.kitCapPermBatteryEnable, S.kitCapPermBatteryOffer)
        "perm.camera" -> Words(S.kitCapPermCameraTitle, S.kitCapPermCameraWhy, S.kitCapPermCameraEnable, S.kitCapPermCameraOffer)
        "perm.mic" -> Words(S.kitCapPermMicTitle, S.kitCapPermMicWhy, S.kitCapPermMicEnable, S.kitCapPermMicOffer)
        "network.tailscale" ->
            Words(S.kitCapNetworkTailscaleTitle, S.kitCapNetworkTailscaleWhy, S.kitCapNetworkTailscaleEnable, S.kitCapNetworkTailscaleOffer)
        "quota.collector" ->
            Words(S.kitCapQuotaCollectorTitle, S.kitCapQuotaCollectorWhy, S.kitCapQuotaCollectorEnable, S.kitCapQuotaCollectorOffer)
        "agent.a2a" -> Words(S.kitCapAgentA2aTitle, S.kitCapAgentA2aWhy, S.kitCapAgentA2aEnable, S.kitCapAgentA2aOffer)
        "flag:fileBrowsing+terminal" -> Words(S.kitCapFlagFileBrowsingTerminalTitle, S.kitCapFlagFileBrowsingTerminalWhy)
        "flag:terminal" -> Words(S.kitCapFlagTerminalTitle, S.kitCapFlagTerminalWhy)
        "flag:sessionDiff" -> Words(S.kitCapFlagSessionDiffTitle, S.kitCapFlagSessionDiffWhy)
        "flag:serverCatalog" -> Words(S.kitCapFlagServerCatalogTitle, S.kitCapFlagServerCatalogWhy)
        "flag:toolInventory" -> Words(S.kitCapFlagToolInventoryTitle, S.kitCapFlagToolInventoryWhy)
        "flag:usageStatistics" -> Words(S.kitCapFlagUsageStatisticsTitle, S.kitCapFlagUsageStatisticsWhy)
        "flag:stagedRevert+sessionNotes" -> Words(S.kitCapFlagStagedRevertSessionNotesTitle, S.kitCapFlagStagedRevertSessionNotesWhy)
        "flag:worktreeCreate+sessionShare+managedWorkspaces" ->
            Words(S.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle, S.kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy)
        "flag:developmentServices" -> Words(S.kitCapFlagDevelopmentServicesTitle, S.kitCapFlagDevelopmentServicesWhy)
        "flag:remoteUpgrade" -> Words(S.kitCapFlagRemoteUpgradeTitle, S.kitCapFlagRemoteUpgradeWhy)
        "flag:promptAttachments" -> Words(S.kitCapFlagPromptAttachmentsTitle, S.kitCapFlagPromptAttachmentsWhy)
        else -> null
    }
}

/** The enable action for [capability] when its flow has a handler; one run at a time, working while it runs. */
@Composable
private fun rememberEnable(capability: String, host: KitHost?, serverName: String?, source: String?): KitAction? {
    val flows = LocalKitEnableFlows.current
    val scope = rememberCoroutineScope()
    var working by remember(capability) { mutableStateOf(false) }
    if (!flows.canEnable(capability)) return null
    val label = KitCapabilityWords.enableLabelOf(capability) ?: return null
    return KitAction(
        label = label,
        onClick = {
            if (!working) {
                working = true
                scope.launch {
                    try {
                        flows.enable(capability, host, serverName, source)
                    } finally {
                        working = false
                    }
                }
            }
        },
        working = working,
    )
}

@Composable
private fun known(capability: String): Boolean {
    val entry = KitCapabilities.byId(capability)
    kitAssert(entry != null) { "KitCapabilityExplainer: unknown capability \"$capability\" (the capabilities.json matrix ids)" }
    return entry != null
}

/**
 * A quiet row saying a capability is missing and why, with its enable flow as a button when the app registered one
 * (Flutter `KitCapabilityExplainer.row`). Without a handler it only explains: never a dead button.
 *
 * States: explains, offers enable, enabling (working)
 * Text: reading
 * Replaces: KitCapabilityExplainer.row (lib/ui/kit/kit_capability_explainer.dart)
 */
@Composable
public fun KitCapabilityRow(
    capability: String,
    modifier: Modifier = Modifier,
    host: KitHost? = null,
    serverName: String? = null,
    title: String? = null,
    source: String? = null,
) {
    if (!known(capability)) return
    val enable = rememberEnable(capability, host, serverName, source)
    CapabilityRow(capability, modifier, host, serverName, title, enable)
}

@Composable
private fun CapabilityRow(
    capability: String,
    modifier: Modifier,
    host: KitHost?,
    serverName: String?,
    title: String?,
    enable: KitAction?,
) {
    Column(modifier.testTag("kit-capability-row"), verticalArrangement = Arrangement.spacedBy(Space.S1.dp)) {
        KitRow(
            title = title ?: KitCapabilityWords.titleOf(capability),
            supporting = KitCapabilityWords.whyOf(capability, host, serverName),
            supportingMaxLines = 4,
        )
        if (enable != null) KitButton(enable, role = KitButtonRole.Secondary)
    }
}

/**
 * A missing-capability state for a page (Flutter `KitCapabilityExplainer.state`): the title, why, and the enable
 * action when there is one, with its [cost] lines. A [prerequisite] must have an action (STATE-13): never a dead end.
 */
@Composable
public fun KitCapabilityState(
    capability: String,
    modifier: Modifier = Modifier,
    host: KitHost? = null,
    serverName: String? = null,
    cost: List<String> = emptyList(),
    prerequisite: Boolean = false,
    source: String? = null,
) {
    if (!known(capability)) return
    val enable = rememberEnable(capability, host, serverName, source)
    kitAssert(!prerequisite || enable != null) {
        "KitCapabilityState: a prerequisite needs an enable action, so \"$capability\" needs a flow and a registered handler"
    }
    val why = KitCapabilityWords.whyOf(capability, host, serverName)
    val shownCost = if (enable == null) emptyList() else cost
    KitStateView(
        kind = KitStateKind.Missing,
        title = KitCapabilityWords.titleOf(capability),
        modifier = modifier,
        body = (listOf(why) + shownCost.joinToString(" · ").takeIf { it.isNotEmpty() }).filterNotNull().joinToString("\n"),
        primary = enable,
        prerequisite = prerequisite && enable != null,
    )
}

/**
 * A gentle offer to turn a capability on (Flutter `KitCapabilityExplainer.offer`): the offer notice with the enable
 * action and "Not now"; [folded] (after Not now) or with no handler it is the quiet row instead.
 */
@Composable
public fun KitCapabilityOffer(
    capability: String,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
    folded: Boolean = false,
    message: String? = null,
    host: KitHost? = null,
    serverName: String? = null,
    source: String? = null,
) {
    if (!known(capability)) return
    val enable = rememberEnable(capability, host, serverName, source)
    val open = !folded && enable != null
    if (open && enable != null) {
        val run = enable.onClick ?: {}
        // The enable action, then "Not now": the order a keyboard reaches them (Flutter's offer Tab order).
        KitNotice(
            message = message ?: KitCapabilityWords.offerOf(capability) ?: KitCapabilityWords.whyOf(capability, host, serverName),
            modifier = modifier.testTag("kit-notice-offer"),
            actions = listOf(
                KitLineAction(enable.label, run),
                KitLineAction(stringResource(Res.string.kitCapNotNow), onNotNow, testTag = "kit-notice-dismiss"),
            ),
        )
    } else {
        CapabilityRow(capability, modifier, host, serverName, null, enable)
    }
}


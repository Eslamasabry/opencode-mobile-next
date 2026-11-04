package io.github.eslamasabry.oc.ui.kit.capability

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.github.eslamasabry.oc.ui.kit.KitAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.state.KitCostNotice
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.kit.state.KitOfferNotice
import io.github.eslamasabry.oc.ui.kit.state.KitStateKind
import io.github.eslamasabry.oc.ui.kit.state.KitStateView
import io.github.eslamasabry.oc.ui.kit.text.KitBidi
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitCapAnd
import io.github.eslamasabry.oc.ui.words.res.kitCapComma
import io.github.eslamasabry.oc.ui.words.res.kitCapEnableFailed
import io.github.eslamasabry.oc.ui.words.res.kitCapNotNow
import io.github.eslamasabry.oc.ui.words.res.kitCapNotOnHost
import io.github.eslamasabry.oc.ui.words.res.kitCapServerOnHost
import io.github.eslamasabry.oc.ui.words.res.kitCapWhyElsewhere
import io.github.eslamasabry.oc.ui.words.res.kitCapWorksOn
import io.github.eslamasabry.oc.ui.words.res.kitWorking
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

public enum class KitCapabilityVariant {
    Row,
    State,
    Offer,
}

/**
 * Human explanation of a missing capability. A turn-on button only exists while a real handler is
 * registered. States: explains, offers-enable, prerequisite, offer, folded, working Text: reading
 * Cap: Reading Motion: none Hidden: none; keyboard activation comes from the kit action and row.
 * Replaces: KitCapabilityExplainer
 */
@Composable
public fun KitCapabilityExplainer(
    capability: String,
    modifier: Modifier = Modifier,
    variant: KitCapabilityVariant = KitCapabilityVariant.Row,
    host: KitHost? = null,
    serverName: String? = null,
    source: String? = null,
    title: String? = null,
    message: String? = null,
    cost: List<String> = emptyList(),
    prerequisite: Boolean = false,
    folded: Boolean = false,
    onNotNow: (() -> Unit)? = null,
) {
    val entry = KitCapabilities.byId(capability) ?: return
    val words = kitCapabilityWords(capability) ?: return
    val registry = LocalKitCapabilities.current
    val scope = rememberCoroutineScope()
    var working by remember(registry, capability) { mutableStateOf(false) }
    var failed by remember(registry, capability) { mutableStateOf(false) }
    val why = kitCapabilityWhy(entry, words, host, serverName)
    val canEnable = registry.canEnable(capability) && words.enable != null
    val run: () -> Unit = {
        if (!working && canEnable) {
            working = true
            failed = false
            scope.launch {
                try {
                    failed = !registry.enable(capability, host, serverName, source)
                } finally {
                    working = false
                }
            }
        }
    }
    val workingLabel = stringResource(Res.string.kitWorking)
    val lineAction =
        if (canEnable)
            KitLineAction(
                label = if (working) workingLabel else words.enable.orEmpty(),
                onClick = run,
                testTag = "capability-enable-$capability",
            )
        else null
    Column(modifier.testTag("capability-$capability")) {
        when {
            variant == KitCapabilityVariant.State -> {
                if (canEnable && cost.isNotEmpty()) KitCostNotice(cost)
                KitStateView(
                    kind = KitStateKind.Missing,
                    title = title ?: words.title,
                    body = why,
                    primary =
                        if (canEnable) KitAction(words.enable.orEmpty(), run, working = working)
                        else null,
                    prerequisite = prerequisite && canEnable,
                )
            }
            variant == KitCapabilityVariant.Offer &&
                !folded &&
                lineAction != null &&
                onNotNow != null ->
                KitOfferNotice(
                    message ?: words.offer ?: words.why,
                    lineAction,
                    KitLineAction(stringResource(Res.string.kitCapNotNow), onNotNow),
                )
            else ->
                KitRow(
                    title = title ?: words.title,
                    supporting = why,
                    supportingMaxLines = 6,
                    trailing = lineAction?.let { KitRowTrailing.Action(it) } ?: KitRowTrailing.None,
                )
        }
        if (failed) KitNotice(stringResource(Res.string.kitCapEnableFailed))
    }
}

@Composable
public fun kitCapabilityWhy(
    entry: KitCapability,
    words: KitCapabilityWords,
    host: KitHost?,
    serverName: String?,
): String {
    if (host == null || host in entry.supported || host in entry.partly) return words.why
    val hostWord = KitBidi.auto(kitCapabilityHostWord(host))
    val name = serverName?.trim()?.takeIf(String::isNotEmpty)
    val place =
        if (name == null) hostWord
        else stringResource(Res.string.kitCapServerOnHost, KitBidi.auto(name), hostWord)
    val count = if (entry.id in pluralTitles) 2 else 1
    val unavailable =
        pluralStringResource(Res.plurals.kitCapNotOnHost, count, count, words.title, place)
    val hosts = kitCapabilityHostsLine(entry)
    return stringResource(Res.string.kitCapWhyElsewhere, unavailable, hosts.ifEmpty { words.why })
}

/**
 * Supported then partial hosts, with the phone covering Termux and demo only when no real host
 * exists.
 */
@Composable
public fun kitCapabilityHostsLine(entry: KitCapability): String {
    val hosts = (entry.supported + entry.partly).toMutableSet()
    if (hosts.any { it != KitHost.Demo }) hosts.remove(KitHost.Demo)
    if (KitHost.ThisPhone in hosts) hosts.remove(KitHost.Termux)
    val ordered =
        KitHost.entries.filter { it in hosts }.map { KitBidi.auto(kitCapabilityHostWord(it)) }
    if (ordered.isEmpty()) return ""
    var list = ordered.first()
    ordered.drop(1).dropLast(1).forEach { list = stringResource(Res.string.kitCapComma, list, it) }
    if (ordered.size > 1) list = stringResource(Res.string.kitCapAnd, list, ordered.last())
    return stringResource(Res.string.kitCapWorksOn, list)
}

private val pluralTitles =
    setOf(
        "team.control",
        "mcp.any",
        "perm.notifications",
        "agent.a2a",
        "flag:fileBrowsing+terminal",
        "flag:serverCatalog",
        "flag:stagedRevert+sessionNotes",
        "flag:worktreeCreate+sessionShare+managedWorkspaces",
        "flag:developmentServices",
        "flag:promptAttachments",
    )

package io.github.eslamasabry.oc.feature.servers.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.model.BackendKind
import io.github.eslamasabry.oc.engine.lifecycle.PhoneHostSupport
import io.github.eslamasabry.oc.engine.lifecycle.PhoneOwnServers
import io.github.eslamasabry.oc.engine.lifecycle.PhoneServerCandidate
import io.github.eslamasabry.oc.feature.servers.presentation.*
import io.github.eslamasabry.oc.ui.design.tokens.Space
import io.github.eslamasabry.oc.ui.design.type.TypeRole
import io.github.eslamasabry.oc.ui.kit.KitMonoText
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.foundation.*
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.modal.*
import io.github.eslamasabry.oc.ui.kit.row.*
import io.github.eslamasabry.oc.ui.kit.theme.LocalKitStyle
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Flutter _ServerRow: state before kind, addresses in Details, named actions in the visible More
 * menu. Each fact once (screen standard §2): the kind is said only when the saved servers are not
 * all the same kind, and two servers whose names read the same are told apart by a number, never
 * by their address (that stays in Details).
 */
@Composable
internal fun ServerSavedRows(
    state: ServersUi,
    actions: ServersActions,
    enabled: KitEnabled,
    phoneHost: Boolean = false,
) {
    var detailsFor by remember { mutableStateOf<ProfileId?>(null) }
    val ordered = savedServersByUrgency(state)
    val names = distinctServerNames(state.servers.map { it.id to it.name })
    val showKind = kindsDiffer(state.servers.map { state.rows.facts[it.id]?.backend })
    val flat = LocalKitStyle.current.spec.tokens.flatLists
    val thisPhone = stringResource(Res.string.settingsV3ThisPhone)
    KitRowGroup(leadingTiles = true) {
        ordered.forEach { server ->
            val facts = state.rows.facts[server.id]
            val phone =
                PhoneOwnServers.isPhoneOwnServer(
                    PhoneServerCandidate(server.id.raw, server.address),
                    if (phoneHost) PhoneHostSupport.Android else PhoneHostSupport.Elsewhere,
                )
            val waiting = if (server.connected) 0 else (facts?.waiting ?: 0)
            val kind =
                stringResource(
                    when (facts?.backend) {
                        BackendKind.Codex -> Res.string.addServerTypeCodex
                        BackendKind.Paseo -> Res.string.addServerTypePaseo
                        BackendKind.OpenCode2 -> Res.string.setupRuntimeTwo
                        null -> Res.string.firstRunAgentOpenCode
                    }
                )
            val secret =
                if (facts?.needsSecret == true)
                    stringResource(
                        if (facts.backend == BackendKind.Codex || facts.backend == BackendKind.Paseo)
                            Res.string.e7SetupTokenRequired
                        else Res.string.e7SetupPasswordRequired
                    )
                else null
            val name = names[server.id] ?: server.name
            val supporting =
                kitServerSupporting(
                    kind = if (showKind) kind else "",
                    connected = server.connected,
                    waiting = waiting,
                    running = facts?.running ?: 0,
                    secret = secret,
                    queued = facts?.queued ?: 0,
                )
            val destination =
                state.rows.moveDestination
                    ?.takeIf { it != server.id }
                    ?.let { id -> state.servers.firstOrNull { it.id == id } }
            val items = buildList {
                if (facts?.account == true)
                    add(
                        KitMenuItem(
                            stringResource(Res.string.agentAccountTitle),
                            { actions.openAccount(server.id) },
                        )
                    )
                add(
                    KitMenuItem(
                        stringResource(Res.string.e7SetupConnect),
                        { actions.connect(server.id) },
                        enabled = enabled,
                    )
                )
                if ((facts?.queued ?: 0) > 0 && destination != null)
                    add(
                        KitMenuItem(
                            pluralStringResource(
                                Res.plurals.rows40Move,
                                facts!!.queued,
                                facts.queued,
                                destination.name,
                            ),
                            { actions.moveQueued(server.id) },
                            enabled = enabled,
                        )
                    )
                add(
                    KitMenuItem(
                        stringResource(Res.string.settingsV3ServerSettings),
                        { actions.settings(server.id) },
                    )
                )
                add(
                    KitMenuItem(
                        stringResource(Res.string.e7SetupEdit),
                        { actions.edit(server.id) },
                        enabled = enabled,
                    )
                )
                add(KitMenuItem(stringResource(Res.string.kitDetails), { detailsFor = server.id }))
                add(
                    KitMenuItem(
                        stringResource(Res.string.capsuleRemove),
                        { actions.remove(server.id) },
                        destructive = true,
                        enabled = enabled,
                    )
                )
            }
            KitRow(
                // The phone glyph is decorative, so a screen reader hears that this server is the phone's own.
                modifier = if (phone) Modifier.semantics { stateDescription = thisPhone } else Modifier,
                title = name,
                supporting = withoutTrailingSeparator(supporting),
                supportingMaxLines =
                    2, // F: lib/ui/screens/servers/server_rows.dart:191-196
                leading =
                    if (waiting <= 0)
                        // Dart KitRowIcon (kit_row_parts.dart:52-66) through _KitRowIconTile (kit_row.dart:994-998): in
                        // flat lists (Solid) the glyph stands bare in the tile's slot, the one in use in the accent;
                        // a panel style keeps the tile.
                        KitRowLeading.Glyph(
                            if (phone) KitIcons.phone else KitIcons.server,
                            tile = !flat,
                            current = server.connected,
                        )
                    else null,
                state = if (waiting > 0) KitRowState(null, KitRowStateTone.NeedsYou) else null,
                selected = server.connected,
                enabled = enabled.isEnabled,
                disabledReason =
                    if (enabled.isEnabled) null
                    else stringResource(Res.string.serversWorking),
                onClick = {
                    if (state.newConversation) actions.create(server.id)
                    else actions.settings(server.id)
                },
                trailing = KitRowTrailing.Menu(name, items, title = name),
            )
        }
    }
    val details = state.servers.firstOrNull { it.id == detailsFor }
    KitSheet(
        visible = details != null,
        onDismissRequest = { detailsFor = null },
        title =
            details?.let { stringResource(Res.string.serverRowDetailsTitle, names[it.id] ?: it.name) }
                ?: stringResource(Res.string.kitDetails),
    ) {
        details?.let {
            KitText(stringResource(Res.string.connectionServerAddress), role = TypeRole.Label)
            KitMonoText(it.address)
            state.rows.facts[it.id]?.version?.takeIf(String::isNotBlank)?.let { version ->
                Spacer(Modifier.height(Space.S3.dp))
                KitText(stringResource(Res.string.serverRowDetailsVersion), role = TypeRole.Label)
                KitMonoText(plainServerVersion(version))
            }
            state.rows.facts[it.id]?.directory?.takeIf(String::isNotBlank)?.let { directory ->
                Spacer(Modifier.height(Space.S3.dp))
                KitText(stringResource(Res.string.codexProjectFolder), role = TypeRole.Label)
                KitMonoText(directory)
            }
        }
    }
}

/**
 * Flutter servers_actions.dart:575-597: preserve list order within each urgency, including idle
 * credentials.
 */
internal fun savedServersByUrgency(
    state: ServersUi
): List<io.github.eslamasabry.oc.engine.connection.SavedServer> =
    state.servers.sortedBy { server ->
        val facts = state.rows.facts[server.id]
        when {
            (facts?.waiting ?: 0) > 0 -> 0
            (facts?.running ?: 0) > 0 -> 1
            else -> 2 // F: lib/ui/screens/servers/servers_actions.dart:589
        }
    }

/** Flutter strips the daemon's explanatory label, keeping a reported prerelease/build suffix. */
internal fun plainServerVersion(raw: String): String =
    Regex("""\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?""").find(raw)?.value ?: raw.trim()

/** The comparison form of a server name: case, spacing and punctuation do not tell two servers apart. */
internal fun nameKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

/** The [nameKey]s shared by more than one saved server. */
internal fun lookAlikeNames(names: List<String>): Set<String> =
    names.groupingBy(::nameKey).eachCount().filterValues { it > 1 }.keys

/**
 * The name each saved server is shown under, in saved order. Names that read the same get their number among
 * them ("My computer (1)", "My computer (2)", Flutter `_distinctServerName`'s last step); the address is technical
 * and stays in Details. Only how the list presents a name: the stored name is never changed.
 */
internal fun <K> distinctServerNames(servers: List<Pair<K, String>>): Map<K, String> {
    val lookAlike = lookAlikeNames(servers.map { it.second })
    val seen = HashMap<String, Int>()
    return servers.associate { (id, name) ->
        val key = nameKey(name)
        if (key !in lookAlike) id to name
        else {
            val n = (seen[key] ?: 0) + 1
            seen[key] = n
            id to "${name.trim()} ($n)"
        }
    }
}

/**
 * Whether the rows say what kind of server each is: only when the list holds more than one kind. A list of four
 * OpenCode 2 servers saying "OpenCode 2" four times tells nobody anything (screen standard §2, each fact once).
 */
internal fun kindsDiffer(kinds: List<BackendKind?>): Boolean = kinds.distinct().size > 1

/** The supporting line without the " · " the kit leaves after its last word when the kind is not said. */
internal fun withoutTrailingSeparator(line: AnnotatedString): AnnotatedString {
    var end = line.length
    while (end > 0 && (line[end - 1] == ' ' || line[end - 1] == '·' || line[end - 1] == '\u00a0')) end--
    return if (end == line.length) line else line.subSequence(0, end)
}

package io.github.eslamasabry.oc.engine.cards

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/*
 * Agent cards (plan 09 §13.2; Flutter lib/domain/genui/gen_ui_types.dart, gen_ui_nodes.dart, gen_ui_asks.dart,
 * gen_ui_answers.dart). Immutable presentation values: constructors never validate agent input, [CardValidator] does.
 * Agent input stays untrusted: no markup, no remote resources, no executable expressions.
 */

/** The trusted place a card was seen in. Never taken from the agent's payload. */
public data class CardScope(
    val profileId: String,
    val sourceId: String,
    val directory: String,
    val workspace: String? = null,
)

/** A display-only catalog suggestion, never a connection or command request (B-CHAT-033). */
public data class ConnectorSuggestion(val catalogId: String, val reason: String)

public data class AgentCard(
    val scope: CardScope,
    /** The agent's own id: display and correlation only. */
    val id: String,
    val title: String,
    val sessionId: String,
    val callId: String,
    val messageId: String,
    /** sha256 of the compact normalised JSON (B-CHAT-031). */
    val revision: String,
    val body: ImmutableList<CardNode> = persistentListOf(),
    val ask: CardAsk? = null,
    val connector: ConnectorSuggestion? = null,
) {
    /** Transport identity (the seven trusted parts, Dart `GenUiCard.identity`); revisions are compared separately. */
    public val identity: String
        get() =
            CardJson.encode(
                listOf(
                    scope.profileId, scope.sourceId, scope.directory, scope.workspace,
                    sessionId, messageId, callId,
                )
            )
}

public sealed interface CardNode {
    public data class Text(val text: String) : CardNode

    public data class KeyValueRow(val key: String, val value: String)

    public data class KeyValue(val rows: ImmutableList<KeyValueRow>) : CardNode

    public enum class ListStyle { Bullet, Check }

    public data class ListItem(val text: String, val done: Boolean = false)

    public data class Checklist(val items: ImmutableList<ListItem>, val style: ListStyle) : CardNode

    public data class Table(val columns: ImmutableList<String>, val rows: ImmutableList<ImmutableList<String>>) : CardNode

    public enum class ChartKind { Bar, Line }

    public data class Series(val name: String, val values: ImmutableList<Double>)

    public data class Chart(
        val kind: ChartKind,
        val unit: String?,
        val labels: ImmutableList<String>,
        val series: ImmutableList<Series>,
    ) : CardNode

    public data class Code(val language: String?, val text: String) : CardNode

    public data class FileStat(val path: String, val added: Long, val removed: Long)

    public data class DiffStat(val files: ImmutableList<FileStat>) : CardNode

    public data class Progress(val label: String, val value: Double) : CardNode

    public enum class CalloutTone { Info, Warning, Success }

    public data class Callout(val tone: CalloutTone, val text: String) : CardNode

    /** [url] is inert text: it opens only through the app's external-link gate, after a tap. */
    public data class Link(val label: String, val url: String) : CardNode
}

public data class CardOption(val id: String, val label: String, val detail: String? = null)

public enum class CardFieldType { Text, Multiline, Number, Toggle, Select, Date }

public data class CardField(
    val id: String,
    val label: String,
    val type: CardFieldType,
    val required: Boolean = false,
    val placeholder: String? = null,
    /** Only String, Long/Double or Boolean; null = unset. */
    val defaultValue: Any? = null,
    val options: ImmutableList<CardOption> = persistentListOf(),
    val min: Double? = null,
    val max: Double? = null,
)

public sealed interface CardAsk {
    public data class Choice(val options: ImmutableList<CardOption>, val multi: Boolean = false) : CardAsk

    public data class Form(val fields: ImmutableList<CardField>, val submitLabel: String? = null) : CardAsk

    public data class Confirm(
        val confirmLabel: String? = null,
        val cancelLabel: String? = null,
        val danger: Boolean = false,
    ) : CardAsk

    public data class Photo(val purpose: String, val max: Int = 1) : CardAsk
}

public sealed interface CardAnswer {
    public data class Choice(val ids: List<String>) : CardAnswer

    /** String, Long/Double or Boolean values; absent optional fields are omitted, never null. */
    public data class Form(val values: Map<String, Any>) : CardAnswer

    public data class Confirm(val value: Boolean) : CardAnswer

    public data class Photo(val count: Int) : CardAnswer
}

public enum class CardProblem { NotACard, Version, TooLarge, UnknownKey, BadValue, SecretField, UnsupportedAsk }

public sealed interface CardParse {
    public data class Parsed(val card: AgentCard) : CardParse

    public data class Unreadable(val reason: CardProblem) : CardParse
}

/** Plan 09 §13.4. Unknown is never treated as answered, absent or actionable. */
public enum class CardState { Waiting, Answered, PassedOver, Report, Unknown }

public enum class CardDelivery { Idle, Held, Sending, DeliveryUnknown, Failed }

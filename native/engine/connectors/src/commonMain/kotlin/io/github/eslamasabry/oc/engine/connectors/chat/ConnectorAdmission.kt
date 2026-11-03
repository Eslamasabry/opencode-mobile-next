package io.github.eslamasabry.oc.engine.connectors.chat

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.failed
import io.github.eslamasabry.oc.core.common.ok
import io.github.eslamasabry.oc.core.domain.McpGateway
import io.github.eslamasabry.oc.core.domain.capabilities.ServerCapabilities
import io.github.eslamasabry.oc.engine.connectors.catalog.McpCatalogItem
import io.github.eslamasabry.oc.engine.connectors.catalog.catalogServerName
import kotlinx.coroutines.CoroutineScope

/** An Agent card as the transcript rendered it: which card, which version of it. */
public data class CardRef(
    /** Profile, source and directory the card was made in (Dart `GenUiScope`), as one comparable value. */
    val scope: String,
    val sessionId: String,
    val identity: String,
    val revision: Int,
)

/** How an observed card stands. Only [Report] cards may act. */
public enum class CardState { Report, Other }

/** The authoritative card the transcript holds for a [CardRef]'s identity and revision. */
public data class ObservedCard(val card: CardRef, val connectorCatalogId: String?)

/** PORT: the Agent-card transcript authority (Dart `_genUiStateForScope`). */
public interface CardAuthority {
    /** The observed card with [card]'s identity and revision; null when not observed (or replaced). */
    public fun observed(card: CardRef): ObservedCard?

    /** Null while not observed (recovery incomplete); [CardState.Other] when replaced, removed or not a report. */
    public fun state(card: CardRef): CardState?

    /** The scope whose cards are live now (Dart `_genUiScope`). */
    public fun activeScope(): String?

    /** Cards for [scope] are registered and ready. */
    public fun registeredReady(scope: String): Boolean

    /** Re-reads the card's session through the current transport (after a browser round trip). */
    public suspend fun recover(card: CardRef)
}

/** Who and where the conversation is (Dart profile/connected identity, location and deletion revision). */
public data class AdmissionContext(
    val activeProfile: ProfileIdentity?,
    val connectedProfile: ProfileIdentity?,
    val profileReadable: Boolean,
    val deletionRevision: Int,
    val directory: String?,
    val workspace: String?,
    val locationRevision: Long,
    val cardsEnabled: Boolean,
)

public data class ProfileIdentity(val id: String, val baseUrl: String, val username: String?, val flavor: String)

/** PORT: the live connection for connector admission. */
public interface AdmissionEnvironment {
    public fun context(): AdmissionContext

    public fun capabilities(): ServerCapabilities

    /** The current operations gateway (Dart `prepareActionRepository`); null when none can be prepared. */
    public suspend fun prepareActionRepository(): McpGateway?

    public val cards: CardAuthority

    /** Brings the card authority in line with the connection before a recovery (Dart `_genUiSync`). */
    public fun syncCards() {}
}

/** Why a suggestion could not be admitted (Dart ProductException copy, mapped by the UI). */
public enum class AdmissionRefusal {
    /** "This connector suggestion is not available. Browse connectors in Tools." */
    NotAvailable,

    /** "A connector with this name already exists. Check it in Tools." */
    NameExists,
}

public sealed interface Admission {
    public data class Admitted(val controller: McpChatController) : Admission

    public data class Refused(val reason: AdmissionRefusal) : Admission
}

/**
 * Admits chat connector controllers only for authoritative cards of the exact connected source (Dart
 * `createMcpChatController`; B-AGENT-134). Renderings of the same card share one controller; a changed transcript,
 * project, profile, deletion or turning cards off invalidates it; a same-profile transport replacement keeps it and
 * the next operation uses the fresh gateway. At most [MAX_PENDING] live controllers; disposed ones do not count.
 */
public class ConnectorAdmission(
    private val environment: AdmissionEnvironment,
    private val scope: CoroutineScope,
    private val callbacks: OAuthCallbackPort? = null,
) {
    private class Slot(val controller: McpChatController, val origin: Any, val current: () -> Boolean)

    private val slots = LinkedHashMap<Any, Slot>()

    public fun admit(card: CardRef, item: McpCatalogItem): Admission {
        val cards = environment.cards
        val observed = cards.observed(card)
        val context = environment.context()
        val owner = context.connectedProfile
        if (observed == null || owner == null || !environment.capabilities().genUi ||
            cards.activeScope() != card.scope ||
            cards.state(card) != CardState.Report || observed.connectorCatalogId != item.entry.name ||
            item.serverName != catalogServerName(item.entry.name)
        ) {
            return Admission.Refused(AdmissionRefusal.NotAvailable)
        }
        val authoritative = observed.card
        onChanged()
        val selectedRevision = context.locationRevision
        val selectedLocation = context.directory to context.workspace
        val deletion = context.deletionRevision
        val key = listOf(owner, selectedLocation, item.serverName)
        val origin = listOf(authoritative.identity, authoritative.revision, item.entry.id)
        slots[key]?.let { existing ->
            if (!existing.controller.isDisposed) {
                return if (existing.origin == origin) {
                    Admission.Admitted(existing.controller)
                } else {
                    Admission.Refused(AdmissionRefusal.NameExists)
                }
            }
        }
        if (slots.size >= MAX_PENDING) return Admission.Refused(AdmissionRefusal.NotAvailable)
        fun current(): Boolean {
            val now = environment.context()
            return now.activeProfile == owner && now.connectedProfile == owner && now.profileReadable &&
                now.locationRevision == selectedRevision && (now.directory to now.workspace) == selectedLocation &&
                now.deletionRevision == deletion && now.cardsEnabled && cards.registeredReady(authoritative.scope) &&
                cards.activeScope() == authoritative.scope &&
                cards.state(authoritative).let { it == null || it == CardState.Report }
        }
        val controller =
            McpChatController(
                item = item,
                gateway = gateway@{
                    if (!current()) return@gateway SOURCE_CHANGED.failed()
                    val gateway = environment.prepareActionRepository()
                    if (!current() || gateway == null) return@gateway SOURCE_CHANGED.failed()
                    // A browser round trip may replace the transport: recover through the fresh source first.
                    environment.syncCards()
                    cards.recover(authoritative)
                    val report = cards.state(authoritative) == CardState.Report
                    if (!current() || !environment.capabilities().genUi || !report) {
                        return@gateway SOURCE_CHANGED.failed()
                    }
                    gateway.ok()
                },
                capabilities = environment::capabilities,
                current = ::current,
                scope = scope,
                callbacks = callbacks ?: OAuthCallbackPort { _, _, _ -> SOURCE_CHANGED.failed() },
            )
        slots[key] = Slot(controller, origin, ::current)
        return Admission.Admitted(controller)
    }

    /** Call on every connection change: drops disposed controllers and invalidates those whose source changed. */
    public fun onChanged() {
        val iterator = slots.entries.iterator()
        while (iterator.hasNext()) {
            val slot = iterator.next().value
            when {
                slot.controller.isDisposed -> iterator.remove()
                !slot.current() -> {
                    iterator.remove()
                    slot.controller.invalidate()
                }
            }
        }
    }

    public fun dispose() {
        slots.values.forEach { it.controller.dispose() }
        slots.clear()
    }

    public companion object {
        public const val MAX_PENDING: Int = 32
        private val SOURCE_CHANGED: Failure = Failure.Unavailable(FeatureId("genUi"))
    }
}

package io.github.eslamasabry.oc.engine.agents.install

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.removal.RemovalRefusal
import kotlinx.coroutines.flow.StateFlow

public enum class AgentUpdateGroup { Needed, Optional }

/** What a person can choose for one optional item. Needed items have no choice. */
public enum class AgentUpdateChoice { Update, Keep, Remove }

public data class AgentUpdateItem(
    val id: AgentId,                 // the catalog id; for the needed parts the component ids below
    // the app's own display name (catalog name / "Node.js" / the helper's name), never an id
    val name: String,
    val fromVersion: String?,        // installed version on disk, null when not readable
    val toVersion: String,           // this app's pin
    val downloadBytes: Long?,        // bytes to download for Update, null when unknown
    val installedBytes: Long?,       // bytes on disk now (what Remove frees), null when unknown
    val group: AgentUpdateGroup,
    val keepAllowed: Boolean,        // false when the old version cannot run with this app (then the UI shows why)
    val removeAllowed: Boolean,      // false for Claude (RemovalRefusal.NotRemovable) and every Needed item
    val notKeptReason: AgentKeepBlock?,   // non-null exactly when keepAllowed == false
    val choice: AgentUpdateChoice?,  // the decision so far: null = not decided (UI preselects Update)
    val neededWhen: AgentNeedRule?,  // Needed items only: when the item is needed; null for Optional items
)

/**
 * Always: must move regardless (a helper outside the tested list).
 * WithAnyUpdate: comes with the first agent chosen for Update.
 */
public enum class AgentNeedRule { Always, WithAnyUpdate }

public enum class AgentKeepBlock { HelperTooNew, NotSupportedAnyMore }

public enum class AgentUpdateNetwork { Unmetered, Metered, Unknown }

public data class AgentUpdateOffer(
    // the app build the offer is for (versionCode as text): the sheet shows once per key
    val appUpdateKey: String,
    val items: List<AgentUpdateItem>,// Needed first, then Optional, each in catalog order
    val network: AgentUpdateNetwork,
    val freeBytes: Long?,            // free space where agents install, null when unknown
    val neededGoesAhead: Boolean,    // true: the Needed group installs even on "Not now" (on Wi-Fi only); see §4
    val sheetDue: Boolean,           // true: Home shows the sheet now (once per appUpdateKey, nothing decided yet)
    val answered: Boolean,           // true once decide() or notNow() ran for this appUpdateKey
)

public interface AgentUpdateChoices {
    /** null when nothing is behind (no offer, no sheet, no Home row from this). */
    public val offer: StateFlow<AgentUpdateOffer?>

    /** Start exactly the chosen updates and removals (removals only after confirmed()). */
    public suspend fun decide(choices: Map<AgentId, AgentUpdateChoice>): Outcome<AgentUpdateStarted>

    /** Defer every Optional item until the next app update; Needed per neededGoesAhead. */
    public suspend fun notNow(): Outcome<Unit>

    /** The facts the confirm before a removal states: read from the existing removal, never guessed. */
    public fun removalFacts(id: AgentId): AgentRemovalFacts

    /**
     * The Home row's "Review": makes the sheet show again (sheetDue = true) for the current key. [raise] false is a
     * reading only (room and versions again): it never makes the sheet due. The open sheet's timer reads that way; a
     * timer that raised the sheet re-opened one already answered.
     */
    public suspend fun review(raise: Boolean = true): Outcome<Unit>
}

public data class AgentUpdateStarted(
    val updating: List<AgentId>,
    val removing: List<AgentId>,
    val kept: List<AgentId>,
    val refused: Map<AgentId, RemovalRefusal> = emptyMap(), // (dev lead, 11-19) a refused removal and its reason
)
// RemovalRefusal is the engine's existing enum (engine/agents/removal/AgentRemoval.kt): NotRemovable, Busy, Unsafe,
// Failed, Unconfirmed. The UI maps it to the existing removal words.

public data class AgentRemovalFacts(
    val freesBytes: Long?,
    val keepsSignIn: Boolean,        // AgentRemoval keeps accounts: true today
    val keepsConversations: Boolean, // AgentRemoval keeps chats: true today
)

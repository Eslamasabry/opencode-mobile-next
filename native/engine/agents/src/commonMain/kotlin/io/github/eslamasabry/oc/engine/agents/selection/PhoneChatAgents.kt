package io.github.eslamasabry.oc.engine.agents.selection

import io.github.eslamasabry.oc.engine.agents.AgentId
import io.github.eslamasabry.oc.engine.agents.catalog.AgentCatalog
import io.github.eslamasabry.oc.engine.agents.certification.AgentCertification

/** What New conversation knows of one phone agent: whether it is ready now, and whether setup can get it there. */
public data class PhoneChatFact(
    val id: AgentId,
    val name: String,
    val iconKey: String,
    /** Installed, signed in, checked and running: it can start a conversation now. */
    val ready: Boolean,
    /** A way forward exists (install, sign in, phone check, resume); Flutter's `fixAction != null`. */
    val fixable: Boolean,
)

/**
 * The agents New conversation offers on this phone (lib/ui/screens/chats/new_chat_screen.dart): [choices] can start
 * the conversation, [needsSetup] are the dimmed ones that open their setup. OpenCode is not in either list; it is
 * always the first choice and is the connected server itself.
 */
public data class PhoneChatOffer(val choices: List<PhoneChatFact>, val needsSetup: List<PhoneChatFact>)

public object PhoneChatAgents {
    /**
     * A choice is every ready agent ([PhoneChatFact.ready], the same rule the Agents page's cards use to offer New
     * conversation), and the one already chosen. A set-up entry is a certified agent with a way forward that is not
     * already a choice. Both follow the catalog's own order (Claude Code first); agents the catalog does not know go
     * last, in the order given.
     *
     * Different from Flutter on purpose (owner decision 2026-11-11, after "Antigravity not showing"): Flutter's
     * `agentChoosable` also asked for chat certification, so a ready agent the bundled matrix has no row for
     * (Antigravity: neither Flutter's nor native's matrix lists it) had a "New conversation" on its card but was absent
     * from this list. The two screens now agree.
     */
    public fun offer(
        facts: List<PhoneChatFact>,
        selected: AgentId,
        certification: AgentCertification = AgentCertification.bundled,
        catalog: AgentCatalog = AgentCatalog.builtIn,
    ): PhoneChatOffer {
        fun rank(fact: PhoneChatFact): Int =
            catalog.agents.indexOfFirst { it.id == fact.id }.let { if (it < 0) catalog.agents.size else it }
        val choices =
            facts.filter { it.ready || it.id == selected }
        val shown = choices.map { it.id }.toSet()
        val setup =
            facts.filter { it.id !in shown && !it.ready && it.fixable && certification.certifiedForChat(it.id) }
        return PhoneChatOffer(choices.sortedBy(::rank), setup.sortedBy(::rank))
    }
}

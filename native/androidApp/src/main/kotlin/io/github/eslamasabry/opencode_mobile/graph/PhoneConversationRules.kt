package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.model.Session

/**
 * What decides which agent runs a conversation of an agents server, and whether this phone's agent conversations
 * need adopting at start (Flutter `_paDesiredDirectories`, `phone_agents.dart`). Pure.
 */
internal object PhoneConversationRules {
    /**
     * The agent that runs [session], as (id, label), read from the model the daemon reports for it
     * (`selection.model.providerId`, else the `provider/model` text). Null when the row does not say, so it stays a
     * plain row rather than being given a wrong mark.
     */
    fun agentOf(session: Session): Pair<String, String?>? {
        val provider =
            session.selection?.model?.providerId?.takeIf { it.isNotBlank() }
                ?: session.model?.substringBefore('/', "")?.takeIf { it.isNotBlank() }
                ?: return null
        return provider to provider
    }

    /**
     * Whether home should save the "Phone agents" source by itself: an agent is installed, the phone's Linux does not
     * wait for a restart, and no such source is saved yet.
     */
    fun shouldAdopt(available: Boolean, needsRestart: Boolean, installed: Int, alreadySaved: Boolean): Boolean =
        available && !needsRestart && installed > 0 && !alreadySaved
}

package io.github.eslamasabry.oc.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.entry.AutomationAlertSession

/** A tap on an automation's alert (B-620): the chat of its run, or the server list when it has none. */
internal data class AutomationAlertTap(val profile: ProfileId, val session: SessionId?)

/**
 * The automation alert this launch carries, or null for any other alert. [session] is the raw session extra: a real
 * session opens that chat; a pause, or an alert that carries no chat ([AutomationAlertSession]), opens Servers, where
 * the automation's row is. The entry decider holds the same rule for the router.
 */
internal fun automationAlertTap(kind: AlertKind?, session: String?, profile: String?): AutomationAlertTap? {
    if (kind == null || !kind.isAutomation) return null
    val owner = profile?.takeIf { it.isNotBlank() }?.let(::ProfileId) ?: return null
    val chat = session?.takeIf { it.isNotBlank() && it.isChat() && kind != AlertKind.AutomationPaused }
    return AutomationAlertTap(owner, chat?.let(::SessionId))
}

private fun String.isChat(): Boolean = !AutomationAlertSession.isNoChat(this)

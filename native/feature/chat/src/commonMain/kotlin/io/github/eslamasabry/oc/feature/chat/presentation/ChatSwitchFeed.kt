package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.feature.chat.arrival.ChatArrival
import io.github.eslamasabry.oc.feature.chat.arrival.ChatArrivals
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.RequestFocus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the edge light hands one conversation's page (plan v3.1 §4 and §7 C1): the "show this request" signal and the
 * arrival after a switch, each filtered to [ref] so a page never reacts to another conversation's. The signal is held
 * by the navigator until the page has shown it, so a page that is created after the switch (fresh) and one that was
 * kept and is composed again both receive it.
 */
internal class ChatSwitchFeed(
    private val ref: ConversationRef,
    private val nav: AppNavigator,
    private val arrivals: ChatArrivals,
    scope: CoroutineScope,
    private val saveDraft: suspend () -> Unit,
) {
    private val flushScope = scope

    /** The navigator's request for this conversation, null for any other conversation's or none. */
    val requests: Flow<RequestFocus?> = nav.requestFocus.map { it.mine() }

    val requestFocus: StateFlow<RequestFocus?> =
        requests.stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), nav.requestFocus.value.mine())

    /** The page showed request [seq]: it is not offered again (the navigator also drops it after 10 s). */
    fun requestShown(seq: Long) = nav.consumeRequestFocus(seq)

    val arrival: StateFlow<ChatArrival?> =
        arrivals.latest.map { it.mine() }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_MS), arrivals.latest.value.mine())

    fun hintTyped(seq: Long) = arrivals.typed(seq)

    fun backNoteShown(seq: Long) = arrivals.noteShown(seq)

    /** This page is gone for good. */
    fun closed() = arrivals.closed(ref)

    /** The page leaves the screen (a switch, Files, Back): the words typed so far reach their store now. */
    fun flushDraft() {
        flushScope.launch { saveDraft() }
    }

    private fun RequestFocus?.mine(): RequestFocus? = this?.takeIf { it.ref == ref }

    private fun ChatArrival?.mine(): ChatArrival? = this?.takeIf { it.target == ref }

    private companion object {
        const val STOP_MS = 5_000L
    }
}

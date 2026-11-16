package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.ids.ProfileId

/**
 * Who may start the agents' helper loop of a source (issue 147, small-faults 301). The home keeps a loop for a source
 * that is shown in its list ([keepListed]); a person opening one of the helper's conversations wants the helper whether
 * the source is shown or hidden ("not shown in the list" hides rows, never the agents' work) ([want] with
 * `evenHidden`). The app coming to the front wants it only for shown sources: a hidden source starts nothing then.
 * The facts are read through functions so the one runtime owns them and the rule is tested without it.
 */
internal class HelperWanting(
    private val joins: HelperJoins,
    private val isHelper: (ProfileId) -> Boolean,
    private val isActive: (ProfileId) -> Boolean,
    private val listed: (ProfileId) -> Boolean,
    private val shown: (ProfileId) -> Boolean,
    private val failure: (ProfileId) -> Failure?,
    /** Starts the source's one loop ([PreviewRuntime.joinPhoneHelper]). */
    private val startLoop: (ProfileId) -> Unit,
) {
    /** A shown helper source that is not listed and has no loop gets one, unless a failure waiting cannot fix. */
    fun keepListed(id: ProfileId) {
        if (!isHelper(id) || isActive(id) || !shown(id)) return
        start(id)
    }

    /**
     * The helper of [id] is needed now. Not listed: its loop runs, and one that pauses between tries gets a new window.
     * A hidden source is wanted only with [evenHidden] (a conversation of the helper opened).
     */
    fun want(id: ProfileId, evenHidden: Boolean = false) {
        if (!isHelper(id) || listed(id)) return
        if (!evenHidden && !shown(id)) return
        val running = joins.running(id)
        if (!isActive(id)) start(id)
        if (running) joins.wake(id)
    }

    private fun start(id: ProfileId) {
        if (listed(id) || joins.running(id)) return
        failure(id)?.let { if (!HelperStartRules.keepTrying(it)) return }
        startLoop(id)
    }
}

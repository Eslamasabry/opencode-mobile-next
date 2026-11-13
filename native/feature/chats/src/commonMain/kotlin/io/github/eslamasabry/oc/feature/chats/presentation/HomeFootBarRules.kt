package io.github.eslamasabry.oc.feature.chats.presentation

/**
 * Where the home's New conversation lives in each of its states (RC1 device pass, 2026-11-13: the + sometimes left the
 * foot bar while the server opened or did not answer, and once it was not on screen at all).
 *
 * | Phase   | New conversation                                                     |
 * |---------|----------------------------------------------------------------------|
 * | Opening | the foot bar's + (the skeleton under it; no count yet)               |
 * | List    | the foot bar's +, whether the server is open, opening or not answering |
 * | NoMatch | the foot bar's + (the calm page offers only its way back)            |
 * | Empty   | the calm page's full-width button at the bottom (G3), no foot bar    |
 * | NotRead | the calm page's New conversation under Try again (G3), no foot bar   |
 *
 * The connection never takes the + away: a tap before the server is open is answered by the home's "still opening"
 * notice.
 */
public object HomeFootBarRules {
    public fun shown(phase: ChatsPhase): Boolean =
        when (phase) {
            ChatsPhase.Opening, ChatsPhase.List, ChatsPhase.NoMatch -> true
            ChatsPhase.Empty, ChatsPhase.NotRead -> false
        }
}

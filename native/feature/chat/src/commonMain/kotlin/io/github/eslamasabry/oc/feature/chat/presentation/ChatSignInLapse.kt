package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.model.transcript.MessageErrorKind
import io.github.eslamasabry.oc.engine.transcript.state.FooterRow

/** Issue 147: whether a reply's footer says the agent's own sign-in no longer works (a failed reply, ProviderAuth). */
internal object ChatSignInLapse {
    fun lapsed(footer: FooterRow): Boolean =
        footer.failed && !footer.stopped && footer.errorKind == MessageErrorKind.ProviderAuth
}

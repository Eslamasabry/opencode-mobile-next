package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.security.ApprovedLink
import io.github.eslamasabry.oc.core.security.ExternalLinkOpener
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.feature.chat.presentation.ChatOutside
import io.github.eslamasabry.oc.feature.chat.presentation.LinkCheck
import io.github.eslamasabry.oc.feature.chat.presentation.NoChatOutside
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatRoute
import io.github.eslamasabry.oc.ui.routes.PreviewRoute

/*
 * P12-CHAT-FEEL: the Android half of the chat's clipboard, share sheet and links. Every reply link goes
 * ExternalLinkPolicy (origin Markdown: asks first) → ExternalLinkOpener (AGENTS.md invariant, R17); nothing here
 * launches a URL the policy did not approve. Nothing is logged (prompt and reply text never reach logs).
 */

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [NoChatOutside::class])
class AndroidChatOutside(
    private val application: Application,
    private val policy: ExternalLinkPolicy,
    // The app's one opener (PreviewQrWiring's AndroidExternalLinkOpener binding).
    private val opener: ExternalLinkOpener,
    private val navigator: AppNavigator,
) : ChatOutside {

    /** Links the policy approved and the person is being asked about (one at a time on the screen). */
    private val asked = LinkedHashMap<String, ApprovedLink>()

    override fun copy(text: String): Boolean =
        platformCall {
            val clipboard = application.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("OpenCode Mobile", text))
        }

    override fun share(text: String): Boolean =
        platformCall {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            application.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

    /** The platform's expected refusals (no app, a system policy) are a plain "it did not work". */
    private inline fun platformCall(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }

    override fun checkLink(url: String): LinkCheck {
        // P12-FIX-PREVIEWQR: `http://localhost:5173` in a reply is the conversation's dev server, not the phone's:
        // it opens in the in-app preview against that conversation's server (Flutter claims these links).
        PreviewRoute.Page.claim(url, (navigator.top.value as? ChatRoute)?.ref)?.let { page ->
            navigator.open(page)
            return LinkCheck.Opened
        }
        return when (val decision = policy.check(url, LinkOrigin.Markdown)) {
            is LinkDecision.Open -> if (opener.open(decision.link) is Outcome.Ok) LinkCheck.Opened else LinkCheck.Refused
            is LinkDecision.AskFirst -> {
                if (asked.size >= MAX_ASKED) asked.remove(asked.keys.first())
                asked[url] = decision.link
                LinkCheck.Ask(decision.shownHost)
            }
            is LinkDecision.Refuse -> LinkCheck.Refused
        }
    }

    override fun openLink(url: String): Boolean {
        val link = asked.remove(url) ?: return false
        return opener.open(link) is Outcome.Ok
    }

    private companion object {
        const val MAX_ASKED = 8
    }
}

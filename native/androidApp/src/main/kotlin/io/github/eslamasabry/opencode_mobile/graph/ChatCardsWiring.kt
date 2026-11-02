package io.github.eslamasabry.opencode_mobile.graph

import android.app.Application
import android.content.Intent
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.data.kv.PrefValue
import io.github.eslamasabry.oc.core.security.ExternalLinkPolicy
import io.github.eslamasabry.oc.core.security.LinkDecision
import io.github.eslamasabry.oc.core.security.LinkOrigin
import io.github.eslamasabry.oc.engine.cards.CardsPrefs
import io.github.eslamasabry.oc.feature.chat.cards.CardLinkOpener

/**
 * P12-CHAT-CARDS Android bindings: the "Cards from agents" choice under the Flutter key `oc.genui.enabled.<profileId>`
 * (off when absent, so a migrated install keeps the person's choice), and card links opened only after the
 * ExternalLinkPolicy check with the agent-card origin (AGENTS.md rule 8; the card already shows the host).
 */
internal class PreviewCardsPrefs(private val stores: PreviewStores) : CardsPrefs {
    override fun enabled(profileId: String): Boolean? =
        try {
            (stores.prefs.get(CardsPrefs.enabledKey(profileId)) as? PrefValue.Bool)?.value
        } catch (e: RuntimeException) {
            null // the store is not open yet: off until it is
        }

    override suspend fun setEnabled(profileId: String, on: Boolean): Outcome<Unit> {
        if (stores.warm() is Outcome.Failed) return Outcome.Failed(Failure.StorageRefused())
        return stores.prefs.commit(stores.lanes.app) { putBool(CardsPrefs.enabledKey(profileId), on) }
    }
}

internal class AndroidCardLinkOpener(private val application: Application) : CardLinkOpener {
    private val policy = ExternalLinkPolicy()

    override fun open(url: String) {
        val link =
            when (val decision = policy.check(url, LinkOrigin.AgentCard)) {
                is LinkDecision.Open -> decision.link
                is LinkDecision.AskFirst -> decision.link // the card's button already names the host it opens
                is LinkDecision.Refuse -> return
            }
        try {
            application.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: android.content.ActivityNotFoundException) {
            // No browser: nothing opens; the card stays as it was.
        }
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
object ChatCardsAndroidBindings {
    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun cardsPrefs(stores: PreviewStores): CardsPrefs = PreviewCardsPrefs(stores)

    @Provides
    @IntoSet
    @SingleIn(AppScope::class)
    fun cardLinks(application: Application): CardLinkOpener = AndroidCardLinkOpener(application)
}

package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.engine.settings.reader.ReaderPreferencesStore
import io.github.eslamasabry.oc.feature.files.presentation.FilesReaderPreferences

/** Uses the same profile writer lane and preferences file as profile deletion. */
@Inject
@ContributesIntoSet(AppScope::class, binding = binding<FilesReaderPreferences>())
class AndroidFilesReaderPreferences(private val stores: PreviewStores) : FilesReaderPreferences {
    override fun open(profile: ProfileId): ReaderPreferencesStore =
        ReaderPreferencesStore(stores.prefs, stores.lanes, profile)
}

/** Markdown egress asks with the policy's displayed host before the opaque ticket can be opened. */
@Inject
@ContributesIntoSet(
    AppScope::class,
    binding = binding<io.github.eslamasabry.oc.feature.files.presentation.FilesExternalLinks>(),
)
class AndroidFilesExternalLinks(
    private val policy: io.github.eslamasabry.oc.core.security.ExternalLinkPolicy,
    private val opener: io.github.eslamasabry.oc.core.security.ExternalLinkOpener,
) : io.github.eslamasabry.oc.feature.files.presentation.FilesExternalLinks {
    override fun check(
        url: String
    ): io.github.eslamasabry.oc.feature.files.presentation.FilesLinkTicket =
        when (
            val decision =
                policy.check(url, io.github.eslamasabry.oc.core.security.LinkOrigin.Markdown)
        ) {
            is io.github.eslamasabry.oc.core.security.LinkDecision.Open ->
                io.github.eslamasabry.oc.feature.files.presentation.FilesLinkTicket.Open(
                    decision.link
                )
            is io.github.eslamasabry.oc.core.security.LinkDecision.AskFirst ->
                io.github.eslamasabry.oc.feature.files.presentation.FilesLinkTicket.AskFirst(
                    decision.shownHost,
                    decision.link,
                )
            is io.github.eslamasabry.oc.core.security.LinkDecision.Refuse ->
                io.github.eslamasabry.oc.feature.files.presentation.FilesLinkTicket.Refused
        }

    override fun open(token: Any): Boolean =
        (token as? io.github.eslamasabry.oc.core.security.ApprovedLink)?.let {
            opener.open(it) is io.github.eslamasabry.oc.core.common.Outcome.Ok
        } ?: false
}

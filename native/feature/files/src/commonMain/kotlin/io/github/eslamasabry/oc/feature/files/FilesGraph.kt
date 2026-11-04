package io.github.eslamasabry.oc.feature.files

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.ConnectionScope
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.FeatureId
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.domain.FileGateway
import io.github.eslamasabry.oc.core.domain.ServerGateway
import io.github.eslamasabry.oc.engine.composer.drafts.ConversationDrafts
import io.github.eslamasabry.oc.engine.composer.input.Mentions
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.engine.files.FileHandoff
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.files.PdfPages
import io.github.eslamasabry.oc.engine.files.ShareHost
import io.github.eslamasabry.oc.engine.files.WifiShare
import io.github.eslamasabry.oc.feature.files.presentation.ComposerMentions
import io.github.eslamasabry.oc.feature.files.presentation.ConversationFiles
import io.github.eslamasabry.oc.feature.files.presentation.FilesConnections
import kotlin.time.Clock
import kotlinx.coroutines.flow.first

/**
 * Files reads its conversation's gateway from that conversation's own graph (ADR §9.1), like the
 * chat does.
 */
@ContributesTo(ConnectionScope::class)
public interface FilesGraphAccessors {
    public val filesGateway: ServerGateway
}

/** Platform halves; an empty set hides the action (desktop has none of them today). */
@ContributesTo(AppScope::class)
public interface FilesPlatformBindings {
    @Multibinds(allowEmpty = true) public val shareHosts: Set<ShareHost>

    @Multibinds(allowEmpty = true) public val fileHandoffs: Set<FileHandoff>

    @Multibinds(allowEmpty = true) public val pdfPages: Set<PdfPages>

    @Multibinds(allowEmpty = true) public val fileSavers: Set<FileSaver>
}

@BindingContainer
@ContributesTo(AppScope::class)
public object FilesProviders {
    /** One Wi-Fi share for the whole app: starting another stops the first. */
    @Provides
    @SingleIn(AppScope::class)
    public fun wifiShare(hosts: Set<ShareHost>, clock: Clock, scope: AppCoroutineScope): WifiShare {
        check(hosts.size <= 1) { "multiple Wi-Fi share hosts" }
        return WifiShare(hosts.singleOrNull(), clock, scope)
    }
}

@Inject
@ContributesBinding(AppScope::class)
public class GraphFilesConnections(private val registry: ConnectionRegistry) : FilesConnections {
    private fun live(ref: ConversationRef): ServerGateway? =
        (registry.graphFor(ref) as? FilesGraphAccessors)?.filesGateway

    // Each screen reads its conversation's own folder (B-NAV-026), never the gateway's shared
    // location.
    override fun gateway(ref: ConversationRef): FileGateway? =
        live(ref)?.let { ConversationFiles(it, ref.sessionId) }

    override fun canBrowse(ref: ConversationRef): Boolean =
        live(ref)?.capabilities?.value?.fileBrowsing == true

    override fun canTerminal(ref: ConversationRef): Boolean =
        live(ref)?.capabilities?.value?.terminal == true

    override suspend fun directory(ref: ConversationRef): String? {
        val gateway = live(ref) ?: return null
        return ConversationFiles(gateway, ref.sessionId).directory()
    }
}

/**
 * "Ask about this file" from a conversation (files_viewer.dart `_askAbout`): appends `@path ` to
 * that conversation's draft and, when the project folder is known, adds the file mention the
 * composer sends as a file part (Mentions.projectFile, B-CHAT-087). The draft owner persists it;
 * the chat's composer restores it on return.
 */
@Inject
@ContributesBinding(AppScope::class)
public class DraftComposerMentions(private val draftStores: Set<ConversationDrafts>) :
    ComposerMentions {
    override suspend fun mention(
        ref: ConversationRef,
        path: String,
        directory: String?,
    ): Outcome<Unit> {
        val drafts =
            draftStores.singleOrNull()
                ?: return Outcome.Failed(Failure.Unavailable(FeatureId("drafts")))
        val session = drafts.open(ref)
        val ready = session.state.first { it.ready || it.failure != null }
        if (!ready.ready) return Outcome.Failed(ready.failure ?: Failure.StorageRefused())
        val mention = "@$path "
        val text = ready.text
        val next =
            when {
                text.contains(mention) -> text
                text.isEmpty() || text.endsWith(' ') || text.endsWith('\n') -> text + mention
                else -> "$text $mention"
            }
        session.changed(next)
        if (directory != null && ready.attachments.none { it.mentionPath == path }) {
            val added =
                session.replaceAttachments(
                    ready.attachments + Mentions.projectFile(directory, path)
                )
            if (added is Outcome.Failed) return added
        }
        return session.flush()
    }
}

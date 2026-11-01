package io.github.eslamasabry.oc.feature.chats

import androidx.compose.foundation.Indication
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.engine.agents.feed.ChatFeedItem
import io.github.eslamasabry.oc.engine.agents.feed.ChatStatus
import io.github.eslamasabry.oc.engine.agents.feed.FeedFolders
import io.github.eslamasabry.oc.feature.chats.presentation.ChatsActions
import io.github.eslamasabry.oc.ui.design.effects.FrostSpec
import io.github.eslamasabry.oc.ui.design.effects.KitAppearance
import io.github.eslamasabry.oc.ui.design.motion.KitHaptic
import io.github.eslamasabry.oc.ui.design.motion.MotionLevel
import io.github.eslamasabry.oc.ui.kit.KitTheme
import io.github.eslamasabry.oc.ui.kit.foundation.KitFillIndication
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatform
import io.github.eslamasabry.oc.ui.kit.platform.KitPlatformFamily
import io.github.eslamasabry.oc.ui.kit.platform.KitSystemSignals
import kotlinx.coroutines.flow.MutableStateFlow

internal val P1 = ProfileId("p1")
internal val BRIEF_SCOPE =
    io.github.eslamasabry.oc.engine.agents.feed.BriefScope(P1, "opencode:p1", "test-location")

/** A feed row as the OpenCode source would build it (fake data, not copy). */
internal fun item(
    id: String,
    dir: String = "/work/api",
    at: Long = 1_000,
    title: String? = "Chat $id",
    status: ChatStatus = ChatStatus.Idle,
    parent: String? = null,
    unseen: Boolean = false,
): ChatFeedItem =
    ChatFeedItem(
        sourceId = "opencode:p1",
        locationKey = BRIEF_SCOPE.location,
        profileId = P1,
        sessionId = SessionId(id),
        title = title,
        directory = dir,
        projectName =
            if (FeedFolders.isOtherFolder(dir)) FeedFolders.otherFolderLabel(dir)
            else FeedFolders.folderName(dir),
        status = status,
        lastActivityMs = at,
        parentId = parent?.let(::SessionId),
        finishedUnseen = unseen,
    )

internal class RecordingChatsActions : ChatsActions {
    val calls = mutableListOf<String>()

    override fun openConversation(ref: ConversationRef) {
        calls += "open ${ref.sessionId.raw}"
    }

    override fun newConversation() {
        calls += "new"
    }

    override fun openSettings() {
        calls += "settings"
    }

    override fun search(query: String) {
        calls += "search $query"
    }

    override fun toggleRunning() {
        calls += "running"
    }

    override fun toggleNeedsYou() {
        calls += "needsYou"
    }

    override fun chooseProject(directory: String) {
        calls += "project $directory"
    }

    override fun clearFilters() {
        calls += "clear"
    }

    override fun dismissBrief(shown: io.github.eslamasabry.oc.engine.agents.feed.ReturnBrief) {
        calls += "dismiss"
    }

    override fun showBriefOverflow(
        shown: io.github.eslamasabry.oc.feature.chats.presentation.BriefUi
    ) {
        calls += "overflow"
    }

    override fun showAll() {
        calls += "all"
    }
}

/** Plan 06 §6's fake platform (kit's own copy is test-internal there). */
internal class TestKitPlatform : KitPlatform {
    override val name: String = "test"
    override val family: KitPlatformFamily = KitPlatformFamily.Desktop
    override val signals: MutableStateFlow<KitSystemSignals> = MutableStateFlow(KitSystemSignals())
    override val isDebug: Boolean = true

    override fun pressIndication(ink: Color): Indication = KitFillIndication(ink)

    override fun haptic(kind: KitHaptic) {}

    override fun frostEffect(spec: FrostSpec, density: Float): RenderEffect? = null
}

@Composable
internal fun Themed(content: @Composable () -> Unit) {
    KitTheme(
        appearance = KitAppearance(motion = MotionLevel.Off),
        platform = TestKitPlatform(),
        localeTag = "en",
        stillFrame = true,
        content = content,
    )
}

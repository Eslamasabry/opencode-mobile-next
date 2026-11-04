package io.github.eslamasabry.oc.feature.chats.presentation

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.github.eslamasabry.oc.core.domain.platform.AppNotice
import io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction
import io.github.eslamasabry.oc.core.domain.platform.AppNotices
import io.github.eslamasabry.oc.core.domain.platform.ReplyNotificationOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Desktop has no Android background connection or notification consent prompt. */
@Inject
@ContributesBinding(AppScope::class)
public class UnsupportedAppNotices : AppNotices {
    override val status: StateFlow<AppNotice?> = MutableStateFlow(null)
    override val replyOffer: StateFlow<ReplyNotificationOffer?> = MutableStateFlow(null)

    override fun act(action: AppNoticeAction) {}

    override fun dismiss(id: String) {}

    override fun answerReplyOffer(allow: Boolean) {}
}

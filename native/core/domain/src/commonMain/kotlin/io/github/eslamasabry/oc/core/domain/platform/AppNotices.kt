package io.github.eslamasabry.oc.core.domain.platform

import kotlinx.coroutines.flow.StateFlow

/** One app condition, with words resolved from native resources by the platform owner. */
public data class AppNotice(
    val id: String,
    val message: String,
    val action: AppNoticeAction? = null,
    val actionLabel: String? = null,
    val dismissLabel: String,
    val stoppedApp: Boolean = false,
)

public enum class AppNoticeAction {
    ResumeBackground,
    KeepRunning,

    /** E09-S04: the Termux move notice's "Set up built-in Linux" (opens This phone). */
    SetUpBuiltInLinux,
}

/** The one-time question, offered only after a real reply has completed. */
public data class ReplyNotificationOffer(
    val message: String,
    val acceptLabel: String,
    val declineLabel: String,
    val busy: Boolean = false,
    val failed: Boolean = false,
)

public interface AppNotices {
    public val status: StateFlow<AppNotice?>
    public val replyOffer: StateFlow<ReplyNotificationOffer?>

    public fun act(action: AppNoticeAction)

    public fun dismiss(id: String)

    public fun answerReplyOffer(allow: Boolean)
}

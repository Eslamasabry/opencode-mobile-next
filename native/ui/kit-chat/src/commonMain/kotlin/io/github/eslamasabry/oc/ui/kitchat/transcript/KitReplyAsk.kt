package io.github.eslamasabry.oc.ui.kitchat.transcript

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

public const val REPLY_ASK_TAG: String = "kit-reply-ask"
public const val REPLY_ASK_YES_TAG: String = "kit-reply-ask-yes"
public const val REPLY_ASK_NO_TAG: String = "kit-reply-ask-no"
public const val REPLY_ASK_SKIP_TAG: String = "kit-reply-ask-skip"
public const val REPLY_ASK_AFTER_TAG: String = "kit-reply-ask-after"
public const val REPLY_ASK_ACTION_TAG: String = "kit-reply-ask-action"

/** Quiet first-reply ask riding in [KitReplyFooter]'s metadata slot. */
@Composable
public fun KitReplyAsk(
    question: String,
    yes: String,
    no: String,
    skip: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
): Unit = Unit

/** Follow-up line shown in [KitReplyFooter]'s metadata slot after Yes or No. */
@Composable
public fun KitReplyAskAfter(
    note: String,
    action: String,
    onAction: () -> Unit,
    star: Boolean,
    modifier: Modifier = Modifier,
): Unit = Unit

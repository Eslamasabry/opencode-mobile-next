package io.github.eslamasabry.oc.feature.chat.presentation

import io.github.eslamasabry.oc.core.domain.platform.AppNotice

/**
 * The app condition a chat shows in its status slot (E05-S22). "OpenCode Mobile was closed …" belongs on the home
 * list only, as in Flutter; above a conversation it would sit over the messages until dismissed.
 */
internal fun AppNotice?.shownInChat(): AppNotice? = this?.takeUnless { it.stoppedApp }

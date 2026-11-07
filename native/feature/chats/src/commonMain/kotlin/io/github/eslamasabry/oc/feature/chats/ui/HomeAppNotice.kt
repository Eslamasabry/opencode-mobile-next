package io.github.eslamasabry.oc.feature.chats.ui

import io.github.eslamasabry.oc.core.domain.platform.AppNotice
import io.github.eslamasabry.oc.core.domain.platform.AppNoticeAction
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction
import io.github.eslamasabry.oc.ui.kit.icon.KitIcons
import io.github.eslamasabry.oc.ui.kit.status.KitStatus
import io.github.eslamasabry.oc.ui.kit.status.KitStatusKind

/**
 * The home list's status line for an app condition (E05-S22). "OpenCode Mobile was closed …" carries the kit's
 * restart glyph, as Flutter's `AppIconography.restart`, not the neutral tone's empty ring.
 */
internal fun homeAppNoticeStatus(
    notice: AppNotice,
    act: (AppNoticeAction) -> Unit,
    dismiss: (String) -> Unit,
): KitStatus =
    KitStatus(
        if (notice.stoppedApp) KitStatusKind.AppStopped else KitStatusKind.Info,
        notice.message,
        id = notice.id,
        action =
            notice.action?.let { action ->
                notice.actionLabel?.let { label -> KitLineAction(label, { act(action) }) }
            },
        onDismiss = { dismiss(notice.id) },
        dismissLabel = notice.dismissLabel,
        more =
            listOfNotNull(
                notice.secondAction?.let { second ->
                    notice.secondActionLabel?.let { label -> KitLineAction(label, { act(second) }) }
                }
            ),
        icon = if (notice.stoppedApp) KitIcons.restart else null,
    )

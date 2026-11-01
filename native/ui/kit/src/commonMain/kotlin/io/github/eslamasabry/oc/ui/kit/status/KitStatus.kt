package io.github.eslamasabry.oc.ui.kit.status

import androidx.compose.runtime.Immutable
import io.github.eslamasabry.oc.ui.design.color.StatusTone
import io.github.eslamasabry.oc.ui.kit.foundation.KitLineAction

/**
 * Which condition a status is, highest first (Dart `KitStatusKind`; KIT-35). `work` sits under the shell's conditions
 * and above updates, so a team's Now line is not hidden by "Update ready".
 */
public enum class KitStatusKind { Connection, AppStopped, Heat, RiskySwitch, Work, Update, Info }

/**
 * One condition, produced by the one status source for its entity (Dart `KitStatus`; ARCH-8): the words are decided
 * there, once; screens pass this object, not strings. [id] is the stable identity for dedupe and announcements
 * ("connection:<profileId>"). [onDismiss] only when dismissing changes nothing real; never on a risky switch.
 */
@Immutable
public data class KitStatus(
    val kind: KitStatusKind,
    val message: String,
    val id: String? = null,
    val tone: StatusTone = StatusTone.Neutral,
    val supporting: String? = null,
    val next: String? = null,
    val action: KitLineAction? = null,
    val onDismiss: (() -> Unit)? = null,
    val dismissLabel: String? = null,
) {
    /** Lower is more important. */
    val priority: Int get() = kind.ordinal

    /** What a screen reader is told once: a change of id, kind or message (not a time in the supporting line). */
    val announcementKey: String get() = "${id ?: kind.name}|${kind.name}|$message"

    public companion object {
        /** The one status to show (Dart `KitStatus.highest`): the lowest priority value; ties keep the first. */
        public fun highest(statuses: Iterable<KitStatus?>): KitStatus? {
            var best: KitStatus? = null
            for (s in statuses) if (s != null && (best == null || s.priority < best.priority)) best = s
            return best
        }
    }
}

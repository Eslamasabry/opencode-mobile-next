package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.ShadeReply
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import kotlinx.collections.immutable.persistentListOf

/**
 * BUILD-REST app wiring (one file per unit). B-PROTO-046: a notification Reply answers the exact request it was posted
 * for. On a question the typed text is the answer; on a permission it rejects with the text as the note (Dart
 * `connection_interaction_test`: "Notification Reply on an OpenCode 2 permission rejects with a message"). An empty
 * reply never reaches here (the shade does not deliver it).
 */
internal object ShadeReplyAnswers {
    fun of(kind: AlertKind, reply: ShadeReply): RequestAnswer =
        when (reply) {
            ShadeReply.AllowOnce -> RequestAnswer.Permission(PermissionReply.Once)
            ShadeReply.Reject ->
                if (kind == AlertKind.Question) RequestAnswer.Decline else RequestAnswer.Permission(PermissionReply.Reject)
            is ShadeReply.Text ->
                if (kind == AlertKind.Question) {
                    RequestAnswer.Question(persistentListOf(persistentListOf(reply.text)))
                } else {
                    RequestAnswer.Permission(PermissionReply.Reject, note = reply.text)
                }
        }
}

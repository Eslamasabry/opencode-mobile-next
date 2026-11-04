package io.github.eslamasabry.opencode_mobile.graph

import io.github.eslamasabry.oc.android.notifications.AlertKind
import io.github.eslamasabry.oc.android.notifications.ShadeReply
import io.github.eslamasabry.oc.core.model.PermissionReply
import io.github.eslamasabry.oc.engine.requests.RequestAnswer
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ShadeReplyAnswersTest {
    @Test
    fun `B-PROTO-046 a typed Reply on a permission rejects it with the message as its note`() {
        assertEquals(
            RequestAnswer.Permission(PermissionReply.Reject, note = "use the staging db"),
            ShadeReplyAnswers.of(AlertKind.Permission, ShadeReply.Text("use the staging db")),
        )
    }

    @Test
    fun `B-PROTO-046 a typed Reply on a question answers it, and Reject and Allow once keep their meaning`() {
        assertEquals(
            RequestAnswer.Question(persistentListOf(persistentListOf("SQLite"))),
            ShadeReplyAnswers.of(AlertKind.Question, ShadeReply.Text("SQLite")),
        )
        assertEquals(RequestAnswer.Decline, ShadeReplyAnswers.of(AlertKind.Question, ShadeReply.Reject))
        assertEquals(RequestAnswer.Permission(PermissionReply.Reject), ShadeReplyAnswers.of(AlertKind.Permission, ShadeReply.Reject))
        assertEquals(RequestAnswer.Permission(PermissionReply.Once), ShadeReplyAnswers.of(AlertKind.Permission, ShadeReply.AllowOnce))
    }
}

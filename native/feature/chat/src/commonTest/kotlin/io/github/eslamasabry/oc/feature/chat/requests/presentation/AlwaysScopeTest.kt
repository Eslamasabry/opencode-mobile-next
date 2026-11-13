package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.core.common.ids.RequestId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import io.github.eslamasabry.oc.core.model.PermissionRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Review 2026-11-13 #3: what Always allow covers, as a choice of words built from the saved patterns. */
class AlwaysScopeTest {
    private fun scope(permission: String, vararg always: String) =
        AlwaysScopes.of(
            PermissionRequest(
                RequestId("p"), SessionId("s"), permission, listOf("x").toImmutableList(), JsonObject(emptyMap()),
                always.toList().toImmutableList(), null, true, null,
            ),
        )

    @Test
    fun `B-CHAT-REV3-010 a star on a file change is any file change, other patterns are named`() {
        assertEquals(AlwaysScope.AnyFileChange, scope("edit", "*"))
        assertEquals(AlwaysScope.FileChangesMatching("src/a.kt, docs"), scope("edit", "src/a.kt", "docs"))
    }

    @Test
    fun `B-CHAT-REV3-011 commands read as every command, a family with any arguments, or the exact command`() {
        assertEquals(AlwaysScope.AnyCommand, scope("bash", "*"))
        assertEquals(AlwaysScope.CommandFamily("npm install"), scope("bash", "npm install *"))
        assertEquals(AlwaysScope.Exactly("git status"), scope("shell", "git status"))
        assertEquals(AlwaysScope.Matching("git *, npm *"), scope("bash", "git *", "npm *"))
    }

    @Test
    fun `B-CHAT-REV3-012 other asks read as every request like this, and nothing saved has no scope`() {
        assertEquals(AlwaysScope.Everything, scope("webfetch", "*"))
        assertEquals(AlwaysScope.Everything, scope("task", "task"))
        assertNull(scope("edit"))
    }
}

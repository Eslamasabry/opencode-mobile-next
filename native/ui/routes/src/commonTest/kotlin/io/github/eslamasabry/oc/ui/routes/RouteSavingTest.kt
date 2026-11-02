package io.github.eslamasabry.oc.ui.routes

import androidx.navigation3.runtime.NavKey
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

/** P-03-5: subclassesOfSealed registers nested sealed routes declared across files. */
class RouteSavingTest {
    private val json = Json { serializersModule = RouteSaving.module }
    private val serializer = PolymorphicSerializer(NavKey::class)

    private val samples: List<AppRoute> =
        listOf(
            ChatsRoute.Home,
            ServersRoute.Home,
            ChatsRoute.All("running"),
            ChatsRoute.New(ProfileId("p1"), draftKey = "d1"),
            ChatRoute(ConversationRef(ProfileId("p1"), SessionId("s1")), focusComposer = true),
        )

    @Test
    fun `every route round-trips through the route saving module`() {
        samples.forEach { route ->
            val text = json.encodeToString(serializer, route)
            assertEquals(route, json.decodeFromString(serializer, text))
        }
    }
}

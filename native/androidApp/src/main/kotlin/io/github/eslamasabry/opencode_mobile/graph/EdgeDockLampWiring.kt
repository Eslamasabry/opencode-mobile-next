package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.AppCoroutineScope
import io.github.eslamasabry.oc.core.common.AppDispatchers
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.engine.connection.ConnectionRegistry
import io.github.eslamasabry.oc.feature.chat.arrival.BackNoteCount
import io.github.eslamasabry.oc.feature.chats.lamp.HeldStops
import io.github.eslamasabry.oc.feature.chats.lamp.LampHeight
import io.github.eslamasabry.oc.feature.chats.lamp.LampPlace
import io.github.eslamasabry.oc.feature.chats.lamp.LampPort
import io.github.eslamasabry.oc.feature.chats.lamp.LampSide
import io.github.eslamasabry.oc.feature.chats.presentation.HeldAnswers
import io.github.eslamasabry.oc.feature.chats.presentation.HomeStop
import io.github.eslamasabry.oc.feature.settings.presentation.OtherConversationsMode
import io.github.eslamasabry.oc.ui.kit.lamp.KitLampSide
import io.github.eslamasabry.oc.ui.routes.AppNavigator
import io.github.eslamasabry.oc.ui.routes.ChatsRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The shared stores of the edge light's list, one per app: the permission answers' undo window (the home rows and the
 * list hold through the same one, so an answer made in one place shows its Undo in the other and leaves once), the
 * stop's own window, and the one stop path of the home row's menu and the list (the chat's own `stopLikeTheChat`).
 * They live in the app scope: closing the panel or leaving the home cancels no hold.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object LampBindings {
    @Provides
    @SingleIn(AppScope::class)
    fun heldAnswers(appScope: AppCoroutineScope, dispatchers: AppDispatchers, answers: HomePermissionAnswerWiring): HeldAnswers {
        val main = CoroutineScope(appScope.coroutineContext + dispatchers.main)
        return HeldAnswers(main) { ref, id, reply -> main.launch { answers.answer(ref, id, reply) } }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun homeStop(connections: ConnectionRegistry): HomeStop = HomeStop { ref -> stopLikeTheChat(connections.graphFor(ref), ref) }

    @Provides
    @SingleIn(AppScope::class)
    fun heldStops(appScope: AppCoroutineScope, dispatchers: AppDispatchers, stopper: HomeStop): HeldStops =
        HeldStops(CoroutineScope(appScope.coroutineContext + dispatchers.main), stopper)
}

/** What the list's acts ask of the app: the dock's pins, names and hides, the routes, the place and the Settings choice. */
@SingleIn(AppScope::class)
@Inject
class AppLampPort(private val dock: AppEdgeDock, private val navigator: AppNavigator) : LampPort {
    override fun pin(ref: ConversationRef) = dock.pin(ref)

    override fun unpin(ref: ConversationRef) = dock.unpin(ref)

    override fun reorder(from: ConversationRef, to: ConversationRef) = dock.reorder(from, to)

    override fun rename(ref: ConversationRef, name: String, onFailed: () -> Unit) = dock.rename(ref, name, onFailed)

    override fun hide(ref: ConversationRef) = dock.remove(ref)

    override fun restore(ref: ConversationRef) = dock.restore(ref)

    override fun newConversation() = navigator.open(ChatsRoute.New(profile = null))

    override fun allConversations() = navigator.open(ChatsRoute.All())

    override fun place(place: LampPlace) =
        dock.setLightPlace(
            EdgeLightPlace(
                side = if (place.side == LampSide.Start) KitLampSide.Start else KitLampSide.End,
                height =
                    when (place.height) {
                        LampHeight.Low -> EdgeLightHeight.Low
                        LampHeight.Middle -> EdgeLightHeight.Middle
                        LampHeight.High -> EdgeLightHeight.High
                    },
            )
        )

    override fun turnOff() = dock.setMode(OtherConversationsMode.Hidden)
}

/** The light's saved place as the list's own kind of place. */
internal fun EdgeLightPlace.toLampPlace(): LampPlace =
    LampPlace(
        side = if (side == KitLampSide.Start) LampSide.Start else LampSide.End,
        height =
            when (height) {
                EdgeLightHeight.Low -> LampHeight.Low
                EdgeLightHeight.Middle -> LampHeight.Middle
                EdgeLightHeight.High -> LampHeight.High
            },
    )

/**
 * How many "Back returns to ‹title›" notes this device has shown, for the conversation page's note after an edge-light
 * switch: the light's own device-wide count (`oc.edgeLight.backNotes`), so it survives a restart and is never swept with
 * a profile.
 */
@ContributesIntoSet(AppScope::class, binding = binding<BackNoteCount>())
@Inject
class AppBackNoteCount(private val dock: AppEdgeDock) : BackNoteCount {
    override fun shown(): Int = dock.lightTaught.value.backNotes

    override fun recordShown() = dock.taughtMore(backNotes = 1)
}

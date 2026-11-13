package io.github.eslamasabry.oc.ui.routes

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed

/** Every destination. Sealed, so the SerializersModule registers all of them in one line (plan 03 S17). */
@Serializable
public sealed interface AppRoute : NavKey {
    /** How the destination enters and leaves; :ui:shell turns it into transitions (ADR §9.4). */
    public val motion: RouteMotion
        get() = RouteMotion.SharedAxisX
}

public enum class RouteMotion { Root, SharedAxisX, GrowFromRow, SlideUp }

public object RouteSaving {
    @OptIn(ExperimentalSerializationApi::class)
    public val module: SerializersModule =
        SerializersModule { polymorphic(NavKey::class) { subclassesOfSealed<AppRoute>() } }

    public val configuration: SavedStateConfiguration = SavedStateConfiguration { serializersModule = module }
}

/** Features contribute these with @ContributesIntoSet(AppScope::class); the shell installs them all. No central list. */
public fun interface EntryInstaller {
    public fun EntryProviderScope<NavKey>.install()
}

/** The only navigation API ViewModels see (plan 03 §15.3). Main thread only. */
public interface AppNavigator {
    public val top: StateFlow<AppRoute?>

    /** Push unless the same route is already on top. A Root-motion route replaces the stack. */
    public fun open(route: AppRoute)

    /** Open a conversation; never stacks a duplicate of the chat on top. */
    public fun openChat(ref: ConversationRef, focusComposer: Boolean = false)

    /** Clear to Home ("landing"). */
    public fun land()

    /** Replace the whole stack. */
    public fun replaceRoot(root: AppRoute)

    /** Pop one entry. With one entry left it does nothing: the root never intercepts Back (ADR §9.4, D5). */
    public fun back()

    /**
     * Go back to the conversation [ref] (a sub-agent's chat going to the conversation that started it): pops the top
     * when [ref]'s chat is the entry right below it (the child was opened from the parent's card), otherwise opens it
     * (the child was opened from the dock, a notification or the Subagents page). Never stacks the parent twice.
     */
    public fun backToChat(ref: ConversationRef) {
        openChat(ref)
    }

    /**
     * The edge light's switch (plan v3.1 §3): go to [target] so that one Back returns to the conversation the person
     * left, exactly as they left it (its open Files, Changes or Git page included), and one more Back goes where that
     * one went before. Only one step is kept: A → B → C, then Back goes C → B → the page under B. Switching back to
     * the kept conversation swaps the two. [request], when given, also asks [target]'s page to show that request card
     * ([requestFocus]). Only the edge light calls this; every other way in keeps [openChat].
     */
    public fun switchTo(target: ConversationRef, request: String? = null) {
        openChat(target)
    }

    /** The waiting "show this request" (§4), or null. A page that subscribes after the emit still receives it. */
    public val requestFocus: StateFlow<RequestFocus?>
        get() = NoRequestFocus

    /** The page showed the request [seq] of [requestFocus]; it is not offered again. */
    public fun consumeRequestFocus(seq: Long): Unit = Unit
}

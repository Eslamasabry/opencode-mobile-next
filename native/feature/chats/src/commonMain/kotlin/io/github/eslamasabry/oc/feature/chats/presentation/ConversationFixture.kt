package io.github.eslamasabry.oc.feature.chats.presentation

import io.github.eslamasabry.oc.core.common.ids.ConversationRef
import io.github.eslamasabry.oc.core.common.ids.ProfileId
import io.github.eslamasabry.oc.core.common.ids.SessionId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * P2 fixture data (fake conversations, not copy): enough rows to scroll past the screen, so the emulator checks can
 * open a row near either end and push it off screen (P2-5). Replaced by the transcript store in slice 2.
 */
internal object ConversationFixture {
    private val profile = ProfileId("p2-fixture")

    private val titles =
        listOf(
            "Fix the reconnect loop", "Release notes for 3.0", "مراجعة واجهة الإعدادات", "Termux bootstrap",
            "Why is the build slow?", "Arabic plurals", "Predictive back test", "Voice input crash",
            "تحسين سرعة القائمة", "Widget refresh", "Pairing with the VPS", "Theme packs",
        )

    fun ref(index: Int): ConversationRef = ConversationRef(profile, SessionId("p2-$index"))

    val rows: ImmutableList<ConversationRowUi> =
        (0 until 36).map { i ->
            ConversationRowUi(ref(i), titles[i % titles.size] + " #" + (i + 1), "Last reply " + (i + 2) + " min ago")
        }.toImmutableList()

    fun preview(ref: ConversationRef): ChatPreviewUi {
        val row = rows.firstOrNull { it.ref == ref }
        return ChatPreviewUi(
            ref = ref,
            title = row?.title ?: ref.sessionId.raw,
            lines = persistentListOf(
                "You: " + (row?.title ?: ""),
                "Agent: Looking at the code now.",
                "Agent: Found it. The fix is in the connection controller.",
                "You: Ship it.",
            ),
        )
    }
}

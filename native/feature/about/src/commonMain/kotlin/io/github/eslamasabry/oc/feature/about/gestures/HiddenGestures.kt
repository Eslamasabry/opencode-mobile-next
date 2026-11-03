package io.github.eslamasabry.oc.feature.about.gestures

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * One gesture a person cannot see on screen, and the visible control that does the same (Flutter
 * docs/design/ui-ledger/gesture-audit.json, UX plan 5.8 item 2: no feature is reachable only by a gesture).
 *
 * @property evidence where the visible equivalent is, as `native/<file>.kt:<line>` — a place in the code, not a
 *   description of one. B-NAV-019's desktop test checks that the file exists and the line is inside it.
 * @property changed true when the visible path was added for the audit (it did not exist before).
 */
public data class HiddenGesture(
    val page: String,
    val element: String,
    val gesture: String,
    val action: String,
    val visibleEquivalent: String,
    val evidence: String,
    val changed: Boolean = false,
)

/** The native app's hidden gestures, page by page. Add a row with every new long-press, swipe, key or pinch. */
public object HiddenGestures {
    public val all: ImmutableList<HiddenGesture> =
        persistentListOf(
            HiddenGesture(
                page = "chats-all",
                element = "chats-all-row-long-press",
                gesture = "long-press on a conversation row",
                action = "open the row menu: Rename, Archive, Select, Delete",
                visibleEquivalent = "the top bar's Select action starts select mode, whose bottom bar has Rename, Archive and Delete",
                evidence = "native/feature/chats/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chats/ui/ChatsAllScreen.kt:111",
            ),
            HiddenGesture(
                page = "chat",
                element = "chat-prompt-long-press",
                gesture = "long-press on a sent prompt",
                action = "open the message actions",
                visibleEquivalent = "a plain tap on the prompt opens the same message actions (labelled 'Message actions')",
                evidence = "native/feature/chat/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chat/ui/ChatTranscript.kt:359",
            ),
            HiddenGesture(
                page = "composer",
                element = "composer-enter-send",
                gesture = "keyboard: Enter (Ctrl/Cmd+Enter on a phone keyboard)",
                action = "send the prompt",
                visibleEquivalent = "the visible Send button",
                evidence = "native/feature/chat-composer/src/commonMain/kotlin/io/github/eslamasabry/oc/feature/chatcomposer/ui/ComposerScreen.kt:206",
            ),
            HiddenGesture(
                page = "kit-tab-strip",
                element = "kit-tab-strip-arrows",
                gesture = "keyboard: Arrow Left / Arrow Right on the tabs",
                action = "select the next or previous tab",
                visibleEquivalent = "every tab is a visible, labelled tap target",
                evidence = "native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/nav/KitTabStrip.kt:204",
            ),
            HiddenGesture(
                page = "kit-search-field",
                element = "kit-search-field-escape",
                gesture = "keyboard: Esc in a search field",
                action = "clear the query",
                visibleEquivalent = "the field's visible Clear button",
                evidence = "native/ui/kit/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/input/KitSearchField.kt:211",
            ),
            HiddenGesture(
                page = "kit-zoom",
                element = "kit-zoom-pinch",
                gesture = "pinch, pan or double tap an image",
                action = "zoom the preview",
                visibleEquivalent = "the visible zoom controls (zoom in, zoom out, fit)",
                evidence = "native/ui/kit-content/src/commonMain/kotlin/io/github/eslamasabry/oc/ui/kit/content/image/KitZoomControls.kt:39",
            ),
        )

    /** `native/<path>.kt:<line>` and nothing else. */
    public val evidencePattern: Regex = Regex("""^native/[\w/.-]+\.kt:(\d+)$""")

    /** Rows missing a field, a well-formed evidence place, or listed twice. Empty when the ledger is sound. */
    public fun problems(rows: List<HiddenGesture> = all): List<String> = buildList {
        val seen = mutableSetOf<String>()
        for (row in rows) {
            val id = "${row.page} / ${row.element}"
            if (!seen.add(id)) add("$id: listed twice")
            listOf("page" to row.page, "element" to row.element, "gesture" to row.gesture, "action" to row.action,
                "visibleEquivalent" to row.visibleEquivalent, "evidence" to row.evidence)
                .filter { it.second.isBlank() }
                .forEach { add("$id: ${it.first}") }
            if (row.evidence.isNotBlank() && !evidencePattern.matches(row.evidence)) add("$id: \"${row.evidence}\" is not native/<file>.kt:<line>")
        }
    }
}

package io.github.eslamasabry.oc.feature.chats.dock

import kotlin.test.Test
import kotlin.test.assertEquals

class DockKitWordsTest {
    private val words =
        DockKitWordsImpl(
            dockLabel = "Dock",
            workingWord = "Working",
            workingTasksTemplate = "Working",
            thinkingWord = "Thinking",
            needsYouWord = "Needs you",
            finishedWord = "Finished",
            failedWord = "Failed",
            childNeedsYouTemplate = "x",
            childFailedTemplate = "x",
            subAgentsByCount = emptyMap(),
            subAgentsOther = "x",
            onServerTemplate = "x",
            allow = "Allow",
            deny = "Deny",
            stop = "Stop",
            rename = "Rename",
            remove = "Remove",
            open = "Open",
            pin = "Pin",
            unpin = "Unpin",
            restore = "Restore",
            newConversation = "New",
            nickname = "Nickname",
            pinnedLabel = "Pinned",
            hiddenByCount = emptyMap(),
            hiddenOther = "x",
            moveDock = "Move",
            showDock = "Show",
            actionOnTemplate = "x",
        )

    @Test
    fun `a quiet ring never says Idle`() {
        assertEquals("", words.idle())
    }
}

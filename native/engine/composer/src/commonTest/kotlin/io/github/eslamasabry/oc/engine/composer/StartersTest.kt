package io.github.eslamasabry.oc.engine.composer

import io.github.eslamasabry.oc.engine.composer.phrases.ChatStartFacts
import io.github.eslamasabry.oc.engine.composer.phrases.StartFact
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.AddTests
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.BuildWebPage
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.ExplainProject
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.FindBug
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.ListFolder
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.NodeProject
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.PythonScript
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.Readme
import io.github.eslamasabry.oc.engine.composer.phrases.StarterKind.WhatChanged
import io.github.eslamasabry.oc.engine.composer.phrases.Starters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StartersTest {
    @Test
    fun `B-CHAT-017 an empty folder gets ways to make something`() {
        assertEquals(
            listOf(BuildWebPage, PythonScript, NodeProject, Readme),
            Starters.suggestions(ChatStartFacts("/root/projects/my-app", entries = 0, git = true)),
        )
    }

    @Test
    fun `B-CHAT-017 a folder with files gets ways to work on them and what changed needs Git history`() {
        assertEquals(listOf(ExplainProject, FindBug, AddTests), Starters.suggestions(ChatStartFacts("/work/oc_app", entries = 12)))
        assertEquals(
            listOf(ExplainProject, WhatChanged, FindBug, AddTests),
            Starters.suggestions(ChatStartFacts("/work/oc_app", entries = 12, git = true, hasHistory = true)),
        )
        assertFalse(WhatChanged in Starters.suggestions(ChatStartFacts("/work/oc_app", entries = 12, git = true)))
    }

    @Test
    fun `B-CHAT-017 an unknown listing is a folder with files and nothing shows while looking`() {
        assertEquals(listOf(ExplainProject, FindBug, AddTests), Starters.suggestions(ChatStartFacts("/work/oc_app")))
        assertEquals(emptyList(), Starters.suggestions(ChatStartFacts.pending("/work/oc_app")))
        assertEquals(listOf(StartFact.Looking), Starters.factsLine(ChatStartFacts.pending("/work/oc_app")))
    }

    @Test
    fun `B-CHAT-017 no project and the projects root are not asked about`() {
        for (directory in listOf(null, "", "   ", "/root/projects")) {
            assertEquals(listOf(ListFolder, FindBug), Starters.suggestions(ChatStartFacts(directory, entries = 4)), directory)
        }
        assertEquals(BuildWebPage, Starters.suggestions(ChatStartFacts("/root/projects/FinanceHub3", entries = 0)).first())
    }

    @Test
    fun `B-CHAT-017 an unfinished sentence goes in with a space, not an ellipsis, Arabic too`() {
        assertEquals("Write a Python script that ", Starters.insertText("Write a Python script that…"))
        assertEquals("Add tests", Starters.insertText("Add tests"))
        assertEquals("اكتب سكربت بايثون ", Starters.insertText("اكتب سكربت بايثون…"))
    }

    @Test
    fun `B-CHAT-017 the facts line names items, Git and changes`() {
        assertEquals(listOf(StartFact.EmptyFolder, StartFact.Git), Starters.factsLine(ChatStartFacts("/a", entries = 0, git = true)))
        assertEquals(
            listOf(StartFact.Items(24), StartFact.Git, StartFact.Changes(3)),
            Starters.factsLine(ChatStartFacts("/a", entries = 24, git = true, changes = 3)),
        )
        assertEquals(emptyList(), Starters.factsLine(ChatStartFacts("/a")))
    }

    @Test
    fun `B-CHAT-018 trailing slashes and Windows separators still yield the name`() {
        assertEquals("oc_app", ChatStartFacts("/work/oc_app/").projectName)
        assertEquals("oc_app", ChatStartFacts("C:\\work\\oc_app").projectName)
        assertEquals("b", ChatStartFacts("C:\\a\\b\\").projectName)
    }
}

package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.AskView

/**
 * P-013: what a permission card shows besides its question, so it says each thing once, in plain words.
 */
public object RequestCardRules {
    /**
     * Lines of a change the card shows before "Show all" (review 2026-11-13 #1): enough to read what it writes, few
     * enough that the card rarely scrolls; Details holds the whole change.
     */
    public const val CARD_PREVIEW_LINES: Int = 4

    /** Verbs whose question already names the kind of action ("Edit game.js?", "Run npm test?"). */
    private val selfNaming =
        setOf(AskVerb.Run, AskVerb.Edit, AskVerb.Create, AskVerb.Delete, AskVerb.Read, AskVerb.ReadFolder, AskVerb.Fetch, AskVerb.WebSearch)

    /**
     * Asks whose question can say "this file" ("Edit this file?", "Read this file?"). A creation or a deletion always
     * names its file ("Create notes.txt?"): their unnamed words count files ("Create 1 file?").
     */
    private val fileVerbs = setOf(AskVerb.Edit, AskVerb.Read)

    /** The "Tool: …" line only where the question does not already say what kind of thing asks. */
    public fun showsToolLine(ask: AskView): Boolean = ask.verb !in selfNaming

    /**
     * What the Details sheet's box shows for [ask], given the request's own full text: a folder read or a search shows
     * what the card names (the project or folder, the pattern), never the server's "." or the folder it searches. Null
     * when there is nothing to show there.
     */
    public fun detailsText(ask: AskView, fullText: String?): String? =
        when (ask.verb) {
            AskVerb.ReadFolder, AskVerb.Search -> ask.targets.firstOrNull()?.text
            else -> fullText
        }

    /** Whether a box with nothing of its own says "(all matching requests)": not for the asks [detailsText] names. */
    public fun detailsSaysAllMatching(ask: AskView): Boolean = ask.verb != AskVerb.ReadFolder && ask.verb != AskVerb.Search

    /** "Copy what it asks to run" belongs to commands only, never to a file change. */
    public fun copiesTargets(ask: AskView): Boolean = ask.verb == AskVerb.Run || ask.verb == AskVerb.OutsideRun

    /**
     * A file ask whose one target is the file the question names: the question drops the name ("Edit this file?")
     * and the target line under it shows the path once, with its change size.
     */
    public fun questionOmitsName(ask: AskView): Boolean {
        if (ask.verb !in fileVerbs) return false
        val name = ask.name?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val target = ask.targets.singleOrNull()?.text?.trim() ?: return false
        return target == name || target.endsWith("/$name") || target.endsWith("\\$name")
    }
}

package io.github.eslamasabry.oc.feature.chat.requests.presentation

import io.github.eslamasabry.oc.engine.requests.ask.AskVerb
import io.github.eslamasabry.oc.engine.requests.ask.AskView

/**
 * P-013: what a permission card shows besides its question, so it says each thing once, in plain words.
 */
public object RequestCardRules {
    /** Verbs whose question already names the kind of action ("Edit game.js?", "Run npm test?"). */
    private val selfNaming =
        setOf(AskVerb.Run, AskVerb.Edit, AskVerb.Create, AskVerb.Delete, AskVerb.Read, AskVerb.Fetch, AskVerb.WebSearch)

    private val fileVerbs = setOf(AskVerb.Edit, AskVerb.Create, AskVerb.Delete, AskVerb.Read)

    /** The "Tool: …" line only where the question does not already say what kind of thing asks. */
    public fun showsToolLine(ask: AskView): Boolean = ask.verb !in selfNaming

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

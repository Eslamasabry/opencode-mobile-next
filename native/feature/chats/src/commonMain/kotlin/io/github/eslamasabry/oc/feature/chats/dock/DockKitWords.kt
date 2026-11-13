package io.github.eslamasabry.oc.feature.chats.dock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import io.github.eslamasabry.oc.core.common.text.Numbers
import io.github.eslamasabry.oc.ui.kit.dock.KitDockModel
import io.github.eslamasabry.oc.ui.kit.dock.KitDockWords
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitRequestAllowOnce
import io.github.eslamasabry.oc.ui.words.res.approvalDeny
import io.github.eslamasabry.oc.ui.words.res.chatUiRename
import io.github.eslamasabry.oc.ui.words.res.chatUiRenameSession
import io.github.eslamasabry.oc.ui.words.res.chatsHomeNewChat
import io.github.eslamasabry.oc.ui.words.res.dockActionOn
import io.github.eslamasabry.oc.ui.words.res.dockChildFailed
import io.github.eslamasabry.oc.ui.words.res.dockChildNeedsYou
import io.github.eslamasabry.oc.ui.words.res.dockFailed
import io.github.eslamasabry.oc.ui.words.res.dockFinished
import io.github.eslamasabry.oc.ui.words.res.dockHidden
import io.github.eslamasabry.oc.ui.words.res.dockLabel
import io.github.eslamasabry.oc.ui.words.res.dockMoveDock
import io.github.eslamasabry.oc.ui.words.res.dockNeedsYou
import io.github.eslamasabry.oc.ui.words.res.dockOnServer
import io.github.eslamasabry.oc.ui.words.res.dockOpen
import io.github.eslamasabry.oc.ui.words.res.dockPin
import io.github.eslamasabry.oc.ui.words.res.dockRemove
import io.github.eslamasabry.oc.ui.words.res.dockRestore
import io.github.eslamasabry.oc.ui.words.res.dockShowDock
import io.github.eslamasabry.oc.ui.words.res.dockStop
import io.github.eslamasabry.oc.ui.words.res.dockSubagents
import io.github.eslamasabry.oc.ui.words.res.dockThinking
import io.github.eslamasabry.oc.ui.words.res.dockUnpin
import io.github.eslamasabry.oc.ui.words.res.dockWorking
import io.github.eslamasabry.oc.ui.words.res.dockWorkingTasks
import io.github.eslamasabry.oc.ui.words.res.kitPinned
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The marks a template carries where a value goes (the kit asks for its sentences outside composition). */
private const val A = "\u0000"
private const val B = "\u0001"

/**
 * The dock's words, read from string resources in composition and filled in later by the kit. Plurals are read for the
 * counts this frame can ask about (each ring's sub-agents, the hidden list); a count outside them uses [subAgentsOther]
 * / [hiddenOther] (only while a new frame is on its way).
 */
@Stable
internal class DockKitWordsImpl(
    override val dockLabel: String,
    private val workingWord: String,
    private val workingTasksTemplate: String,
    private val thinkingWord: String,
    private val needsYouWord: String,
    private val finishedWord: String,
    private val failedWord: String,
    private val childNeedsYouTemplate: String,
    private val childFailedTemplate: String,
    private val subAgentsByCount: Map<Int, String>,
    private val subAgentsOther: String,
    private val onServerTemplate: String,
    override val allow: String,
    override val deny: String,
    override val stop: String,
    override val rename: String,
    override val remove: String,
    override val open: String,
    override val pin: String,
    override val unpin: String,
    override val restore: String,
    override val newConversation: String,
    override val nickname: String,
    override val pinnedLabel: String,
    private val hiddenByCount: Map<Int, String>,
    private val hiddenOther: String,
    override val moveDock: String,
    override val showDock: String,
    private val actionOnTemplate: String,
) : KitDockWords {
    override fun working(): String = workingWord

    override fun workingTasks(done: Int, total: Int): String =
        workingTasksTemplate.replace(A, Numbers.format(done)).replace(B, Numbers.format(total))

    override fun thinking(): String = thinkingWord

    override fun needsYou(): String = needsYouWord

    override fun finished(): String = finishedWord

    override fun failed(): String = failedWord

    /**
     * A quiet ring says nothing: "Idle" is engine talk, and the kit's model carries no "last active" time to show
     * instead (KitDockRing has none, idle() takes no argument), so the state word is empty.
     */
    override fun idle(): String = ""

    override fun childNeedsYou(child: String): String = childNeedsYouTemplate.replace(A, child)

    override fun childFailed(child: String): String = childFailedTemplate.replace(A, child)

    override fun subAgents(count: Int): String =
        (subAgentsByCount[count] ?: subAgentsOther).replace(A, Numbers.format(count))

    override fun onServer(server: String): String = onServerTemplate.replace(A, server)

    override fun hidden(count: Int): String = (hiddenByCount[count] ?: hiddenOther).replace(A, Numbers.format(count))

    override fun actionOn(action: String, target: String): String =
        actionOnTemplate.replace(A, action).replace(B, target)
}

/** The counts whose plural forms a frame may ask for: each ring's sub-agents and the hidden list. */
internal fun dockCounts(model: KitDockModel): List<Int> =
    (model.rings.map { it.children.size + it.childOverflow } + model.hidden.size)
        .filter { it > 0 }
        .distinct()
        .sorted()

@Composable
internal fun rememberDockKitWords(model: KitDockModel): KitDockWords {
    val counts = dockCounts(model)
    val subAgents = HashMap<Int, String>(counts.size)
    val hidden = HashMap<Int, String>(counts.size)
    for (n in counts) {
        subAgents[n] = pluralStringResource(Res.plurals.dockSubagents, n, A)
        hidden[n] = pluralStringResource(Res.plurals.dockHidden, n, A)
    }
    val subAgentsOther = pluralStringResource(Res.plurals.dockSubagents, 100, A)
    val hiddenOther = pluralStringResource(Res.plurals.dockHidden, 100, A)
    val label = stringResource(Res.string.dockLabel)
    val working = stringResource(Res.string.dockWorking)
    val workingTasks = stringResource(Res.string.dockWorkingTasks, A, B)
    val thinking = stringResource(Res.string.dockThinking)
    val needsYou = stringResource(Res.string.dockNeedsYou)
    val finished = stringResource(Res.string.dockFinished)
    val failed = stringResource(Res.string.dockFailed)
    val childNeedsYou = stringResource(Res.string.dockChildNeedsYou, A)
    val childFailed = stringResource(Res.string.dockChildFailed, A)
    val onServer = stringResource(Res.string.dockOnServer, A)
    val allow = stringResource(Res.string.kitRequestAllowOnce)
    val deny = stringResource(Res.string.approvalDeny)
    val stop = stringResource(Res.string.dockStop)
    val rename = stringResource(Res.string.chatUiRename)
    val remove = stringResource(Res.string.dockRemove)
    val open = stringResource(Res.string.dockOpen)
    val pin = stringResource(Res.string.dockPin)
    val unpin = stringResource(Res.string.dockUnpin)
    val restore = stringResource(Res.string.dockRestore)
    val newConversation = stringResource(Res.string.chatsHomeNewChat)
    // The rename field renames the conversation itself (one name everywhere), so it is named for that.
    val nickname = stringResource(Res.string.chatUiRenameSession)
    val pinned = stringResource(Res.string.kitPinned)
    val moveDock = stringResource(Res.string.dockMoveDock)
    val showDock = stringResource(Res.string.dockShowDock)
    val actionOn = stringResource(Res.string.dockActionOn, A, B)
    return remember(
        subAgents, hidden, subAgentsOther, hiddenOther, label, working, workingTasks, thinking, needsYou, finished,
        failed, childNeedsYou, childFailed, onServer, allow, deny, stop, rename, remove, open, pin, unpin, restore,
        newConversation, nickname, pinned, moveDock, showDock, actionOn,
    ) {
        DockKitWordsImpl(
            dockLabel = label,
            workingWord = working,
            workingTasksTemplate = workingTasks,
            thinkingWord = thinking,
            needsYouWord = needsYou,
            finishedWord = finished,
            failedWord = failed,
            childNeedsYouTemplate = childNeedsYou,
            childFailedTemplate = childFailed,
            subAgentsByCount = subAgents,
            subAgentsOther = subAgentsOther,
            onServerTemplate = onServer,
            allow = allow,
            deny = deny,
            stop = stop,
            rename = rename,
            remove = remove,
            open = open,
            pin = pin,
            unpin = unpin,
            restore = restore,
            newConversation = newConversation,
            nickname = nickname,
            pinnedLabel = pinned,
            hiddenByCount = hidden,
            hiddenOther = hiddenOther,
            moveDock = moveDock,
            showDock = showDock,
            actionOnTemplate = actionOn,
        )
    }
}

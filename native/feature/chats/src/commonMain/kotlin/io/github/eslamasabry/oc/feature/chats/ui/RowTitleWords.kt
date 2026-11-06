package io.github.eslamasabry.oc.feature.chats.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.chats.presentation.RowTitle
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.commandUntitledChat
import io.github.eslamasabry.oc.ui.words.res.workspaceNewSession
import org.jetbrains.compose.resources.stringResource

/**
 * N-54: one wording for a conversation's title on every list, the same as the chat's own header: a conversation
 * with no title is "Untitled conversation"; only the server's dated placeholder reads "New conversation".
 */
@Composable
internal fun rowTitleWords(title: RowTitle): String =
    when (title) {
        is RowTitle.Text -> title.text
        RowTitle.New -> stringResource(Res.string.workspaceNewSession)
        RowTitle.Untitled -> stringResource(Res.string.commandUntitledChat)
    }

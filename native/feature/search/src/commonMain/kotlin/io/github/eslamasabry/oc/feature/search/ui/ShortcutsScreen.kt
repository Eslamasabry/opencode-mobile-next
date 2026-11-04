package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.search.presentation.ShortcutHelp
import io.github.eslamasabry.oc.feature.search.presentation.ShortcutsActions
import io.github.eslamasabry.oc.feature.search.presentation.ShortcutsUi
import io.github.eslamasabry.oc.ui.kit.KitText
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.kitTopBarBack
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceShortcuts
import io.github.eslamasabry.oc.ui.words.res.nativeSearchShortcutHistory
import io.github.eslamasabry.oc.ui.words.res.nativeSearchShortcutNewline
import io.github.eslamasabry.oc.ui.words.res.nativeSearchShortcutRefresh
import io.github.eslamasabry.oc.ui.words.res.nativeSearchShortcutSend
import io.github.eslamasabry.oc.ui.words.res.nativeSearchShortcutTabs
import io.github.eslamasabry.oc.ui.words.res.nativeSearchShortcutsBody
import org.jetbrains.compose.resources.stringResource

/** Only the shortcuts handled by the current composer/kit are described here. */
@Composable
public fun ShortcutsScreen(state: ShortcutsUi, actions: ShortcutsActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.nativeSearchPlaceShortcuts),
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn {
            item { KitText(stringResource(Res.string.nativeSearchShortcutsBody)) }
            items(state.shortcuts) { shortcut ->
                KitRow(
                    title =
                        stringResource(
                            when (shortcut) {
                                ShortcutHelp.Send -> Res.string.nativeSearchShortcutSend
                                ShortcutHelp.Newline -> Res.string.nativeSearchShortcutNewline
                                ShortcutHelp.History -> Res.string.nativeSearchShortcutHistory
                                ShortcutHelp.Refresh -> Res.string.nativeSearchShortcutRefresh
                                ShortcutHelp.Tabs -> Res.string.nativeSearchShortcutTabs
                            }
                        )
                )
            }
        }
    }
}

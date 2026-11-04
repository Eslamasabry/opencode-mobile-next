package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.core.model.VersionControlSetupState
import io.github.eslamasabry.oc.feature.search.presentation.ProjectHealthActions
import io.github.eslamasabry.oc.feature.search.presentation.ProjectHealthUi
import io.github.eslamasabry.oc.ui.kit.KitButton
import io.github.eslamasabry.oc.ui.kit.frame.KitScreen
import io.github.eslamasabry.oc.ui.kit.frame.KitScreenWidth
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBar
import io.github.eslamasabry.oc.ui.kit.frame.KitTopBarExit
import io.github.eslamasabry.oc.ui.kit.row.KitRow
import io.github.eslamasabry.oc.ui.kit.row.KitRowTrailing
import io.github.eslamasabry.oc.ui.kit.row.KitSectionLabel
import io.github.eslamasabry.oc.ui.kit.state.KitNotice
import io.github.eslamasabry.oc.ui.words.res.*
import org.jetbrains.compose.resources.stringResource

@Composable
public fun ProjectHealthScreen(state: ProjectHealthUi, actions: ProjectHealthActions) {
    KitScreen(
        topBar = {
            KitTopBar(
                title = stringResource(Res.string.nativeSearchPlaceProjectHealth),
                subtitle = state.directory,
                exit = KitTopBarExit.Back,
                exitLabel = stringResource(Res.string.kitTopBarBack),
                onExit = actions::back,
            )
        },
        loading = state.loading,
        loadingLabel = stringResource(Res.string.nativeSearchHealthLoading),
        width = KitScreenWidth.Reading,
    ) {
        LazyColumn {
            item {
                KitButton(
                    stringResource(Res.string.nativeSearchHealthRefresh),
                    actions::refresh,
                    working = state.loading,
                )
            }
            if (state.failure != null)
                item { KitNotice(stringResource(Res.string.nativeSearchHealthFailed)) }
            state.snapshot?.let { snapshot ->
                snapshot.vcs?.let { vcs ->
                    item {
                        KitRow(
                            title = stringResource(Res.string.nativeSearchHealthBranch),
                            trailing =
                                KitRowTrailing.Value(
                                    vcs.branch
                                        ?: stringResource(
                                            if (vcs.setupState == VersionControlSetupState.Absent)
                                                Res.string.nativeSearchHealthNoGit
                                            else Res.string.nativeSearchHealthUnknown
                                        )
                                ),
                        )
                    }
                }
                if (snapshot.languagesAvailable) {
                    item { KitSectionLabel(stringResource(Res.string.nativeSearchHealthLanguages)) }
                    if (snapshot.languages.isEmpty())
                        item { KitNotice(stringResource(Res.string.nativeSearchHealthNoLanguages)) }
                    for (language in snapshot.languages) item(key = "language-" + language.id) {
                        KitRow(
                            title = language.name,
                            supporting = language.root,
                            trailing =
                                KitRowTrailing.Value(
                                    stringResource(
                                        when (language.status.lowercase()) {
                                            "connected",
                                            "running",
                                            "ready" -> Res.string.nativeSearchHealthReady
                                            "error",
                                            "failed" -> Res.string.nativeSearchHealthError
                                            else -> Res.string.nativeSearchHealthUnknown
                                        }
                                    )
                                ),
                        )
                    }
                }
                if (snapshot.formattersAvailable) {
                    item {
                        KitSectionLabel(stringResource(Res.string.nativeSearchHealthFormatters))
                    }
                    if (snapshot.formatters.isEmpty())
                        item {
                            KitNotice(stringResource(Res.string.nativeSearchHealthNoFormatters))
                        }
                    for ((index, formatter) in snapshot.formatters.withIndex()) item(
                        key = "formatter-" + index
                    ) {
                        KitRow(
                            title = formatter.name,
                            supporting = formatter.extensions.joinToString(", "),
                            trailing =
                                KitRowTrailing.Value(
                                    stringResource(
                                        if (formatter.enabled) Res.string.nativeSearchHealthEnabled
                                        else Res.string.nativeSearchHealthDisabled
                                    )
                                ),
                        )
                    }
                }
            }
        }
    }
}

package io.github.eslamasabry.oc.feature.search.ui

import androidx.compose.runtime.Composable
import io.github.eslamasabry.oc.feature.search.presentation.SearchPlace
import io.github.eslamasabry.oc.ui.words.res.Res
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesAgents
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesAiSetup
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesAllConversations
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesAppearance
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesBackgroundChecks
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesChats
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesCommands
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesIntegrations
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesLanguage
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesModels
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesNotifications
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesPhone
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesPhoneStorage
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProjectChanges
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProjectFiles
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProjectHealth
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProjectSearch
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProjectTerminal
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProjectWorktrees
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProjects
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesProviders
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesReferences
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesRunning
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesServices
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesSettings
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesShortcuts
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesSkills
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesTips
import io.github.eslamasabry.oc.ui.words.res.nativeSearchAliasesTools
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceAgents
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceAiSetup
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceAllConversations
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceAppearance
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceBackgroundChecks
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceChats
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceCommands
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceIntegrations
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceLanguage
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceModels
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceNotifications
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlacePhone
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlacePhoneStorage
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProjectChanges
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProjectFiles
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProjectHealth
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProjectSearch
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProjectTerminal
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProjectWorktrees
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProjects
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceProviders
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceReferences
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceRunning
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceServices
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceSettings
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceShortcuts
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceSkills
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceTips
import io.github.eslamasabry.oc.ui.words.res.nativeSearchPlaceTools
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun searchPlaceWords(place: SearchPlace): Pair<String, String> =
    when (place) {
        SearchPlace.Projects ->
            stringResource(Res.string.nativeSearchPlaceProjects) to
                stringResource(Res.string.nativeSearchAliasesProjects)
        SearchPlace.Chats ->
            stringResource(Res.string.nativeSearchPlaceChats) to
                stringResource(Res.string.nativeSearchAliasesChats)
        SearchPlace.ProjectFiles ->
            stringResource(Res.string.nativeSearchPlaceProjectFiles) to
                stringResource(Res.string.nativeSearchAliasesProjectFiles)
        SearchPlace.ProjectChanges ->
            stringResource(Res.string.nativeSearchPlaceProjectChanges) to
                stringResource(Res.string.nativeSearchAliasesProjectChanges)
        SearchPlace.ProjectTerminal ->
            stringResource(Res.string.nativeSearchPlaceProjectTerminal) to
                stringResource(Res.string.nativeSearchAliasesProjectTerminal)
        SearchPlace.ProjectHealth ->
            stringResource(Res.string.nativeSearchPlaceProjectHealth) to
                stringResource(Res.string.nativeSearchAliasesProjectHealth)
        SearchPlace.ProjectWorktrees ->
            stringResource(Res.string.nativeSearchPlaceProjectWorktrees) to
                stringResource(Res.string.nativeSearchAliasesProjectWorktrees)
        SearchPlace.ProjectSearch ->
            stringResource(Res.string.nativeSearchPlaceProjectSearch) to
                stringResource(Res.string.nativeSearchAliasesProjectSearch)
        SearchPlace.AllConversations ->
            stringResource(Res.string.nativeSearchPlaceAllConversations) to
                stringResource(Res.string.nativeSearchAliasesAllConversations)
        SearchPlace.Settings ->
            stringResource(Res.string.nativeSearchPlaceSettings) to
                stringResource(Res.string.nativeSearchAliasesSettings)
        SearchPlace.Models ->
            stringResource(Res.string.nativeSearchPlaceModels) to
                stringResource(Res.string.nativeSearchAliasesModels)
        SearchPlace.Providers ->
            stringResource(Res.string.nativeSearchPlaceProviders) to
                stringResource(Res.string.nativeSearchAliasesProviders)
        SearchPlace.Integrations ->
            stringResource(Res.string.nativeSearchPlaceIntegrations) to
                stringResource(Res.string.nativeSearchAliasesIntegrations)
        SearchPlace.Commands ->
            stringResource(Res.string.nativeSearchPlaceCommands) to
                stringResource(Res.string.nativeSearchAliasesCommands)
        SearchPlace.Tools ->
            stringResource(Res.string.nativeSearchPlaceTools) to
                stringResource(Res.string.nativeSearchAliasesTools)
        SearchPlace.Skills ->
            stringResource(Res.string.nativeSearchPlaceSkills) to
                stringResource(Res.string.nativeSearchAliasesSkills)
        SearchPlace.References ->
            stringResource(Res.string.nativeSearchPlaceReferences) to
                stringResource(Res.string.nativeSearchAliasesReferences)
        SearchPlace.AiSetup ->
            stringResource(Res.string.nativeSearchPlaceAiSetup) to
                stringResource(Res.string.nativeSearchAliasesAiSetup)
        SearchPlace.Agents ->
            stringResource(Res.string.nativeSearchPlaceAgents) to
                stringResource(Res.string.nativeSearchAliasesAgents)
        SearchPlace.Phone ->
            stringResource(Res.string.nativeSearchPlacePhone) to
                stringResource(Res.string.nativeSearchAliasesPhone)
        SearchPlace.Running ->
            stringResource(Res.string.nativeSearchPlaceRunning) to
                stringResource(Res.string.nativeSearchAliasesRunning)
        SearchPlace.Services ->
            stringResource(Res.string.nativeSearchPlaceServices) to
                stringResource(Res.string.nativeSearchAliasesServices)
        SearchPlace.PhoneStorage ->
            stringResource(Res.string.nativeSearchPlacePhoneStorage) to
                stringResource(Res.string.nativeSearchAliasesPhoneStorage)
        SearchPlace.Notifications ->
            stringResource(Res.string.nativeSearchPlaceNotifications) to
                stringResource(Res.string.nativeSearchAliasesNotifications)
        SearchPlace.BackgroundChecks ->
            stringResource(Res.string.nativeSearchPlaceBackgroundChecks) to
                stringResource(Res.string.nativeSearchAliasesBackgroundChecks)
        SearchPlace.Appearance ->
            stringResource(Res.string.nativeSearchPlaceAppearance) to
                stringResource(Res.string.nativeSearchAliasesAppearance)
        SearchPlace.Language ->
            stringResource(Res.string.nativeSearchPlaceLanguage) to
                stringResource(Res.string.nativeSearchAliasesLanguage)
        SearchPlace.Tips ->
            stringResource(Res.string.nativeSearchPlaceTips) to
                stringResource(Res.string.nativeSearchAliasesTips)
        SearchPlace.Shortcuts ->
            stringResource(Res.string.nativeSearchPlaceShortcuts) to
                stringResource(Res.string.nativeSearchAliasesShortcuts)
    }

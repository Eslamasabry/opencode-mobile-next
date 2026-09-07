import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_en.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
    : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations)!;
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
        delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[Locale('en')];

  /// Application title shown in the task switcher / window title
  ///
  /// In en, this message translates to:
  /// **'OpenCode Mobile'**
  String get appTitle;

  /// Section label above the More hub's browse destination grid
  ///
  /// In en, this message translates to:
  /// **'Browse'**
  String get libraryBrowseSection;

  /// Section label above the More hub's manage destination grid
  ///
  /// In en, this message translates to:
  /// **'Manage'**
  String get libraryManageSection;

  /// More hub card: model and agent catalog
  ///
  /// In en, this message translates to:
  /// **'Models & agents'**
  String get libraryModelsAgentsTitle;

  /// More hub card: provider integrations
  ///
  /// In en, this message translates to:
  /// **'Providers'**
  String get libraryProvidersTitle;

  /// More hub card: Model Context Protocol server integrations
  ///
  /// In en, this message translates to:
  /// **'MCP'**
  String get libraryMcpTitle;

  /// More hub card: commands, skills, and native tools
  ///
  /// In en, this message translates to:
  /// **'Commands & tools'**
  String get libraryCommandsToolsTitle;

  /// More hub card: server terminal sessions
  ///
  /// In en, this message translates to:
  /// **'Terminal'**
  String get libraryTerminalTitle;

  /// More hub card: app settings
  ///
  /// In en, this message translates to:
  /// **'Settings'**
  String get librarySettingsTitle;

  /// Installed app version and build shown in About
  ///
  /// In en, this message translates to:
  /// **'OpenCode Mobile {version}+{buildNumber}'**
  String aboutBuildVersion(String version, String buildNumber);

  /// Label for the installed Android APK signer fingerprint
  ///
  /// In en, this message translates to:
  /// **'Signing certificate SHA-256'**
  String get aboutSigningCertificate;

  /// Tooltip for the chat model cycling menu
  ///
  /// In en, this message translates to:
  /// **'Switch model for this session'**
  String get modelSwitchSession;

  /// Cycle forward through recent models
  ///
  /// In en, this message translates to:
  /// **'Next recent model · F2'**
  String get modelNextRecent;

  /// Cycle backward through recent models
  ///
  /// In en, this message translates to:
  /// **'Previous recent model · Shift+F2'**
  String get modelPreviousRecent;

  /// Cycle through the server profile's favorite models
  ///
  /// In en, this message translates to:
  /// **'Next favorite model'**
  String get modelNextFavorite;

  /// No description provided for @modelChooseTitle.
  ///
  /// In en, this message translates to:
  /// **'Choose a model'**
  String get modelChooseTitle;

  /// No description provided for @modelTitleCompact.
  ///
  /// In en, this message translates to:
  /// **'Models'**
  String get modelTitleCompact;

  /// No description provided for @modelSearchHint.
  ///
  /// In en, this message translates to:
  /// **'Search models'**
  String get modelSearchHint;

  /// No description provided for @modelAll.
  ///
  /// In en, this message translates to:
  /// **'All models'**
  String get modelAll;

  /// No description provided for @modelFavorites.
  ///
  /// In en, this message translates to:
  /// **'Favorites'**
  String get modelFavorites;

  /// No description provided for @modelRecent.
  ///
  /// In en, this message translates to:
  /// **'Recent'**
  String get modelRecent;

  /// No description provided for @modelOptions.
  ///
  /// In en, this message translates to:
  /// **'Options'**
  String get modelOptions;

  /// No description provided for @modelThinkingMode.
  ///
  /// In en, this message translates to:
  /// **'Thinking mode'**
  String get modelThinkingMode;

  /// No description provided for @modelDefaultMode.
  ///
  /// In en, this message translates to:
  /// **'Default mode'**
  String get modelDefaultMode;

  /// No description provided for @modelSessionScopeNote.
  ///
  /// In en, this message translates to:
  /// **'Applies to this session\'s next turns.'**
  String get modelSessionScopeNote;

  /// No description provided for @modelSelectionLoading.
  ///
  /// In en, this message translates to:
  /// **'Loading session selection…'**
  String get modelSelectionLoading;

  /// No description provided for @modelServerDefault.
  ///
  /// In en, this message translates to:
  /// **'Server default'**
  String get modelServerDefault;

  /// No description provided for @modelSelectionSaving.
  ///
  /// In en, this message translates to:
  /// **'Saving session selection…'**
  String get modelSelectionSaving;

  /// No description provided for @modelAgentSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save the agent. Try again.'**
  String get modelAgentSaveFailed;

  /// No description provided for @modelUnavailableSelection.
  ///
  /// In en, this message translates to:
  /// **'The session\'s model is unavailable in this catalog. Refresh models or choose another.'**
  String get modelUnavailableSelection;

  /// No description provided for @modelScopeChanged.
  ///
  /// In en, this message translates to:
  /// **'The connection changed. Reopen the model selector to continue.'**
  String get modelScopeChanged;

  /// No description provided for @commonClearSearch.
  ///
  /// In en, this message translates to:
  /// **'Clear search'**
  String get commonClearSearch;

  /// No description provided for @commonUndo.
  ///
  /// In en, this message translates to:
  /// **'Undo'**
  String get commonUndo;

  /// No description provided for @workTitle.
  ///
  /// In en, this message translates to:
  /// **'Running work'**
  String get workTitle;

  /// No description provided for @workDescription.
  ///
  /// In en, this message translates to:
  /// **'Agents and commands related to this chat.'**
  String get workDescription;

  /// No description provided for @workAgents.
  ///
  /// In en, this message translates to:
  /// **'Agents'**
  String get workAgents;

  /// No description provided for @workCommands.
  ///
  /// In en, this message translates to:
  /// **'Commands'**
  String get workCommands;

  /// No description provided for @workEmpty.
  ///
  /// In en, this message translates to:
  /// **'Nothing running'**
  String get workEmpty;

  /// No description provided for @workEmptyDescription.
  ///
  /// In en, this message translates to:
  /// **'Finished work stays in the conversation.'**
  String get workEmptyDescription;

  /// No description provided for @workRefresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get workRefresh;

  /// No description provided for @workClose.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get workClose;

  /// No description provided for @workRetry.
  ///
  /// In en, this message translates to:
  /// **'Try again'**
  String get workRetry;

  /// No description provided for @workCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get workCancel;

  /// No description provided for @workRunning.
  ///
  /// In en, this message translates to:
  /// **'Running'**
  String get workRunning;

  /// No description provided for @workFinished.
  ///
  /// In en, this message translates to:
  /// **'Finished'**
  String get workFinished;

  /// No description provided for @workTimedOut.
  ///
  /// In en, this message translates to:
  /// **'Timed out'**
  String get workTimedOut;

  /// No description provided for @workStopped.
  ///
  /// In en, this message translates to:
  /// **'Stopped'**
  String get workStopped;

  /// No description provided for @workUnknown.
  ///
  /// In en, this message translates to:
  /// **'Status unavailable'**
  String get workUnknown;

  /// No description provided for @workOutput.
  ///
  /// In en, this message translates to:
  /// **'Command output'**
  String get workOutput;

  /// No description provided for @workViewOutput.
  ///
  /// In en, this message translates to:
  /// **'View output'**
  String get workViewOutput;

  /// No description provided for @workNoOutput.
  ///
  /// In en, this message translates to:
  /// **'Waiting for output…'**
  String get workNoOutput;

  /// No description provided for @workNoFinalOutput.
  ///
  /// In en, this message translates to:
  /// **'This command produced no output.'**
  String get workNoFinalOutput;

  /// No description provided for @workCopyOutput.
  ///
  /// In en, this message translates to:
  /// **'Copy output'**
  String get workCopyOutput;

  /// No description provided for @workCopied.
  ///
  /// In en, this message translates to:
  /// **'Output copied'**
  String get workCopied;

  /// No description provided for @workFollow.
  ///
  /// In en, this message translates to:
  /// **'Follow output'**
  String get workFollow;

  /// No description provided for @workMoreOutput.
  ///
  /// In en, this message translates to:
  /// **'Load more output'**
  String get workMoreOutput;

  /// No description provided for @workTrimmed.
  ///
  /// In en, this message translates to:
  /// **'Showing the most recent output. Earlier text was trimmed.'**
  String get workTrimmed;

  /// No description provided for @workStop.
  ///
  /// In en, this message translates to:
  /// **'Stop command'**
  String get workStop;

  /// No description provided for @workStopTitle.
  ///
  /// In en, this message translates to:
  /// **'Stop this command?'**
  String get workStopTitle;

  /// No description provided for @workStopDescription.
  ///
  /// In en, this message translates to:
  /// **'This stops the command and removes its saved output from the server. Text already loaded here stays visible until you close it.'**
  String get workStopDescription;

  /// No description provided for @workTimeout.
  ///
  /// In en, this message translates to:
  /// **'Change timeout'**
  String get workTimeout;

  /// No description provided for @workTimeoutTitle.
  ///
  /// In en, this message translates to:
  /// **'Time remaining'**
  String get workTimeoutTitle;

  /// No description provided for @workTimeoutDescription.
  ///
  /// In en, this message translates to:
  /// **'The new timeout starts now.'**
  String get workTimeoutDescription;

  /// No description provided for @workTimeoutOneMinute.
  ///
  /// In en, this message translates to:
  /// **'1 minute'**
  String get workTimeoutOneMinute;

  /// No description provided for @workTimeoutFiveMinutes.
  ///
  /// In en, this message translates to:
  /// **'5 minutes'**
  String get workTimeoutFiveMinutes;

  /// No description provided for @workTimeoutFifteenMinutes.
  ///
  /// In en, this message translates to:
  /// **'15 minutes'**
  String get workTimeoutFifteenMinutes;

  /// No description provided for @workTimeoutOneHour.
  ///
  /// In en, this message translates to:
  /// **'1 hour'**
  String get workTimeoutOneHour;

  /// No description provided for @workTimeoutNone.
  ///
  /// In en, this message translates to:
  /// **'No timeout'**
  String get workTimeoutNone;

  /// No description provided for @workTimeoutSaved.
  ///
  /// In en, this message translates to:
  /// **'Timeout updated'**
  String get workTimeoutSaved;

  /// No description provided for @workUnavailable.
  ///
  /// In en, this message translates to:
  /// **'This command is no longer available. It may have been removed or cancelled when the server restarted.'**
  String get workUnavailable;

  /// No description provided for @workRestarted.
  ///
  /// In en, this message translates to:
  /// **'The server restarted and this command is no longer available. Its loaded output is shown below.'**
  String get workRestarted;

  /// No description provided for @workDisconnected.
  ///
  /// In en, this message translates to:
  /// **'Reconnecting. Output will refresh when the server is available.'**
  String get workDisconnected;

  /// No description provided for @workContextChanged.
  ///
  /// In en, this message translates to:
  /// **'The server or workspace changed. Close this view and reopen Running work.'**
  String get workContextChanged;

  /// No description provided for @workCount.
  ///
  /// In en, this message translates to:
  /// **'Running work · {count}'**
  String workCount(int count);

  /// No description provided for @workExitCode.
  ///
  /// In en, this message translates to:
  /// **'Exit code {code}'**
  String workExitCode(int code);

  /// No description provided for @workStatusElapsed.
  ///
  /// In en, this message translates to:
  /// **'{status} · {elapsed}'**
  String workStatusElapsed(String status, String elapsed);

  /// No description provided for @composerClearTextTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear draft text'**
  String get composerClearTextTitle;

  /// No description provided for @composerClearTextSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Keeps attachments · Undo available'**
  String get composerClearTextSubtitle;

  /// No description provided for @composerDraftCleared.
  ///
  /// In en, this message translates to:
  /// **'Draft text cleared'**
  String get composerDraftCleared;

  /// No description provided for @composerReuseTitle.
  ///
  /// In en, this message translates to:
  /// **'Reuse a prompt'**
  String get composerReuseTitle;

  /// No description provided for @queueSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save the queued draft on this device. Your text is still here. Check available storage and try again.'**
  String get queueSaveFailed;

  /// No description provided for @fileCopy.
  ///
  /// In en, this message translates to:
  /// **'Copy'**
  String get fileCopy;

  /// No description provided for @fileReference.
  ///
  /// In en, this message translates to:
  /// **'Reference'**
  String get fileReference;

  /// No description provided for @fileAttach.
  ///
  /// In en, this message translates to:
  /// **'Attach'**
  String get fileAttach;

  /// No description provided for @fileSave.
  ///
  /// In en, this message translates to:
  /// **'Save'**
  String get fileSave;

  /// No description provided for @fileReload.
  ///
  /// In en, this message translates to:
  /// **'Reload'**
  String get fileReload;

  /// No description provided for @queueRemoveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not remove this draft from device storage. It is still queued. Check available storage and try again.'**
  String get queueRemoveFailed;

  /// No description provided for @composerReuseSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Reuse text from this conversation and recent sends'**
  String get composerReuseSubtitle;

  /// No description provided for @composerReuseDescription.
  ///
  /// In en, this message translates to:
  /// **'Text from loaded prompts in this conversation and recent sends on this server. Selecting one appends it to your draft. Attachments are not copied. With a keyboard, use Up at the start or Down at the end to browse and restore your draft.'**
  String get composerReuseDescription;

  /// No description provided for @composerReuseSearch.
  ///
  /// In en, this message translates to:
  /// **'Search recent prompts'**
  String get composerReuseSearch;

  /// No description provided for @composerReuseEmpty.
  ///
  /// In en, this message translates to:
  /// **'No matching prompts'**
  String get composerReuseEmpty;

  /// No description provided for @backgroundSubagentsTitle.
  ///
  /// In en, this message translates to:
  /// **'Background subagents'**
  String get backgroundSubagentsTitle;

  /// No description provided for @backgroundWorkTitle.
  ///
  /// In en, this message translates to:
  /// **'Move running work to background'**
  String get backgroundWorkTitle;

  /// No description provided for @backgroundWorkShortcut.
  ///
  /// In en, this message translates to:
  /// **'Continue this work while you use the chat · Ctrl+B'**
  String get backgroundWorkShortcut;

  /// No description provided for @backgroundWorkNoop.
  ///
  /// In en, this message translates to:
  /// **'No foreground subagents to background.'**
  String get backgroundWorkNoop;

  /// No description provided for @backgroundWorkPromoted.
  ///
  /// In en, this message translates to:
  /// **'Subagents are continuing in the background.'**
  String get backgroundWorkPromoted;

  /// No description provided for @librarySearchHint.
  ///
  /// In en, this message translates to:
  /// **'Find settings, tools, and help'**
  String get librarySearchHint;

  /// No description provided for @libraryDefaultModel.
  ///
  /// In en, this message translates to:
  /// **'Default for new chats'**
  String get libraryDefaultModel;

  /// No description provided for @libraryNoModel.
  ///
  /// In en, this message translates to:
  /// **'No model selected'**
  String get libraryNoModel;

  /// Number of matching destinations in More
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0 {No matching tools for “{query}”.} one {1 result for “{query}”.} other {{count} results for “{query}”.}}'**
  String librarySearchResults(int count, String query);

  /// No description provided for @chatAttachmentUnsupported.
  ///
  /// In en, this message translates to:
  /// **'Only PNG, JPEG, GIF, WebP, PDF, and text files can be attached.'**
  String get chatAttachmentUnsupported;

  /// Action that restarts the app-managed OpenCode server running in Termux
  ///
  /// In en, this message translates to:
  /// **'Restart local server'**
  String get termuxRestartServer;

  /// Confirmation title before restarting the managed Termux server
  ///
  /// In en, this message translates to:
  /// **'Restart the local server?'**
  String get termuxRestartTitle;

  /// Confirmation explanation before restarting the managed Termux server
  ///
  /// In en, this message translates to:
  /// **'OpenCode will be briefly unavailable. The app will keep your current workspace and reconnect automatically.'**
  String get termuxRestartMessage;

  /// Additional restart warning when one or more sessions are generating
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 session is generating. Restarting will interrupt it.} other{{count} sessions are generating. Restarting will interrupt them.}}'**
  String termuxRestartBusyMessage(int count);

  /// Confirmation button that starts a managed Termux server restart
  ///
  /// In en, this message translates to:
  /// **'Restart'**
  String get termuxRestartConfirm;

  /// Progress label while the managed Termux server restarts
  ///
  /// In en, this message translates to:
  /// **'Restarting local server...'**
  String get termuxRestarting;

  /// Progress explanation while the managed Termux server restarts
  ///
  /// In en, this message translates to:
  /// **'The installed OpenCode version and saved credential are unchanged. The app will reconnect when the server is ready.'**
  String get termuxRestartProgress;

  /// Success message after a managed Termux server restart
  ///
  /// In en, this message translates to:
  /// **'Local server restarted and reconnected.'**
  String get termuxRestartSucceeded;

  /// Message after restart preflight fails while the existing managed server remains healthy
  ///
  /// In en, this message translates to:
  /// **'Restart was not performed. The existing local server is still running.'**
  String get termuxRestartNotPerformed;

  /// No description provided for @chatCopyCompleteReply.
  ///
  /// In en, this message translates to:
  /// **'Copy complete reply'**
  String get chatCopyCompleteReply;

  /// No description provided for @chatCopyReplySoFar.
  ///
  /// In en, this message translates to:
  /// **'Copy reply so far'**
  String get chatCopyReplySoFar;

  /// No description provided for @commandRunTitle.
  ///
  /// In en, this message translates to:
  /// **'Run /{command}'**
  String commandRunTitle(String command);

  /// No description provided for @commandDestination.
  ///
  /// In en, this message translates to:
  /// **'Chat'**
  String get commandDestination;

  /// No description provided for @commandNewChat.
  ///
  /// In en, this message translates to:
  /// **'New chat'**
  String get commandNewChat;

  /// No description provided for @commandUntitledChat.
  ///
  /// In en, this message translates to:
  /// **'Untitled chat'**
  String get commandUntitledChat;

  /// No description provided for @commandArguments.
  ///
  /// In en, this message translates to:
  /// **'Arguments (optional)'**
  String get commandArguments;

  /// No description provided for @commandRun.
  ///
  /// In en, this message translates to:
  /// **'Run'**
  String get commandRun;

  /// No description provided for @commandRunning.
  ///
  /// In en, this message translates to:
  /// **'Starting…'**
  String get commandRunning;

  /// No description provided for @commandLocationChanged.
  ///
  /// In en, this message translates to:
  /// **'The server or workspace changed. Close this dialog and open the command again.'**
  String get commandLocationChanged;

  /// No description provided for @refreshFailed.
  ///
  /// In en, this message translates to:
  /// **'Couldn’t refresh'**
  String get refreshFailed;

  /// No description provided for @refreshRetry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get refreshRetry;

  /// No description provided for @filesProjectRoot.
  ///
  /// In en, this message translates to:
  /// **'Project root'**
  String get filesProjectRoot;

  /// No description provided for @filesOpenFolder.
  ///
  /// In en, this message translates to:
  /// **'Open folder {folder}'**
  String filesOpenFolder(String folder);

  /// No description provided for @filesCurrentFolder.
  ///
  /// In en, this message translates to:
  /// **'Current folder: {folder}'**
  String filesCurrentFolder(String folder);

  /// No description provided for @globalSessionsLoadMore.
  ///
  /// In en, this message translates to:
  /// **'Load more sessions'**
  String get globalSessionsLoadMore;

  /// Generic failure when refreshing the global session inventory
  ///
  /// In en, this message translates to:
  /// **'Could not refresh sessions.'**
  String get globalSessionsRefreshFailed;

  /// No description provided for @workspaceSearchAllSessions.
  ///
  /// In en, this message translates to:
  /// **'Search all sessions'**
  String get workspaceSearchAllSessions;

  /// No description provided for @workspaceProjectListUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Project list unavailable'**
  String get workspaceProjectListUnavailable;

  /// No description provided for @workspaceProjectListFallback.
  ///
  /// In en, this message translates to:
  /// **'Your conversations can still be available. Search all sessions to find previous work.'**
  String get workspaceProjectListFallback;

  /// No description provided for @workspaceRetryProjects.
  ///
  /// In en, this message translates to:
  /// **'Retry projects'**
  String get workspaceRetryProjects;

  /// No description provided for @historyLoadOlder.
  ///
  /// In en, this message translates to:
  /// **'Load older messages'**
  String get historyLoadOlder;

  /// No description provided for @historyReload.
  ///
  /// In en, this message translates to:
  /// **'Reload recent history'**
  String get historyReload;

  /// No description provided for @historyCursorExpired.
  ///
  /// In en, this message translates to:
  /// **'Older history changed or expired. Reload recent history to continue.'**
  String get historyCursorExpired;

  /// No description provided for @historyRefreshed.
  ///
  /// In en, this message translates to:
  /// **'History refreshed. Older messages remain available above.'**
  String get historyRefreshed;

  /// No description provided for @historyLoadedOnly.
  ///
  /// In en, this message translates to:
  /// **'Only loaded messages are included. Load older history to include more.'**
  String get historyLoadedOnly;

  /// No description provided for @historyLoadedTotals.
  ///
  /// In en, this message translates to:
  /// **'Usage and loaded history'**
  String get historyLoadedTotals;

  /// No description provided for @historyCopyLoadedReply.
  ///
  /// In en, this message translates to:
  /// **'Copy loaded reply'**
  String get historyCopyLoadedReply;

  /// No description provided for @historyLoadedMessages.
  ///
  /// In en, this message translates to:
  /// **'Loaded messages'**
  String get historyLoadedMessages;

  /// No description provided for @historyLoadedCost.
  ///
  /// In en, this message translates to:
  /// **'Cost of loaded messages'**
  String get historyLoadedCost;

  /// No description provided for @historyServerTotalsNote.
  ///
  /// In en, this message translates to:
  /// **'Rows marked reported by server cover the session. Message counts and other estimates cover loaded history.'**
  String get historyServerTotalsNote;

  /// No description provided for @sessionsLoadedOnly.
  ///
  /// In en, this message translates to:
  /// **'Showing loaded sessions. Load more to include older conversations.'**
  String get sessionsLoadedOnly;

  /// No description provided for @sessionsDetailsUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Session details could not be loaded. Try again.'**
  String get sessionsDetailsUnavailable;

  /// No description provided for @sessionsLoadMore.
  ///
  /// In en, this message translates to:
  /// **'Load more sessions'**
  String get sessionsLoadMore;

  /// No description provided for @sessionsReload.
  ///
  /// In en, this message translates to:
  /// **'Reload recent sessions'**
  String get sessionsReload;

  /// No description provided for @sessionsNoLoadedRecent.
  ///
  /// In en, this message translates to:
  /// **'No recent sessions in loaded results'**
  String get sessionsNoLoadedRecent;

  /// No description provided for @sessionsNoLoadedArchived.
  ///
  /// In en, this message translates to:
  /// **'No archived sessions in loaded results'**
  String get sessionsNoLoadedArchived;

  /// No description provided for @sessionsLoadedCount.
  ///
  /// In en, this message translates to:
  /// **'{count} loaded'**
  String sessionsLoadedCount(int count);

  /// No description provided for @revertStageTitle.
  ///
  /// In en, this message translates to:
  /// **'Stage a revert from this prompt?'**
  String get revertStageTitle;

  /// No description provided for @revertStageDescription.
  ///
  /// In en, this message translates to:
  /// **'This prompt and the conversation after it will be hidden while the revert is staged. Review the result before making it permanent.'**
  String get revertStageDescription;

  /// No description provided for @revertApplyFiles.
  ///
  /// In en, this message translates to:
  /// **'Revert file changes too'**
  String get revertApplyFiles;

  /// No description provided for @revertApplyFilesHint.
  ///
  /// In en, this message translates to:
  /// **'Applies file changes immediately when staging. Clear can restore the staged files from the saved snapshot.'**
  String get revertApplyFilesHint;

  /// No description provided for @revertStageAction.
  ///
  /// In en, this message translates to:
  /// **'Stage and review'**
  String get revertStageAction;

  /// No description provided for @revertReviewTitle.
  ///
  /// In en, this message translates to:
  /// **'Review staged revert'**
  String get revertReviewTitle;

  /// No description provided for @revertReviewChanged.
  ///
  /// In en, this message translates to:
  /// **'This session or its staged revert changed. Review the latest state before continuing.'**
  String get revertReviewChanged;

  /// No description provided for @revertReviewLatest.
  ///
  /// In en, this message translates to:
  /// **'Review latest state'**
  String get revertReviewLatest;

  /// No description provided for @revertBusy.
  ///
  /// In en, this message translates to:
  /// **'Wait for the current session action to finish.'**
  String get revertBusy;

  /// No description provided for @revertCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get revertCancel;

  /// No description provided for @revertCommitTitle.
  ///
  /// In en, this message translates to:
  /// **'Make this revert permanent?'**
  String get revertCommitTitle;

  /// No description provided for @revertCommitDescription.
  ///
  /// In en, this message translates to:
  /// **'Removes the staged conversation history permanently. File changes already applied during staging will remain. You cannot clear this revert afterward.'**
  String get revertCommitDescription;

  /// No description provided for @revertCommitAction.
  ///
  /// In en, this message translates to:
  /// **'Make revert permanent'**
  String get revertCommitAction;

  /// No description provided for @revertClearTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear this staged revert?'**
  String get revertClearTitle;

  /// No description provided for @revertClearDescription.
  ///
  /// In en, this message translates to:
  /// **'Restores the hidden conversation and the files included in this stage from the saved snapshot. Changes made to those files since staging may be replaced. Queued work may resume.'**
  String get revertClearDescription;

  /// No description provided for @revertClearAction.
  ///
  /// In en, this message translates to:
  /// **'Clear staged revert'**
  String get revertClearAction;

  /// No description provided for @revertNoStage.
  ///
  /// In en, this message translates to:
  /// **'There is no staged revert to review.'**
  String get revertNoStage;

  /// No description provided for @revertBoundaryLabel.
  ///
  /// In en, this message translates to:
  /// **'Staged from prompt'**
  String get revertBoundaryLabel;

  /// No description provided for @revertPreviewDescription.
  ///
  /// In en, this message translates to:
  /// **'These are the file changes reported for this stage. Staging may already have applied them.'**
  String get revertPreviewDescription;

  /// No description provided for @revertPreviewUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The server did not provide a file preview. This does not establish whether files changed.'**
  String get revertPreviewUnavailable;

  /// No description provided for @revertPreviewEmpty.
  ///
  /// In en, this message translates to:
  /// **'No file changes were reported for this stage.'**
  String get revertPreviewEmpty;

  /// No description provided for @revertStaged.
  ///
  /// In en, this message translates to:
  /// **'Revert staged'**
  String get revertStaged;

  /// No description provided for @revertReview.
  ///
  /// In en, this message translates to:
  /// **'Review'**
  String get revertReview;

  /// No description provided for @revertFromHere.
  ///
  /// In en, this message translates to:
  /// **'Revert from this prompt'**
  String get revertFromHere;

  /// No description provided for @revertUndoDescription.
  ///
  /// In en, this message translates to:
  /// **'Stage a revert and review the affected files'**
  String get revertUndoDescription;

  /// No description provided for @revertClearShortDescription.
  ///
  /// In en, this message translates to:
  /// **'Review and clear the staged revert'**
  String get revertClearShortDescription;

  /// No description provided for @revertPromptUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The boundary prompt could not be loaded.'**
  String get revertPromptUnavailable;

  /// No description provided for @revertPromptLoading.
  ///
  /// In en, this message translates to:
  /// **'Loading the boundary prompt…'**
  String get revertPromptLoading;

  /// No description provided for @revertAttachmentPrompt.
  ///
  /// In en, this message translates to:
  /// **'Attachment-only prompt'**
  String get revertAttachmentPrompt;

  /// No description provided for @revertResolveBeforeSending.
  ///
  /// In en, this message translates to:
  /// **'Review the staged revert, then clear it or make it permanent before sending. Your draft is kept.'**
  String get revertResolveBeforeSending;

  /// No description provided for @sessionNoteTitle.
  ///
  /// In en, this message translates to:
  /// **'Note for the agent'**
  String get sessionNoteTitle;

  /// No description provided for @sessionNoteDescription.
  ///
  /// In en, this message translates to:
  /// **'Keep a short instruction for this session. Saving or removing it takes effect at the next agent step and appears in the transcript then. It does not start a run.'**
  String get sessionNoteDescription;

  /// No description provided for @sessionNoteHint.
  ///
  /// In en, this message translates to:
  /// **'For example: Keep explanations brief and run the relevant checks before finishing.'**
  String get sessionNoteHint;

  /// No description provided for @sessionNoteSave.
  ///
  /// In en, this message translates to:
  /// **'Save note'**
  String get sessionNoteSave;

  /// No description provided for @sessionNoteRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove saved note'**
  String get sessionNoteRemove;

  /// No description provided for @sessionNoteSaved.
  ///
  /// In en, this message translates to:
  /// **'Note saved'**
  String get sessionNoteSaved;

  /// No description provided for @sessionNoteRemoved.
  ///
  /// In en, this message translates to:
  /// **'Note removed'**
  String get sessionNoteRemoved;

  /// No description provided for @sessionNotePending.
  ///
  /// In en, this message translates to:
  /// **'Applies at the next agent step.'**
  String get sessionNotePending;

  /// No description provided for @sessionInstructionsUpdated.
  ///
  /// In en, this message translates to:
  /// **'Instructions updated'**
  String get sessionInstructionsUpdated;

  /// No description provided for @sessionInstructionsApplied.
  ///
  /// In en, this message translates to:
  /// **'The agent\'s session instructions have been updated for this step.'**
  String get sessionInstructionsApplied;

  /// No description provided for @sessionNoteUnsupported.
  ///
  /// In en, this message translates to:
  /// **'This server does not support session notes.'**
  String get sessionNoteUnsupported;

  /// No description provided for @sessionNoteAuthorization.
  ///
  /// In en, this message translates to:
  /// **'Check this server\'s password and permissions, then try again. Your draft is kept.'**
  String get sessionNoteAuthorization;

  /// No description provided for @sessionNoteChanged.
  ///
  /// In en, this message translates to:
  /// **'The session or its instructions changed. Refresh the saved note before saving again. Your draft is kept.'**
  String get sessionNoteChanged;

  /// No description provided for @sessionNoteInvalid.
  ///
  /// In en, this message translates to:
  /// **'The saved note has a format this editor cannot safely change.'**
  String get sessionNoteInvalid;

  /// No description provided for @sessionNoteTooLarge.
  ///
  /// In en, this message translates to:
  /// **'Shorten the note to fit the server\'s size limit.'**
  String get sessionNoteTooLarge;

  /// No description provided for @sessionNoteBusy.
  ///
  /// In en, this message translates to:
  /// **'A note change is already being saved. Try again when it finishes.'**
  String get sessionNoteBusy;

  /// No description provided for @sessionNoteRefresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh saved note'**
  String get sessionNoteRefresh;

  /// No description provided for @sessionNoteSavedVersion.
  ///
  /// In en, this message translates to:
  /// **'Current saved note — review before replacing'**
  String get sessionNoteSavedVersion;

  /// No description provided for @sessionNoteNone.
  ///
  /// In en, this message translates to:
  /// **'No saved note'**
  String get sessionNoteNone;

  /// No description provided for @sessionNoteDiscard.
  ///
  /// In en, this message translates to:
  /// **'Discard your note changes?'**
  String get sessionNoteDiscard;

  /// No description provided for @sessionNoteKeepEditing.
  ///
  /// In en, this message translates to:
  /// **'Keep editing'**
  String get sessionNoteKeepEditing;

  /// No description provided for @sessionNoteDiscardAction.
  ///
  /// In en, this message translates to:
  /// **'Discard changes'**
  String get sessionNoteDiscardAction;

  /// No description provided for @sessionNoteBytes.
  ///
  /// In en, this message translates to:
  /// **'{used} / {limit} bytes'**
  String sessionNoteBytes(int used, int limit);

  /// No description provided for @usageTitle.
  ///
  /// In en, this message translates to:
  /// **'Usage and cost'**
  String get usageTitle;

  /// No description provided for @usageDescription.
  ///
  /// In en, this message translates to:
  /// **'Activity recorded by this OpenCode server across your sessions.'**
  String get usageDescription;

  /// No description provided for @usageRefresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh usage'**
  String get usageRefresh;

  /// No description provided for @usageToday.
  ///
  /// In en, this message translates to:
  /// **'Today'**
  String get usageToday;

  /// No description provided for @usageThirtyDays.
  ///
  /// In en, this message translates to:
  /// **'30 days'**
  String get usageThirtyDays;

  /// No description provided for @usageYear.
  ///
  /// In en, this message translates to:
  /// **'This year'**
  String get usageYear;

  /// No description provided for @usageAllTime.
  ///
  /// In en, this message translates to:
  /// **'All time'**
  String get usageAllTime;

  /// No description provided for @usageScope.
  ///
  /// In en, this message translates to:
  /// **'Project scope'**
  String get usageScope;

  /// No description provided for @usageAllProjects.
  ///
  /// In en, this message translates to:
  /// **'All projects'**
  String get usageAllProjects;

  /// No description provided for @usageCurrentProject.
  ///
  /// In en, this message translates to:
  /// **'Current project'**
  String get usageCurrentProject;

  /// No description provided for @usageLoading.
  ///
  /// In en, this message translates to:
  /// **'Loading usage'**
  String get usageLoading;

  /// No description provided for @usageUnsupported.
  ///
  /// In en, this message translates to:
  /// **'This server does not support aggregate usage.'**
  String get usageUnsupported;

  /// No description provided for @usageProjectUnavailable.
  ///
  /// In en, this message translates to:
  /// **'No current project could be identified. Choose All projects or open a project first.'**
  String get usageProjectUnavailable;

  /// No description provided for @usageTimezoneUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Could not read this device\'s timezone. Retry to load correctly dated usage.'**
  String get usageTimezoneUnavailable;

  /// No description provided for @usageRefreshInterrupted.
  ///
  /// In en, this message translates to:
  /// **'The connection changed while loading usage. Refresh to try again.'**
  String get usageRefreshInterrupted;

  /// No description provided for @usageInvalidResponse.
  ///
  /// In en, this message translates to:
  /// **'The server returned incomplete usage data. Refresh to try again.'**
  String get usageInvalidResponse;

  /// No description provided for @usageAuthorization.
  ///
  /// In en, this message translates to:
  /// **'Check this server\'s password and permissions, then refresh.'**
  String get usageAuthorization;

  /// No description provided for @usagePreviousResult.
  ///
  /// In en, this message translates to:
  /// **'Showing the previous result for these filters.'**
  String get usagePreviousResult;

  /// No description provided for @usageLocationChanged.
  ///
  /// In en, this message translates to:
  /// **'The active server or location changed. Reopen Usage from Settings.'**
  String get usageLocationChanged;

  /// No description provided for @usageTinyCost.
  ///
  /// In en, this message translates to:
  /// **'Less than \$0.000001'**
  String get usageTinyCost;

  /// No description provided for @usageReportedCost.
  ///
  /// In en, this message translates to:
  /// **'Reported cost · USD'**
  String get usageReportedCost;

  /// No description provided for @usageSessions.
  ///
  /// In en, this message translates to:
  /// **'Sessions'**
  String get usageSessions;

  /// No description provided for @usageSubagents.
  ///
  /// In en, this message translates to:
  /// **'Subagent sessions'**
  String get usageSubagents;

  /// No description provided for @usagePrompts.
  ///
  /// In en, this message translates to:
  /// **'Prompts'**
  String get usagePrompts;

  /// No description provided for @usageSteps.
  ///
  /// In en, this message translates to:
  /// **'Agent steps'**
  String get usageSteps;

  /// No description provided for @usageActiveDays.
  ///
  /// In en, this message translates to:
  /// **'Active days'**
  String get usageActiveDays;

  /// No description provided for @usageStreak.
  ///
  /// In en, this message translates to:
  /// **'Longest streak · days'**
  String get usageStreak;

  /// No description provided for @usageEmpty.
  ///
  /// In en, this message translates to:
  /// **'No activity in this range. Try a wider range or All projects.'**
  String get usageEmpty;

  /// No description provided for @usageTokens.
  ///
  /// In en, this message translates to:
  /// **'Tokens'**
  String get usageTokens;

  /// No description provided for @usageTotalTokens.
  ///
  /// In en, this message translates to:
  /// **'Total'**
  String get usageTotalTokens;

  /// No description provided for @usageInput.
  ///
  /// In en, this message translates to:
  /// **'Input'**
  String get usageInput;

  /// No description provided for @usageOutput.
  ///
  /// In en, this message translates to:
  /// **'Output'**
  String get usageOutput;

  /// No description provided for @usageReasoning.
  ///
  /// In en, this message translates to:
  /// **'Reasoning'**
  String get usageReasoning;

  /// No description provided for @usageCacheRead.
  ///
  /// In en, this message translates to:
  /// **'Cache read'**
  String get usageCacheRead;

  /// No description provided for @usageCacheWrite.
  ///
  /// In en, this message translates to:
  /// **'Cache write'**
  String get usageCacheWrite;

  /// No description provided for @usageModels.
  ///
  /// In en, this message translates to:
  /// **'Model usage'**
  String get usageModels;

  /// No description provided for @usageNoModels.
  ///
  /// In en, this message translates to:
  /// **'No model usage was recorded in this range.'**
  String get usageNoModels;

  /// No description provided for @usageCostShare.
  ///
  /// In en, this message translates to:
  /// **'Share of reported cost'**
  String get usageCostShare;

  /// No description provided for @usageToolReliability.
  ///
  /// In en, this message translates to:
  /// **'Tool reliability'**
  String get usageToolReliability;

  /// No description provided for @usageToolsUnavailable.
  ///
  /// In en, this message translates to:
  /// **'This response does not include tool reliability.'**
  String get usageToolsUnavailable;

  /// No description provided for @usageNoTools.
  ///
  /// In en, this message translates to:
  /// **'No tool calls were recorded in this range.'**
  String get usageNoTools;

  /// No description provided for @usageNoFinishedTools.
  ///
  /// In en, this message translates to:
  /// **'No finished tool calls yet.'**
  String get usageNoFinishedTools;

  /// No description provided for @usageToolCalls.
  ///
  /// In en, this message translates to:
  /// **'Calls'**
  String get usageToolCalls;

  /// No description provided for @usageSucceeded.
  ///
  /// In en, this message translates to:
  /// **'Succeeded'**
  String get usageSucceeded;

  /// No description provided for @usageFailed.
  ///
  /// In en, this message translates to:
  /// **'Failed'**
  String get usageFailed;

  /// No description provided for @usageUnfinished.
  ///
  /// In en, this message translates to:
  /// **'Unfinished'**
  String get usageUnfinished;

  /// No description provided for @usageCostDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Costs are estimates reported by OpenCode, not a provider invoice. Unfinished tool calls are excluded from the success rate.'**
  String get usageCostDisclosure;

  /// No description provided for @usagePeriod.
  ///
  /// In en, this message translates to:
  /// **'{from} – {to}'**
  String usagePeriod(String from, String to);

  /// No description provided for @usageTimezone.
  ///
  /// In en, this message translates to:
  /// **'Timezone: {timezone}'**
  String usageTimezone(String timezone);

  /// No description provided for @usageModelSteps.
  ///
  /// In en, this message translates to:
  /// **'{steps} steps'**
  String usageModelSteps(String steps);

  /// No description provided for @usageModelTokens.
  ///
  /// In en, this message translates to:
  /// **'{tokens} tokens'**
  String usageModelTokens(String tokens);

  /// No description provided for @usageSuccessRate.
  ///
  /// In en, this message translates to:
  /// **'{rate} of finished calls succeeded'**
  String usageSuccessRate(String rate);

  /// No description provided for @usageUpdated.
  ///
  /// In en, this message translates to:
  /// **'Updated at {time}'**
  String usageUpdated(String time);

  /// No description provided for @mcpRuntimeTitle.
  ///
  /// In en, this message translates to:
  /// **'Until server restart'**
  String get mcpRuntimeTitle;

  /// No description provided for @mcpRuntimeDescription.
  ///
  /// In en, this message translates to:
  /// **'Adds this MCP server to the selected location and tries to connect it now. It is removed when OpenCode restarts. For permanent setup, edit the server configuration.'**
  String get mcpRuntimeDescription;

  /// No description provided for @mcpCurrentLocation.
  ///
  /// In en, this message translates to:
  /// **'Current location'**
  String get mcpCurrentLocation;

  /// No description provided for @mcpDefaultLocation.
  ///
  /// In en, this message translates to:
  /// **'OpenCode server’s default location'**
  String get mcpDefaultLocation;

  /// No description provided for @mcpWorkspaceLocation.
  ///
  /// In en, this message translates to:
  /// **'Workspace: {workspace}'**
  String mcpWorkspaceLocation(String workspace);

  /// No description provided for @mcpLocationChanged.
  ///
  /// In en, this message translates to:
  /// **'The connection or location changed. Your draft is still here; reopen setup in the intended location before adding it.'**
  String get mcpLocationChanged;

  /// No description provided for @mcpAdding.
  ///
  /// In en, this message translates to:
  /// **'Adding MCP server'**
  String get mcpAdding;

  /// No description provided for @mcpAdd.
  ///
  /// In en, this message translates to:
  /// **'Add MCP server'**
  String get mcpAdd;

  /// No description provided for @mcpRuntimeEmpty.
  ///
  /// In en, this message translates to:
  /// **'Add tools for the current location until OpenCode restarts.'**
  String get mcpRuntimeEmpty;

  /// No description provided for @mcpRuntimeAdded.
  ///
  /// In en, this message translates to:
  /// **'MCP server added for this location'**
  String get mcpRuntimeAdded;

  /// No description provided for @sessionUnread.
  ///
  /// In en, this message translates to:
  /// **'Unread result'**
  String get sessionUnread;

  /// No description provided for @shareSessionViewsTitle.
  ///
  /// In en, this message translates to:
  /// **'Sync read state'**
  String get shareSessionViewsTitle;

  /// No description provided for @shareSessionViewsOn.
  ///
  /// In en, this message translates to:
  /// **'Let your other OpenCode clients know which completed results you have viewed.'**
  String get shareSessionViewsOn;

  /// No description provided for @shareSessionViewsOff.
  ///
  /// In en, this message translates to:
  /// **'Reading stays private to this device. Unread results use local read history.'**
  String get shareSessionViewsOff;

  /// No description provided for @shareSessionViewsSaveError.
  ///
  /// In en, this message translates to:
  /// **'Could not save this preference. Read-state sharing is off on this device for now.'**
  String get shareSessionViewsSaveError;

  /// No description provided for @exportTitle.
  ///
  /// In en, this message translates to:
  /// **'Export conversation'**
  String get exportTitle;

  /// No description provided for @exportDescription.
  ///
  /// In en, this message translates to:
  /// **'Choose a format to save this conversation on your device.'**
  String get exportDescription;

  /// No description provided for @exportJson.
  ///
  /// In en, this message translates to:
  /// **'Complete conversation · JSON'**
  String get exportJson;

  /// No description provided for @exportJsonDescription.
  ///
  /// In en, this message translates to:
  /// **'Downloads the full session from the server, including older messages.'**
  String get exportJsonDescription;

  /// No description provided for @exportMarkdown.
  ///
  /// In en, this message translates to:
  /// **'Readable transcript · Markdown'**
  String get exportMarkdown;

  /// No description provided for @exportMarkdownDescription.
  ///
  /// In en, this message translates to:
  /// **'Saves the messages currently loaded in this chat. Load older messages first if you need them included.'**
  String get exportMarkdownDescription;

  /// No description provided for @exportRedact.
  ///
  /// In en, this message translates to:
  /// **'Redact sensitive data'**
  String get exportRedact;

  /// No description provided for @exportRedactDescription.
  ///
  /// In en, this message translates to:
  /// **'Replaces conversation text and sensitive fields with placeholders. Turn this off to back up the original text. Review any export before sharing.'**
  String get exportRedactDescription;

  /// No description provided for @exportUnredacted.
  ///
  /// In en, this message translates to:
  /// **'The unredacted file may contain secrets, local paths, and private tool output.'**
  String get exportUnredacted;

  /// No description provided for @exportSave.
  ///
  /// In en, this message translates to:
  /// **'Save file'**
  String get exportSave;

  /// No description provided for @exportCancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel download'**
  String get exportCancel;

  /// No description provided for @exportDownloading.
  ///
  /// In en, this message translates to:
  /// **'Downloading complete conversation…'**
  String get exportDownloading;

  /// No description provided for @exportSaving.
  ///
  /// In en, this message translates to:
  /// **'Saving file…'**
  String get exportSaving;

  /// No description provided for @exportSaved.
  ///
  /// In en, this message translates to:
  /// **'Conversation saved'**
  String get exportSaved;

  /// No description provided for @exportChanged.
  ///
  /// In en, this message translates to:
  /// **'The connection or location changed. Reopen export from the intended conversation.'**
  String get exportChanged;

  /// No description provided for @exportUnsupported.
  ///
  /// In en, this message translates to:
  /// **'This server does not support JSON export. You can still save the loaded Markdown transcript.'**
  String get exportUnsupported;

  /// No description provided for @exportAuthorization.
  ///
  /// In en, this message translates to:
  /// **'The server denied access. Check your connection credentials and try again.'**
  String get exportAuthorization;

  /// No description provided for @exportMissing.
  ///
  /// In en, this message translates to:
  /// **'This conversation no longer exists on the server. You can still save the loaded Markdown transcript.'**
  String get exportMissing;

  /// No description provided for @exportFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not export the conversation. Check your connection and storage, then try again.'**
  String get exportFailed;

  /// No description provided for @importTitle.
  ///
  /// In en, this message translates to:
  /// **'Import conversation'**
  String get importTitle;

  /// No description provided for @importDescription.
  ///
  /// In en, this message translates to:
  /// **'Restore a JSON export to this OpenCode server. Choose a file, then review where it will be imported.'**
  String get importDescription;

  /// No description provided for @importChoose.
  ///
  /// In en, this message translates to:
  /// **'Choose JSON file'**
  String get importChoose;

  /// No description provided for @importChooseAnother.
  ///
  /// In en, this message translates to:
  /// **'Choose another file'**
  String get importChooseAnother;

  /// No description provided for @importAction.
  ///
  /// In en, this message translates to:
  /// **'Import conversation'**
  String get importAction;

  /// No description provided for @importUntitled.
  ///
  /// In en, this message translates to:
  /// **'Untitled conversation'**
  String get importUntitled;

  /// No description provided for @importMessageCount.
  ///
  /// In en, this message translates to:
  /// **'{count} message records'**
  String importMessageCount(int count);

  /// No description provided for @importRedacted.
  ///
  /// In en, this message translates to:
  /// **'This file contains redacted placeholders. Import cannot recover the original text; use an unredacted export if you need it.'**
  String get importRedacted;

  /// No description provided for @importParent.
  ///
  /// In en, this message translates to:
  /// **'Parent conversation {id} must already exist on this server. Import the parent first.'**
  String importParent(String id);

  /// No description provided for @importArchived.
  ///
  /// In en, this message translates to:
  /// **'This conversation is archived. Import will keep its archived status.'**
  String get importArchived;

  /// No description provided for @importDestination.
  ///
  /// In en, this message translates to:
  /// **'Import into'**
  String get importDestination;

  /// No description provided for @importChooseDestination.
  ///
  /// In en, this message translates to:
  /// **'Choose a directory on this server'**
  String get importChooseDestination;

  /// No description provided for @importChangeDestination.
  ///
  /// In en, this message translates to:
  /// **'Change destination'**
  String get importChangeDestination;

  /// No description provided for @importNoDestinations.
  ///
  /// In en, this message translates to:
  /// **'No project directories are available. Open a project on this server, then try again.'**
  String get importNoDestinations;

  /// No description provided for @importDestinationFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not load destination projects or workspaces. Try again; your file is still selected.'**
  String get importDestinationFailed;

  /// No description provided for @importPreserves.
  ///
  /// In en, this message translates to:
  /// **'Your source file stays unchanged. Existing conversations are never replaced, and importing does not start an agent run.'**
  String get importPreserves;

  /// No description provided for @importReading.
  ///
  /// In en, this message translates to:
  /// **'Preparing import…'**
  String get importReading;

  /// No description provided for @importSending.
  ///
  /// In en, this message translates to:
  /// **'Importing conversation…'**
  String get importSending;

  /// No description provided for @importSucceeded.
  ///
  /// In en, this message translates to:
  /// **'Conversation imported'**
  String get importSucceeded;

  /// No description provided for @importOpen.
  ///
  /// In en, this message translates to:
  /// **'Open conversation'**
  String get importOpen;

  /// No description provided for @importOpenFailed.
  ///
  /// In en, this message translates to:
  /// **'The conversation was imported, but could not be opened. Find it in All sessions on the destination server.'**
  String get importOpenFailed;

  /// No description provided for @importChanged.
  ///
  /// In en, this message translates to:
  /// **'The connection or location changed. Your file is still here. Reopen import on the intended server before continuing.'**
  String get importChanged;

  /// No description provided for @importUnsupported.
  ///
  /// In en, this message translates to:
  /// **'This server does not support JSON import.'**
  String get importUnsupported;

  /// No description provided for @importInvalidFile.
  ///
  /// In en, this message translates to:
  /// **'Choose a valid OpenCode JSON export with session information and message records. Markdown transcripts cannot be imported.'**
  String get importInvalidFile;

  /// No description provided for @importTooLarge.
  ///
  /// In en, this message translates to:
  /// **'This file exceeds the mobile import limit of 128 MiB. It has not been uploaded or truncated. Use a desktop or server transfer for this file.'**
  String get importTooLarge;

  /// No description provided for @importConflict.
  ///
  /// In en, this message translates to:
  /// **'A conversation with this ID already exists on this server. Nothing was replaced. Find it in All sessions, or import this file on another server.'**
  String get importConflict;

  /// No description provided for @importAuthorization.
  ///
  /// In en, this message translates to:
  /// **'The server denied access. Check your connection credentials. Your file is still selected.'**
  String get importAuthorization;

  /// No description provided for @importParentMissing.
  ///
  /// In en, this message translates to:
  /// **'The parent conversation is missing from this server. Import the parent first, then retry this file.'**
  String get importParentMissing;

  /// No description provided for @importRejected.
  ///
  /// In en, this message translates to:
  /// **'The server rejected this export format. Your file is still selected; check that it came from a compatible OpenCode server.'**
  String get importRejected;

  /// No description provided for @importUnconfirmed.
  ///
  /// In en, this message translates to:
  /// **'Import could not be confirmed. Check All sessions before retrying: the server may have received it. Your source file is unchanged.'**
  String get importUnconfirmed;

  /// No description provided for @sessionsNoOtherRecent.
  ///
  /// In en, this message translates to:
  /// **'No other recent conversations'**
  String get sessionsNoOtherRecent;

  /// No description provided for @sessionPin.
  ///
  /// In en, this message translates to:
  /// **'Pin on this device'**
  String get sessionPin;

  /// No description provided for @sessionUnpin.
  ///
  /// In en, this message translates to:
  /// **'Unpin'**
  String get sessionUnpin;

  /// No description provided for @sessionPinned.
  ///
  /// In en, this message translates to:
  /// **'Pinned'**
  String get sessionPinned;

  /// No description provided for @sessionPinFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save this pin. Check device storage and that the session location has not changed, then try again.'**
  String get sessionPinFailed;

  /// No description provided for @sessionPinsLoadFailed.
  ///
  /// In en, this message translates to:
  /// **'Some pinned conversations could not be loaded. Refresh to try again.'**
  String get sessionPinsLoadFailed;

  /// No description provided for @promptStashSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save this prompt. Your composer is unchanged. Check device storage and try again.'**
  String get promptStashSaveFailed;

  /// No description provided for @promptOriginalDraft.
  ///
  /// In en, this message translates to:
  /// **'Restore original draft'**
  String get promptOriginalDraft;

  /// No description provided for @promptStashTitle.
  ///
  /// In en, this message translates to:
  /// **'Saved prompts'**
  String get promptStashTitle;

  /// Search local saved prompt text, attachment names, references and locations without loading attachment payloads
  ///
  /// In en, this message translates to:
  /// **'Search saved prompts'**
  String get promptStashSearch;

  /// Filtered saved-prompts empty state, distinct from an empty stash
  ///
  /// In en, this message translates to:
  /// **'No saved prompts match your search. Clear or change the search to see more.'**
  String get promptStashNoMatches;

  /// No description provided for @promptStashDeleteFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not delete this saved prompt. Try again.'**
  String get promptStashDeleteFailed;

  /// No description provided for @promptRestoreTitle.
  ///
  /// In en, this message translates to:
  /// **'Restore saved prompt?'**
  String get promptRestoreTitle;

  /// No description provided for @promptRestorePreserve.
  ///
  /// In en, this message translates to:
  /// **'Your current prompt will be saved to the stash first, including its attachments and references.'**
  String get promptRestorePreserve;

  /// No description provided for @promptStashDelete.
  ///
  /// In en, this message translates to:
  /// **'Delete'**
  String get promptStashDelete;

  /// No description provided for @promptStashFull.
  ///
  /// In en, this message translates to:
  /// **'Your stash has 50 prompts. Delete a saved prompt to make room; your current prompt is unchanged.'**
  String get promptStashFull;

  /// No description provided for @promptStashListDescription.
  ///
  /// In en, this message translates to:
  /// **'Saved on this device for this server. Restoring a prompt also saves any current prompt for later.'**
  String get promptStashListDescription;

  /// No description provided for @promptStashDeleteTitle.
  ///
  /// In en, this message translates to:
  /// **'Delete saved prompt?'**
  String get promptStashDeleteTitle;

  /// No description provided for @promptStashAttachments.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 attachment} other{{count} attachments}}'**
  String promptStashAttachments(int count);

  /// No description provided for @promptStashReferences.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 reference} other{{count} references}}'**
  String promptStashReferences(int count);

  /// No description provided for @promptRestoredCopyKept.
  ///
  /// In en, this message translates to:
  /// **'Available content restored. A saved copy remains in your stash. Review attachments and references before sending.'**
  String get promptRestoredCopyKept;

  /// No description provided for @promptAttachmentsUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Some attachments cannot be restored'**
  String get promptAttachmentsUnavailable;

  /// No description provided for @promptRestore.
  ///
  /// In en, this message translates to:
  /// **'Restore'**
  String get promptRestore;

  /// No description provided for @promptHistorySaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Prompt sent, but its history could not be saved on this device.'**
  String get promptHistorySaveFailed;

  /// No description provided for @promptAttachmentsUnavailableDetail.
  ///
  /// In en, this message translates to:
  /// **'Missing, damaged or temporary attachments: {names}. Restore the available content and reattach these files before sending. The saved copy will stay in your stash.'**
  String promptAttachmentsUnavailableDetail(String names);

  /// No description provided for @promptStashMigrationPending.
  ///
  /// In en, this message translates to:
  /// **'Some saved attachments could not be moved to local attachment storage yet. Your saved content has been kept. Free device storage and retry.'**
  String get promptStashMigrationPending;

  /// No description provided for @commonRetry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get commonRetry;

  /// No description provided for @shareWaitingForServer.
  ///
  /// In en, this message translates to:
  /// **'Connect to a server and the shared text opens in a new session.'**
  String get shareWaitingForServer;

  /// No description provided for @shareSessionFailed.
  ///
  /// In en, this message translates to:
  /// **'Shared text kept. Could not open a session. Retry when the connection is ready.'**
  String get shareSessionFailed;

  /// No description provided for @webSourcesDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Web search is not available through this connection’s app gateway. Paste a public URL and optionally an excerpt you want to include. No page is fetched. Nothing is sent to the model here.'**
  String get webSourcesDisclosure;

  /// No description provided for @webSourcesScopeChanged.
  ///
  /// In en, this message translates to:
  /// **'Connection changed. Close and reopen Add web source.'**
  String get webSourcesScopeChanged;

  /// No description provided for @webSourcesUrl.
  ///
  /// In en, this message translates to:
  /// **'Public URL'**
  String get webSourcesUrl;

  /// No description provided for @webSourcesLabel.
  ///
  /// In en, this message translates to:
  /// **'Title (optional)'**
  String get webSourcesLabel;

  /// No description provided for @webSourcesExcerpt.
  ///
  /// In en, this message translates to:
  /// **'Pasted excerpt (optional)'**
  String get webSourcesExcerpt;

  /// No description provided for @webSourcesExcerptHint.
  ///
  /// In en, this message translates to:
  /// **'User-provided text, not verified page content.'**
  String get webSourcesExcerptHint;

  /// No description provided for @webSourcesAdd.
  ///
  /// In en, this message translates to:
  /// **'Add to review'**
  String get webSourcesAdd;

  /// No description provided for @webSourcesReviewCount.
  ///
  /// In en, this message translates to:
  /// **'Review sources ({count}/10)'**
  String webSourcesReviewCount(int count);

  /// No description provided for @webSourcesReviewHint.
  ///
  /// In en, this message translates to:
  /// **'Only checked sources will be returned to your draft.'**
  String get webSourcesReviewHint;

  /// No description provided for @webSourcesEmpty.
  ///
  /// In en, this message translates to:
  /// **'No sources added yet.'**
  String get webSourcesEmpty;

  /// No description provided for @webSourcesOpen.
  ///
  /// In en, this message translates to:
  /// **'Open in browser'**
  String get webSourcesOpen;

  /// No description provided for @webSourcesUseCount.
  ///
  /// In en, this message translates to:
  /// **'Use selected sources ({count})'**
  String webSourcesUseCount(int count);

  /// No description provided for @digestTitle.
  ///
  /// In en, this message translates to:
  /// **'Completion digests'**
  String get digestTitle;

  /// No description provided for @digestSubtitle.
  ///
  /// In en, this message translates to:
  /// **'On demand · cached metadata, not AI summaries'**
  String get digestSubtitle;

  /// No description provided for @digestEmpty.
  ///
  /// In en, this message translates to:
  /// **'No ended-run metadata available in this location. Idle alone does not establish successful completion.'**
  String get digestEmpty;

  /// No description provided for @digestIdle.
  ///
  /// In en, this message translates to:
  /// **'Server idle recorded · outcome unverified'**
  String get digestIdle;

  /// Completion digest status; idle does not prove a successful run
  ///
  /// In en, this message translates to:
  /// **'Server reported idle. Success or failure is not verified.'**
  String get digestStatusUnverified;

  /// The server did not provide a session-wide changed-file total
  ///
  /// In en, this message translates to:
  /// **'Changed files: unknown.'**
  String get digestChangedFilesUnknown;

  /// Session-wide changed-file total; it is not evidence for this run
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0 {No changed files in the session total; this run is unknown.} one {1 changed file in the session total; this run is unknown.} other {{count} changed files in the session total; this run is unknown.}}'**
  String digestChangedFiles(int count);

  /// The pending-request snapshot is unavailable
  ///
  /// In en, this message translates to:
  /// **'Pending decisions: unknown.'**
  String get digestPendingDecisionsUnknown;

  /// Known pending-request count from the current cache
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =0 {No pending decisions in the current cache.} one {1 pending decision in the current cache.} other {{count} pending decisions in the current cache.}}'**
  String digestPendingDecisions(int count);

  /// The metadata-only digest does not report tool outcomes or remaining tasks
  ///
  /// In en, this message translates to:
  /// **'Tool outcomes and remaining tasks: unknown.'**
  String get digestOutcomesUnknown;

  /// Provenance disclosure for a metadata-only completion digest
  ///
  /// In en, this message translates to:
  /// **'Cached server metadata only. No AI summary or model call. Open the conversation to verify results and review changes or tasks.'**
  String get digestProvenance;

  /// No description provided for @digestOpenConversation.
  ///
  /// In en, this message translates to:
  /// **'Open conversation'**
  String get digestOpenConversation;

  /// No description provided for @digestReview.
  ///
  /// In en, this message translates to:
  /// **'Review next actions'**
  String get digestReview;

  /// No description provided for @digestCopy.
  ///
  /// In en, this message translates to:
  /// **'Copy digest'**
  String get digestCopy;

  /// Accessible confirmation after a completion digest is copied
  ///
  /// In en, this message translates to:
  /// **'Digest copied'**
  String get digestCopySucceeded;

  /// No description provided for @digestCopyFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not copy digest'**
  String get digestCopyFailed;

  /// No description provided for @digestDismiss.
  ///
  /// In en, this message translates to:
  /// **'Dismiss'**
  String get digestDismiss;

  /// No description provided for @attentionDisclosure.
  ///
  /// In en, this message translates to:
  /// **'A local overview, not live monitoring across servers. Cached signals may be incomplete or out of date. Open a server to check its current activity.'**
  String get attentionDisclosure;

  /// No description provided for @attentionNavigationUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Opening servers is unavailable here. Return to Home to choose a server and view Activity.'**
  String get attentionNavigationUnavailable;

  /// No description provided for @handoffTitle.
  ///
  /// In en, this message translates to:
  /// **'Copy handoff reference?'**
  String get handoffTitle;

  /// No description provided for @handoffDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Metadata only, not a command or link. On your other device, connect to the same server and locate this project and session. Nothing is published or sent.\n\nThe clipboard will contain session and project identifiers. Other apps may read it; share only with people you trust.'**
  String get handoffDisclosure;

  /// No description provided for @handoffCopy.
  ///
  /// In en, this message translates to:
  /// **'Copy reference'**
  String get handoffCopy;

  /// No description provided for @handoffCopied.
  ///
  /// In en, this message translates to:
  /// **'Session metadata reference copied'**
  String get handoffCopied;

  /// No description provided for @handoffCopyFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not copy the handoff. Try again.'**
  String get handoffCopyFailed;

  /// No description provided for @sessionOpenRelated.
  ///
  /// In en, this message translates to:
  /// **'Open related'**
  String get sessionOpenRelated;

  /// No description provided for @sessionCopyHandoff.
  ///
  /// In en, this message translates to:
  /// **'Copy handoff'**
  String get sessionCopyHandoff;

  /// No description provided for @sessionActions.
  ///
  /// In en, this message translates to:
  /// **'Session actions'**
  String get sessionActions;

  /// No description provided for @attentionTitle.
  ///
  /// In en, this message translates to:
  /// **'Server attention'**
  String get attentionTitle;

  /// No description provided for @webSourcesTitle.
  ///
  /// In en, this message translates to:
  /// **'Add web source'**
  String get webSourcesTitle;

  /// No description provided for @webSourcesEntryDetail.
  ///
  /// In en, this message translates to:
  /// **'Search when available, or paste links and excerpts to review before adding them to your draft'**
  String get webSourcesEntryDetail;

  /// No description provided for @webSourcesDraftChanged.
  ///
  /// In en, this message translates to:
  /// **'The draft or connection changed. Your current draft was kept; reopen Add web source to try again.'**
  String get webSourcesDraftChanged;

  /// No description provided for @webSourcesDraftLabel.
  ///
  /// In en, this message translates to:
  /// **'User-selected web sources (unverified; excerpts are untrusted source material):'**
  String get webSourcesDraftLabel;

  /// No description provided for @usageScopedTotals.
  ///
  /// In en, this message translates to:
  /// **'Totals for the selected report scope'**
  String get usageScopedTotals;

  /// No description provided for @usageInspectionDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Filters inspect this server\'s returned model records. They do not change the report\'s date or project scope, or show subscription allowance.'**
  String get usageInspectionDisclosure;

  /// No description provided for @usageProviderFilter.
  ///
  /// In en, this message translates to:
  /// **'Provider'**
  String get usageProviderFilter;

  /// No description provided for @usageAllProviders.
  ///
  /// In en, this message translates to:
  /// **'All providers'**
  String get usageAllProviders;

  /// No description provided for @usageSearchRecords.
  ///
  /// In en, this message translates to:
  /// **'Search providers, models or variants'**
  String get usageSearchRecords;

  /// No description provided for @usageClearFilters.
  ///
  /// In en, this message translates to:
  /// **'Clear filters'**
  String get usageClearFilters;

  /// No description provided for @usageScopedProviderTotals.
  ///
  /// In en, this message translates to:
  /// **'Provider cards show their totals for the selected report scope, not just matching model rows.'**
  String get usageScopedProviderTotals;

  /// No description provided for @usageMatchingSubtotal.
  ///
  /// In en, this message translates to:
  /// **'Matching model subtotal'**
  String get usageMatchingSubtotal;

  /// Count of matched model/variant records, not distinct models
  ///
  /// In en, this message translates to:
  /// **'{count} matching records'**
  String usageMatchingRecords(String count);

  /// No description provided for @usageNoMatchingRecords.
  ///
  /// In en, this message translates to:
  /// **'No records match these filters. Clear or change the filters to see more.'**
  String get usageNoMatchingRecords;

  /// Recovery card for a saved sign-in attempt
  ///
  /// In en, this message translates to:
  /// **'Pending sign-in: {integration}'**
  String pendingAuthTitle(String integration);

  /// No description provided for @pendingAuthDetail.
  ///
  /// In en, this message translates to:
  /// **'Continue the existing browser sign-in, then explicitly check its status or enter its code. The browser link is not saved.'**
  String get pendingAuthDetail;

  /// No description provided for @pendingAuthResume.
  ///
  /// In en, this message translates to:
  /// **'Resume / check status'**
  String get pendingAuthResume;

  /// No description provided for @pendingAuthEnterCode.
  ///
  /// In en, this message translates to:
  /// **'Enter code'**
  String get pendingAuthEnterCode;

  /// No description provided for @pendingAuthComplete.
  ///
  /// In en, this message translates to:
  /// **'Sign-in complete.'**
  String get pendingAuthComplete;

  /// No description provided for @pendingAuthStillPending.
  ///
  /// In en, this message translates to:
  /// **'Sign-in is still pending. No new attempt was started.'**
  String get pendingAuthStillPending;

  /// No description provided for @pendingAuthServerFailed.
  ///
  /// In en, this message translates to:
  /// **'The server reported that sign-in failed. Provider error details are hidden.'**
  String get pendingAuthServerFailed;

  /// No description provided for @pendingAuthExpired.
  ///
  /// In en, this message translates to:
  /// **'This attempt is expired or outside the device’s recovery window. Cancellation is a separate server action.'**
  String get pendingAuthExpired;

  /// No description provided for @pendingAuthFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not confirm the action. Check pending sign-ins before trying again. No new sign-in was started.'**
  String get pendingAuthFailed;

  /// No description provided for @pendingAuthSaveUncertain.
  ///
  /// In en, this message translates to:
  /// **'Recovery could not be saved reliably. Keep this app open and retry saving; restarting may lose this attempt. If no browser page opened, cancel the attempt before starting again.'**
  String get pendingAuthSaveUncertain;

  /// No description provided for @pendingAuthRetrySave.
  ///
  /// In en, this message translates to:
  /// **'Retry saving recovery'**
  String get pendingAuthRetrySave;

  /// No description provided for @pendingAuthForget.
  ///
  /// In en, this message translates to:
  /// **'Forget on this device'**
  String get pendingAuthForget;

  /// No description provided for @pendingAuthForgetDetail.
  ///
  /// In en, this message translates to:
  /// **'Remove only this device’s recovery record? This does not cancel a server command, revoke credentials, or finish authorization. The server attempt may keep running until it expires.'**
  String get pendingAuthForgetDetail;

  /// No description provided for @pendingAuthUnsupported.
  ///
  /// In en, this message translates to:
  /// **'This connection cannot recover earlier sign-ins. Legacy sign-ins work only while their original screen and connection remain available.'**
  String get pendingAuthUnsupported;

  /// No description provided for @pendingAuthOtherSource.
  ///
  /// In en, this message translates to:
  /// **'Other pending sign-ins belong to another server origin or location. Return to their original source to manage them.'**
  String get pendingAuthOtherSource;

  /// No description provided for @connectionHelpTitle.
  ///
  /// In en, this message translates to:
  /// **'Connection help'**
  String get connectionHelpTitle;

  /// No description provided for @connectionHelpEntrySubtitle.
  ///
  /// In en, this message translates to:
  /// **'Explain an address locally, without connecting'**
  String get connectionHelpEntrySubtitle;

  /// No description provided for @connectionHelpGuideTip.
  ///
  /// In en, this message translates to:
  /// **'Keep the server off the public internet. Use private HTTPS or an encrypted tunnel ending on the device running this app. Localhost on your computer is not localhost on your phone. Open Connection help above for steps and examples.'**
  String get connectionHelpGuideTip;

  /// No description provided for @connectionHelpPrivacy.
  ///
  /// In en, this message translates to:
  /// **'This checks address rules only, not connectivity. Nothing is sent or saved. Input is hidden and cleared after checking. Paste only an address, not a password or pairing code.'**
  String get connectionHelpPrivacy;

  /// No description provided for @connectionHelpAddress.
  ///
  /// In en, this message translates to:
  /// **'Server address'**
  String get connectionHelpAddress;

  /// No description provided for @connectionHelpCheck.
  ///
  /// In en, this message translates to:
  /// **'Explain address'**
  String get connectionHelpCheck;

  /// No description provided for @connectionHelpEmpty.
  ///
  /// In en, this message translates to:
  /// **'Enter a server address to explain.'**
  String get connectionHelpEmpty;

  /// No description provided for @connectionHelpMalformed.
  ///
  /// In en, this message translates to:
  /// **'This address could not be understood. Use a complete origin such as https://server.example, with no path, credentials or query.'**
  String get connectionHelpMalformed;

  /// No description provided for @connectionHelpCredentials.
  ///
  /// In en, this message translates to:
  /// **'Credentials do not belong in a URL. Remove them and enter the server username and password separately in Servers. The pasted value has been cleared.'**
  String get connectionHelpCredentials;

  /// No description provided for @connectionHelpQuery.
  ///
  /// In en, this message translates to:
  /// **'Remove query parameters and fragments. They can contain secrets; enter only the server origin. The pasted value has been cleared.'**
  String get connectionHelpQuery;

  /// No description provided for @connectionHelpPath.
  ///
  /// In en, this message translates to:
  /// **'Remove the path. This app needs the server origin, not a page or API route.'**
  String get connectionHelpPath;

  /// No description provided for @connectionHelpScheme.
  ///
  /// In en, this message translates to:
  /// **'Use HTTPS for a remote server, or HTTP only for this device\'s supported loopback addresses.'**
  String get connectionHelpScheme;

  /// No description provided for @connectionHelpRemoteHttp.
  ///
  /// In en, this message translates to:
  /// **'Remote HTTP is blocked, including LAN and 100.64.0.0/10 addresses. A VPN does not change this rule. Set up private HTTPS or an encrypted tunnel ending on this device.'**
  String get connectionHelpRemoteHttp;

  /// No description provided for @connectionHelpHttps.
  ///
  /// In en, this message translates to:
  /// **'This address passes the HTTPS address rules. That does not verify its certificate, reachability, sign-in or privacy. A bare remote address is interpreted as HTTPS.'**
  String get connectionHelpHttps;

  /// No description provided for @connectionHelpLoopback.
  ///
  /// In en, this message translates to:
  /// **'This address passes the loopback address rules. Localhost means this device, not another computer. A server or tunnel must be listening here; this check does not verify that.'**
  String get connectionHelpLoopback;

  /// No description provided for @connectionHelpPrivateTitle.
  ///
  /// In en, this message translates to:
  /// **'Private HTTPS or reverse proxy'**
  String get connectionHelpPrivateTitle;

  /// No description provided for @connectionHelpPrivateSteps.
  ///
  /// In en, this message translates to:
  /// **'1. Keep the server on its host\'s loopback with authentication enabled.\n2. Connect both devices to your private network and restrict access to intended users.\n3. Configure private HTTPS, such as Tailscale Serve, or a reverse proxy with a trusted certificate forwarding to the server. Support streaming and WebSockets.\n4. Add the HTTPS origin in Servers with sign-in in separate fields.\nTailscale Funnel exposes the service publicly; it is not a private-network fix. This app cannot infer VPN presence. The example below is a placeholder.'**
  String get connectionHelpPrivateSteps;

  /// No description provided for @connectionHelpTunnelTitle.
  ///
  /// In en, this message translates to:
  /// **'Localhost on the wrong device?'**
  String get connectionHelpTunnelTitle;

  /// No description provided for @connectionHelpTunnelSteps.
  ///
  /// In en, this message translates to:
  /// **'Localhost, 127.0.0.1 and [::1] refer to the device running this app. For a server on another computer, use private HTTPS or an encrypted tunnel ending here. If an SSH client is available on this device, adapt the example below, verify the host key and keep it running. Replace user@host with your SSH destination. Running it on another computer does not forward this device\'s port. Keep server authentication enabled.'**
  String get connectionHelpTunnelSteps;

  /// No description provided for @connectionHelpVerifyTitle.
  ///
  /// In en, this message translates to:
  /// **'Verify connectivity separately'**
  String get connectionHelpVerifyTitle;

  /// No description provided for @connectionHelpVerifySteps.
  ///
  /// In en, this message translates to:
  /// **'On this device, check private-network membership, DNS, firewall access and certificate trust using your network tools. Check server and proxy configuration on the host, then use Servers to connect. Never disable TLS verification or share passwords, pairing codes or unredacted logs. Access to this server is shell access.'**
  String get connectionHelpVerifySteps;

  /// No description provided for @connectionHelpCopyExample.
  ///
  /// In en, this message translates to:
  /// **'Copy example'**
  String get connectionHelpCopyExample;

  /// No description provided for @connectionHelpCopied.
  ///
  /// In en, this message translates to:
  /// **'Example copied'**
  String get connectionHelpCopied;

  /// No description provided for @connectionHelpCopyFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not copy the example. Select the example text to copy it manually.'**
  String get connectionHelpCopyFailed;

  /// No description provided for @voiceConversationTitle.
  ///
  /// In en, this message translates to:
  /// **'Voice conversation'**
  String get voiceConversationTitle;

  /// No description provided for @voiceConversationDescription.
  ///
  /// In en, this message translates to:
  /// **'Listen, review, then Send. No automatic listening or reading.'**
  String get voiceConversationDescription;

  /// No description provided for @voiceConversationPausedTitle.
  ///
  /// In en, this message translates to:
  /// **'Voice conversation paused'**
  String get voiceConversationPausedTitle;

  /// No description provided for @voiceConversationPausedDetail.
  ///
  /// In en, this message translates to:
  /// **'Voice conversation is paused. Reconnect, wait for the reply, or review pending decisions on screen.'**
  String get voiceConversationPausedDetail;

  /// No description provided for @voiceConversationDraftFirst.
  ///
  /// In en, this message translates to:
  /// **'Send, save, or clear your current draft before starting voice conversation.'**
  String get voiceConversationDraftFirst;

  /// No description provided for @voiceConversationListen.
  ///
  /// In en, this message translates to:
  /// **'Listen'**
  String get voiceConversationListen;

  /// No description provided for @voiceConversationExit.
  ///
  /// In en, this message translates to:
  /// **'Exit voice mode'**
  String get voiceConversationExit;

  /// No description provided for @voiceConversationCommandsOnly.
  ///
  /// In en, this message translates to:
  /// **'Use the typed composer for slash commands.'**
  String get voiceConversationCommandsOnly;

  /// No description provided for @voiceConversationInterrupted.
  ///
  /// In en, this message translates to:
  /// **'Voice conversation was interrupted. Review before sending again.'**
  String get voiceConversationInterrupted;

  /// No description provided for @voiceReviewExplicitAction.
  ///
  /// In en, this message translates to:
  /// **'Edit before inserting. Sending always requires an explicit action.'**
  String get voiceReviewExplicitAction;

  /// No description provided for @voiceInputInterrupted.
  ///
  /// In en, this message translates to:
  /// **'Voice input was interrupted. Close and start again when ready.'**
  String get voiceInputInterrupted;

  /// No description provided for @voiceInputClose.
  ///
  /// In en, this message translates to:
  /// **'Close voice input'**
  String get voiceInputClose;

  /// No description provided for @voiceInputUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Voice input is unavailable. Check the local model and microphone settings.'**
  String get voiceInputUnavailable;

  /// No description provided for @voiceConversationInstructions.
  ///
  /// In en, this message translates to:
  /// **'Review and insert your transcript, then tap Send in the composer. Choose Read aloud on a reply; nothing is read automatically. Unsent text is discarded when you leave voice mode, the chat, or the app.'**
  String get voiceConversationInstructions;

  /// No description provided for @desktopDropFailedTitle.
  ///
  /// In en, this message translates to:
  /// **'Could not attach dropped files'**
  String get desktopDropFailedTitle;

  /// No description provided for @desktopDropFailedRecovery.
  ///
  /// In en, this message translates to:
  /// **'Check the attachments already added before trying again. You can also use the keyboard to open Add, then Attach file.'**
  String get desktopDropFailedRecovery;

  /// No description provided for @desktopContextMenuShortcutKeys.
  ///
  /// In en, this message translates to:
  /// **'Right click / Shift + F10 / Menu'**
  String get desktopContextMenuShortcutKeys;

  /// Open or resume server-side command sign-in even when the provider already has a connection
  ///
  /// In en, this message translates to:
  /// **'Server sign-in'**
  String get commandAuthManage;

  /// Command authentication runs server-side; do not imply an app shell or browser flow
  ///
  /// In en, this message translates to:
  /// **'Runs the provider\'s sign-in method on your selected server, not on this phone. You may need to finish interactive steps on the server.'**
  String get commandAuthMethodHint;

  /// Explicit consent before executing a server-side authentication method
  ///
  /// In en, this message translates to:
  /// **'Start sign-in on the server?'**
  String get commandAuthConfirmTitle;

  /// Trust boundary of executable provider authentication
  ///
  /// In en, this message translates to:
  /// **'OpenCode will execute this provider\'s declared sign-in method on the selected server. Continue only if you trust that server and provider. The app does not run or copy a shell command on your phone.'**
  String get commandAuthConfirmDetail;

  /// Launch a command authentication attempt after confirmation
  ///
  /// In en, this message translates to:
  /// **'Start server sign-in'**
  String get commandAuthStart;

  /// Pending status without fabricated instructions or automatic cancellation
  ///
  /// In en, this message translates to:
  /// **'Sign-in is pending on the server. Finish any server-side interaction, then check its status. Closing this sheet does not cancel it.'**
  String get commandAuthPending;

  /// Read the pinned command-auth attempt status
  ///
  /// In en, this message translates to:
  /// **'Check status'**
  String get commandAuthCheck;

  /// Cancel the selected command-auth attempt, not all credentials
  ///
  /// In en, this message translates to:
  /// **'Cancel sign-in'**
  String get commandAuthCancel;

  /// Safe failure without raw provider logs or tokens
  ///
  /// In en, this message translates to:
  /// **'Could not complete or confirm server sign-in. Check the existing attempt before starting another.'**
  String get commandAuthFailed;

  /// Terminal status reported by the server, not proof of a particular active credential
  ///
  /// In en, this message translates to:
  /// **'The server reported that sign-in completed. Refresh Providers to see its current connections.'**
  String get commandAuthComplete;

  /// Server-reported terminal expiry
  ///
  /// In en, this message translates to:
  /// **'This sign-in attempt expired. You can start a new attempt.'**
  String get commandAuthExpired;

  /// Reject actions against the wrong provider authentication scope
  ///
  /// In en, this message translates to:
  /// **'The server or project changed. Return to the original location and reopen sign-in to manage its attempt.'**
  String get commandAuthScopeChanged;

  /// Unknown dispatch outcome blocks duplicate executable auth attempts
  ///
  /// In en, this message translates to:
  /// **'The server may have started sign-in, but the app could not safely recover its attempt. Check on the server before retrying; automatic restart is blocked to avoid duplicate processes.'**
  String get commandAuthUncertainStart;

  /// Explicitly read the loaded assistant reply, excluding code and tool details
  ///
  /// In en, this message translates to:
  /// **'Read reply prose'**
  String get readAloudAction;

  /// Visible control that stops speech or cancels pending speech setup
  ///
  /// In en, this message translates to:
  /// **'Stop reading aloud'**
  String get readAloudStop;

  /// Choose another installed voice and read the selected reply
  ///
  /// In en, this message translates to:
  /// **'Read with another voice'**
  String get readAloudOtherVoice;

  /// Picker of installed system voices marked offline
  ///
  /// In en, this message translates to:
  /// **'Choose a reading voice'**
  String get readAloudChooseVoice;

  /// Consent before any system speech engine access
  ///
  /// In en, this message translates to:
  /// **'Use the system speech engine?'**
  String get readAloudConsentTitle;

  /// Discloses external engine access and audible output without promising network isolation
  ///
  /// In en, this message translates to:
  /// **'The loaded reply prose will be sent to your system speech engine. Only voices marked offline are offered, but the engine is separate software and its privacy practices apply. Code blocks and tool details are omitted. Others may hear the audio. Playback stops when this chat is covered or the app goes into the background.'**
  String get readAloudConsentDetail;

  /// Accept speech disclosure and request installed voice metadata
  ///
  /// In en, this message translates to:
  /// **'Choose voice'**
  String get readAloudContinue;

  /// Unsupported platform, without native calls
  ///
  /// In en, this message translates to:
  /// **'Read-aloud is not available on this platform.'**
  String get readAloudUnsupported;

  /// No automatic engine or model installation is performed
  ///
  /// In en, this message translates to:
  /// **'No installed voice marked offline is available. Configure an offline voice in your system speech settings and try again.'**
  String get readAloudNoVoice;

  /// Safe system speech failure without spoken text or raw engine errors
  ///
  /// In en, this message translates to:
  /// **'The speech engine could not read this reply. Try again or choose another voice.'**
  String get readAloudUnavailable;

  /// Bounded speech input is rejected rather than silently truncated
  ///
  /// In en, this message translates to:
  /// **'This reply is too long to read aloud. Choose a shorter reply.'**
  String get readAloudTooLong;

  /// Audio focus or microphone conflict prevents playback
  ///
  /// In en, this message translates to:
  /// **'Speech playback is unavailable while audio capture or another audio interruption is active.'**
  String get readAloudBusy;

  /// Explicit empty prose result without invoking a speech engine
  ///
  /// In en, this message translates to:
  /// **'There is no reply prose to read. Code and tool details are not spoken.'**
  String get readAloudNoProse;

  /// Open individual saved provider credential management
  ///
  /// In en, this message translates to:
  /// **'Manage accounts'**
  String get credentialManage;

  /// Describes the metadata-only credential list
  ///
  /// In en, this message translates to:
  /// **'Only saved account labels are shown. API keys and login tokens stay on your server.'**
  String get credentialMetadataOnly;

  /// Cold start or stream gap cannot establish an active credential
  ///
  /// In en, this message translates to:
  /// **'Active account unknown. The saved-account list does not report which account is active.'**
  String get credentialActiveUnknown;

  /// An explicit nullable credential-switched event reported no active credential
  ///
  /// In en, this message translates to:
  /// **'The server reported no active saved account.'**
  String get credentialNoneActive;

  /// Distinguishes event-confirmed activation from a successful command response
  ///
  /// In en, this message translates to:
  /// **'The Active badge reflects the latest server event.'**
  String get credentialActiveObserved;

  /// Live-region feedback after a valid credential-switched event
  ///
  /// In en, this message translates to:
  /// **'Active account updated from the server.'**
  String get credentialActiveUpdated;

  /// Accepted but unconfirmed activation, without an indefinite spinner or invented badge
  ///
  /// In en, this message translates to:
  /// **'Switch requested. This request has not yet been confirmed by a server event.'**
  String get credentialSwitchRequested;

  /// Server-event-confirmed active saved credential badge
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get credentialActive;

  /// Request activation of one saved provider credential
  ///
  /// In en, this message translates to:
  /// **'Set active'**
  String get credentialSetActive;

  /// Edit a saved credential label, not its secret
  ///
  /// In en, this message translates to:
  /// **'Rename account'**
  String get credentialRename;

  /// Single-line saved credential label input
  ///
  /// In en, this message translates to:
  /// **'Account label'**
  String get credentialLabel;

  /// Submit only the edited credential label
  ///
  /// In en, this message translates to:
  /// **'Save label'**
  String get credentialSave;

  /// Destructive confirmation naming the saved provider credential
  ///
  /// In en, this message translates to:
  /// **'Remove {label}?'**
  String credentialRemoveTitle(String label);

  /// Discloses server-wide credential removal and avoids promising successor activation
  ///
  /// In en, this message translates to:
  /// **'Remove this saved sign-in from the server. Other projects using it may be affected. This does not edit environment configuration; the server determines which account, if any, becomes active afterward.'**
  String get credentialRemoveDetail;

  /// Blocks operations from a previous credential-management scope
  ///
  /// In en, this message translates to:
  /// **'The server or project changed. Close and reopen account management before making changes.'**
  String get credentialScopeChanged;

  /// Fresh integration read no longer contains the selected provider
  ///
  /// In en, this message translates to:
  /// **'This provider is no longer in the server\'s integration list.'**
  String get credentialProviderMissing;

  /// Safe credential metadata refresh failure
  ///
  /// In en, this message translates to:
  /// **'Could not refresh saved accounts. Try again.'**
  String get credentialLoadFailed;

  /// Uncertain credential mutation result without raw server or secret data
  ///
  /// In en, this message translates to:
  /// **'Could not confirm the account change. Refresh before retrying; the server may already have applied it.'**
  String get credentialMutationFailed;

  /// Refetch safe credential metadata, not a guarantee of active-state confirmation
  ///
  /// In en, this message translates to:
  /// **'Refresh accounts'**
  String get credentialRefresh;

  /// Empty credential list without claiming provider disconnection
  ///
  /// In en, this message translates to:
  /// **'No saved accounts were reported for this provider.'**
  String get credentialEmpty;

  /// Environment-backed integration connection is not an editable credential
  ///
  /// In en, this message translates to:
  /// **'Managed by the server environment. It cannot be removed here.'**
  String get credentialEnvironment;

  /// Display-only ordinal for a credential with no label; not a server-reported identity
  ///
  /// In en, this message translates to:
  /// **'Saved account {index}'**
  String credentialUnnamed(int index);

  /// Remove an MCP server from the current runtime location
  ///
  /// In en, this message translates to:
  /// **'Remove'**
  String get mcpRemove;

  /// Confirmation title naming the selected MCP server
  ///
  /// In en, this message translates to:
  /// **'Remove {name}?'**
  String mcpRemoveTitle(String name);

  /// Distinguishes runtime MCP removal from persistent configuration changes
  ///
  /// In en, this message translates to:
  /// **'Remove this MCP server from the current runtime location. Its tools will no longer be available there. This does not erase persistent server configuration; it may return after a server restart.'**
  String get mcpRemoveRuntimeDetail;

  /// Safe feedback for an uncertain removal outcome without raw configuration or server errors
  ///
  /// In en, this message translates to:
  /// **'Could not confirm MCP removal. Refresh the list before trying again; the server may already have applied the change.'**
  String get mcpRemoveFailed;

  /// Generic MCP inventory or resource refresh failure
  ///
  /// In en, this message translates to:
  /// **'Could not refresh MCP data. Try again.'**
  String get mcpLoadFailed;

  /// No description provided for @mcpSavedStatus.
  ///
  /// In en, this message translates to:
  /// **'Saved in OpenCode'**
  String get mcpSavedStatus;

  /// No description provided for @mcpConnectionUnconfirmed.
  ///
  /// In en, this message translates to:
  /// **'App connection not confirmed'**
  String get mcpConnectionUnconfirmed;

  /// No description provided for @mcpRetryReconnect.
  ///
  /// In en, this message translates to:
  /// **'Retry reconnect'**
  String get mcpRetryReconnect;

  /// No description provided for @mcpReconnecting.
  ///
  /// In en, this message translates to:
  /// **'Reconnecting'**
  String get mcpReconnecting;

  /// No description provided for @mcpStillDisconnected.
  ///
  /// In en, this message translates to:
  /// **'OpenCode is still disconnected. Try again.'**
  String get mcpStillDisconnected;

  /// Warns against acting on MCP data from a previous profile or location
  ///
  /// In en, this message translates to:
  /// **'The server or project changed. Refresh to load its MCP servers before making changes.'**
  String get mcpScopeChanged;

  /// No description provided for @promptStashRestoreFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not finish restoring the prompt. Saved copies remain available; check the composer before trying again.'**
  String get promptStashRestoreFailed;

  /// No description provided for @promptStashEmpty.
  ///
  /// In en, this message translates to:
  /// **'Nothing saved yet. Use Stash current prompt in Prompt tools to keep a prompt for later.'**
  String get promptStashEmpty;

  /// No description provided for @promptStashContextOnly.
  ///
  /// In en, this message translates to:
  /// **'Attachments and references'**
  String get promptStashContextOnly;

  /// No description provided for @promptRestoredReferences.
  ///
  /// In en, this message translates to:
  /// **'Prompt restored. Saved references are snapshots; their server files may have changed.'**
  String get promptRestoredReferences;

  /// No description provided for @promptDefaultLocation.
  ///
  /// In en, this message translates to:
  /// **'the server default directory'**
  String get promptDefaultLocation;

  /// No description provided for @promptStashed.
  ///
  /// In en, this message translates to:
  /// **'Prompt saved to your stash.'**
  String get promptStashed;

  /// Stash save succeeded but persisting the cleared or restored composer draft failed; the saved stash remains available
  ///
  /// In en, this message translates to:
  /// **'Prompt saved to your stash. The composer draft still needs to be saved; use Retry in the draft warning.'**
  String get promptStashedDraftPending;

  /// No description provided for @promptStashReadFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not read saved prompts. Their stored data has been kept.'**
  String get promptStashReadFailed;

  /// No description provided for @promptStashDeleteDetail.
  ///
  /// In en, this message translates to:
  /// **'This removes the saved text, attachments and references from this device.'**
  String get promptStashDeleteDetail;

  /// No description provided for @promptStashDescription.
  ///
  /// In en, this message translates to:
  /// **'Save text, attachments and references for later'**
  String get promptStashDescription;

  /// No description provided for @promptRestoreAvailable.
  ///
  /// In en, this message translates to:
  /// **'Restore available content'**
  String get promptRestoreAvailable;

  /// No description provided for @promptRestored.
  ///
  /// In en, this message translates to:
  /// **'Prompt restored. Review it before sending.'**
  String get promptRestored;

  /// No description provided for @promptStashAction.
  ///
  /// In en, this message translates to:
  /// **'Stash current prompt'**
  String get promptStashAction;

  /// No description provided for @promptStashLocation.
  ///
  /// In en, this message translates to:
  /// **'This prompt refers to files in {directory}. Switch to its original project and workspace before restoring it.'**
  String promptStashLocation(String directory);

  /// No description provided for @promptStashScopeChanged.
  ///
  /// In en, this message translates to:
  /// **'The server or location changed. Close and reopen Saved prompts.'**
  String get promptStashScopeChanged;

  /// No description provided for @transcriptFindTitle.
  ///
  /// In en, this message translates to:
  /// **'Find in conversation'**
  String get transcriptFindTitle;

  /// No description provided for @transcriptFindHint.
  ///
  /// In en, this message translates to:
  /// **'Search conversation'**
  String get transcriptFindHint;

  /// No description provided for @transcriptFindScope.
  ///
  /// In en, this message translates to:
  /// **'Messages, reasoning and tool data'**
  String get transcriptFindScope;

  /// No description provided for @transcriptFindClose.
  ///
  /// In en, this message translates to:
  /// **'Close search'**
  String get transcriptFindClose;

  /// No description provided for @transcriptFindPrevious.
  ///
  /// In en, this message translates to:
  /// **'Previous match'**
  String get transcriptFindPrevious;

  /// No description provided for @transcriptFindNext.
  ///
  /// In en, this message translates to:
  /// **'Next match'**
  String get transcriptFindNext;

  /// No description provided for @transcriptFindNone.
  ///
  /// In en, this message translates to:
  /// **'No matches'**
  String get transcriptFindNone;

  /// No description provided for @transcriptFindCount.
  ///
  /// In en, this message translates to:
  /// **'{total, plural, =1{1 match} other{{current} of {total} matches}}'**
  String transcriptFindCount(int current, int total);

  /// No description provided for @transcriptFindTotal.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 match in message text} other{{count} matches in message text}}'**
  String transcriptFindTotal(int count);

  /// No description provided for @transcriptFindPartial.
  ///
  /// In en, this message translates to:
  /// **'Loaded messages only. Load older messages to search further.'**
  String get transcriptFindPartial;

  /// No description provided for @transcriptFindComplete.
  ///
  /// In en, this message translates to:
  /// **'All available message content searched.'**
  String get transcriptFindComplete;

  /// No description provided for @transcriptFindReasoning.
  ///
  /// In en, this message translates to:
  /// **'Reasoning'**
  String get transcriptFindReasoning;

  /// No description provided for @transcriptFindTool.
  ///
  /// In en, this message translates to:
  /// **'Tool data'**
  String get transcriptFindTool;

  /// No description provided for @transcriptFindFile.
  ///
  /// In en, this message translates to:
  /// **'File name'**
  String get transcriptFindFile;

  /// No description provided for @transcriptFindAll.
  ///
  /// In en, this message translates to:
  /// **'Search all history'**
  String get transcriptFindAll;

  /// No description provided for @skillMenu.
  ///
  /// In en, this message translates to:
  /// **'Use a skill'**
  String get skillMenu;

  /// No description provided for @skillUse.
  ///
  /// In en, this message translates to:
  /// **'Add to conversation'**
  String get skillUse;

  /// No description provided for @skillActivationHelp.
  ///
  /// In en, this message translates to:
  /// **'Adds these skill instructions to this conversation. Your unsent draft stays in the composer.'**
  String get skillActivationHelp;

  /// No description provided for @skillRunNow.
  ///
  /// In en, this message translates to:
  /// **'Run agent now'**
  String get skillRunNow;

  /// No description provided for @skillRunHelp.
  ///
  /// In en, this message translates to:
  /// **'Turn off to add the skill without starting another response.'**
  String get skillRunHelp;

  /// No description provided for @skillLocationChanged.
  ///
  /// In en, this message translates to:
  /// **'The connection or project changed. Reopen Skills from the conversation.'**
  String get skillLocationChanged;

  /// No description provided for @skillUnsupported.
  ///
  /// In en, this message translates to:
  /// **'Skill activation is unavailable on this server. You can still preview skills.'**
  String get skillUnsupported;

  /// No description provided for @skillStaged.
  ///
  /// In en, this message translates to:
  /// **'Resolve the staged revert in the conversation before adding a skill.'**
  String get skillStaged;

  /// No description provided for @skillBusy.
  ///
  /// In en, this message translates to:
  /// **'A skill is already being added to this conversation.'**
  String get skillBusy;

  /// No description provided for @skillUncertain.
  ///
  /// In en, this message translates to:
  /// **'The server did not confirm the result. The skill may have been added. Close this sheet and check the conversation before trying again.'**
  String get skillUncertain;

  /// No description provided for @skillApplied.
  ///
  /// In en, this message translates to:
  /// **'Skill added to this conversation.'**
  String get skillApplied;

  /// No description provided for @skillAppliedOriginal.
  ///
  /// In en, this message translates to:
  /// **'Skill added to the original conversation. Close this sheet to return.'**
  String get skillAppliedOriginal;

  /// No description provided for @activeContextTitle.
  ///
  /// In en, this message translates to:
  /// **'Active context'**
  String get activeContextTitle;

  /// No description provided for @activeContextSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Inspect messages after compaction'**
  String get activeContextSubtitle;

  /// No description provided for @activeContextHelp.
  ///
  /// In en, this message translates to:
  /// **'Active messages returned by the server after its latest compaction. Message counts are not token counts.'**
  String get activeContextHelp;

  /// No description provided for @activeContextRefresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh active context'**
  String get activeContextRefresh;

  /// No description provided for @activeContextSearch.
  ///
  /// In en, this message translates to:
  /// **'Search active messages'**
  String get activeContextSearch;

  /// No description provided for @activeContextAll.
  ///
  /// In en, this message translates to:
  /// **'All'**
  String get activeContextAll;

  /// No description provided for @activeContextCount.
  ///
  /// In en, this message translates to:
  /// **'{shown} of {total} messages'**
  String activeContextCount(int shown, int total);

  /// No description provided for @activeContextEmpty.
  ///
  /// In en, this message translates to:
  /// **'The server returned no active context messages.'**
  String get activeContextEmpty;

  /// No description provided for @activeContextNoMatches.
  ///
  /// In en, this message translates to:
  /// **'No active messages match these filters.'**
  String get activeContextNoMatches;

  /// No description provided for @activeContextNoText.
  ///
  /// In en, this message translates to:
  /// **'No supported text content in this entry.'**
  String get activeContextNoText;

  /// No description provided for @activeContextUnsupported.
  ///
  /// In en, this message translates to:
  /// **'Active context inspection is unavailable on this server.'**
  String get activeContextUnsupported;

  /// No description provided for @activeContextChanged.
  ///
  /// In en, this message translates to:
  /// **'The connection, project or conversation changed. Reopen this inspector from the conversation.'**
  String get activeContextChanged;

  /// No description provided for @activeContextInvalid.
  ///
  /// In en, this message translates to:
  /// **'The server returned an invalid context snapshot. Refresh to try again.'**
  String get activeContextInvalid;

  /// No description provided for @activeContextRefreshFailed.
  ///
  /// In en, this message translates to:
  /// **'Showing the previous snapshot. Could not refresh: {error}'**
  String activeContextRefreshFailed(String error);

  /// No description provided for @activeContextContentHelp.
  ///
  /// In en, this message translates to:
  /// **'Snapshot of available message content. Binary attachment bodies, URLs and internal metadata are not displayed. This is not the complete provider request.'**
  String get activeContextContentHelp;

  /// No description provided for @activeContextUser.
  ///
  /// In en, this message translates to:
  /// **'User prompt'**
  String get activeContextUser;

  /// No description provided for @activeContextAssistant.
  ///
  /// In en, this message translates to:
  /// **'Assistant'**
  String get activeContextAssistant;

  /// No description provided for @activeContextSystem.
  ///
  /// In en, this message translates to:
  /// **'System instructions'**
  String get activeContextSystem;

  /// No description provided for @activeContextSynthetic.
  ///
  /// In en, this message translates to:
  /// **'Synthetic message'**
  String get activeContextSynthetic;

  /// No description provided for @activeContextSkill.
  ///
  /// In en, this message translates to:
  /// **'Skill'**
  String get activeContextSkill;

  /// No description provided for @activeContextShell.
  ///
  /// In en, this message translates to:
  /// **'Shell'**
  String get activeContextShell;

  /// No description provided for @activeContextCompaction.
  ///
  /// In en, this message translates to:
  /// **'Compaction'**
  String get activeContextCompaction;

  /// No description provided for @activeContextChange.
  ///
  /// In en, this message translates to:
  /// **'Session change'**
  String get activeContextChange;

  /// No description provided for @activeContextText.
  ///
  /// In en, this message translates to:
  /// **'Text'**
  String get activeContextText;

  /// No description provided for @activeContextToolInput.
  ///
  /// In en, this message translates to:
  /// **'Tool input'**
  String get activeContextToolInput;

  /// No description provided for @activeContextToolOutput.
  ///
  /// In en, this message translates to:
  /// **'Tool output'**
  String get activeContextToolOutput;

  /// No description provided for @activeContextFile.
  ///
  /// In en, this message translates to:
  /// **'File attachment'**
  String get activeContextFile;

  /// No description provided for @activeContextNotice.
  ///
  /// In en, this message translates to:
  /// **'Server notice'**
  String get activeContextNotice;

  /// No description provided for @activeContextPruned.
  ///
  /// In en, this message translates to:
  /// **'Content pruned by the server'**
  String get activeContextPruned;

  /// No description provided for @activeContextTruncated.
  ///
  /// In en, this message translates to:
  /// **'Output truncated by the server'**
  String get activeContextTruncated;

  /// No description provided for @draftSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Draft not saved. Copy your text or retry.'**
  String get draftSaveFailed;

  /// No description provided for @draftStorageFull.
  ///
  /// In en, this message translates to:
  /// **'Draft storage is full. Copy your text before leaving.'**
  String get draftStorageFull;

  /// No description provided for @draftProfileRemoved.
  ///
  /// In en, this message translates to:
  /// **'The original server was removed. Copy your draft to keep it.'**
  String get draftProfileRemoved;

  /// No description provided for @draftRetrySave.
  ///
  /// In en, this message translates to:
  /// **'Retry saving draft'**
  String get draftRetrySave;

  /// No description provided for @draftClearFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not clear the saved draft. Retry before leaving.'**
  String get draftClearFailed;

  /// No description provided for @activeContextTypeCount.
  ///
  /// In en, this message translates to:
  /// **'{type} · {count}'**
  String activeContextTypeCount(String type, int count);

  /// No description provided for @activeContextPartHeading.
  ///
  /// In en, this message translates to:
  /// **'{kind} · {name}'**
  String activeContextPartHeading(String kind, String name);

  /// No description provided for @draftLeaveTitle.
  ///
  /// In en, this message translates to:
  /// **'Draft could not be saved'**
  String get draftLeaveTitle;

  /// No description provided for @draftLeaveMessage.
  ///
  /// In en, this message translates to:
  /// **'Keep editing to copy your text or retry saving. Leaving now may lose your unsaved changes.'**
  String get draftLeaveMessage;

  /// No description provided for @draftLeaveAction.
  ///
  /// In en, this message translates to:
  /// **'Leave without saving'**
  String get draftLeaveAction;

  /// No description provided for @draftKeepEditing.
  ///
  /// In en, this message translates to:
  /// **'Keep editing'**
  String get draftKeepEditing;

  /// No description provided for @draftUnsaved.
  ///
  /// In en, this message translates to:
  /// **'Unsaved'**
  String get draftUnsaved;

  /// No description provided for @draftAttachmentsLocal.
  ///
  /// In en, this message translates to:
  /// **'Attachments save with this draft on this device.'**
  String get draftAttachmentsLocal;

  /// No description provided for @draftAttachmentsFailed.
  ///
  /// In en, this message translates to:
  /// **'Attachments need recovery or could not be saved. Retry before sending.'**
  String get draftAttachmentsFailed;

  /// No description provided for @draftAttachmentRecoveryTitle.
  ///
  /// In en, this message translates to:
  /// **'Some attachments need attention'**
  String get draftAttachmentRecoveryTitle;

  /// No description provided for @draftAttachmentRecoveryDetail.
  ///
  /// In en, this message translates to:
  /// **'These saved attachments are missing, unreadable, or belong to another project: {names}. Use the available attachments and remove these from the draft, or keep the saved draft and retry later.'**
  String draftAttachmentRecoveryDetail(String names);

  /// No description provided for @draftUseAvailableAttachments.
  ///
  /// In en, this message translates to:
  /// **'Use available attachments'**
  String get draftUseAvailableAttachments;

  /// No description provided for @draftKeepSavedAttachments.
  ///
  /// In en, this message translates to:
  /// **'Keep saved draft'**
  String get draftKeepSavedAttachments;

  /// No description provided for @photoLibraryAction.
  ///
  /// In en, this message translates to:
  /// **'Photo library'**
  String get photoLibraryAction;

  /// No description provided for @photoLibraryDescription.
  ///
  /// In en, this message translates to:
  /// **'Choose a photo or screenshot'**
  String get photoLibraryDescription;

  /// No description provided for @photoCameraAction.
  ///
  /// In en, this message translates to:
  /// **'Take photo'**
  String get photoCameraAction;

  /// No description provided for @photoTooLarge.
  ///
  /// In en, this message translates to:
  /// **'Choose a photo smaller than 10 MB.'**
  String get photoTooLarge;

  /// No description provided for @photoStorageFailed.
  ///
  /// In en, this message translates to:
  /// **'The photo could not be saved on this device. Free some space and retry.'**
  String get photoStorageFailed;

  /// No description provided for @photoPendingOther.
  ///
  /// In en, this message translates to:
  /// **'A photo is waiting in its original conversation. Keep it there, or discard it before choosing another photo.'**
  String get photoPendingOther;

  /// No description provided for @photoUnavailable.
  ///
  /// In en, this message translates to:
  /// **'The photo could not be opened. Try adding it again from Photo library or Take photo.'**
  String get photoUnavailable;

  /// No description provided for @photoPermissionDenied.
  ///
  /// In en, this message translates to:
  /// **'Photo access was denied. Allow camera or photo access in Android app settings, then try again.'**
  String get photoPermissionDenied;

  /// No description provided for @photoPendingTitle.
  ///
  /// In en, this message translates to:
  /// **'Pending photo'**
  String get photoPendingTitle;

  /// No description provided for @photoDiscard.
  ///
  /// In en, this message translates to:
  /// **'Discard pending photo'**
  String get photoDiscard;

  /// No description provided for @photoAddToDraft.
  ///
  /// In en, this message translates to:
  /// **'Add recovered photo to draft'**
  String get photoAddToDraft;

  /// No description provided for @photoOtherLocation.
  ///
  /// In en, this message translates to:
  /// **'Return to the photo\'s original server and project before adding it.'**
  String get photoOtherLocation;

  /// No description provided for @photoDraftFull.
  ///
  /// In en, this message translates to:
  /// **'Remove an attachment first. A draft holds up to 5 files and 20 MB in total.'**
  String get photoDraftFull;

  /// No description provided for @legacyDraftsTitle.
  ///
  /// In en, this message translates to:
  /// **'Older drafts'**
  String get legacyDraftsTitle;

  /// No description provided for @legacyDraftsDescription.
  ///
  /// In en, this message translates to:
  /// **'Review drafts saved before server tracking'**
  String get legacyDraftsDescription;

  /// No description provided for @legacyDraftsExplanation.
  ///
  /// In en, this message translates to:
  /// **'These drafts have no recorded server. Review their text before using it in this conversation.'**
  String get legacyDraftsExplanation;

  /// No description provided for @legacyDraftInsertExplanation.
  ///
  /// In en, this message translates to:
  /// **'Insert adds this text after your current draft. The original saved copy stays here until you delete it.'**
  String get legacyDraftInsertExplanation;

  /// No description provided for @legacyDraftTextOnly.
  ///
  /// In en, this message translates to:
  /// **'Only text can be inserted here. Any saved attachments remain with the older draft.'**
  String get legacyDraftTextOnly;

  /// No description provided for @legacyDraftDelete.
  ///
  /// In en, this message translates to:
  /// **'Delete saved copy'**
  String get legacyDraftDelete;

  /// No description provided for @legacyDraftDeleteExplanation.
  ///
  /// In en, this message translates to:
  /// **'Permanently remove this older draft and its saved attachments from this device?'**
  String get legacyDraftDeleteExplanation;

  /// No description provided for @legacyDraftDeleteFailed.
  ///
  /// In en, this message translates to:
  /// **'The draft changed or could not be removed. Reopen it and retry.'**
  String get legacyDraftDeleteFailed;

  /// No description provided for @legacyDraftInsert.
  ///
  /// In en, this message translates to:
  /// **'Insert into draft'**
  String get legacyDraftInsert;

  /// No description provided for @legacyDraftSearch.
  ///
  /// In en, this message translates to:
  /// **'Search older drafts'**
  String get legacyDraftSearch;

  /// No description provided for @legacyDraftsEmpty.
  ///
  /// In en, this message translates to:
  /// **'No older drafts found'**
  String get legacyDraftsEmpty;

  /// No description provided for @legacyDraftLocationChanged.
  ///
  /// In en, this message translates to:
  /// **'The project changed. Reopen Older drafts to choose where to insert the text.'**
  String get legacyDraftLocationChanged;

  /// Read-only subscription quota screen title
  ///
  /// In en, this message translates to:
  /// **'Remaining usage'**
  String get quotaTitle;

  /// Settings row explaining remaining quota requires an optional server extension
  ///
  /// In en, this message translates to:
  /// **'Optional Codex collector · setup required'**
  String get quotaSettingsSummary;

  /// Distinguishes account-wide rate-limit windows from project consumption
  ///
  /// In en, this message translates to:
  /// **'Choose a provider to view its reported account windows. These are separate from OpenCode token usage and cost.'**
  String get quotaDescription;

  /// Label above the explicitly selected server origin
  ///
  /// In en, this message translates to:
  /// **'Collector server'**
  String get quotaSource;

  /// Selected quota source profile and provider heading
  ///
  /// In en, this message translates to:
  /// **'{profile} · {provider}'**
  String quotaSourceTitle(String profile, String provider);

  /// Safe fallback when a quota source origin is unavailable
  ///
  /// In en, this message translates to:
  /// **'No saved server'**
  String get quotaUnknownSource;

  /// A retained quota screen lost its original scope or profile
  ///
  /// In en, this message translates to:
  /// **'The server or project changed, or its local data is being removed. Reopen Remaining usage to review the source again.'**
  String get quotaSourceChanged;

  /// First-visit quota setup heading; no claim of built-in OpenCode support
  ///
  /// In en, this message translates to:
  /// **'An optional collector is required'**
  String get quotaSetupTitle;

  /// Informed consent before sending existing server authentication to an optional same-origin route
  ///
  /// In en, this message translates to:
  /// **'Your server operator must install and protect this route at the same origin as OpenCode. Reading it uses this profile\'s server sign-in. Confirm only if you installed or trust that deployment. Provider tokens stay on the server.'**
  String get quotaSetupDescription;

  /// Explains operator configuration and visit-only consent
  ///
  /// In en, this message translates to:
  /// **'Setup instructions are in tool/quota/README.md in the app repository. This screen does not install services or remember permission after you leave.'**
  String get quotaSetupGuide;

  /// Quota reads are unavailable for missing credentials or an unsafe source
  ///
  /// In en, this message translates to:
  /// **'Use a saved server with a password and HTTPS, or phone loopback. Update its connection settings before checking the collector.'**
  String get quotaSetupNeeded;

  /// Explicit opt-in checkbox; does not install or configure a collector
  ///
  /// In en, this message translates to:
  /// **'I installed and trust this collector on this server.'**
  String get quotaConsent;

  /// Explicit first quota read after informed consent
  ///
  /// In en, this message translates to:
  /// **'Read remaining usage'**
  String get quotaRead;

  /// Manual refresh or retry of the same trusted quota source
  ///
  /// In en, this message translates to:
  /// **'Refresh remaining usage'**
  String get quotaRefresh;

  /// Progress semantics for a quota read
  ///
  /// In en, this message translates to:
  /// **'Reading remaining usage'**
  String get quotaLoading;

  /// Clear this visit's consent and in-memory quota snapshot; no remote mutation
  ///
  /// In en, this message translates to:
  /// **'Stop using this collector'**
  String get quotaForgetConsent;

  /// Collector or proxy authentication failure, distinct from provider reauthentication
  ///
  /// In en, this message translates to:
  /// **'The collector route did not accept this server sign-in. Ask the server operator to check its authentication setup.'**
  String get quotaCollectorAuth;

  /// Optional collector returned a missing route or unsupported method
  ///
  /// In en, this message translates to:
  /// **'The optional collector route is not available on this server. Check its installation and proxy routing.'**
  String get quotaCollectorMissing;

  /// Safe quota network/service failure without raw errors
  ///
  /// In en, this message translates to:
  /// **'Remaining usage could not be refreshed. Check the connection and collector, then retry.'**
  String get quotaUnavailable;

  /// Malformed or incompatible quota response
  ///
  /// In en, this message translates to:
  /// **'The collector returned an unsupported or invalid snapshot. No new allowance is shown.'**
  String get quotaInvalidResponse;

  /// The collector is reachable but lacks an explicitly configured credential source
  ///
  /// In en, this message translates to:
  /// **'The collector has no authorized account source configured. Ask its operator to finish setup.'**
  String get quotaUnconfigured;

  /// Honest first-provider/auth-method limitation
  ///
  /// In en, this message translates to:
  /// **'The selected OAuth login or provider usage route is not supported by this collector.'**
  String get quotaProviderUnsupported;

  /// Provider login expired or was unreadable; not collector Basic authentication failure
  ///
  /// In en, this message translates to:
  /// **'Sign in again using the provider\'s existing login tool on the server. This app does not read or refresh that login.'**
  String get quotaProviderAuth;

  /// A polling rate limit is distinct from an exhausted subscription window
  ///
  /// In en, this message translates to:
  /// **'The provider limited quota checks. Wait before refreshing; this does not prove your coding allowance is exhausted.'**
  String get quotaRateLimited;

  /// Missing or mismatched account identity must not display measurements
  ///
  /// In en, this message translates to:
  /// **'The collector could not verify the selected account. No allowance is shown. Check the login source on the server.'**
  String get quotaAccountUnverified;

  /// Heading for Codex entitlements, not all ChatGPT product allowances
  ///
  /// In en, this message translates to:
  /// **'Codex account windows'**
  String get quotaCodexAccount;

  /// Provider-reported plan label, from a safe allowlist
  ///
  /// In en, this message translates to:
  /// **'Reported plan: {plan}'**
  String quotaPlan(String plan);

  /// Collector snapshot time, formatted in the device locale
  ///
  /// In en, this message translates to:
  /// **'Snapshot checked {time}'**
  String quotaChecked(String time);

  /// An expired, interrupted or failed-refresh snapshot is not live provider truth
  ///
  /// In en, this message translates to:
  /// **'Previous snapshot — refresh to check the latest allowance.'**
  String get quotaStale;

  /// Explicit provider eligibility signal, independent of quota arithmetic
  ///
  /// In en, this message translates to:
  /// **'The provider reports that ordinary Codex use is currently blocked. Window percentages alone do not determine access.'**
  String get quotaUseBlocked;

  /// Unknown allowance; never means zero or unlimited
  ///
  /// In en, this message translates to:
  /// **'Not reported'**
  String get quotaNotReported;

  /// First provider rate-limit window without assuming a five-hour duration
  ///
  /// In en, this message translates to:
  /// **'Primary window'**
  String get quotaPrimaryWindow;

  /// Second provider rate-limit window without assuming a weekly duration
  ///
  /// In en, this message translates to:
  /// **'Secondary window'**
  String get quotaSecondaryWindow;

  /// Safe display name for an additional bounded window
  ///
  /// In en, this message translates to:
  /// **'Usage window {number}'**
  String quotaOtherWindow(int number);

  /// Percentage remaining within one reported provider window
  ///
  /// In en, this message translates to:
  /// **'{percent} remaining'**
  String quotaRemaining(String percent);

  /// Progress-bar semantics label; its numeric value is expressed separately
  ///
  /// In en, this message translates to:
  /// **'{window}: remaining percentage'**
  String quotaWindowRemainingLabel(String window);

  /// Provider-reported percentage used within one window
  ///
  /// In en, this message translates to:
  /// **'{percent} used'**
  String quotaUsed(String percent);

  /// Absolute provider reset time in device locale
  ///
  /// In en, this message translates to:
  /// **'Reported reset: {time}'**
  String quotaResetAt(String time);

  /// Missing provider reset time is not fabricated
  ///
  /// In en, this message translates to:
  /// **'Reset time not reported'**
  String get quotaResetUnknown;

  /// Passing a reset deadline does not invent a new allowance
  ///
  /// In en, this message translates to:
  /// **'Reset time passed — refresh to check. The displayed allowance has not been replenished locally.'**
  String get quotaResetPassed;

  /// Exact whole-day provider window duration
  ///
  /// In en, this message translates to:
  /// **'{count, plural, one{1-day window} other{{count}-day window}}'**
  String quotaDays(int count);

  /// Exact whole-hour provider window duration
  ///
  /// In en, this message translates to:
  /// **'{count, plural, one{1-hour window} other{{count}-hour window}}'**
  String quotaHours(int count);

  /// Exact duration when a provider window is not whole hours or days
  ///
  /// In en, this message translates to:
  /// **'{count, plural, one{1-second window} other{{count}-second window}}'**
  String quotaSeconds(int count);

  /// Honest limits and provenance of optional provider quota collectors
  ///
  /// In en, this message translates to:
  /// **'Read-only snapshot from the optional collector using an internal provider endpoint. Other product allowances, model-specific limits, credits and eligibility are not included. Missing data is unknown, not unlimited.'**
  String get quotaSourceDisclosure;

  /// Codex provider selector
  ///
  /// In en, this message translates to:
  /// **'Codex'**
  String get quotaCodex;

  /// Claude provider selector
  ///
  /// In en, this message translates to:
  /// **'Claude'**
  String get quotaClaude;

  /// Explains the disabled Claude subscription collection path without suggesting an OAuth workaround
  ///
  /// In en, this message translates to:
  /// **'Claude subscription usage is unavailable here pending a supported, permitted integration. Current OpenCode does not include Claude Pro/Max sign-in. This app will not read or reuse that subscription login.'**
  String get quotaClaudeUnavailable;

  /// iOS app identity without describing it as an Android or desktop build
  ///
  /// In en, this message translates to:
  /// **'OpenCode for iOS'**
  String get iosAppTitle;

  /// Truthful initial iOS remote-control scope
  ///
  /// In en, this message translates to:
  /// **'A remote client for the OpenCode server you choose. On-device server hosting and background monitoring are not available in this iOS build.'**
  String get iosRemoteSummary;

  /// iOS credential storage guidance
  ///
  /// In en, this message translates to:
  /// **'Server passwords use this device\'s Keychain. They are not stored in plain profile preferences.'**
  String get iosKeychainGuide;

  /// Platform-neutral storage copy rather than incorrectly promising Linux libsecret everywhere
  ///
  /// In en, this message translates to:
  /// **'Server passwords use this platform\'s secure credential storage. They are not stored in plain profile preferences.'**
  String get platformSecureStorageGuide;

  /// Claude allowances for the operator-selected OAuth login
  ///
  /// In en, this message translates to:
  /// **'Claude login windows'**
  String get quotaClaudeAccount;

  /// Distinguishes credential-bound Claude usage from provider-confirmed account identity
  ///
  /// In en, this message translates to:
  /// **'Tied to the collector\'s configured Claude login. The usage response does not independently identify the account.'**
  String get quotaSourceBound;

  /// Provider grouping within server consumption statistics
  ///
  /// In en, this message translates to:
  /// **'Providers'**
  String get usageProviders;

  /// Limits the meaning of provider-grouped consumption
  ///
  /// In en, this message translates to:
  /// **'Totals from this server\'s returned model records for the selected scope. Not provider billing or subscription allowances.'**
  String get usageProviderScope;

  /// Distinct model IDs within a provider; variants are not counted as new models
  ///
  /// In en, this message translates to:
  /// **'{count, plural, one{1 model} other{{count} models}}'**
  String usageProviderModelCount(int count);

  /// Invalid numeric aggregate is not rendered as a plausible cost
  ///
  /// In en, this message translates to:
  /// **'Cost subtotal unavailable'**
  String get usageProviderCostUnavailable;

  /// Provider subtotal divided by the selected server consumption total, when consistent
  ///
  /// In en, this message translates to:
  /// **'{percent} of reported cost'**
  String usageProviderCostShare(String percent);

  /// No description provided for @setupOutputWaiting.
  ///
  /// In en, this message translates to:
  /// **'Waiting for Termux output…'**
  String get setupOutputWaiting;

  /// No description provided for @setupOutputWaitingDetail.
  ///
  /// In en, this message translates to:
  /// **'Setup messages will appear here when Termux responds.'**
  String get setupOutputWaitingDetail;

  /// No description provided for @setupStartInstalled.
  ///
  /// In en, this message translates to:
  /// **'Start installed OpenCode'**
  String get setupStartInstalled;

  /// No description provided for @setupMissingCredential.
  ///
  /// In en, this message translates to:
  /// **'This app has no saved credential for that installation. Connect with its server address, or run setup to configure it.'**
  String get setupMissingCredential;

  /// No description provided for @setupUbuntuOption.
  ///
  /// In en, this message translates to:
  /// **'Managed Ubuntu installation'**
  String get setupUbuntuOption;

  /// No description provided for @setupOwnOption.
  ///
  /// In en, this message translates to:
  /// **'Use your own setup'**
  String get setupOwnOption;

  /// No description provided for @setupOwnDescription.
  ///
  /// In en, this message translates to:
  /// **'Connect an existing OpenCode 1 or OpenCode 2 server by address. A native musl installation needs a compatible Linux environment and is not managed by this app.'**
  String get setupOwnDescription;

  /// No description provided for @setupConnectExisting.
  ///
  /// In en, this message translates to:
  /// **'Connect existing server'**
  String get setupConnectExisting;

  /// No description provided for @setupScreenTitle.
  ///
  /// In en, this message translates to:
  /// **'On-device setup'**
  String get setupScreenTitle;

  /// No description provided for @setupInstallStart.
  ///
  /// In en, this message translates to:
  /// **'Install & start'**
  String get setupInstallStart;

  /// No description provided for @setupCheckAgain.
  ///
  /// In en, this message translates to:
  /// **'Check again'**
  String get setupCheckAgain;

  /// No description provided for @uncertainAuthTitle.
  ///
  /// In en, this message translates to:
  /// **'Unconfirmed sign-in: {integrationID}'**
  String uncertainAuthTitle(String integrationID);

  /// No description provided for @uncertainAuthDetail.
  ///
  /// In en, this message translates to:
  /// **'The server may have started sign-in, but no attempt ID was received. Check on the server before starting again.'**
  String get uncertainAuthDetail;

  /// No description provided for @uncertainAuthForgetTitle.
  ///
  /// In en, this message translates to:
  /// **'Forget uncertain start?'**
  String get uncertainAuthForgetTitle;

  /// No description provided for @uncertainAuthForgetDetail.
  ///
  /// In en, this message translates to:
  /// **'This clears only the local retry block. It does not cancel sign-in on the server. Check the server first to avoid running a second sign-in. No new sign-in will start.'**
  String get uncertainAuthForgetDetail;

  /// No description provided for @uncertainAuthForget.
  ///
  /// In en, this message translates to:
  /// **'Forget uncertain start'**
  String get uncertainAuthForget;

  /// No description provided for @uncertainAuthCloseHint.
  ///
  /// In en, this message translates to:
  /// **'Close this sheet and use the unconfirmed sign-in row to clear its local retry block after checking the server.'**
  String get uncertainAuthCloseHint;

  /// No description provided for @pluginsTitle.
  ///
  /// In en, this message translates to:
  /// **'Plugins'**
  String get pluginsTitle;

  /// No description provided for @pluginsDescription.
  ///
  /// In en, this message translates to:
  /// **'Plugins reported for this server location. Inspect status and source here; manage plugins on the server.'**
  String get pluginsDescription;

  /// No description provided for @pluginsUnsupported.
  ///
  /// In en, this message translates to:
  /// **'This server does not support plugin inspection.'**
  String get pluginsUnsupported;

  /// No description provided for @pluginsDisconnected.
  ///
  /// In en, this message translates to:
  /// **'Connect to a server to inspect its plugins.'**
  String get pluginsDisconnected;

  /// No description provided for @pluginsEmpty.
  ///
  /// In en, this message translates to:
  /// **'No plugins reported for this location.'**
  String get pluginsEmpty;

  /// No description provided for @pluginsLoadFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not load plugins. Try again.'**
  String get pluginsLoadFailed;

  /// No description provided for @pluginsRefresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh plugins'**
  String get pluginsRefresh;

  /// No description provided for @pluginsRetry.
  ///
  /// In en, this message translates to:
  /// **'Try again'**
  String get pluginsRetry;

  /// No description provided for @pluginsUnnamed.
  ///
  /// In en, this message translates to:
  /// **'Plugin without an ID'**
  String get pluginsUnnamed;

  /// No description provided for @pluginsStatusActive.
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get pluginsStatusActive;

  /// No description provided for @pluginsStatusFailed.
  ///
  /// In en, this message translates to:
  /// **'Failed'**
  String get pluginsStatusFailed;

  /// No description provided for @pluginsStatusUnknown.
  ///
  /// In en, this message translates to:
  /// **'Unknown status'**
  String get pluginsStatusUnknown;

  /// No description provided for @pluginsSourceBuiltin.
  ///
  /// In en, this message translates to:
  /// **'Built in'**
  String get pluginsSourceBuiltin;

  /// No description provided for @pluginsSourcePackage.
  ///
  /// In en, this message translates to:
  /// **'Package'**
  String get pluginsSourcePackage;

  /// No description provided for @pluginsSourceLocal.
  ///
  /// In en, this message translates to:
  /// **'Local file (path hidden)'**
  String get pluginsSourceLocal;

  /// No description provided for @pluginsSourceSdk.
  ///
  /// In en, this message translates to:
  /// **'SDK'**
  String get pluginsSourceSdk;

  /// No description provided for @pluginsSourceUnknown.
  ///
  /// In en, this message translates to:
  /// **'Unknown source'**
  String get pluginsSourceUnknown;

  /// No description provided for @pluginsTerminalUi.
  ///
  /// In en, this message translates to:
  /// **'Terminal UI declared'**
  String get pluginsTerminalUi;

  /// No description provided for @pluginsFailureDetail.
  ///
  /// In en, this message translates to:
  /// **'Failure details are hidden because they may contain credentials.'**
  String get pluginsFailureDetail;

  /// No description provided for @demoReviewChanges.
  ///
  /// In en, this message translates to:
  /// **'Review changes'**
  String get demoReviewChanges;

  /// No description provided for @demoSetUpServer.
  ///
  /// In en, this message translates to:
  /// **'Set up your own server'**
  String get demoSetUpServer;

  /// No description provided for @handoffCommandTitle.
  ///
  /// In en, this message translates to:
  /// **'Continue on computer'**
  String get handoffCommandTitle;

  /// No description provided for @handoffCommandDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Run this command in a POSIX shell on a computer with OpenCode installed and access to this server. Set OPENCODE_SERVER_PASSWORD privately on that computer if the server requires it. The clipboard will contain the server address, username, project directory and session ID, but no password.'**
  String get handoffCommandDisclosure;

  /// No description provided for @handoffCopyCommand.
  ///
  /// In en, this message translates to:
  /// **'Copy command'**
  String get handoffCopyCommand;

  /// No description provided for @handoffCommandCopied.
  ///
  /// In en, this message translates to:
  /// **'Resume command copied'**
  String get handoffCommandCopied;

  /// No description provided for @handoffCommandUnavailable.
  ///
  /// In en, this message translates to:
  /// **'A resume command is unavailable for this connection or workspace. Continuing on another computer needs a supported OpenCode command and a reachable HTTPS server; a localhost address points to each device itself. You can still copy the session metadata below.'**
  String get handoffCommandUnavailable;

  /// No description provided for @quotaMiniMax.
  ///
  /// In en, this message translates to:
  /// **'MiniMax'**
  String get quotaMiniMax;

  /// No description provided for @quotaMiniMaxAccount.
  ///
  /// In en, this message translates to:
  /// **'MiniMax subscription windows'**
  String get quotaMiniMaxAccount;

  /// No description provided for @quotaMiniMaxSourceBound.
  ///
  /// In en, this message translates to:
  /// **'Tied to the collector\'s configured MiniMax Subscription Key. The quota response does not independently identify the account. Only reported general-pool percentages are shown; other limits may apply.'**
  String get quotaMiniMaxSourceBound;

  /// No description provided for @managedHealthTitle.
  ///
  /// In en, this message translates to:
  /// **'On-device server'**
  String get managedHealthTitle;

  /// No description provided for @managedHealthUnchecked.
  ///
  /// In en, this message translates to:
  /// **'Check the server managed by this app in Termux.'**
  String get managedHealthUnchecked;

  /// No description provided for @managedHealthCheck.
  ///
  /// In en, this message translates to:
  /// **'Check status'**
  String get managedHealthCheck;

  /// No description provided for @managedHealthChecking.
  ///
  /// In en, this message translates to:
  /// **'Checking Termux…'**
  String get managedHealthChecking;

  /// No description provided for @managedHealthFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not check Termux. Open setup to check permissions or try again.'**
  String get managedHealthFailed;

  /// No description provided for @managedHealthReady.
  ///
  /// In en, this message translates to:
  /// **'Server process running'**
  String get managedHealthReady;

  /// No description provided for @managedHealthWorking.
  ///
  /// In en, this message translates to:
  /// **'Setup is in progress'**
  String get managedHealthWorking;

  /// No description provided for @managedHealthStopped.
  ///
  /// In en, this message translates to:
  /// **'Server stopped'**
  String get managedHealthStopped;

  /// No description provided for @managedHealthNeedsSetup.
  ///
  /// In en, this message translates to:
  /// **'Setup needs attention'**
  String get managedHealthNeedsSetup;

  /// No description provided for @managedHealthAbsent.
  ///
  /// In en, this message translates to:
  /// **'No managed setup found'**
  String get managedHealthAbsent;

  /// No description provided for @managedHealthUnknown.
  ///
  /// In en, this message translates to:
  /// **'Server state unavailable'**
  String get managedHealthUnknown;

  /// No description provided for @managedHealthManage.
  ///
  /// In en, this message translates to:
  /// **'Open setup controls'**
  String get managedHealthManage;

  /// No description provided for @managedHealthObserved.
  ///
  /// In en, this message translates to:
  /// **'Last checked at {time}. Check again for the current state.'**
  String managedHealthObserved(String time);

  /// No description provided for @managedHealthVersion.
  ///
  /// In en, this message translates to:
  /// **'OpenCode {version}'**
  String managedHealthVersion(String version);

  /// No description provided for @managedHealthUbuntu.
  ///
  /// In en, this message translates to:
  /// **'Runner: Ubuntu'**
  String get managedHealthUbuntu;

  /// No description provided for @managedHealthLifetime.
  ///
  /// In en, this message translates to:
  /// **'Android may stop either app. Keeping the mobile connection alive does not guarantee the Termux server will keep running overnight.'**
  String get managedHealthLifetime;

  /// No description provided for @quotaBudgetTitle.
  ///
  /// In en, this message translates to:
  /// **'Personal alert threshold'**
  String get quotaBudgetTitle;

  /// No description provided for @quotaBudgetDescription.
  ///
  /// In en, this message translates to:
  /// **'Choose a percentage used for this source, account and window. This does not change provider limits.'**
  String get quotaBudgetDescription;

  /// No description provided for @quotaBudgetOff.
  ///
  /// In en, this message translates to:
  /// **'Off'**
  String get quotaBudgetOff;

  /// No description provided for @quotaBudgetPercent.
  ///
  /// In en, this message translates to:
  /// **'{percent}% used'**
  String quotaBudgetPercent(String percent);

  /// No description provided for @quotaBudgetOptIn.
  ///
  /// In en, this message translates to:
  /// **'Show threshold attention'**
  String get quotaBudgetOptIn;

  /// No description provided for @quotaBudgetAttentionScope.
  ///
  /// In en, this message translates to:
  /// **'Only after a fresh read on this page. No background polling or device notifications. A window without a reset time alerts once until you change this rule.'**
  String get quotaBudgetAttentionScope;

  /// No description provided for @quotaBudgetSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save this budget change. Your last saved settings remain in effect.'**
  String get quotaBudgetSaveFailed;

  /// No description provided for @quotaBudgetAttention.
  ///
  /// In en, this message translates to:
  /// **'A personal threshold was reached in the latest provider reading. Review the reported windows below.'**
  String get quotaBudgetAttention;

  /// No description provided for @quotaGlm.
  ///
  /// In en, this message translates to:
  /// **'GLM'**
  String get quotaGlm;

  /// No description provided for @quotaGlmAccount.
  ///
  /// In en, this message translates to:
  /// **'Configured GLM Coding Plan source'**
  String get quotaGlmAccount;

  /// No description provided for @quotaGlmTokenWindow.
  ///
  /// In en, this message translates to:
  /// **'Reported token-plan window'**
  String get quotaGlmTokenWindow;

  /// No description provided for @quotaGlmMcpWindow.
  ///
  /// In en, this message translates to:
  /// **'Reported MCP window'**
  String get quotaGlmMcpWindow;

  /// No description provided for @usageBudgetTitle.
  ///
  /// In en, this message translates to:
  /// **'Personal consumption budgets'**
  String get usageBudgetTitle;

  /// No description provided for @usageBudgetDescription.
  ///
  /// In en, this message translates to:
  /// **'Budgets use all reported consumption for the selected server, project, timezone and date-window start. Model filters do not change them. A new window start needs a new budget. These do not change subscription allowances or stop requests.'**
  String get usageBudgetDescription;

  /// No description provided for @usageBudgetUsd.
  ///
  /// In en, this message translates to:
  /// **'Set USD budget'**
  String get usageBudgetUsd;

  /// No description provided for @usageBudgetTokens.
  ///
  /// In en, this message translates to:
  /// **'Set token budget'**
  String get usageBudgetTokens;

  /// No description provided for @usageBudgetAmount.
  ///
  /// In en, this message translates to:
  /// **'Budget amount'**
  String get usageBudgetAmount;

  /// No description provided for @usageBudgetInvalid.
  ///
  /// In en, this message translates to:
  /// **'Enter a positive finite amount. Token budgets must use whole numbers.'**
  String get usageBudgetInvalid;

  /// No description provided for @usageBudgetRemove.
  ///
  /// In en, this message translates to:
  /// **'Remove budget'**
  String get usageBudgetRemove;

  /// No description provided for @usageBudgetProgress.
  ///
  /// In en, this message translates to:
  /// **'{used} of {limit} {unit}'**
  String usageBudgetProgress(String used, String limit, String unit);

  /// No description provided for @usageBudgetTokenUnit.
  ///
  /// In en, this message translates to:
  /// **'tokens'**
  String get usageBudgetTokenUnit;

  /// No description provided for @usageBudgetReached.
  ///
  /// In en, this message translates to:
  /// **'Personal budget reached in this reading.'**
  String get usageBudgetReached;

  /// No description provided for @usageBudgetPrevious.
  ///
  /// In en, this message translates to:
  /// **'Previous reading reached this budget. Refresh to check current consumption.'**
  String get usageBudgetPrevious;

  /// No description provided for @usageBudgetClearAll.
  ///
  /// In en, this message translates to:
  /// **'Clear saved consumption budgets'**
  String get usageBudgetClearAll;

  /// No description provided for @usageBudgetClearDescription.
  ///
  /// In en, this message translates to:
  /// **'Remove all current and past consumption budgets for this saved server? Provider thresholds are kept.'**
  String get usageBudgetClearDescription;

  /// No description provided for @monitorTitle.
  ///
  /// In en, this message translates to:
  /// **'Saved-server attention'**
  String get monitorTitle;

  /// No description provided for @monitorScope.
  ///
  /// In en, this message translates to:
  /// **'Counts cover each server’s last selected location, not every project on that server.'**
  String get monitorScope;

  /// No description provided for @monitorDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Monitoring is off until you enable it for a server. Checks run about once a minute while this app is open. Background checks run no more often than every five minutes, only while Keep live is already on and Android’s service is running. Android can stop that service; no remaining runtime is promised.'**
  String get monitorDisclosure;

  /// No description provided for @monitorConfigure.
  ///
  /// In en, this message translates to:
  /// **'Monitoring settings'**
  String get monitorConfigure;

  /// No description provided for @monitorRefresh.
  ///
  /// In en, this message translates to:
  /// **'Check monitored servers'**
  String get monitorRefresh;

  /// No description provided for @monitorOptIn.
  ///
  /// In en, this message translates to:
  /// **'Monitor this server'**
  String get monitorOptIn;

  /// No description provided for @monitorOptInDetail.
  ///
  /// In en, this message translates to:
  /// **'Check pending permissions, questions and forms in its last selected location.'**
  String get monitorOptInDetail;

  /// No description provided for @monitorNotifications.
  ///
  /// In en, this message translates to:
  /// **'Notify when attention is needed'**
  String get monitorNotifications;

  /// No description provided for @monitorWifi.
  ///
  /// In en, this message translates to:
  /// **'Wi-Fi only'**
  String get monitorWifi;

  /// No description provided for @monitorWifiDetail.
  ///
  /// In en, this message translates to:
  /// **'Checks pause unless Android reports an active Wi-Fi network. VPN or unavailable network information may pause checks.'**
  String get monitorWifiDetail;

  /// No description provided for @monitorWifiUnsupported.
  ///
  /// In en, this message translates to:
  /// **'Wi-Fi detection is unavailable on this platform.'**
  String get monitorWifiUnsupported;

  /// No description provided for @monitorQuiet.
  ///
  /// In en, this message translates to:
  /// **'Quiet hours'**
  String get monitorQuiet;

  /// No description provided for @monitorQuietDetail.
  ///
  /// In en, this message translates to:
  /// **'Mute attention alerts during these local times. Checks continue.'**
  String get monitorQuietDetail;

  /// No description provided for @monitorQuietStart.
  ///
  /// In en, this message translates to:
  /// **'Quiet hours start'**
  String get monitorQuietStart;

  /// No description provided for @monitorQuietEnd.
  ///
  /// In en, this message translates to:
  /// **'Quiet hours end'**
  String get monitorQuietEnd;

  /// No description provided for @monitorDisabled.
  ///
  /// In en, this message translates to:
  /// **'Not monitored · attention unknown'**
  String get monitorDisabled;

  /// No description provided for @monitorWaiting.
  ///
  /// In en, this message translates to:
  /// **'Waiting for a check · attention unknown'**
  String get monitorWaiting;

  /// No description provided for @monitorChecking.
  ///
  /// In en, this message translates to:
  /// **'Checking · attention unknown'**
  String get monitorChecking;

  /// No description provided for @monitorUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Could not check · attention unknown'**
  String get monitorUnavailable;

  /// No description provided for @monitorWifiRequired.
  ///
  /// In en, this message translates to:
  /// **'Waiting for Wi-Fi · attention unknown'**
  String get monitorWifiRequired;

  /// No description provided for @monitorPaused.
  ///
  /// In en, this message translates to:
  /// **'Paused in background · attention unknown'**
  String get monitorPaused;

  /// No description provided for @monitorCurrent.
  ///
  /// In en, this message translates to:
  /// **'Current observation'**
  String get monitorCurrent;

  /// No description provided for @monitorAllClear.
  ///
  /// In en, this message translates to:
  /// **'No pending requests in the checked location'**
  String get monitorAllClear;

  /// No description provided for @monitorNoServers.
  ///
  /// In en, this message translates to:
  /// **'Add a server to monitor attention.'**
  String get monitorNoServers;

  /// No description provided for @monitorSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save monitoring settings. Try again.'**
  String get monitorSaveFailed;

  /// No description provided for @monitorOpenFailed.
  ///
  /// In en, this message translates to:
  /// **'This request or its server location changed. Refresh the inbox and try again.'**
  String get monitorOpenFailed;

  /// No description provided for @monitorSwitchTitle.
  ///
  /// In en, this message translates to:
  /// **'Switch server to review?'**
  String get monitorSwitchTitle;

  /// No description provided for @monitorSwitchDetail.
  ///
  /// In en, this message translates to:
  /// **'A run is active on the selected server. Switching changes the connection shown in this app; it does not stop that server’s run.'**
  String get monitorSwitchDetail;

  /// No description provided for @monitorSwitch.
  ///
  /// In en, this message translates to:
  /// **'Switch server'**
  String get monitorSwitch;

  /// No description provided for @monitorSession.
  ///
  /// In en, this message translates to:
  /// **'Session'**
  String get monitorSession;

  /// No description provided for @monitorPermission.
  ///
  /// In en, this message translates to:
  /// **'Permission needed'**
  String get monitorPermission;

  /// No description provided for @monitorQuestion.
  ///
  /// In en, this message translates to:
  /// **'Answer needed'**
  String get monitorQuestion;

  /// No description provided for @monitorForm.
  ///
  /// In en, this message translates to:
  /// **'Form response needed'**
  String get monitorForm;

  /// No description provided for @monitorUnknown.
  ///
  /// In en, this message translates to:
  /// **'Unknown'**
  String get monitorUnknown;

  /// No description provided for @monitorLastChecked.
  ///
  /// In en, this message translates to:
  /// **'Last checked'**
  String get monitorLastChecked;

  /// No description provided for @monitorNextCheck.
  ///
  /// In en, this message translates to:
  /// **'Next check'**
  String get monitorNextCheck;

  /// No description provided for @monitorPending.
  ///
  /// In en, this message translates to:
  /// **'Current pending requests'**
  String get monitorPending;

  /// No description provided for @monitorUnknownServers.
  ///
  /// In en, this message translates to:
  /// **'Servers with unknown attention'**
  String get monitorUnknownServers;

  /// Saved-server attention counts in the current inbox
  ///
  /// In en, this message translates to:
  /// **'Current pending requests: {pendingCount}\nServers with unknown attention: {unknownCount}'**
  String monitorPendingSummary(int pendingCount, int unknownCount);

  /// Saved-server request row summary
  ///
  /// In en, this message translates to:
  /// **'{profile} · {kind}\n{lastChecked}: {time}'**
  String monitorRequestSummary(
    String profile,
    String kind,
    String lastChecked,
    String time,
  );

  /// A localized monitor timestamp with its label
  ///
  /// In en, this message translates to:
  /// **'{label}: {time}'**
  String monitorLabeledTime(String label, String time);

  /// No description provided for @monitorSelected.
  ///
  /// In en, this message translates to:
  /// **'Selected location'**
  String get monitorSelected;

  /// No description provided for @monitorNoNotifications.
  ///
  /// In en, this message translates to:
  /// **'Background notifications also require Keep live and notification permission in Background settings.'**
  String get monitorNoNotifications;

  /// No description provided for @quotaBudgetClearAll.
  ///
  /// In en, this message translates to:
  /// **'Clear saved provider thresholds'**
  String get quotaBudgetClearAll;

  /// No description provided for @quotaBudgetClearDescription.
  ///
  /// In en, this message translates to:
  /// **'Remove all provider thresholds and attention settings for this saved server, including previous accounts? Consumption budgets are kept.'**
  String get quotaBudgetClearDescription;

  /// No description provided for @managedStorageSummary.
  ///
  /// In en, this message translates to:
  /// **'Termux storage: {available} GiB free of {total} GiB'**
  String managedStorageSummary(String available, String total);

  /// No description provided for @managedStorageFailed.
  ///
  /// In en, this message translates to:
  /// **'Termux storage could not be checked. Retry Check status.'**
  String get managedStorageFailed;

  /// No description provided for @managedRecoveryTitle.
  ///
  /// In en, this message translates to:
  /// **'Recover a crashed managed server'**
  String get managedRecoveryTitle;

  /// No description provided for @managedRecoveryPolicy.
  ///
  /// In en, this message translates to:
  /// **'Opt in to at most 3 restart attempts, with delays of at least 5, 15 and 45 seconds. Only while this app is in the foreground. No install or update.'**
  String get managedRecoveryPolicy;

  /// No description provided for @managedRecoveryAttempts.
  ///
  /// In en, this message translates to:
  /// **'Attempts used: {attempts} of 3. The limit survives app restarts.'**
  String managedRecoveryAttempts(int attempts);

  /// No description provided for @managedRecoveryExhausted.
  ///
  /// In en, this message translates to:
  /// **'Recovery limit reached. Check the server and start it manually before resetting the retry budget.'**
  String get managedRecoveryExhausted;

  /// No description provided for @managedRecoveryBackground.
  ///
  /// In en, this message translates to:
  /// **'Recovery waits while the app is in the background.'**
  String get managedRecoveryBackground;

  /// No description provided for @managedRecoveryChecking.
  ///
  /// In en, this message translates to:
  /// **'Checking the managed recovery operation…'**
  String get managedRecoveryChecking;

  /// No description provided for @managedRecoveryNext.
  ///
  /// In en, this message translates to:
  /// **'Next recovery attempt no earlier than {time}.'**
  String managedRecoveryNext(String time);

  /// No description provided for @managedRecoveryCheck.
  ///
  /// In en, this message translates to:
  /// **'Check recovery status'**
  String get managedRecoveryCheck;

  /// No description provided for @managedRecoveryReset.
  ///
  /// In en, this message translates to:
  /// **'Reset retry budget'**
  String get managedRecoveryReset;

  /// No description provided for @managedRecoverySaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Recovery settings could not be saved. Retry.'**
  String get managedRecoverySaveFailed;

  /// No description provided for @managedRecoveryRevokeFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save or revoke recovery. Keep this profile and retry before removing it.'**
  String get managedRecoveryRevokeFailed;

  /// No description provided for @managedRecoverySettingsUnreadable.
  ///
  /// In en, this message translates to:
  /// **'Recovery settings could not be read. Check the server before enabling recovery.'**
  String get managedRecoverySettingsUnreadable;

  /// No description provided for @managedRecoveryEnableFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not enable recovery. Start the managed server, then try again.'**
  String get managedRecoveryEnableFailed;

  /// No description provided for @managedRecoveryOwnershipChanged.
  ///
  /// In en, this message translates to:
  /// **'The managed operation changed. Check the server before enabling recovery again.'**
  String get managedRecoveryOwnershipChanged;

  /// No description provided for @managedRecoveryUncertain.
  ///
  /// In en, this message translates to:
  /// **'Recovery paused because Termux did not confirm the result. Check status to continue.'**
  String get managedRecoveryUncertain;

  /// No description provided for @managedRecoveryRetryDisable.
  ///
  /// In en, this message translates to:
  /// **'Retry disabling recovery'**
  String get managedRecoveryRetryDisable;

  /// No description provided for @managedRecoveryStoppedWithCleanupError.
  ///
  /// In en, this message translates to:
  /// **'The local server is stopped. Recovery settings could not be fully cleared; retry disabling recovery in Servers before removing the profile.'**
  String get managedRecoveryStoppedWithCleanupError;

  /// No description provided for @pluginMappingPersonal.
  ///
  /// In en, this message translates to:
  /// **'Your command links · not verified plugin ownership'**
  String get pluginMappingPersonal;

  /// No description provided for @pluginMappingReview.
  ///
  /// In en, this message translates to:
  /// **'Review /{command}'**
  String pluginMappingReview(String command);

  /// No description provided for @pluginMappingManage.
  ///
  /// In en, this message translates to:
  /// **'Link commands'**
  String get pluginMappingManage;

  /// No description provided for @pluginMappingDescription.
  ///
  /// In en, this message translates to:
  /// **'Choose commands you associate with this plugin. These personal links apply only to this server location. Each action opens a review of the chat and arguments before you run it.'**
  String get pluginMappingDescription;

  /// No description provided for @pluginMappingEmpty.
  ///
  /// In en, this message translates to:
  /// **'No server commands are available to link.'**
  String get pluginMappingEmpty;

  /// No description provided for @pluginMappingUnavailable.
  ///
  /// In en, this message translates to:
  /// **'This plugin or command is no longer available here. Refresh and review your links.'**
  String get pluginMappingUnavailable;

  /// No description provided for @pluginMappingLimit.
  ///
  /// In en, this message translates to:
  /// **'Choose up to 16 commands for this plugin.'**
  String get pluginMappingLimit;

  /// No description provided for @pluginMappingSave.
  ///
  /// In en, this message translates to:
  /// **'Save links'**
  String get pluginMappingSave;

  /// No description provided for @pluginMappingSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Links could not be saved. Check that this server location is still selected and try again.'**
  String get pluginMappingSaveFailed;

  /// No description provided for @pluginMappingLoadFailed.
  ///
  /// In en, this message translates to:
  /// **'Commands could not be loaded. Try again when connected.'**
  String get pluginMappingLoadFailed;

  /// No description provided for @mobileTasksDescription.
  ///
  /// In en, this message translates to:
  /// **'Server-reported tasks · mobile view'**
  String get mobileTasksDescription;

  /// No description provided for @mobileTasksUnfinished.
  ///
  /// In en, this message translates to:
  /// **'Show unfinished only'**
  String get mobileTasksUnfinished;

  /// No description provided for @mobileTasksNoUnfinished.
  ///
  /// In en, this message translates to:
  /// **'No unfinished tasks in this list.'**
  String get mobileTasksNoUnfinished;

  /// No description provided for @mobileTaskPending.
  ///
  /// In en, this message translates to:
  /// **'Pending'**
  String get mobileTaskPending;

  /// No description provided for @mobileTaskInProgress.
  ///
  /// In en, this message translates to:
  /// **'In progress'**
  String get mobileTaskInProgress;

  /// No description provided for @mobileTaskCompleted.
  ///
  /// In en, this message translates to:
  /// **'Completed'**
  String get mobileTaskCompleted;

  /// No description provided for @mobileTaskCancelled.
  ///
  /// In en, this message translates to:
  /// **'Cancelled'**
  String get mobileTaskCancelled;

  /// Task card progress caption and progress-bar semantics label: completed tasks out of tracked (non-cancelled) tasks
  ///
  /// In en, this message translates to:
  /// **'{done} of {total} done'**
  String mobileTasksProgress(int done, int total);

  /// Task card button: copies the full server-reported task list as plain text, ignoring the local unfinished-only filter
  ///
  /// In en, this message translates to:
  /// **'Copy all tasks'**
  String get mobileTasksCopyAll;

  /// Snackbar after the task list was placed on the clipboard
  ///
  /// In en, this message translates to:
  /// **'All tasks copied'**
  String get mobileTasksCopied;

  /// Snackbar when the clipboard write fails
  ///
  /// In en, this message translates to:
  /// **'Could not copy the task list.'**
  String get mobileTasksCopyFailed;

  /// No description provided for @mobileTaskPriorityHigh.
  ///
  /// In en, this message translates to:
  /// **'High priority'**
  String get mobileTaskPriorityHigh;

  /// No description provided for @mobileTaskPriorityMedium.
  ///
  /// In en, this message translates to:
  /// **'Medium priority'**
  String get mobileTaskPriorityMedium;

  /// No description provided for @mobileTaskPriorityLow.
  ///
  /// In en, this message translates to:
  /// **'Low priority'**
  String get mobileTaskPriorityLow;

  /// No description provided for @pluginMappingClearAll.
  ///
  /// In en, this message translates to:
  /// **'Clear personal links'**
  String get pluginMappingClearAll;

  /// No description provided for @pluginMappingClearTitle.
  ///
  /// In en, this message translates to:
  /// **'Clear all personal command links?'**
  String get pluginMappingClearTitle;

  /// No description provided for @pluginMappingClearDescription.
  ///
  /// In en, this message translates to:
  /// **'Remove personal plugin-command links for every location in this server profile, including previous locations. Server plugins and commands stay installed.'**
  String get pluginMappingClearDescription;

  /// No description provided for @pluginMappingClearConfirm.
  ///
  /// In en, this message translates to:
  /// **'Clear links'**
  String get pluginMappingClearConfirm;

  /// No description provided for @pluginMappingClearFailed.
  ///
  /// In en, this message translates to:
  /// **'Personal links could not be cleared. Check that this server profile is still selected and try again.'**
  String get pluginMappingClearFailed;

  /// No description provided for @quotaMonitorTitle.
  ///
  /// In en, this message translates to:
  /// **'Quota monitoring'**
  String get quotaMonitorTitle;

  /// No description provided for @quotaMonitorConsentTitle.
  ///
  /// In en, this message translates to:
  /// **'Monitor this provider source?'**
  String get quotaMonitorConsentTitle;

  /// No description provided for @quotaMonitorConsent.
  ///
  /// In en, this message translates to:
  /// **'Allow this app to keep reading the trusted collector for this exact provider account after you leave this page, including after app restart. A cycle checks at most three saved sources, every five minutes in the foreground or fifteen minutes while your existing background service is active. With more than three sources, each source may wait several cycles. Device alerts require the separate switch below and a freshly reported window at or above the selected percentage used. An alert records that past reading; open it to check current usage. Personal page thresholds are separate. No service is started here.'**
  String get quotaMonitorConsent;

  /// No description provided for @quotaMonitorRuntime.
  ///
  /// In en, this message translates to:
  /// **'Sources are checked in rotation, at most three per cycle; larger lists take several cycles. Background reads require the existing live service to be active; Android may stop it. Displayed readings expire when the collector says they do. Device alerts record past threshold readings, not current remaining allowance. This page never switches your active server.'**
  String get quotaMonitorRuntime;

  /// No description provided for @quotaMonitorEmpty.
  ///
  /// In en, this message translates to:
  /// **'No provider sources are monitored. Read Remaining for a trusted collector, then enable monitoring for that source.'**
  String get quotaMonitorEmpty;

  /// No description provided for @quotaMonitorEnable.
  ///
  /// In en, this message translates to:
  /// **'Enable quota monitoring'**
  String get quotaMonitorEnable;

  /// No description provided for @quotaMonitorNotifications.
  ///
  /// In en, this message translates to:
  /// **'Device alerts for reported quota thresholds'**
  String get quotaMonitorNotifications;

  /// No description provided for @quotaMonitorWifi.
  ///
  /// In en, this message translates to:
  /// **'Read only on confirmed Wi-Fi'**
  String get quotaMonitorWifi;

  /// No description provided for @quotaMonitorQuiet.
  ///
  /// In en, this message translates to:
  /// **'Quiet hours: 22:00–08:00 local time'**
  String get quotaMonitorQuiet;

  /// No description provided for @quotaMonitorDisabled.
  ///
  /// In en, this message translates to:
  /// **'Monitoring is off.'**
  String get quotaMonitorDisabled;

  /// No description provided for @quotaMonitorWaiting.
  ///
  /// In en, this message translates to:
  /// **'Waiting for a fresh reading.'**
  String get quotaMonitorWaiting;

  /// No description provided for @quotaMonitorChecking.
  ///
  /// In en, this message translates to:
  /// **'Checking the trusted collector…'**
  String get quotaMonitorChecking;

  /// No description provided for @quotaMonitorCurrent.
  ///
  /// In en, this message translates to:
  /// **'Fresh reading from the consented provider source.'**
  String get quotaMonitorCurrent;

  /// No description provided for @quotaMonitorPaused.
  ///
  /// In en, this message translates to:
  /// **'Monitoring is paused. Open the app or check the existing background service.'**
  String get quotaMonitorPaused;

  /// No description provided for @quotaMonitorWifiRequired.
  ///
  /// In en, this message translates to:
  /// **'Waiting for confirmed Wi-Fi. Unknown network status does not permit a read.'**
  String get quotaMonitorWifiRequired;

  /// No description provided for @quotaMonitorSourceChanged.
  ///
  /// In en, this message translates to:
  /// **'This provider account or source changed, or could not be verified. Open Remaining, read it again and review new consent.'**
  String get quotaMonitorSourceChanged;

  /// No description provided for @quotaMonitorSaveFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not save quota monitoring. A failed disable stays paused in this app; retry before closing the app.'**
  String get quotaMonitorSaveFailed;

  /// No description provided for @quotaMonitorDisable.
  ///
  /// In en, this message translates to:
  /// **'Disable quota monitoring'**
  String get quotaMonitorDisable;

  /// No description provided for @setupChooseServerTitle.
  ///
  /// In en, this message translates to:
  /// **'Choose your server setup'**
  String get setupChooseServerTitle;

  /// No description provided for @setupChooseServerDescription.
  ///
  /// In en, this message translates to:
  /// **'Connect an existing server, or use Termux to run OpenCode on this phone.'**
  String get setupChooseServerDescription;

  /// No description provided for @setupUncheckedTitle.
  ///
  /// In en, this message translates to:
  /// **'Continue without an installation check?'**
  String get setupUncheckedTitle;

  /// No description provided for @setupUncheckedDescription.
  ///
  /// In en, this message translates to:
  /// **'The current installation could not be checked. Continuing may install or update OpenCode 1 in the app-managed Ubuntu environment. Existing Ubuntu files are kept. You can check again or connect by address instead.'**
  String get setupUncheckedDescription;

  /// No description provided for @setupUncheckedContinue.
  ///
  /// In en, this message translates to:
  /// **'Continue with Ubuntu'**
  String get setupUncheckedContinue;

  /// No description provided for @webSearchDisclosure.
  ///
  /// In en, this message translates to:
  /// **'Search sends your query to this server’s selected search provider. Review results before adding them to your editable draft. Nothing is sent to the model here.'**
  String get webSearchDisclosure;

  /// No description provided for @webSearchManual.
  ///
  /// In en, this message translates to:
  /// **'Or paste a source'**
  String get webSearchManual;

  /// No description provided for @webSearchUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Web search is unavailable. Configure a search provider on this server, then refresh providers. You can still paste a source below.'**
  String get webSearchUnavailable;

  /// No description provided for @webSearchAuthentication.
  ///
  /// In en, this message translates to:
  /// **'The server did not authorize web search. Check this connection’s credentials.'**
  String get webSearchAuthentication;

  /// No description provided for @webSearchInvalidResponse.
  ///
  /// In en, this message translates to:
  /// **'The search response did not match this connection or the supported format. Refresh providers or paste a source.'**
  String get webSearchInvalidResponse;

  /// No description provided for @webSearchFailed.
  ///
  /// In en, this message translates to:
  /// **'Web search could not finish. Try again or paste a source.'**
  String get webSearchFailed;

  /// No description provided for @webSearchRefresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh providers'**
  String get webSearchRefresh;

  /// No description provided for @webSearchProvider.
  ///
  /// In en, this message translates to:
  /// **'Search provider'**
  String get webSearchProvider;

  /// No description provided for @webSearchQuery.
  ///
  /// In en, this message translates to:
  /// **'Search query'**
  String get webSearchQuery;

  /// No description provided for @webSearchSubmit.
  ///
  /// In en, this message translates to:
  /// **'Search'**
  String get webSearchSubmit;

  /// No description provided for @webSearchEmpty.
  ///
  /// In en, this message translates to:
  /// **'No usable results for this query.'**
  String get webSearchEmpty;

  /// No description provided for @webSearchOmitted.
  ///
  /// In en, this message translates to:
  /// **'Some results were omitted because their links or excerpts exceeded the review limits.'**
  String get webSearchOmitted;

  /// No description provided for @setupReinstallStart.
  ///
  /// In en, this message translates to:
  /// **'Reinstall & start'**
  String get setupReinstallStart;

  /// No description provided for @setupInstallVersionStart.
  ///
  /// In en, this message translates to:
  /// **'Install {version} & start'**
  String setupInstallVersionStart(String version);

  /// No description provided for @setupReplaceTitle.
  ///
  /// In en, this message translates to:
  /// **'Replace installed OpenCode?'**
  String get setupReplaceTitle;

  /// No description provided for @setupReplaceDescription.
  ///
  /// In en, this message translates to:
  /// **'Replace OpenCode {installedVersion} with {targetVersion} in the managed Ubuntu environment and restart the local server. Existing Ubuntu files are kept.'**
  String setupReplaceDescription(String installedVersion, String targetVersion);

  /// No description provided for @setupInstallRestart.
  ///
  /// In en, this message translates to:
  /// **'Install & restart'**
  String get setupInstallRestart;

  /// No description provided for @queueStorageUnreadable.
  ///
  /// In en, this message translates to:
  /// **'Saved queued prompts could not be read. New prompts cannot be queued until this device data is cleared.'**
  String get queueStorageUnreadable;

  /// No description provided for @queueStorageDiscardUnreadable.
  ///
  /// In en, this message translates to:
  /// **'This permanently deletes the unreadable queued prompts and their attachments from this device. Their contents and count are unknown. Nothing on the server is affected.'**
  String get queueStorageDiscardUnreadable;

  /// No description provided for @filesViewerScopeChanged.
  ///
  /// In en, this message translates to:
  /// **'Connection changed. Close and reopen this file.'**
  String get filesViewerScopeChanged;

  /// No description provided for @filesViewerPathChanged.
  ///
  /// In en, this message translates to:
  /// **'File context changed. Close and reopen this file.'**
  String get filesViewerPathChanged;

  /// No description provided for @queueStorageCountUnknown.
  ///
  /// In en, this message translates to:
  /// **'Saved queued data could not be read. The number of queued prompts is unknown.'**
  String get queueStorageCountUnknown;

  /// No description provided for @codexConnectionVerified.
  ///
  /// In en, this message translates to:
  /// **'Connection verified. Save and connect to continue.'**
  String get codexConnectionVerified;

  /// No description provided for @codexApprovalRecoveryNotice.
  ///
  /// In en, this message translates to:
  /// **'After reconnecting, review any pending approvals on your computer.'**
  String get codexApprovalRecoveryNotice;

  /// No description provided for @connectionTokenRejected.
  ///
  /// In en, this message translates to:
  /// **'The connection token was rejected. Update it to reconnect.'**
  String get connectionTokenRejected;

  /// No description provided for @updateConnectionToken.
  ///
  /// In en, this message translates to:
  /// **'Update token'**
  String get updateConnectionToken;

  /// No description provided for @codexDraftReconnectNotice.
  ///
  /// In en, this message translates to:
  /// **'Review draft stays here; nothing is sent automatically.'**
  String get codexDraftReconnectNotice;

  /// No description provided for @codexTextOnlyPrompt.
  ///
  /// In en, this message translates to:
  /// **'This connection supports text only. Remove attachments before sending.'**
  String get codexTextOnlyPrompt;

  /// No description provided for @codexOfflineDraftSaved.
  ///
  /// In en, this message translates to:
  /// **'Reconnect before sending. Your draft is kept on this device.'**
  String get codexOfflineDraftSaved;

  /// No description provided for @codexReconnectBeforeSending.
  ///
  /// In en, this message translates to:
  /// **'Reconnect before sending.'**
  String get codexReconnectBeforeSending;

  /// No description provided for @connectionTypeLabel.
  ///
  /// In en, this message translates to:
  /// **'CONNECTION TYPE'**
  String get connectionTypeLabel;

  /// No description provided for @openCodeConnectionLabel.
  ///
  /// In en, this message translates to:
  /// **'OpenCode'**
  String get openCodeConnectionLabel;

  /// No description provided for @codexExperimentalLabel.
  ///
  /// In en, this message translates to:
  /// **'Codex (experimental)'**
  String get codexExperimentalLabel;

  /// No description provided for @connectionDisplayName.
  ///
  /// In en, this message translates to:
  /// **'Display name (optional)'**
  String get connectionDisplayName;

  /// No description provided for @connectionDisplayNameHint.
  ///
  /// In en, this message translates to:
  /// **'Defaults to the server host'**
  String get connectionDisplayNameHint;

  /// No description provided for @connectionServerAddress.
  ///
  /// In en, this message translates to:
  /// **'Server address'**
  String get connectionServerAddress;

  /// No description provided for @codexAddressHint.
  ///
  /// In en, this message translates to:
  /// **'wss://codex.example or ws://127.0.0.1:4500'**
  String get codexAddressHint;

  /// No description provided for @codexAddressHelp.
  ///
  /// In en, this message translates to:
  /// **'Use wss:// for remote servers. ws:// is limited to this device.'**
  String get codexAddressHelp;

  /// No description provided for @codexProjectFolder.
  ///
  /// In en, this message translates to:
  /// **'Project folder on server'**
  String get codexProjectFolder;

  /// No description provided for @codexTokenReentry.
  ///
  /// In en, this message translates to:
  /// **'Re-enter connection token'**
  String get codexTokenReentry;

  /// No description provided for @codexTokenLabel.
  ///
  /// In en, this message translates to:
  /// **'Connection token'**
  String get codexTokenLabel;

  /// No description provided for @codexTokenStorageHelp.
  ///
  /// In en, this message translates to:
  /// **'Stored securely on this device and sent only to this Codex server.'**
  String get codexTokenStorageHelp;

  /// No description provided for @codexShowToken.
  ///
  /// In en, this message translates to:
  /// **'Show connection token'**
  String get codexShowToken;

  /// No description provided for @codexHideToken.
  ///
  /// In en, this message translates to:
  /// **'Hide connection token'**
  String get codexHideToken;

  /// No description provided for @codexPasteToken.
  ///
  /// In en, this message translates to:
  /// **'Paste connection token'**
  String get codexPasteToken;

  /// No description provided for @connectionCloseEditor.
  ///
  /// In en, this message translates to:
  /// **'Close server editor'**
  String get connectionCloseEditor;

  /// No description provided for @connectionCredentialUnavailable.
  ///
  /// In en, this message translates to:
  /// **'A saved connection credential can no longer be read. Edit the active server and re-enter it before connecting.'**
  String get connectionCredentialUnavailable;

  /// No description provided for @projectContextTitle.
  ///
  /// In en, this message translates to:
  /// **'Project context'**
  String get projectContextTitle;

  /// No description provided for @projectConfiguredFolder.
  ///
  /// In en, this message translates to:
  /// **'Configured folder'**
  String get projectConfiguredFolder;

  /// No description provided for @termuxGuideTitle.
  ///
  /// In en, this message translates to:
  /// **'Connect Termux once'**
  String get termuxGuideTitle;

  /// No description provided for @termuxGuideIntro.
  ///
  /// In en, this message translates to:
  /// **'We copy the command for you. Here is what to do when Termux opens.'**
  String get termuxGuideIntro;

  /// No description provided for @termuxGuideAutomaticCheck.
  ///
  /// In en, this message translates to:
  /// **'When you return, we will check the connection automatically.'**
  String get termuxGuideAutomaticCheck;

  /// No description provided for @termuxGuideShowCommand.
  ///
  /// In en, this message translates to:
  /// **'Show command'**
  String get termuxGuideShowCommand;

  /// No description provided for @termuxGuideOpening.
  ///
  /// In en, this message translates to:
  /// **'Opening Termux...'**
  String get termuxGuideOpening;

  /// No description provided for @termuxGuideCopyTitle.
  ///
  /// In en, this message translates to:
  /// **'1. Copy & open'**
  String get termuxGuideCopyTitle;

  /// No description provided for @termuxGuideCopyDescription.
  ///
  /// In en, this message translates to:
  /// **'Tap Copy & open Termux above. Allow Android\'s permission request if shown.'**
  String get termuxGuideCopyDescription;

  /// No description provided for @termuxGuidePasteTitle.
  ///
  /// In en, this message translates to:
  /// **'2. Press and hold, then Paste'**
  String get termuxGuidePasteTitle;

  /// No description provided for @termuxGuidePasteDescription.
  ///
  /// In en, this message translates to:
  /// **'In Termux, press and hold near the blinking cursor. Tap Paste in the menu.'**
  String get termuxGuidePasteDescription;

  /// No description provided for @termuxGuideEnterTitle.
  ///
  /// In en, this message translates to:
  /// **'3. Enter, then return'**
  String get termuxGuideEnterTitle;

  /// Keep bridge-unlocked unchanged: it is the literal terminal command output.
  ///
  /// In en, this message translates to:
  /// **'Press the keyboard Enter or return key. When Termux shows bridge-unlocked, switch back to this app.'**
  String get termuxGuideEnterDescription;

  /// No description provided for @termuxGuideCopied.
  ///
  /// In en, this message translates to:
  /// **'Command copied'**
  String get termuxGuideCopied;

  /// No description provided for @termuxGuidePaste.
  ///
  /// In en, this message translates to:
  /// **'Paste'**
  String get termuxGuidePaste;

  /// No description provided for @termuxGuideEnterKey.
  ///
  /// In en, this message translates to:
  /// **'Enter'**
  String get termuxGuideEnterKey;

  /// No description provided for @termuxGuideIllustrationNote.
  ///
  /// In en, this message translates to:
  /// **'Illustrations only. Your keyboard and Paste menu may look different.'**
  String get termuxGuideIllustrationNote;

  /// No description provided for @termuxGuideOpenFailed.
  ///
  /// In en, this message translates to:
  /// **'The command was copied, but Termux could not open. Open Termux yourself or try Copy & open Termux again.'**
  String get termuxGuideOpenFailed;

  /// No description provided for @termuxGuideCopyOpenFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not copy the command or open Termux.'**
  String get termuxGuideCopyOpenFailed;

  /// No description provided for @termuxPermissionDenied.
  ///
  /// In en, this message translates to:
  /// **'Android denied the Termux command permission. Allow it in OpenCode app settings.'**
  String get termuxPermissionDenied;

  /// Snackbar shown once when the New task home-screen shortcut arrives while the saved server is still connecting
  ///
  /// In en, this message translates to:
  /// **'Connecting to the saved server. The new task opens when it is ready.'**
  String get launchShortcutWaiting;

  /// Snackbar shown on the servers screen when the New task shortcut arrives with no saved server selected
  ///
  /// In en, this message translates to:
  /// **'Choose a server, then start a new task.'**
  String get launchShortcutNoServer;

  /// Snackbar shown on the servers screen when the New task shortcut arrives while the saved server needs its password or token entered again
  ///
  /// In en, this message translates to:
  /// **'Enter the credentials for the saved server, then start a new task.'**
  String get launchShortcutReentry;

  /// Snackbar shown on the servers screen when the New task shortcut arrives after the saved server connection failed
  ///
  /// In en, this message translates to:
  /// **'Could not connect to the saved server. Choose or fix a server, then start a new task.'**
  String get launchShortcutConnectionFailed;

  /// Snackbar shown when the New task shortcut reached a connected server but creating the session failed
  ///
  /// In en, this message translates to:
  /// **'Could not start a new task. {error}'**
  String launchShortcutNewTaskFailed(String error);

  /// Queued draft bubble label while the offline flush is dispatching it
  ///
  /// In en, this message translates to:
  /// **'Sending…'**
  String get queuedSending;

  /// Queued draft bubble label for a send that left the device without a confirmed outcome; never resent automatically
  ///
  /// In en, this message translates to:
  /// **'Delivery unconfirmed — review before resending'**
  String get queuedDeliveryUnconfirmed;

  /// Queued draft bubble label for an unconfirmed send that also recorded a transport error
  ///
  /// In en, this message translates to:
  /// **'Delivery unconfirmed: {error}'**
  String queuedDeliveryUnconfirmedWithError(String error);

  /// Tooltip on the queued draft bubble's explicit resend action
  ///
  /// In en, this message translates to:
  /// **'Send again'**
  String get queuedResendTooltip;

  /// Confirmation dialog title before resending an unconfirmed queued draft
  ///
  /// In en, this message translates to:
  /// **'Send this draft again?'**
  String get queuedResendTitle;

  /// Confirmation dialog body before resending an unconfirmed queued draft
  ///
  /// In en, this message translates to:
  /// **'It may already have reached OpenCode. Sending again can duplicate it.'**
  String get queuedResendMessage;

  /// Confirmation dialog affirmative button for resending an unconfirmed queued draft
  ///
  /// In en, this message translates to:
  /// **'Send again'**
  String get queuedResendConfirm;

  /// Cancel label on the resend and discard dialogs for an unconfirmed queued draft; the draft stays queued for review
  ///
  /// In en, this message translates to:
  /// **'Keep for review'**
  String get queuedKeepForReview;

  /// Discard sheet body for a queued draft whose send was never confirmed
  ///
  /// In en, this message translates to:
  /// **'Its earlier send was never confirmed; it may already be in the session.'**
  String get queuedDiscardUnconfirmedMessage;

  /// First-time on-device server runtime selection
  ///
  /// In en, this message translates to:
  /// **'Which OpenCode would you like to use?'**
  String get setupRuntimeTitle;

  /// Existing OpenCode server generation
  ///
  /// In en, this message translates to:
  /// **'OpenCode 1'**
  String get setupRuntimeOne;

  /// Description of the default first-run runtime
  ///
  /// In en, this message translates to:
  /// **'Recommended for the widest feature support in this app.'**
  String get setupRuntimeOneDetail;

  /// Experimental new OpenCode server generation
  ///
  /// In en, this message translates to:
  /// **'OpenCode 2 beta'**
  String get setupRuntimeTwo;

  /// Honest support note for the optional beta runtime
  ///
  /// In en, this message translates to:
  /// **'Try the new server API. Some features are unavailable in this beta.'**
  String get setupRuntimeTwoDetail;

  /// Names the exact runtime and pinned version before installation
  ///
  /// In en, this message translates to:
  /// **'Install {runtime} ({version}) in an app-managed Ubuntu environment. Existing Ubuntu files are reused.'**
  String setupRuntimeInstallDetail(String runtime, String version);

  /// Names the selected runtime and pinned version in the update confirmation
  ///
  /// In en, this message translates to:
  /// **'The app will install {runtime} {version}, restart only the managed local server, and reconnect this profile.'**
  String setupRuntimeUpdateDetail(String runtime, String version);

  /// Connection banner line counting queued drafts whose send was never confirmed
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 draft with an unconfirmed send to review.} other{{count} drafts with an unconfirmed send to review.}}'**
  String queuedBannerReview(int count);

  /// No description provided for @markdownCopyCode.
  ///
  /// In en, this message translates to:
  /// **'Copy code'**
  String get markdownCopyCode;

  /// No description provided for @markdownCopied.
  ///
  /// In en, this message translates to:
  /// **'Code copied'**
  String get markdownCopied;

  /// No description provided for @markdownCopyFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not copy code. Try again.'**
  String get markdownCopyFailed;

  /// No description provided for @markdownCopyRetry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get markdownCopyRetry;

  /// No description provided for @markdownWrapCode.
  ///
  /// In en, this message translates to:
  /// **'Wrap lines'**
  String get markdownWrapCode;

  /// No description provided for @markdownScrollCode.
  ///
  /// In en, this message translates to:
  /// **'Scroll lines'**
  String get markdownScrollCode;

  /// No description provided for @markdownExpandCode.
  ///
  /// In en, this message translates to:
  /// **'Full screen'**
  String get markdownExpandCode;

  /// No description provided for @markdownReaderTitle.
  ///
  /// In en, this message translates to:
  /// **'Code reader'**
  String get markdownReaderTitle;

  /// No description provided for @markdownSnapshot.
  ///
  /// In en, this message translates to:
  /// **'Snapshot of the code when opened. Close and reopen to read later updates.'**
  String get markdownSnapshot;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['en'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'en':
      return AppLocalizationsEn();
  }

  throw FlutterError(
    'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
    'an issue with the localizations generation tool. Please file an issue '
    'on GitHub with a reproducible sample app and the gen-l10n configuration '
    'that was used.',
  );
}

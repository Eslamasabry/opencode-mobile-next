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
  /// **'Add text from this conversation'**
  String get composerReuseSubtitle;

  /// No description provided for @composerReuseDescription.
  ///
  /// In en, this message translates to:
  /// **'Adds text to your draft. Attachments aren’t copied.'**
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

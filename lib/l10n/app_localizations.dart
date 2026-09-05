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

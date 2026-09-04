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

  /// No description provided for @backgroundWorkMove.
  ///
  /// In en, this message translates to:
  /// **'Move running work to background'**
  String get backgroundWorkMove;

  /// No description provided for @backgroundSubagents.
  ///
  /// In en, this message translates to:
  /// **'Background subagents'**
  String get backgroundSubagents;

  /// No description provided for @backgroundWorkMoved.
  ///
  /// In en, this message translates to:
  /// **'Running work moved to background.'**
  String get backgroundWorkMoved;

  /// No description provided for @backgroundSubagentsUnavailable.
  ///
  /// In en, this message translates to:
  /// **'No foreground subagents were available to background.'**
  String get backgroundSubagentsUnavailable;

  /// No description provided for @backgroundWorkUnsupported.
  ///
  /// In en, this message translates to:
  /// **'This OpenCode server cannot move running work to background.'**
  String get backgroundWorkUnsupported;

  /// No description provided for @backgroundWorkRunningCount.
  ///
  /// In en, this message translates to:
  /// **'{count} running'**
  String backgroundWorkRunningCount(int count);

  /// No description provided for @backgroundWorkTitle.
  ///
  /// In en, this message translates to:
  /// **'Running work'**
  String get backgroundWorkTitle;

  /// No description provided for @backgroundWorkEmptyDescription.
  ///
  /// In en, this message translates to:
  /// **'Nothing is running for this session.'**
  String get backgroundWorkEmptyDescription;

  /// No description provided for @backgroundWorkItemCount.
  ///
  /// In en, this message translates to:
  /// **'{count, plural, =1{1 item still running} other{{count} items still running}}'**
  String backgroundWorkItemCount(int count);

  /// No description provided for @backgroundWorkEmpty.
  ///
  /// In en, this message translates to:
  /// **'No running work'**
  String get backgroundWorkEmpty;

  /// No description provided for @backgroundWorkRunning.
  ///
  /// In en, this message translates to:
  /// **'Running'**
  String get backgroundWorkRunning;

  /// No description provided for @backgroundSubagentRunning.
  ///
  /// In en, this message translates to:
  /// **'Subagent - running'**
  String get backgroundSubagentRunning;

  /// No description provided for @backgroundShellRunning.
  ///
  /// In en, this message translates to:
  /// **'Shell - {elapsed}'**
  String backgroundShellRunning(String elapsed);

  /// No description provided for @backgroundShellActions.
  ///
  /// In en, this message translates to:
  /// **'Shell actions'**
  String get backgroundShellActions;

  /// No description provided for @backgroundShellTimeoutMinute.
  ///
  /// In en, this message translates to:
  /// **'Timeout in 1 minute'**
  String get backgroundShellTimeoutMinute;

  /// No description provided for @backgroundShellTimeoutFiveMinutes.
  ///
  /// In en, this message translates to:
  /// **'Timeout in 5 minutes'**
  String get backgroundShellTimeoutFiveMinutes;

  /// No description provided for @backgroundShellTimeoutClear.
  ///
  /// In en, this message translates to:
  /// **'Clear timeout'**
  String get backgroundShellTimeoutClear;

  /// No description provided for @backgroundShellStop.
  ///
  /// In en, this message translates to:
  /// **'Stop command'**
  String get backgroundShellStop;

  /// No description provided for @backgroundShellStopTitle.
  ///
  /// In en, this message translates to:
  /// **'Stop this shell command?'**
  String get backgroundShellStopTitle;

  /// No description provided for @backgroundShellOutput.
  ///
  /// In en, this message translates to:
  /// **'Live output'**
  String get backgroundShellOutput;

  /// No description provided for @backgroundShellOutputTruncated.
  ///
  /// In en, this message translates to:
  /// **'Live output - earlier bytes truncated'**
  String get backgroundShellOutputTruncated;

  /// No description provided for @backgroundShellOutputWaiting.
  ///
  /// In en, this message translates to:
  /// **'Waiting for output...'**
  String get backgroundShellOutputWaiting;

  /// No description provided for @refresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refresh;

  /// Composer status while a prompt will wait for the active assistant run
  ///
  /// In en, this message translates to:
  /// **'Queued after run'**
  String get chatComposerQueuedAfterRun;

  /// Composer status while sending a prompt will steer the active assistant run
  ///
  /// In en, this message translates to:
  /// **'Steers current run'**
  String get chatComposerSteersCurrentRun;

  /// Composer action that stops the active assistant run
  ///
  /// In en, this message translates to:
  /// **'Stop'**
  String get chatStop;

  /// Live-region announcement while the assistant is generating
  ///
  /// In en, this message translates to:
  /// **'Assistant is working'**
  String get chatAssistantWorking;

  /// Tooltip and accessibility label for transcript message actions
  ///
  /// In en, this message translates to:
  /// **'Message actions'**
  String get chatMessageActions;

  /// Collapsed transcript disclosure label for assistant reasoning
  ///
  /// In en, this message translates to:
  /// **'Reasoning'**
  String get chatReasoningLabel;

  /// Error shown when a prompt attachment is an unsupported binary format
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

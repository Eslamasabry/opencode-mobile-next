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

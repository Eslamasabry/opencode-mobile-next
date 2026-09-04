// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'OpenCode Mobile';

  @override
  String get libraryBrowseSection => 'Browse';

  @override
  String get libraryManageSection => 'Manage';

  @override
  String get libraryModelsAgentsTitle => 'Models & agents';

  @override
  String get libraryProvidersTitle => 'Providers';

  @override
  String get libraryMcpTitle => 'MCP';

  @override
  String get libraryCommandsToolsTitle => 'Commands & tools';

  @override
  String get libraryTerminalTitle => 'Terminal';

  @override
  String get librarySettingsTitle => 'Settings';

  @override
  String aboutBuildVersion(String version, String buildNumber) {
    return 'OpenCode Mobile $version+$buildNumber';
  }

  @override
  String get aboutSigningCertificate => 'Signing certificate SHA-256';

  @override
  String get modelSwitchSession => 'Switch model for this session';

  @override
  String get modelNextRecent => 'Next recent model · F2';

  @override
  String get modelPreviousRecent => 'Previous recent model · Shift+F2';

  @override
  String get modelNextFavorite => 'Next favorite model';

  @override
  String get modelChooseTitle => 'Choose a model';

  @override
  String get modelTitleCompact => 'Models';

  @override
  String get modelSearchHint => 'Search models';

  @override
  String get modelAll => 'All models';

  @override
  String get modelFavorites => 'Favorites';

  @override
  String get modelRecent => 'Recent';

  @override
  String get modelOptions => 'Options';

  @override
  String get modelThinkingMode => 'Thinking mode';

  @override
  String get modelDefaultMode => 'Default mode';

  @override
  String get modelSessionScopeNote => 'Applies to this session\'s next turns.';

  @override
  String get commonClearSearch => 'Clear search';

  @override
  String get commonUndo => 'Undo';

  @override
  String get composerClearTextTitle => 'Clear draft text';

  @override
  String get composerClearTextSubtitle => 'Keeps attachments · Undo available';

  @override
  String get composerDraftCleared => 'Draft text cleared';

  @override
  String get composerReuseTitle => 'Reuse a prompt';

  @override
  String get composerReuseSubtitle => 'Add text from this conversation';

  @override
  String get composerReuseDescription =>
      'Adds text to your draft. Attachments aren’t copied.';

  @override
  String get composerReuseSearch => 'Search recent prompts';

  @override
  String get composerReuseEmpty => 'No matching prompts';

  @override
  String get backgroundSubagentsTitle => 'Background subagents';

  @override
  String get backgroundWorkTitle => 'Move running work to background';

  @override
  String get backgroundWorkShortcut =>
      'Continue this work while you use the chat · Ctrl+B';

  @override
  String get backgroundWorkNoop => 'No foreground subagents to background.';

  @override
  String get backgroundWorkPromoted =>
      'Subagents are continuing in the background.';

  @override
  String get librarySearchHint => 'Find settings, tools, and help';

  @override
  String get libraryDefaultModel => 'Default for new chats';

  @override
  String get libraryNoModel => 'No model selected';

  @override
  String librarySearchResults(int count, String query) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count results for “$query”.',
      one: '1 result for “$query”.',
      zero: 'No matching tools for “$query”.',
    );
    return '$_temp0';
  }
}

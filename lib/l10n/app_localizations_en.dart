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
  String get backgroundWorkMove => 'Move running work to background';

  @override
  String get backgroundSubagents => 'Background subagents';

  @override
  String get backgroundWorkMoved => 'Running work moved to background.';

  @override
  String get backgroundSubagentsUnavailable =>
      'No foreground subagents were available to background.';

  @override
  String get backgroundWorkUnsupported =>
      'This OpenCode server cannot move running work to background.';

  @override
  String backgroundWorkRunningCount(int count) {
    return '$count running';
  }

  @override
  String get backgroundWorkTitle => 'Running work';

  @override
  String get backgroundWorkEmptyDescription =>
      'Nothing is running for this session.';

  @override
  String backgroundWorkItemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count items still running',
      one: '1 item still running',
    );
    return '$_temp0';
  }

  @override
  String get backgroundWorkEmpty => 'No running work';

  @override
  String get backgroundWorkRunning => 'Running';

  @override
  String get backgroundSubagentRunning => 'Subagent - running';

  @override
  String backgroundShellRunning(String elapsed) {
    return 'Shell - $elapsed';
  }

  @override
  String get backgroundShellActions => 'Shell actions';

  @override
  String get backgroundShellTimeoutMinute => 'Timeout in 1 minute';

  @override
  String get backgroundShellTimeoutFiveMinutes => 'Timeout in 5 minutes';

  @override
  String get backgroundShellTimeoutClear => 'Clear timeout';

  @override
  String get backgroundShellStop => 'Stop command';

  @override
  String get backgroundShellStopTitle => 'Stop this shell command?';

  @override
  String get backgroundShellOutput => 'Live output';

  @override
  String get backgroundShellOutputTruncated =>
      'Live output - earlier bytes truncated';

  @override
  String get backgroundShellOutputWaiting => 'Waiting for output...';

  @override
  String get refresh => 'Refresh';

  @override
  String get chatComposerQueuedAfterRun => 'Queued after run';

  @override
  String get chatComposerSteersCurrentRun => 'Steers current run';

  @override
  String get chatStop => 'Stop';

  @override
  String get chatAssistantWorking => 'Assistant is working';

  @override
  String get chatMessageActions => 'Message actions';

  @override
  String get chatReasoningLabel => 'Reasoning';

  @override
  String get chatAttachmentUnsupported =>
      'Only PNG, JPEG, GIF, WebP, PDF, and text files can be attached.';

  @override
  String get termuxRestartServer => 'Restart local server';

  @override
  String get termuxRestartTitle => 'Restart the local server?';

  @override
  String get termuxRestartMessage =>
      'OpenCode will be briefly unavailable. The app will keep your current workspace and reconnect automatically.';

  @override
  String termuxRestartBusyMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count sessions are generating. Restarting will interrupt them.',
      one: '1 session is generating. Restarting will interrupt it.',
    );
    return '$_temp0';
  }

  @override
  String get termuxRestartConfirm => 'Restart';

  @override
  String get termuxRestarting => 'Restarting local server...';

  @override
  String get termuxRestartProgress =>
      'The installed OpenCode version and saved credential are unchanged. The app will reconnect when the server is ready.';

  @override
  String get termuxRestartSucceeded =>
      'Local server restarted and reconnected.';

  @override
  String get termuxRestartNotPerformed =>
      'Restart was not performed. The existing local server is still running.';
}

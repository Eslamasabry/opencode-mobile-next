import '../l10n/app_localizations.dart';
import '../l10n/app_localizations_en.dart';
import 'widgets/tool_card.dart' show toolLabel;

String permissionRequestTitle(String permission, {AppLocalizations? l10n}) {
  final strings = l10n ?? AppLocalizationsEn();
  return switch (permission) {
    'bash' || 'shell' => strings.e7PermissionAction1,
    'edit' => strings.e7PermissionAction2,
    'read' => strings.e7PermissionAction3,
    'external_directory' => strings.e7PermissionAction4,
    'doom_loop' => strings.e7PermissionAction5,
    'webfetch' => strings.chatRequestTitleFetch,
    'websearch' => strings.chatRequestTitleWebSearch,
    'grep' || 'glob' => strings.chatRequestTitleSearch,
    'task' => strings.chatRequestTitleTask,
    'skill' => strings.chatRequestTitleSkill,
    'mode' => strings.chatRequestTitleMode,
    _ when permission.trim().isEmpty => strings.e7PermissionAction6,
    // Any other id names a tool: in words, as its step row names it, never
    // the id itself (that stays under the request's Details).
    _ => strings.e7PermissionAction7(toolLabel(permission, l10n: strings)),
  };
}

/// The ask on a request card, as one question ("Run this command?"). Kinds
/// without their own words keep [fallback], the statement
/// ([permissionRequestTitle]).
///
/// [toolName] is the tool whose call asks (the request names the call, not
/// the action): an `external_directory` ask then says whether that call
/// reads, writes or runs something outside the project.
String permissionRequestQuestion(
  String permission, {
  required String fallback,
  AppLocalizations? l10n,
  String? toolName,
}) {
  final strings = l10n ?? AppLocalizationsEn();
  return switch (permission) {
    'bash' || 'shell' => strings.chatRequestAskCommand,
    'edit' || 'write' || 'multiedit' || 'patch' => strings.chatRequestAskEdit,
    'read' => strings.chatRequestAskRead,
    'external_directory' => switch (externalDirectoryAction(toolName)) {
      ExternalDirectoryAction.write => strings.chatRequestAskOutsideWrite,
      ExternalDirectoryAction.read => strings.chatRequestAskOutsideRead,
      ExternalDirectoryAction.run => strings.chatRequestAskOutsideRun,
      ExternalDirectoryAction.other => strings.chatRequestAskDirectory,
    },
    'webfetch' => strings.chatRequestAskFetch,
    _ => fallback,
  };
}

/// What a call that reaches outside the project does there.
enum ExternalDirectoryAction { read, write, run, other }

/// Classifies the tool behind an `external_directory` ask by its name.
ExternalDirectoryAction externalDirectoryAction(String? toolName) {
  switch (toolName?.trim().toLowerCase()) {
    case 'write' || 'edit' || 'multiedit' || 'patch' || 'apply_patch':
      return ExternalDirectoryAction.write;
    case 'read' || 'glob' || 'grep' || 'list' || 'ls':
      return ExternalDirectoryAction.read;
    case 'bash' || 'shell':
      return ExternalDirectoryAction.run;
    default:
      return ExternalDirectoryAction.other;
  }
}

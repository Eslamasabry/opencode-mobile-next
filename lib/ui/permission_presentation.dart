import '../l10n/app_localizations.dart';
import '../l10n/app_localizations_en.dart';
import 'widgets/tool_card.dart' show toolLabel;

String permissionRequestTitle(String permission, {AppLocalizations? l10n}) {
  final strings = l10n ?? AppLocalizationsEn();
  return switch (permission) {
    'bash' => strings.e7PermissionAction1,
    'edit' => strings.e7PermissionAction2,
    'read' => strings.e7PermissionAction3,
    'external_directory' => strings.e7PermissionAction4,
    'doom_loop' => strings.e7PermissionAction5,
    _ when permission.trim().isEmpty => strings.e7PermissionAction6,
    // Any other id names a tool: in words, as its step row names it, never
    // the id itself (that stays under the request's Details).
    _ => strings.e7PermissionAction7(toolLabel(permission, l10n: strings)),
  };
}

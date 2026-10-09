// Shared by the settings coverage ratchets.
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/ui/screens/saved_permissions_screen.dart';
import 'package:opencode_mobile/ui/screens/settings_screen.dart';

import 'lists_support.dart';

/// The repository the settings pages read from.
class SettingsRepository extends ListsRepository {
  List<SavedPermission> saved = const [];
  TerminalShellSettings shells = const TerminalShellSettings(
    selected: '',
    options: [],
  );

  @override
  Future<List<SavedPermission>> listSavedPermissions() async => saved;

  @override
  Future<void> removeSavedPermission(String id) async {
    calls.add('removeSavedPermission');
    saved = [
      for (final item in saved)
        if (item.id != id) item,
    ];
  }

  @override
  Future<TerminalShellSettings> loadTerminalShellSettings() async => shells;

  @override
  Future<void> selectTerminalShell(String value) async =>
      calls.add('selectTerminalShell');
}

extension SettingsScreens on ListsScreens {
  Future<void> savedPermissions() =>
      show(SavedPermissionsScreen(controller: controller));

  Future<void> defaultShell() => show(
    Scaffold(body: DefaultShellRow(controller: controller)),
    then: () async {
      final row = find.byKey(const ValueKey('default-shell-settings-entry'));
      if (row.evaluate().isNotEmpty) await tester.tap(row);
    },
  );
}

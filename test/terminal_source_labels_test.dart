// The Terminal's host switch never shows two identical labels: the app's
// built-in Linux and a server profile that is itself called "This phone"
// (lib/ui/screens/terminal_screen.dart, owner screenshot of APK 2099).
import 'package:flutter/widgets.dart' show Locale;
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';

void main() {
  final l10n = lookupAppLocalizations(const Locale('en'));

  test('the built-in shell and a server called "This phone" read apart', () {
    final labels = terminalSourceLabels(l10n, 'This phone');
    expect(labels.phone, 'Built-in Linux');
    expect(labels.server, 'This phone');
    expect(labels.phone, isNot(labels.server));
  });

  test('a server with another name keeps it', () {
    final labels = terminalSourceLabels(l10n, 'Laptop');
    expect(labels.server, 'Laptop');
  });

  test('a nameless server is the OpenCode server', () {
    expect(terminalSourceLabels(l10n, null).server, 'OpenCode server');
    expect(terminalSourceLabels(l10n, '  ').server, 'OpenCode server');
  });

  test('a collision gets the runtime added, never two equal labels', () {
    final labels = terminalSourceLabels(l10n, 'Built-in Linux');
    expect(labels.phone, isNot(labels.server));
    expect(labels.server, 'Built-in Linux · OpenCode server');
  });
}

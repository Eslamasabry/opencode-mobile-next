// slice-R10: the capability registry gains a terminal-only entry, a
// tool-inventory entry and a real "why" for a server that cannot add extra
// tools (docs/ux-system/revamp/leftover-units.json, slice-R10).
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_capability_explainer.dart';

const _hostColumns = <String, KitHost>{
  'builtin': KitHost.thisPhone,
  'oc2Computer': KitHost.openCode2,
  'codex': KitHost.codex,
  'paseo': KitHost.paseo,
  'demo': KitHost.demo,
};

Map<String, dynamic> _row(String capability) {
  final json =
      jsonDecode(File('docs/ux-system/capabilities.json').readAsStringSync())
          as Map<String, dynamic>;
  return (json['matrix'] as List).cast<Map<String, dynamic>>().singleWhere(
    (row) => row['capability'] == capability,
  );
}

Set<KitHost> _hostsWith(Map<String, dynamic> row, String status) => {
  for (final MapEntry(:key, :value)
      in (row['hosts'] as Map<String, dynamic>).entries)
    if ((value as Map<String, dynamic>)['status'] == status) _hostColumns[key]!,
};

late BuildContext _context;

Future<void> _pump(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      home: Scaffold(
        body: Builder(
          builder: (context) {
            _context = context;
            return child;
          },
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  tearDown(KitCapabilities.debugReset);

  group('terminal-only entry', () {
    test('flag:terminal matches its capabilities.json row, explains only', () {
      final row = _row('flag:terminal');
      final entry = KitCapabilities.byId('flag:terminal');
      expect(entry, isNotNull);
      expect(entry!.supported, _hostsWith(row, 'supported'));
      expect(entry.partly, _hostsWith(row, 'partly'));
      expect(entry.supported, isNot(contains(KitHost.codex)));
      expect(entry.supported, isNot(contains(KitHost.paseo)));
      expect(entry.enableFlow, isNull);
    });

    testWidgets('title, reason and hosts line are about the terminal alone', (
      tester,
    ) async {
      await _pump(tester, const SizedBox());
      expect(
        KitCapabilityExplainer.titleOf(_context, 'flag:terminal'),
        'Terminal',
      );
      final reason = KitCapabilityExplainer.whyOf(_context, 'flag:terminal');
      expect(reason, "This server doesn't open a terminal for you.");
      expect(reason.toLowerCase(), isNot(contains('file')));
      expect(
        KitCapabilityExplainer.hostsLineOf(_context, 'flag:terminal'),
        'Works on \u2068this phone\u2069 and '
        '\u2068computers with OpenCode 2\u2069',
      );
      final onPaseo = KitCapabilityExplainer.whyOf(
        _context,
        'flag:terminal',
        host: KitHost.paseo,
        serverName: 'laptop',
      );
      expect(onPaseo, contains('Terminal'));
      expect(onPaseo, contains("isn't available"));
      expect(onPaseo, contains('\u2068laptop\u2069'));
      expect(onPaseo, isNot(contains('Files')));
      expect(onPaseo, contains('this phone'));
    });

    testWidgets('the row explains without an enable action', (tester) async {
      await _pump(
        tester,
        const KitCapabilityExplainer.row(
          capability: 'flag:terminal',
          host: KitHost.codex,
        ),
      );
      expect(find.text('Terminal'), findsOneWidget);
      expect(find.textContaining('Codex'), findsOneWidget);
      expect(
        KitCapabilityExplainer.enableLabelOf(_context, 'flag:terminal'),
        isNull,
      );
      expect(KitCapabilities.canEnable('flag:terminal'), isFalse);
    });
  });

  group('tool inventory entry', () {
    test('flag:toolInventory matches its capabilities.json row', () {
      final row = _row('flag:toolInventory');
      final entry = KitCapabilities.byId('flag:toolInventory');
      expect(entry, isNotNull);
      expect(entry!.supported, _hostsWith(row, 'supported'));
      expect(entry.partly, _hostsWith(row, 'partly'));
      expect(entry.supported, isEmpty);
      expect(entry.enableFlow, isNull);
    });

    testWidgets('says the server does not list its tools, not "OpenCode 1"', (
      tester,
    ) async {
      await _pump(tester, const SizedBox());
      expect(
        KitCapabilityExplainer.titleOf(_context, 'flag:toolInventory'),
        'Tool list',
      );
      final reason = KitCapabilityExplainer.whyOf(
        _context,
        'flag:toolInventory',
      );
      expect(reason, "This server doesn't list the tools its agent can use.");
      expect(reason, isNot(contains('OpenCode')));
      final onOc2 = KitCapabilityExplainer.whyOf(
        _context,
        'flag:toolInventory',
        host: KitHost.openCode2,
      );
      expect(onOc2, contains("isn't available"));
      expect(onOc2, isNot(contains('OpenCode 1')));
      expect(onOc2, isNot(contains('Works on')));
    });
  });

  group('extra tools why', () {
    testWidgets('explains a server that cannot add them, not the offer', (
      tester,
    ) async {
      await _pump(tester, const SizedBox());
      final l10n = AppLocalizations.of(_context);
      final why = KitCapabilityExplainer.whyOf(_context, 'mcp.any');
      expect(why, "This server can't add extra tools from the app.");
      expect(why, isNot(l10n.kitCapMcpAnyOffer));
      expect(why.toLowerCase(), isNot(contains('yet')));
      expect(why.toLowerCase(), isNot(contains('mcp')));
    });
  });
}

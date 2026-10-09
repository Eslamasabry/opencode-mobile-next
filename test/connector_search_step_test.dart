// The "Searched connectors" step: the words searched for on the line, and,
// while the catalogue is off, one button to turn it on in place.
import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/agent_tools/agent_tool_adapter.dart';
import 'package:opencode_mobile/domain/connector_search_result.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/widgets/tool_card.dart';

final _tool = AgentToolAdapter.claude.connectorSearchName!;

ToolState _off() => ToolState.fromJson({
  'status': 'completed',
  'input': {'query': 'github'},
  'output': jsonEncode({
    'status': 'catalogue_off',
    'message': 'The connector catalogue is off.',
    'matches': <Object>[],
  }),
  'metadata': <String, Object?>{},
}, toolName: _tool);

Future<void> _pump(
  WidgetTester tester,
  ToolState state, {
  Future<bool> Function()? enable,
  String? name,
}) async {
  tester.view.physicalSize = const Size(412, 915);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: child!,
      ),
      home: Scaffold(
        body: SingleChildScrollView(
          child: ToolCard(
            toolName: name ?? _tool,
            state: state,
            onEnableConnectorCatalogue: enable,
          ),
        ),
      ),
    ),
  );
}

void main() {
  test('catalogue_off is a status of its own', () {
    final result = parseConnectorSearchResult(
      text: '{"status":"catalogue_off","message":"x","matches":[]}',
    );
    expect(result?.status, ConnectorSearchStatus.catalogueOff);
  });

  testWidgets('the step shows the query and that the catalogue is off', (
    tester,
  ) async {
    await _pump(tester, _off());
    expect(find.text('Searched connectors'), findsOneWidget);
    expect(find.textContaining('github'), findsOneWidget);
    expect(find.textContaining('Catalogue is off'), findsOneWidget);
  });

  testWidgets('Turn on enables the catalogue and then asks to search again', (
    tester,
  ) async {
    final gate = Completer<bool>();
    var calls = 0;
    await _pump(
      tester,
      _off(),
      enable: () {
        calls++;
        return gate.future;
      },
    );
    await tester.tap(find.text('Searched connectors'));
    await tester.pumpAndSettle();
    expect(find.textContaining('nothing gets connected'), findsOneWidget);
    final button = find.text('Turn on connector catalogue');
    expect(button, findsOneWidget);
    await tester.tap(button);
    await tester.pump();
    await tester.tap(button);
    await tester.pump();
    expect(calls, 1);
    gate.complete(true);
    await tester.pumpAndSettle();
    expect(find.text('Turn on connector catalogue'), findsNothing);
    expect(find.textContaining('search again'), findsOneWidget);
  });

  testWidgets('a failed Turn on says so and keeps the button', (tester) async {
    await _pump(tester, _off(), enable: () async => false);
    await tester.tap(find.text('Searched connectors'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Turn on connector catalogue'));
    await tester.pumpAndSettle();
    expect(find.textContaining('could not be turned on'), findsOneWidget);
    expect(find.text('Turn on connector catalogue'), findsOneWidget);
  });

  testWidgets('no host action means no button', (tester) async {
    await _pump(tester, _off());
    await tester.tap(find.text('Searched connectors'));
    await tester.pumpAndSettle();
    expect(find.text('Turn on connector catalogue'), findsNothing);
  });

  testWidgets('Load tools shows what was looked up', (tester) async {
    await _pump(
      tester,
      ToolState.fromJson({
        'status': 'completed',
        'input': {'query': 'select:mcp__oc-ui__show'},
        'output': 'Loaded.',
        'metadata': <String, Object?>{},
      }, toolName: 'toolsearch'),
      name: 'toolsearch',
    );
    expect(find.text('Load tools'), findsOneWidget);
    expect(find.textContaining('select:mcp__oc-ui__show'), findsOneWidget);
  });
}

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  _Repository(this.empty);
  final bool empty;
  Object? failure;
  Completer<List<CommandInfo>>? pendingCommands;

  void _check() {
    if (failure != null) throw failure!;
  }

  @override
  Future<List<CommandInfo>> listCommands() async {
    _check();
    if (pendingCommands != null) return pendingCommands!.future;
    return empty ? [] : [const CommandInfo(name: 'review', subtask: false)];
  }

  @override
  Future<List<SkillInfo>> listSkills() async {
    _check();
    return empty
        ? []
        : [
            const SkillInfo(
              name: 'review',
              location: '/skills/review',
              content: '',
              slashCommand: false,
            ),
          ];
  }

  @override
  Future<List<ReferenceInfo>> listReferences() async {
    _check();
    return empty
        ? []
        : [const ReferenceInfo(name: 'review', path: '/review.md')];
  }

  @override
  Future<List<TerminalProcess>> listTerminals() async {
    _check();
    return empty
        ? []
        : [
            const TerminalProcess(
              id: 'terminal',
              title: 'review',
              command: 'bash',
              arguments: [],
              directory: '/work',
              pid: 1,
              running: true,
            ),
          ];
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(_Repository repository) async {
  SharedPreferences.setMockInitialValues({});
  return ConnectionController(
      ProfileStore(prefs: await SharedPreferences.getInstance()),
    )
    ..repository = repository
    ..status = StreamStatus.connected;
}

void main() {
  final screens = <String, Widget Function(ConnectionController)>{
    'commands': (controller) => CommandsScreen(controller: controller),
    'skills': (controller) => SkillsScreen(controller: controller),
    'references': (controller) => ReferencesScreen(controller: controller),
    'terminals': (controller) =>
        Scaffold(body: TerminalScreen(controller: controller)),
  };
  for (final screen in screens.entries) {
    for (final empty in [false, true]) {
      testWidgets(
        '${screen.key} retains ${empty ? 'empty' : 'loaded'} content after failed refresh and retries',
        (tester) async {
          final repository = _Repository(empty);
          final controller = await _controller(repository);
          addTearDown(controller.dispose);
          await tester.pumpWidget(MaterialApp(home: screen.value(controller)));
          await tester.pumpAndSettle();
          final content = tester.element(find.byType(RefreshIndicator));
          repository.failure = const ProductException('Refresh unavailable');
          await tester
              .widget<RefreshIndicator>(find.byType(RefreshIndicator))
              .onRefresh();
          await tester.pumpAndSettle();
          expect(find.textContaining('Couldn’t refresh'), findsOneWidget);
          expect(find.textContaining('Refresh unavailable'), findsOneWidget);
          expect(tester.element(find.byType(RefreshIndicator)), same(content));
          if (!empty) expect(find.textContaining('review'), findsWidgets);
          repository.failure = null;
          await tester.tap(find.text('Retry'));
          await tester.pumpAndSettle();
          expect(find.textContaining('Couldn’t refresh'), findsNothing);
          expect(tester.element(find.byType(RefreshIndicator)), same(content));
        },
      );
    }
  }

  testWidgets('older command refresh cannot clear a newer refresh error', (
    tester,
  ) async {
    final repository = _Repository(false);
    final controller = await _controller(repository);
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: CommandsScreen(controller: controller)),
    );
    await tester.pumpAndSettle();
    final pending = Completer<List<CommandInfo>>();
    repository.pendingCommands = pending;
    final oldRefresh = tester
        .widget<RefreshIndicator>(find.byType(RefreshIndicator))
        .onRefresh();
    await tester.pump();
    repository.failure = const ProductException('Latest failure');
    await tester
        .widget<RefreshIndicator>(find.byType(RefreshIndicator))
        .onRefresh();
    pending.complete([]);
    await oldRefresh;
    await tester.pumpAndSettle();
    expect(find.textContaining('Latest failure'), findsOneWidget);
    expect(find.text('/review'), findsOneWidget);
  });
}

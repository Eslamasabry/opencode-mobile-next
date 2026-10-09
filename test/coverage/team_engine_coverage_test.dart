// Coverage ratchet for what the team's own engine reports the project pages:
// the workspace the app reads from the engine (schema 1, no contract file; its
// fields are the ones TeamWorkspace.toJson writes). team_engine_ledger.json
// decides every field by its path:
//   "shown"                      its value must be on a project page
//   "shown: <text> | <text>"     these words stand for the value (a number,
//                                a flag, a reworded status)
//   "ignored: <reason>"          nothing a person reads; its value must NOT
//                                be on a page by accident
// The sample (team_engine_support.dart) sets every field to its own value, and
// the test fails on a field left at its default, so a new field cannot slip in
// unseen.
import 'dart:async';
import 'dart:convert';
import 'dart:ui' as ui;
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show pushKitPage;
import 'package:opencode_mobile/ui/screens/team/projects/team_project_conversation.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_editors.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_projects_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import '../goldens/kit/kit_gallery.dart' show loadKitGalleryFonts;
import '../kit/kit_harness.dart';
import 'paseo_coverage_support.dart' show flat, screenText;
import 'team_engine_support.dart';

class _Gateway implements OrchestrationProjectGateway {
  @override
  Future<TeamWorkspace> teamWorkspace() async => populatedEngineWorkspace();
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand c) async =>
      const TeamCommandResult(accepted: true);
  @override
  Future<void> close() async {}
  @override
  Future<void> deleteLocalData() async {}
}

void _flatten(Object? v, String path, Map<String, Object?> out) {
  if (v is Map) {
    if (v.isEmpty) out[path] = '{}';
    v.forEach((k, x) => _flatten(x, path.isEmpty ? '$k' : '$path.$k', out));
  } else if (v is List) {
    if (v.isEmpty) out[path] = '[]';
    for (final x in v) {
      _flatten(x, '$path[]', out);
    }
  } else {
    out[path] = v;
  }
}

Map<String, Object?> _paths() {
  final out = <String, Object?>{};
  _flatten(
    jsonDecode(jsonEncode(populatedEngineWorkspace().toJson())),
    '',
    out,
  );
  return out;
}

bool _isDefault(Object? v) =>
    v == null ||
    v == '' ||
    v == 0 ||
    v == false ||
    v == '[]' ||
    v == '{}' ||
    v == 0.0;

/// What a person would read for [value] when the ledger gives no words.
List<String> _defaultProbes(Object? value) => switch (value) {
  String s => [s],
  int n => ['$n'],
  double d => [d.toString(), d.toStringAsFixed(2), d.toStringAsFixed(1)],
  _ => const [],
};

Future<void> _settle(WidgetTester tester) async {
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final ledger =
      (jsonDecode(
                File(
                  'test/fixtures/coverage/team_engine_ledger.json',
                ).readAsStringSync(),
              )
              as Map)
          .cast<String, String>();
  final paths = _paths();

  setUpAll(() async {
    await loadKitGalleryFonts();
    await loadCaptureFonts();
  });
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('every field the engine can report has a decision and a value', () {
    final fields = paths.keys.toSet();
    expect(
      fields.difference(ledger.keys.toSet()),
      isEmpty,
      reason: 'fields with no entry in team_engine_ledger.json',
    );
    expect(
      ledger.keys.toSet().difference(fields),
      isEmpty,
      reason: 'ledger entries for fields the engine no longer has',
    );
    expect(
      [
        for (final e in paths.entries)
          // A real engine reports simulated: false, and a budget with limits
          // is not unlimited: those two read false on purpose.
          if (_isDefault(e.value) &&
              !e.key.endsWith('simulated') &&
              !e.key.endsWith('budget.unlimited'))
            e.key,
      ],
      isEmpty,
      reason: 'sample fields left at their default: give them a value',
    );
    for (final e in ledger.entries) {
      expect(
        e.value == 'shown' ||
            e.value.startsWith('shown: ') ||
            (e.value.startsWith('ignored: ') && e.value.length > 20),
        isTrue,
        reason: '${e.key}: "shown", "shown: <words>" or "ignored: <reason>"',
      );
    }
  });

  testWidgets('what the engine reports is on the project pages', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(412, 915);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    final controller = TeamProjectController(_Gateway());
    await controller.load();
    addTearDown(controller.dispose);
    final text = StringBuffer();
    Future<void> shoot(String name) async {
      final render =
          tester.renderObject(find.byType(RepaintBoundary).first)
              as RenderRepaintBoundary;
      await tester.runAsync(() async {
        final image = await render.toImage();
        final png = await image.toByteData(format: ui.ImageByteFormat.png);
        File(
          'build/coverage/team-engine-$name.png',
        ).writeAsBytesSync(png!.buffer.asUint8List());
      });
    }

    Future<void> page(String name, WidgetBuilder build) async {
      final context = await pumpKitHost(tester);
      pushKitPage<void>(context, build);
      await _settle(tester);
      await shoot(name);
      text.writeln('## $name');
      text.writeln(screenText(tester).join('\n'));
      await tester.pumpWidget(const SizedBox.shrink());
    }

    Future<void> editor(
      String name,
      Future<void> Function(BuildContext) open,
    ) async {
      final context = await pumpKitHost(tester);
      unawaited(open(context));
      await _settle(tester);
      await shoot(name);
      text.writeln('## $name');
      text.writeln(screenText(tester).join('\n'));
      await tester.pumpWidget(const SizedBox.shrink());
    }

    Widget overview(BuildContext _) =>
        TeamProjectOverview(controller: controller, projectId: 'prj-shop');
    await page('projects', (_) => TeamProjectsScreen(controller: controller));
    await page('overview', overview);
    await page(
      'task',
      (_) => TeamProjectConversation(
        controller: controller,
        projectId: 'prj-shop',
        taskId: 'task-pay',
      ),
    );
    await page(
      'board',
      (_) => TeamProjectBoard(controller: controller, projectId: 'prj-shop'),
    );
    await page(
      'timeline',
      (_) => TeamProjectTimeline(controller: controller, projectId: 'prj-shop'),
    );
    await page(
      'servers',
      (_) => TeamProjectServers(controller: controller, projectId: 'prj-shop'),
    );
    await editor(
      'settings',
      (c) => openTeamProjectSettings(c, controller, 'prj-shop'),
    );
    await editor('spec', (c) => openTeamSpecEditor(c, controller, 'prj-shop'));
    await editor('plan', (c) => openTeamPlanEditor(c, controller, 'prj-shop'));
    await editor('roles', (c) => openTeamRoles(c, controller));
    await editor('defaults', (c) => openTeamDefaults(c, controller));
    File(
      'build/coverage/team_engine_screens.txt',
    ).writeAsStringSync(text.toString());
    final screen = flat(text.toString());
    final report = <String>[];
    final problems = <String>[];
    for (final e in paths.entries) {
      final decision = ledger[e.key];
      if (decision == null) continue;
      final probes = decision.startsWith('shown: ')
          ? decision.substring(7).split('|').map((p) => p.trim()).toList()
          : decision == 'shown'
          ? _defaultProbes(e.value)
          : _defaultProbes(e.value);
      final visible = [for (final p in probes) screen.contains(flat(p))];
      report.add('${e.key} = ${e.value} -> ${visible.contains(true)}');
      if (decision.startsWith('shown')) {
        if (probes.isEmpty ||
            !visible.every((v) => v) &&
                !decision.startsWith('shown: ') &&
                !visible.any((v) => v)) {
          problems.add('${e.key}: "${probes.join(' / ')}" is not on a page');
        } else if (decision.startsWith('shown: ') && !visible.every((v) => v)) {
          problems.add('${e.key}: "${probes.join(' / ')}" is not on a page');
        }
      } else if (e.value is String &&
          (e.value as String).length > 4 &&
          visible.contains(true)) {
        problems.add(
          '${e.key}: "${e.value}" is on a page but ledgered ignored',
        );
      }
    }
    File(
      'build/coverage/team_engine_report.txt',
    ).writeAsStringSync(report.join('\n'));
    expect(problems, isEmpty, reason: problems.join('\n'));
  });
}

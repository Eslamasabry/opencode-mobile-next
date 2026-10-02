// Behaviour tests for the New project sheet and Agents fixes from the
// 2026-09-30 end-to-end report (B-5, B-7, B-8, B-9, B-13, B-17).
import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/model_name_order.dart';
import 'package:opencode_mobile/api/provider_presentation.dart';
import 'package:opencode_mobile/domain/server_gateway.dart' show CatalogModel;
import 'package:opencode_mobile/state/team_project_controller.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/team/projects/team_project_editors.dart';
import 'package:opencode_mobile/ui/screens/team/team_model_sheet.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'kit/kit_harness.dart';

class _Gateway implements OrchestrationProjectGateway {
  _Gateway(this.workspace);
  TeamWorkspace workspace;
  final commands = <TeamProjectCommand>[];
  @override
  Future<TeamWorkspace> teamWorkspace() async => workspace;
  @override
  Stream<TeamWorkspace> watchTeamWorkspace() => const Stream.empty();
  @override
  Future<TeamCommandResult> executeProject(TeamProjectCommand command) async {
    commands.add(command);
    return const TeamCommandResult(accepted: true, revision: 8);
  }

  @override
  Future<void> close() async {}
  @override
  Future<void> deleteLocalData() async {}
}

Future<void> _tap(WidgetTester tester, String label) async {
  final target = find.text(label).last;
  await tester.ensureVisible(target);
  await tester.pumpAndSettle();
  await tester.tap(target);
  await tester.pumpAndSettle();
}

Future<void> _type(WidgetTester tester, String key, String value) async {
  final field = find.descendant(
    of: find.byKey(ValueKey(key)),
    matching: find.byType(EditableText),
  );
  await tester.ensureVisible(field);
  await tester.enterText(field, value);
  tester.testTextInput.hide();
  await tester.pumpAndSettle();
}

Future<(_Gateway, TeamProjectController)> _open(
  WidgetTester tester,
  TeamWorkspace workspace, {
  double textScale = 1,
}) async {
  if (textScale != 1) {
    tester.platformDispatcher.textScaleFactorTestValue = textScale;
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
  }
  final gateway = _Gateway(workspace);
  final controller = TeamProjectController(gateway);
  await controller.load();
  addTearDown(controller.dispose);
  final context = await pumpKitHost(
    tester,
    effects: const KitEffects(motion: KitMotionLevel.off),
  );
  unawaited(openTeamNewProject(context, controller));
  await tester.pumpAndSettle();
  return (gateway, controller);
}

KitAction _primary(WidgetTester tester) =>
    tester.widget<KitSheet>(find.byType(KitSheet)).primary!;

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets('B-8: a typed repo counts without tapping Add repo', (
    tester,
  ) async {
    final (gateway, _) = await _open(
      tester,
      const TeamWorkspace(
        servers: [TeamServer(id: 'pc', name: 'Home PC')],
      ),
    );
    await _tap(tester, 'Single lane');
    await _tap(tester, 'No limit');
    await _type(tester, 'goal', 'Read saved articles');
    await _type(tester, 'repoName', 'App');
    expect(
      _primary(tester).disabledReason,
      'Finish the repo: a name, a folder and where it runs.',
    );
    await _type(tester, 'repoPath', '/projects/reader');
    await _tap(tester, 'Home PC');
    expect(_primary(tester).onPressed, isNotNull);
    await _tap(tester, 'Start planning');
    expect(gateway.commands, hasLength(1));
    expect(gateway.commands.single.repos, hasLength(1));
    expect(gateway.commands.single.repos.single.name, 'App');
    expect(gateway.commands.single.repos.single.serverId, 'pc');
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpAndSettle();
  });

  testWidgets('B-7: the cost line is measured, or says it is not', (
    tester,
  ) async {
    await _open(
      tester,
      const TeamWorkspace(
        servers: [
          TeamServer(id: 'pc', name: 'Home PC', memoryMb: 120),
          TeamServer(id: 'box', name: 'Build box'),
          TeamServer(id: 'me', name: 'Phone', phone: true),
        ],
      ),
    );
    await _tap(tester, 'Single lane');
    expect(
      find.text('Choose where the work runs to see what a lane costs there.'),
      findsOneWidget,
    );
    await _tap(tester, 'Home PC');
    expect(find.textContaining('about 120 MB of memory per lane'), findsOne);
    await _tap(tester, 'Build box');
    expect(
      find.text(
        'Not measured on Build box yet. Your conversation stays first.',
      ),
      findsOne,
    );
    await _tap(tester, 'Phone');
    expect(
      find.text(
        'Not measured on this phone yet. Your conversation stays first.',
      ),
      findsOne,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpAndSettle();
  });

  testWidgets('B-9: the budget hint and the button hint agree', (tester) async {
    await _open(
      tester,
      const TeamWorkspace(
        servers: [TeamServer(id: 'pc', name: 'Home PC')],
      ),
    );
    await _tap(tester, 'Single lane');
    await _tap(tester, 'Set limits');
    await _type(tester, 'daily', '5');
    expect(find.textContaining('Leave a total'), findsNothing);
    expect(
      _primary(tester).disabledReason,
      'Enter a limit per day and a total limit, each above zero.',
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpAndSettle();
  });

  testWidgets('B-17: at 2.0x text the hint scrolls with the form', (
    tester,
  ) async {
    await _open(
      tester,
      const TeamWorkspace(
        servers: [TeamServer(id: 'pc', name: 'Home PC')],
      ),
      textScale: 2,
    );
    final reason = _primary(tester).disabledReason;
    expect(reason, isNull);
    expect(find.byKey(const ValueKey('kit-action-reason')), findsNothing);
    expect(
      find.text('Choose Single lane or Parallel agents.', skipOffstage: false),
      findsOneWidget,
    );
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pumpAndSettle();
  });

  group('roles', () {
    const workspace = TeamWorkspace(
      roles: [TeamProjectRole(id: 'checker', name: 'Checker', readOnly: true)],
    );

    Future<_Gateway> openRole(
      WidgetTester tester,
      TeamRolesModelPicker picker,
    ) async {
      final gateway = _Gateway(workspace);
      final controller = TeamProjectController(gateway);
      await controller.load();
      addTearDown(controller.dispose);
      final context = await pumpKitHost(
        tester,
        effects: const KitEffects(motion: KitMotionLevel.off),
      );
      unawaited(openTeamRoles(context, controller, modelPicker: picker));
      await tester.pumpAndSettle();
      expect(find.text('Read-only'), findsOneWidget);
      await _tap(tester, 'Checker');
      return gateway;
    }

    testWidgets('B-5: model and fallback use the picker; checker is marked', (
      tester,
    ) async {
      final asked = <bool>[];
      final gateway = await openRole(tester, (
        context, {
        required current,
        required fallback,
      }) async {
        asked.add(fallback);
        return TeamModelChoice(fallback ? 'zai/glm-4.7' : 'zai/glm-5.3');
      });
      expect(
        find.text(
          'Read-only: this agent can read the project but not change it.',
        ),
        findsOneWidget,
      );
      // No typed fields for the models.
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('roleModel')),
          matching: find.byType(EditableText),
        ),
        findsNothing,
      );
      await tester.tap(find.byKey(const ValueKey('roleModel')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('roleFallback')));
      await tester.pumpAndSettle();
      expect(asked, [false, true]);
      await _tap(tester, 'Save changes');
      expect(gateway.commands.single.role!.model, 'zai/glm-5.3');
      expect(gateway.commands.single.role!.fallbackModel, 'zai/glm-4.7');
      expect(gateway.commands.single.role!.readOnly, isTrue);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpAndSettle();
    });
  });

  test('B-13: models read by family, newest version first', () {
    final names = [
      'GLM-4.7',
      'GLM-5-Turbo',
      'GLM-5.3',
      'Claude Opus 4',
      'Claude Opus 5.5',
    ]..sort(compareModelNames);
    expect(names, [
      'Claude Opus 5.5',
      'Claude Opus 4',
      'GLM-5.3',
      'GLM-5-Turbo',
      'GLM-4.7',
    ]);
    CatalogModel model(String id, String name) => CatalogModel(
      id: id,
      providerID: 'zai',
      name: name,
      enabled: true,
      status: 'active',
      contextLimit: 0,
      outputLimit: 0,
      reasoning: false,
      attachments: false,
      tools: true,
      variants: const [],
    );
    final shown = presentModels([
      model('a', 'GLM-4.7'),
      model('b', 'GLM-5-Turbo'),
      model('c', 'GLM-5.3'),
    ]).map((m) => m.name).toList();
    expect(shown, ['GLM-5.3', 'GLM-5-Turbo', 'GLM-4.7']);
  });
}

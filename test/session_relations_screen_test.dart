import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/session_relations_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

final _parent = Session(
  id: 'parent',
  title: 'Build native mobile parity',
  directory: '/work/app',
  workspaceID: 'phone',
  time: SessionTime(created: 100, updated: 400),
);

final _childOne = Session(
  id: 'child-1',
  title: 'Audit OpenCode contracts (@explore subagent)',
  parentID: 'parent',
  directory: '/work/app',
  workspaceID: 'phone',
  time: SessionTime(created: 200, updated: 300),
);

final _childTwo = Session(
  id: 'child-2',
  title: 'Implement native session tree (@general subagent)',
  parentID: 'parent',
  directory: '/work/app',
  workspaceID: 'phone',
  time: SessionTime(created: 300, updated: 400),
);

class _RelationsRepository implements ProductRepository {
  final sessions = <String, Session>{
    _parent.id: _parent,
    _childOne.id: _childOne,
    _childTwo.id: _childTwo,
  };

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<Session> getSessionDetails(String id) async => sessions[id]!;

  @override
  Future<List<Session>> listSessionChildren(String id) async => [
    _childOne,
    _childTwo,
  ];

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _RelationsController extends ConnectionController {
  _RelationsController(super.store);

  @override
  Future<ProductRepository?> prepareActionRepository() async => repository;
}

Future<_RelationsController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return _RelationsController(ProfileStore(prefs: preferences))
    ..repository = _RelationsRepository()
    ..directory = '/work/app'
    ..workspace = 'phone'
    ..status = StreamStatus.connected;
}

Widget _screen(ConnectionController controller, String sessionID) =>
    MaterialApp(
      home: SessionRelationsScreen(
        controller: controller,
        sessionID: sessionID,
      ),
    );

class _RelationsHarness extends StatefulWidget {
  final ConnectionController controller;

  const _RelationsHarness({required this.controller});

  @override
  State<_RelationsHarness> createState() => _RelationsHarnessState();
}

class _RelationsHarnessState extends State<_RelationsHarness> {
  String? selected;

  Future<void> _open() async {
    final session = await Navigator.of(context).push<Session>(
      MaterialPageRoute<Session>(
        builder: (_) => SessionRelationsScreen(
          controller: widget.controller,
          sessionID: 'parent',
        ),
      ),
    );
    if (mounted) setState(() => selected = session?.id);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: Center(
      child: FilledButton(
        key: const ValueKey('open-relations'),
        onPressed: _open,
        child: Text(selected ?? 'Open'),
      ),
    ),
  );
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets(
    'shows parent and ordered subagents on a compact large-text phone',
    (tester) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final controller = await _controller();
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: _screen(controller, 'child-2'),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Subagent sessions'), findsOneWidget);
      expect(find.textContaining('2 delegated sessions'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('session-relation-parent')),
        findsOneWidget,
      );
      await tester.scrollUntilVisible(
        find.byKey(const ValueKey('session-relation-child-2')),
        120,
        scrollable: find.byType(Scrollable).first,
      );
      final current = tester.widget<ListTile>(
        find.byKey(const ValueKey('session-relation-child-2')),
      );
      final first = tester.widget<ListTile>(
        find.byKey(const ValueKey('session-relation-child-1')),
      );
      expect(current.selected, isTrue);
      expect((first.subtitle! as Text).data, startsWith('1 of 2'));
      expect((current.subtitle! as Text).data, startsWith('2 of 2'));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('returns the exact selected child session', (tester) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      MaterialApp(home: _RelationsHarness(controller: controller)),
    );

    await tester.tap(find.byKey(const ValueKey('open-relations')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('session-relation-child-1')));
    await tester.pumpAndSettle();

    expect(find.text('child-1'), findsOneWidget);
  });
}

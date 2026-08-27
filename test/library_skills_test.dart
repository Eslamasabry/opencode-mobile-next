import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _SkillsRepository implements ProductRepository {
  const _SkillsRepository(this.skills);

  final List<SkillInfo> skills;

  @override
  Future<List<SkillInfo>> listSkills() async => skills;

  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller(ProductRepository repository) async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences))
    ..repository = repository
    ..status = StreamStatus.connected;
}

Widget _app(ConnectionController controller) =>
    MaterialApp(home: SkillsScreen(controller: controller));

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('skill content uses the shared Markdown and code renderer', (
    tester,
  ) async {
    final controller = await _controller(
      const _SkillsRepository([
        SkillInfo(
          name: 'release-check',
          description: 'Verify a release',
          location: '/work/.opencode/skills/release-check/SKILL.md',
          content: '''# Release check

| Gate | Command |
| --- | --- |
| Tests | `flutter test` |

```sh
flutter analyze
```''',
          slashCommand: true,
        ),
      ]),
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    await tester.tap(find.text('release-check'));
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('skill-content-preview')), findsOneWidget);
    expect(find.byKey(const Key('file-preview-text')), findsOneWidget);
    expect(find.text('Rendered'), findsOneWidget);
    expect(find.text('Raw'), findsOneWidget);
    expect(find.text('Release check'), findsOneWidget);
    expect(find.text('Gate'), findsOneWidget);
    expect(find.text('Tests'), findsOneWidget);
    expect(find.text('flutter analyze'), findsOneWidget);
    expect(find.textContaining('| Gate | Command |'), findsNothing);

    await tester.tap(find.text('Raw'));
    await tester.pumpAndSettle();

    expect(find.textContaining('| Gate | Command |'), findsOneWidget);
    expect(find.text('markdown'), findsOneWidget);
  });

  testWidgets('skill preview fits a narrow large-text phone', (tester) async {
    tester.view.physicalSize = const Size(640, 1280);
    tester.view.devicePixelRatio = 2;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final controller = await _controller(
      const _SkillsRepository([
        SkillInfo(
          name: 'mobile-skill',
          location: '/work/.opencode/skills/mobile-skill/SKILL.md',
          content: '# Mobile skill\n\nUse **careful** rendering.',
          slashCommand: false,
        ),
      ]),
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(textScaler: TextScaler.linear(2)),
        child: _app(controller),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('mobile-skill'));
    await tester.pumpAndSettle();

    expect(tester.takeException(), isNull);
    expect(find.text('Mobile skill'), findsOneWidget);
    expect(find.text('Raw'), findsOneWidget);
  });
}

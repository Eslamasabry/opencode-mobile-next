import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/mission_control_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<ConnectionController> _controller() async {
  SharedPreferences.setMockInitialValues({});
  final preferences = await SharedPreferences.getInstance();
  return ConnectionController(ProfileStore(prefs: preferences))
    ..repository = _Repository()
    ..status = StreamStatus.connected;
}

Widget _app(ConnectionController controller) => MaterialApp(
  localizationsDelegates: AppLocalizations.localizationsDelegates,
  supportedLocales: AppLocalizations.supportedLocales,
  home: Scaffold(body: LibraryScreen(controller: controller)),
);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('More hub leads the Browse grid with a Mission Control card', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller();
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    final card = find.byKey(const ValueKey('library-mission-control'));
    expect(card, findsOneWidget);
    expect(find.text('Mission Control'), findsOneWidget);
    // First slot of the grid: no destination card sits above or to its left.
    final cardRect = tester.getRect(card);
    final modelsRect = tester.getRect(
      find.widgetWithText(Card, 'Models & agents'),
    );
    expect(cardRect.top, lessThanOrEqualTo(modelsRect.top));
    expect(cardRect.left, lessThan(modelsRect.left));
    // No pending work: the card carries no badge.
    expect(
      find.descendant(of: card, matching: find.byType(Badge)),
      findsNothing,
    );

    await tester.tap(card);
    await tester.pumpAndSettle();

    expect(find.byType(MissionControlScreen), findsOneWidget);
  });

  testWidgets('Mission Control card surfaces the pending request count', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final controller = await _controller()
      ..permissions = {
        'perm-1': PermissionRequest(
          id: 'perm-1',
          sessionID: 'ses_run',
          permission: 'edit',
          patterns: const ['lib/main.dart'],
        ),
      }
      ..questions = {
        'q-1': const PendingQuestion(
          id: 'q-1',
          sessionID: 'ses_idle',
          prompts: [
            QuestionPrompt(
              title: 'Direction',
              question: 'Proceed?',
              multiple: false,
              custom: true,
              choices: [],
            ),
          ],
        ),
      };
    addTearDown(controller.dispose);

    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();

    final card = find.byKey(const ValueKey('library-mission-control'));
    expect(card, findsOneWidget);
    expect(find.descendant(of: card, matching: find.text('2')), findsOneWidget);
  });
}

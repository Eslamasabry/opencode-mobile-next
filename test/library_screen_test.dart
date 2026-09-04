import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/screens/library_screen.dart';
import 'package:opencode_mobile/ui/screens/terminal_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

class _Repository implements ProductRepository {
  @override
  void setLocation({String? directory, String? workspace}) {}

  @override
  Future<List<TerminalProcess>> listTerminals() async => [];

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

  testWidgets('search finds aliases, recovers from no results, and clears', (
    tester,
  ) async {
    final controller = await _controller();
    addTearDown(controller.dispose);
    await tester.pumpWidget(_app(controller));
    await tester.pumpAndSettle();
    final search = find.byKey(const Key('library-search'));
    await tester.enterText(search, 'shell');
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('library-terminal')), findsOneWidget);
    expect(find.text('Providers'), findsNothing);
    await tester.enterText(search, 'not-a-real-tool');
    await tester.pumpAndSettle();
    expect(find.textContaining('No matching tools'), findsOneWidget);
    await tester.tap(find.byTooltip('Clear search'));
    await tester.pumpAndSettle();
    expect(find.text('Providers'), findsOneWidget);
    expect(find.byKey(const ValueKey('library-active-setup')), findsOneWidget);
  });

  testWidgets(
    'the default model uses the catalog name and explains its scope',
    (tester) async {
      final controller = await _controller();
      addTearDown(controller.dispose);
      controller.selectedModel = ModelRef(
        providerID: 'opencode',
        modelID: 'nemotron-free',
      );
      controller.catalog = const CatalogSnapshot(
        providers: [],
        agents: [],
        models: [
          CatalogModel(
            id: 'nemotron-free',
            providerID: 'opencode',
            name: 'Nemotron Ultra',
            enabled: true,
            status: 'active',
            contextLimit: 100000,
            outputLimit: 8000,
            reasoning: true,
            attachments: false,
            tools: true,
            variants: [],
          ),
        ],
      );
      await tester.pumpWidget(_app(controller));
      await tester.pumpAndSettle();
      expect(find.text('Nemotron Ultra'), findsOneWidget);
      expect(find.text('opencode/nemotron-free'), findsNothing);
      expect(find.textContaining('Default for new chats'), findsOneWidget);
    },
  );

  testWidgets('More uses compact rows with Models & agents first', (
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

    // UX-P0-01: pending work lives on the Activity destination alone, so the
    // hub no longer repeats it as Mission Control or Requests.
    expect(find.text('Mission Control'), findsNothing);
    expect(find.text('Requests'), findsNothing);
    expect(find.byKey(const ValueKey('library-mission-control')), findsNothing);

    final card = find.widgetWithText(ListTile, 'Models & agents');
    final cardRect = tester.getRect(card);
    final providersRect = tester.getRect(
      find.widgetWithText(ListTile, 'Providers'),
    );
    expect(cardRect.bottom, lessThanOrEqualTo(providersRect.top));
    expect(cardRect.height, lessThanOrEqualTo(72));
    expect(cardRect.left, providersRect.left);
  });

  testWidgets('the hub carries no pending badge of its own', (tester) async {
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

    // One global badge only, and it is not here.
    expect(find.byType(Badge), findsNothing);
    expect(find.text('2'), findsNothing);
  });

  testWidgets('Terminal moved into the hub and opens with an app bar', (
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

    final card = find.byKey(const ValueKey('library-terminal'));
    expect(card, findsOneWidget);

    await tester.ensureVisible(card);
    await tester.pumpAndSettle();
    await tester.tap(card);
    await tester.pumpAndSettle();

    expect(find.byType(TerminalScreen), findsOneWidget);
    expect(
      find.descendant(of: find.byType(AppBar), matching: find.text('Terminal')),
      findsOneWidget,
    );
  });
}

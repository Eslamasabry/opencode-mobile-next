// slice-P0.7-port hand-off: the "In-app Ubuntu" card says how many prompts wait
// for its server and offers to move them to the connected server, exactly
// like a Servers row does (slice-queue-move): "3 prompts waiting to send" in
// its line, "Move 3 waiting prompts to Laptop" in its menu, which opens the
// one move sheet.
//
// Goldens: the card with its menu open, phone 412x915 and one wide window
// (1280x800), dark, real fonts, DPR 1. Regenerate deliberately:
//   flutter test --update-goldens test/phone_server_card_queued_prompts_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'revamp/screen_phone_1_fixtures.dart'
    show phoneSize, pumpPhone, unmountPhone, wideSize;
import 'revamp/shared_phone_1_fixtures.dart';
import 'support/fake_setup_engine.dart';

final _laptop = ServerProfile(
  id: 'laptop',
  name: 'Laptop',
  baseUrl: 'http://192.168.1.20:4096',
);

/// Prompts queued for This phone while it was stopped, and Laptop as the
/// connected server they can move to (null: connected to none).
class _QueueConnection extends PhoneConnection {
  _QueueConnection(super.store);

  final queued = <QueuedPrompt>[];
  ServerProfile? destination;

  @override
  int queuedPromptCountForProfile(String profileID) =>
      queuedPromptsForProfile(profileID).length;

  @override
  List<QueuedPrompt> queuedPromptsForProfile(String profileID) => [
    for (final prompt in queued)
      if (prompt.profileID == profileID) prompt,
  ];

  @override
  ServerProfile? get queuedPromptMoveDestination => destination;

  void queue(int count) {
    final now = DateTime.now().millisecondsSinceEpoch;
    queued.addAll([
      for (var i = 0; i < count; i++)
        QueuedPrompt(
          id: 'q${queued.length + i}',
          profileID: 'phone',
          sessionID: 'ses_phone',
          text: 'Queued prompt ${queued.length + i + 1}: run the tests again',
          createdAt: now - (count - i) * 60000,
        ),
    ]);
    notifyListeners();
  }
}

void main() {
  late PhoneStore store;
  late _QueueConnection connection;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = PhoneStore(prefs: await SharedPreferences.getInstance());
    store.saved.addAll([phoneProfile(), _laptop]);
    connection = _QueueConnection(store);
    PhoneSetup.engine = FakeSetupEngine();
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('1.18.29');
  });

  tearDown(() {
    serverProbe = probeServerConnection;
    connection.dispose();
  });

  Future<void> mount(WidgetTester tester, {bool asRow = false}) async {
    final linux = PhoneLinux(running: false);
    final profile = store.saved.first;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          builtinLinuxProvider.overrideWithValue(linux),
          builtinServerStarterProvider.overrideWith(
            (ref) => BuiltinServerStarter(linux: linux),
          ),
        ],
        child: MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: ListView(
              children: [
                if (asRow)
                  KitRowGroup(
                    children: [
                      PhoneServerCard.row(
                        connection: connection,
                        profile: profile,
                        linux: linux,
                        pollInterval: null,
                      ),
                    ],
                  )
                else
                  PhoneServerCard(
                    connection: connection,
                    profile: profile,
                    linux: linux,
                    pollInterval: null,
                  ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> openMenu(WidgetTester tester) async {
    await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
    await tester.pumpAndSettle();
  }

  final moveItem = find.byKey(const ValueKey('phone-server-move-queued'));

  testWidgets('says how many prompts wait and moves them to the connected '
      'server', (tester) async {
    connection
      ..destination = _laptop
      ..queue(3);
    await mount(tester);

    expect(
      find.textContaining(l10n.serverRowQueuedWaiting(3), findRichText: true),
      findsOneWidget,
    );
    await openMenu(tester);
    expect(
      find.descendant(
        of: moveItem,
        matching: find.text(l10n.serverRowMoveQueued(3, 'Laptop')),
      ),
      findsOneWidget,
    );
    await tester.tap(moveItem);
    await tester.pumpAndSettle();

    // The one move sheet, for this phone's prompts only.
    expect(find.byKey(const ValueKey('queued-move-sheet')), findsOneWidget);
    expect(find.byKey(const ValueKey('queued-move-prompt-q0')), findsOneWidget);
    expect(
      find.text(l10n.queuedMoveAction(3, 'Laptop')),
      findsOneWidget,
      reason: 'the primary names the act and the destination',
    );
    await tester.tap(find.byKey(const ValueKey('queued-move-cancel')));
    await tester.pumpAndSettle();
    expect(connection.queued, hasLength(3), reason: 'cancel moves nothing');
  });

  testWidgets('the row shape says and offers the same', (tester) async {
    connection
      ..destination = _laptop
      ..queue(1);
    await mount(tester, asRow: true);
    expect(
      find.textContaining(l10n.serverRowQueuedWaiting(1), findRichText: true),
      findsOneWidget,
    );
    await openMenu(tester);
    expect(find.text(l10n.serverRowMoveQueued(1, 'Laptop')), findsOneWidget);
  });

  testWidgets('with no connected server the count shows and no move', (
    tester,
  ) async {
    connection.queue(2);
    await mount(tester);
    expect(
      find.textContaining(l10n.serverRowQueuedWaiting(2), findRichText: true),
      findsOneWidget,
    );
    await openMenu(tester);
    expect(moveItem, findsNothing);
  });

  testWidgets('connected to This phone, nothing waits for it', (tester) async {
    connection
      ..queue(2)
      ..api = OpenCodeApi(baseUrl: BuiltinLinux.serverUrl);
    await store.setActiveId('phone');
    await mount(tester);
    expect(
      find.textContaining(l10n.serverRowQueuedWaiting(2), findRichText: true),
      findsNothing,
    );
    await openMenu(tester);
    expect(moveItem, findsNothing);
  });

  testWidgets('a prompt queued while the card is open shows at once', (
    tester,
  ) async {
    connection.destination = _laptop;
    await mount(tester);
    expect(
      find.textContaining(l10n.serverRowQueuedWaiting(1), findRichText: true),
      findsNothing,
    );
    connection.queue(1);
    await tester.pumpAndSettle();
    expect(
      find.textContaining(l10n.serverRowQueuedWaiting(1), findRichText: true),
      findsOneWidget,
    );
  });

  group('goldens', () {
    setUpAll(loadCaptureFonts);
    setUp(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(
            const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
            (_) async => null,
          );
    });

    for (final size in [phoneSize, wideSize]) {
      final where = '${size.width.toInt()}x${size.height.toInt()}';
      testWidgets('card with waiting prompts, menu open ($where dark)', (
        tester,
      ) async {
        connection
          ..destination = _laptop
          ..queue(3);
        final boundary = GlobalKey();
        final linux = PhoneLinux(running: false);
        debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
        try {
          await pumpPhone(
            tester,
            size: size,
            linux: linux,
            boundary: boundary,
            home: Scaffold(
              body: SafeArea(
                child: ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    PhoneServerCard(
                      connection: connection,
                      profile: store.saved.first,
                      linux: linux,
                      pollInterval: null,
                    ),
                  ],
                ),
              ),
            ),
          );
          await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
          await tester.pumpAndSettle();
          expect(tester.takeException(), isNull);
          final label = [
            'phone_server_card_queued_menu',
            if (size != phoneSize) where,
            'dark',
          ].join('_');
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile('goldens/$label.png'),
          );
        } finally {
          debugDefaultTargetPlatformOverride = null;
          await unmountPhone(tester);
        }
      });
    }
  });
}

// Golden renders of coord-main (the app root): the bootstrap gate when the
// saved servers cannot be read, the connecting page after three failed
// tries of a Tailscale address, a share waiting for the server as the app's
// status line over that page, and the share-failed line. Phone 412x915 and
// a wide window (1280x800), real fonts at DPR 1.
//
// Regenerate deliberately:
//   flutter test --update-goldens test/revamp/coord_main_golden_test.dart
// and look at every changed image before committing it.
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/diagnostics/app_diagnostics.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/main.dart';
import 'package:opencode_mobile/platform/share_intent.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/widgets/saved_server_connection_card.dart';
import 'package:opencode_mobile/update/shorebird_update_notice.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _name(String shot, Size size, bool light) => [
  'coord_main_$shot',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  light ? 'light' : 'dark',
].join('_');

class _NoUpdateService implements AppUpdateService {
  @override
  bool get isAvailable => false;

  @override
  Future<AppUpdateState> checkForUpdate() async => AppUpdateState.unavailable;

  @override
  Future<void> downloadUpdate() async {}
}

class _OneServerStore extends ProfileStore {
  _OneServerStore({required super.prefs, required this.saved});

  final ServerProfile saved;

  @override
  List<ServerProfile> get profiles => [saved];

  @override
  String? get activeId => saved.id;
}

Future<void> _frame(
  WidgetTester tester,
  Size size,
  Future<void> Function(GlobalKey boundary) body,
) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final boundary = GlobalKey();
  try {
    await body(boundary);
  } finally {
    KitUndo.commitPending();
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
  }
}

Widget _app(GlobalKey boundary, Widget home, {required bool light}) =>
    RepaintBoundary(
      key: boundary,
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: captureTheme(light: light),
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: true),
          child: child!,
        ),
        home: home,
      ),
    );

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  tearDown(KitCapabilities.debugReset);

  for (final (size, light) in [(_phone, false), (_wide, true)]) {
    testWidgets('bootstrap gate: saved servers unreadable ${size.width}', (
      tester,
    ) async {
      await _frame(tester, size, (boundary) async {
        tester.platformDispatcher.platformBrightnessTestValue = light
            ? Brightness.light
            : Brightness.dark;
        addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: AppBootstrapGate(
              diagnostics: AppDiagnosticsController(),
              loader: () async => throw PlatformException(
                code: 'KeyStoreException',
                message: 'User not authenticated',
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.text("Can't read saved servers"), findsOneWidget);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile(
            'goldens/${_name('bootstrap_failed', size, light)}.png',
          ),
        );
      });
    });

    testWidgets(
      'root connecting: a Tailscale address, three tries ${size.width}',
      (tester) async {
        KitCapabilities.registerFlow(
          KitEnableFlows.tailscaleSetup,
          (context, request) async {},
        );
        await _frame(tester, size, (boundary) async {
          await tester.pumpWidget(
            _app(
              boundary,
              KitScreen(
                body: SavedServerConnectionCard(
                  profileName: 'Laptop',
                  baseUrl: 'http://100.101.12.7:4096',
                  error: 'Health check failed: connection refused',
                  attempts: 3,
                  supportsTermux: true,
                  onChangeServer: () {},
                  onRetry: () {},
                ),
              ),
              light: light,
            ),
          );
          await tester.pumpAndSettle();
          expect(find.text('Set up Tailscale'), findsOneWidget);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile(
              'goldens/${_name('root_failed_tailnet', size, light)}.png',
            ),
          );
        });
      },
    );

    testWidgets('share waiting: the line is the share, the card the '
        'connection ${size.width}', (tester) async {
      final messenger = tester.binding.defaultBinaryMessenger;
      const secure = MethodChannel(
        'plugins.it_nomads.com/flutter_secure_storage',
      );
      messenger.setMockMethodCallHandler(
        secure,
        (call) async => call.method == 'readAll' ? <String, String>{} : null,
      );
      addTearDown(() => messenger.setMockMethodCallHandler(secure, null));
      const thermal = EventChannel('oc/thermal/events');
      messenger.setMockStreamHandler(
        thermal,
        MockStreamHandler.inline(onListen: (_, _) {}),
      );
      addTearDown(() => messenger.setMockStreamHandler(thermal, null));
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final controller = ConnectionController(
        _OneServerStore(
          prefs: prefs,
          saved: ServerProfile(
            id: 'laptop',
            name: 'Laptop',
            baseUrl: 'http://192.168.1.20:4096',
          ),
        ),
      );
      addTearDown(controller.dispose);
      final share = ShareIntent(
        channel: const MethodChannel('oc/share-golden'),
      );
      addTearDown(share.dispose);
      await _frame(tester, size, (boundary) async {
        tester.platformDispatcher.platformBrightnessTestValue = light
            ? Brightness.light
            : Brightness.dark;
        addTearDown(tester.platformDispatcher.clearPlatformBrightnessTestValue);
        await tester.pumpWidget(
          RepaintBoundary(
            key: boundary,
            child: ProviderScope(
              overrides: [
                bootstrapProvider.overrideWithValue(
                  AppBootstrap(controller.store),
                ),
                connProvider.overrideWithValue(controller),
              ],
              child: OcApp(
                updateService: _NoUpdateService(),
                shareIntent: share,
              ),
            ),
          ),
        );
        await tester.pump();
        share.pending.value = 'https://example.com/issue/42';
        await tester.pump();
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 600));
        // The card is this server's connection state (with its own Try
        // again), so the page leaves the shared connection line out and
        // says it once (KitScreen.bodySays, P4.4); the app's line carries
        // the waiting share instead.
        expect(
          find.byKey(const ValueKey('connection-status-banner')),
          findsNothing,
        );
        expect(
          find.byKey(const ValueKey('kit-status-app:share-waiting')),
          findsOneWidget,
        );
        expect(find.byType(KitStatusLine), findsOneWidget);
        expect(
          find.text(
            'Connect to a server and the shared text opens in a new '
            'conversation.',
          ),
          findsOneWidget,
        );
        expect(find.textContaining('Laptop'), findsNothing);
        expect(share.pending.value, isNotNull);
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile(
            'goldens/${_name('share_waiting', size, light)}.png',
          ),
        );
      });
      controller.dispose();
    });

    // The line main.dart shows when the shared text could not open a
    // conversation, in the slot every KitScreen draws (the same KitStatus
    // fields and words; the routing itself is in share_routing_test).
    testWidgets('share failed: the app line ${size.width}', (tester) async {
      await _frame(tester, size, (boundary) async {
        await tester.pumpWidget(
          _app(
            boundary,
            Builder(
              builder: (context) {
                final l10n = AppLocalizations.of(context);
                return KitScreen(
                  topBar: KitTopBar(title: l10n.shellTabChats),
                  status: KitStatus(
                    kind: KitStatusKind.work,
                    id: 'app:share-failed',
                    icon: AppIconography.outbox,
                    tone: AppStatusTone.failure,
                    message: l10n.shareFailedLine,
                    supporting: l10n.productErrorTimedOut,
                    action: KitAction(
                      label: l10n.commonRetry,
                      onPressed: () {},
                    ),
                    more: [
                      KitAction(label: l10n.shareFailedCopy, onPressed: () {}),
                      KitAction(
                        label: l10n.shareFailedDiscard,
                        onPressed: () {},
                      ),
                    ],
                  ),
                  body: const SizedBox.expand(),
                );
              },
            ),
            light: light,
          ),
        );
        await tester.pumpAndSettle();
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile(
            'goldens/${_name('share_failed', size, light)}.png',
          ),
        );
      });
    });
  }
}

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'support/fake_server_gateway.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/local_terminal.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/builtin/setup/setup_contract.dart';
import 'package:opencode_mobile/domain/opencode_history_copy.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/l10n/app_localizations_ar.dart';
import 'package:opencode_mobile/l10n/app_localizations_en.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/ui/kit/kit.dart';
import 'package:opencode_mobile/ui/screens/local_terminal_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';
import 'package:opencode_mobile/ui/widgets/server_switcher_sheet.dart';
import 'package:opencode_mobile/state/queued_prompt_removal.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'support/fake_local_terminal.dart';
import 'support/fake_setup_engine.dart';

// Screen D (docs/design/phone-setup-v2-2026-09-24.md): "In-app Ubuntu", the one
// place OpenCode inside the app is managed, in the switcher and in Servers.

class _ThrowingSetupEngine extends FakeSetupEngine {
  @override
  Future<void> run(
    Set<String> ids, {
    Map<String, Map<String, String>> params = const {},
  }) async => throw StateError('private-setup-diagnostic');
}

class _Store extends ProfileStore {
  _Store({required super.prefs});

  final saved = <ServerProfile>[];
  String? selected;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => selected;

  @override
  Future<void> setActiveId(String? id) async => selected = id;

  @override
  Future<void> upsert(ServerProfile profile) async {
    saved
      ..removeWhere((item) => item.id == profile.id)
      ..add(profile);
  }
}

class _Connection extends ConnectionController {
  _Connection(_Store super.store);

  final deleted = <String>[];
  var disconnects = 0;

  @override
  Future<void> disconnect({
    bool keepActive = false,
    bool silent = false,
  }) async {
    disconnects++;
    api = null;
    notifyListeners();
  }

  @override
  Future<DeleteProfileResult> deleteProfileAndLocalData(
    String profileId, {
    QueuedPromptRemovalPlan? queuedPrompts,
    bool keepQueuedPrompts = false,
    bool linuxGone = false,
  }) async {
    deleted.add(profileId);
    final store = this.store as _Store;
    store.saved.removeWhere((profile) => profile.id == profileId);
    if (store.selected == profileId) store.selected = null;
    return const DeleteProfileResult();
  }
}

/// The Android side of OpenCode inside the app: installed, a server that
/// runs once started, a measured size.
class _Linux extends BuiltinLinux {
  bool installed = true;
  bool running = true;
  int? bytesUsed = 734003200;
  final calls = <String>[];
  Completer<void>? startGate;
  BuiltinLinuxException? startError;
  BuiltinLinuxRunResult historyCheck = const BuiltinLinuxRunResult(
    exitCode: 0,
    output: '',
  );
  bool historyCheckThrows = false;
  final historyChecks = <String>[];

  @override
  Future<BuiltinLinuxStatus> status() async => BuiltinLinuxStatus(
    installed: installed,
    phase: installed ? BuiltinLinuxPhase.ready : BuiltinLinuxPhase.idle,
    serverRunning: running,
    bytesUsed: bytesUsed,
  );

  @override
  Future<BuiltinLinuxRunResult> run(
    String script, {
    Duration timeout = const Duration(minutes: 2),
  }) async {
    if (script == openCode1HistorySwitchCheckScript) {
      historyChecks.add(script);
      if (historyCheckThrows) throw StateError('history unavailable');
      return historyCheck;
    }
    return const BuiltinLinuxRunResult(exitCode: 0, output: '');
  }

  @override
  Future<void> startServer(
    String script, {
    int port = 4097,
    BuiltinServerRestoreRecipe? restoreRecipe,
  }) async {
    calls.add('start');
    await startGate?.future;
    if (startError case final error?) throw error;
    running = true;
  }

  @override
  Future<void> stopServer() async {
    calls.add('stop');
    running = false;
  }

  @override
  Future<String> serverLog({int tailBytes = 32768}) async =>
      'opencode server listening\n';

  /// The remove sheet's reading: the runtime and the projects kept apart.
  @override
  Future<BuiltinProjectStorage> projectStorage() async =>
      const BuiltinProjectStorage(
        runtimeBytes: 734003200,
        projectsBytes: 52428800,
        measuredAtMilliseconds: 0,
      );

  @override
  Future<void> uninstall() async {
    calls.add('uninstall');
    installed = false;
    running = false;
  }
}

/// Words and addresses a person must never see for this server.
const _forbidden = ['0 B', '127.0.0.1', '4097', 'built-in'];

void expectNoForbiddenText() {
  for (final text in _forbidden) {
    expect(find.textContaining(text), findsNothing, reason: text);
  }
}

void main() {
  late _Store store;
  late _Connection connection;
  late _Linux linux;
  late FakeSetupEngine engine;
  late FakeLocalTerminalBackend terminalBackend;
  late int progressOpened;
  late int startOpened;
  Set<String>? customizeAnswer;

  ServerProfile phone({
    ServerFlavor flavor = ServerFlavor.v2,
    String id = 'phone',
  }) => ServerProfile(
    id: id,
    name: 'This phone, built-in (OpenCode 1)',
    baseUrl: BuiltinLinux.serverUrl,
    username: BuiltinLinux.serverUsername,
    password: 'secret',
    flavor: flavor,
    serverVersion: '2.0.10',
  );
  final work = ServerProfile(
    id: 'work',
    name: 'Work server',
    baseUrl: 'https://work.example.test',
    password: 'w',
  );

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = _Store(prefs: await SharedPreferences.getInstance());
    connection = _Connection(store);
    linux = _Linux();
    engine = FakeSetupEngine();
    terminalBackend = FakeLocalTerminalBackend();
    PhoneSetup.engine = engine;
    progressOpened = 0;
    startOpened = 0;
    customizeAnswer = {'python'};
    PhoneServerCardRoutes.openProgressOverride = (_) async => progressOpened++;
    PhoneServerCardRoutes.openStartOverride = (_) async => startOpened++;
    PhoneServerCardRoutes.customizeOverride = (_) async => customizeAnswer;
    serverProbe = ({required baseUrl, username, password}) async =>
        linux.running
        ? const ServerProbeResult.success('2.0.10')
        : const ServerProbeResult.failure('refused');
  });

  tearDown(() {
    PhoneServerCardRoutes.openProgressOverride = null;
    PhoneServerCardRoutes.openStartOverride = null;
    PhoneServerCardRoutes.customizeOverride = null;
    serverProbe = probeServerConnection;
    connection.dispose();
  });

  /// The dead in-app server is polled every 10 s while its card shows; a test
  /// that ends with the card still up ends the polling first.
  Future<void> settleLiveness(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    connection.dispose();
  }

  Widget app(
    Widget home, {
    Locale locale = const Locale('en'),
    double textScale = 1,
    Map<String, WidgetBuilder> routes = const {},
  }) => ProviderScope(
    overrides: [
      bootstrapProvider.overrideWithValue(AppBootstrap(store)),
      connProvider.overrideWithValue(connection),
      builtinLinuxProvider.overrideWithValue(linux),
      localTerminalProvider.overrideWith((ref) {
        final sessions = LocalTerminalSessions(backend: terminalBackend);
        ref.onDispose(sessions.dispose);
        return sessions;
      }),
      builtinServerStarterProvider.overrideWith((ref) {
        final starter = BuiltinServerStarter(
          linux: linux,
          pollInterval: Duration.zero,
        );
        ref.onDispose(starter.dispose);
        return starter;
      }),
    ],
    child: MaterialApp(
      locale: locale,
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(
          context,
        ).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      routes: routes,
      home: home,
    ),
  );

  Future<ServerProfile> mountCard(
    WidgetTester tester, {
    ServerProfile? profile,
    bool connected = false,
    VoidCallback? onOpen,
    VoidCallback? onOpenPage,
    void Function(PhoneServerAction, int?)? onAction,
    VoidCallback? onRemoved,
    Locale locale = const Locale('en'),
    Size size = const Size(400, 800),
    double textScale = 1,
    // The working mark turns while a job runs, so such a card never settles.
    bool settle = true,
    bool asRow = false,
  }) async {
    final saved = profile ?? phone();
    store
      ..saved.add(saved)
      ..selected = saved.id;
    tester.view
      ..physicalSize = size
      ..devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      app(
        Scaffold(
          body: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              if (asRow)
                // The Servers list's one group: the phone is one of its rows.
                KitRowGroup(
                  margin: EdgeInsets.zero,
                  children: [
                    PhoneServerCard.row(
                      connection: connection,
                      profile: saved,
                      connected: connected,
                      onOpen: onOpen,
                      onOpenPage: onOpenPage,
                      onAction: onAction,
                      onRemoved: onRemoved,
                      pollInterval: null,
                    ),
                    KitRow(title: 'Laptop', onTap: () {}),
                  ],
                )
              else
                PhoneServerCard(
                  connection: connection,
                  profile: saved,
                  connected: connected,
                  onOpen: onOpen,
                  onAction: onAction,
                  onRemoved: onRemoved,
                  pollInterval: null,
                ),
            ],
          ),
        ),
        locale: locale,
        textScale: textScale,
      ),
    );
    if (settle) {
      await tester.pumpAndSettle();
    } else {
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
    }
    return saved;
  }

  testWidgets('upgrade setup errors keep technical text inside Details', (
    tester,
  ) async {
    PhoneSetup.engine = _ThrowingSetupEngine();
    late BuildContext actionContext;
    await tester.pumpWidget(
      app(
        Builder(
          builder: (context) {
            actionContext = context;
            return const SizedBox.shrink();
          },
        ),
      ),
    );
    final action = runPhoneServerAction(
      actionContext,
      PhoneServerAction.switchRuntime,
      connection: connection,
      linux: linux,
      profile: phone(),
    );
    await tester.pumpAndSettle();
    expect(
      find.text(AppLocalizationsEn().openCode2UpgradeFailed),
      findsOneWidget,
    );
    expect(find.textContaining('private-setup-diagnostic'), findsNothing);
    await tester.tap(find.text('Details'));
    await tester.pumpAndSettle();
    expect(find.textContaining('private-setup-diagnostic'), findsOneWidget);
    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();
    await action;
  });

  // The state word is a KitText; its words are read from the Text it draws.
  String status(WidgetTester tester) => tester
      .widget<Text>(
        find.descendant(
          of: find.byKey(const ValueKey('phone-server-status')),
          matching: find.byType(Text),
        ),
      )
      .data!;

  // The detail is the card row's supporting line (design standard §6), a
  // Text.rich.
  String detail(WidgetTester tester) {
    final text = tester.widget<Text>(
      find.byKey(const ValueKey('phone-server-detail')),
    );
    return text.data ?? text.textSpan!.toPlainText();
  }

  Future<void> choose(WidgetTester tester, String key) async {
    await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(ValueKey(key)));
    await tester.pumpAndSettle();
  }

  group('state', () {
    testWidgets('running: This phone and its version; Stop and Show log in '
        'its menu, no buttons under the row', (tester) async {
      await mountCard(tester, connected: true);
      expect(find.text('On this phone'), findsOneWidget);
      expect(status(tester), 'Running');
      // The size is not on the line (it is said where it matters: Remove).
      expect(detail(tester), 'Connected · OpenCode 2.0.10');
      expect(find.byKey(const ValueKey('phone-server-stop')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('phone-server-stop')), findsOneWidget);
      expect(find.text('Stop OpenCode on this phone'), findsOneWidget);
      expect(
        find.byKey(const ValueKey('phone-server-show-log')),
        findsOneWidget,
      );
      await tester.tapAt(Offset.zero);
      await tester.pumpAndSettle();
      // Connected already: nothing to open, nothing to start.
      expect(find.byKey(const ValueKey('phone-server-open')), findsNothing);
      expect(find.byKey(const ValueKey('phone-server-start')), findsNothing);
      expectNoForbiddenText();
    });

    testWidgets('an unmeasured or empty size is left out, never "0 B"', (
      tester,
    ) async {
      linux.bytesUsed = 0;
      await mountCard(tester);
      expect(detail(tester), 'OpenCode 2.0.10');
      expectNoForbiddenText();
    });

    testWidgets('without a known version it names the OpenCode generation', (
      tester,
    ) async {
      linux.bytesUsed = null;
      await mountCard(tester, profile: phone()..serverVersion = null);
      expect(detail(tester), 'OpenCode 2');
    });

    testWidgets('running and not connected: the row connects, and no button '
        'repeats it', (tester) async {
      var opened = 0;
      await mountCard(tester, onOpen: () => opened++);
      final filled = find.byWidgetPredicate((w) => w is FilledButton);
      expect(filled, findsNothing);
      expect(find.byKey(const ValueKey('phone-server-open')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('phone-server-title')));
      expect(opened, 1);
    });

    testWidgets('not installed any more: Set up', (tester) async {
      linux.installed = false;
      linux.running = false;
      await mountCard(tester);
      expect(status(tester), 'Not set up');
      await tester.tap(find.byKey(const ValueKey('phone-server-set-up')));
      await tester.pumpAndSettle();
      expect(startOpened, 1);
    });

    testWidgets('a running setup job shows Setting up and its progress', (
      tester,
    ) async {
      engine.emit(
        const SetupProgress(
          state: SetupState.running,
          components: [],
          overall: .4,
        ),
      );
      await mountCard(tester, settle: false);
      expect(status(tester), 'Setting up');
      await tester.tap(find.byKey(const ValueKey('phone-server-progress')));
      await tester.pump();
      expect(progressOpened, 1);
      // Installing again while one runs would only queue behind it.
      await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.byKey(const ValueKey('phone-server-update')), findsNothing);
    });

    testWidgets('an interrupted job offers Continue setup', (tester) async {
      engine.emit(
        const SetupProgress(
          state: SetupState.interrupted,
          components: [],
          overall: .4,
        ),
      );
      await mountCard(tester, connected: true);
      await tester.tap(find.byKey(const ValueKey('phone-server-continue')));
      await tester.pumpAndSettle();
      expect(progressOpened, 1);
    });
  });

  group('row variant for the Servers list (slice-R14)', () {
    testWidgets('stopped: Start is the row\'s trailing button, the '
        'state joins the line, and the row draws no panel of its own', (
      tester,
    ) async {
      linux.running = false;
      await mountCard(tester, asRow: true);
      expect(find.byKey(const ValueKey('phone-server-row')), findsOneWidget);
      expect(find.byKey(const ValueKey('phone-server-card')), findsNothing);
      // One group: the caller's, holding the phone and the other server.
      expect(find.byType(KitRowGroup), findsOneWidget);
      expect(find.byKey(const ValueKey('phone-server-status')), findsNothing);
      expect(detail(tester), 'OpenCode 2.0.10 · Stopped');
      final start = find.byKey(const ValueKey('phone-server-start'));
      expect(start, findsOneWidget);
      // The row names the server; its button says only what it does.
      expect(
        find.descendant(of: start, matching: find.text('Start')),
        findsOneWidget,
      );
      // Trailing, on the row's own line: level with the name, at its end.
      final title = tester.getRect(
        find.byKey(const ValueKey('phone-server-title')),
      );
      final button = tester.getRect(start);
      expect(button.left, greaterThanOrEqualTo(title.right));
      expect(button.center.dy, closeTo(title.center.dy, 16));
      await tester.tap(start);
      await tester.pumpAndSettle();
      expect(linux.calls, ['start']);
      expect(detail(tester), 'OpenCode 2.0.10 · Running');
      expect(start, findsNothing);
      expectNoForbiddenText();
    });

    testWidgets('320 dp at 2.5x text: Start moves under the line, nothing '
        'overflows', (tester) async {
      linux.running = false;
      await mountCard(
        tester,
        asRow: true,
        size: const Size(320, 800),
        textScale: 2.5,
      );
      expect(tester.takeException(), isNull);
      final start = tester.getRect(
        find.byKey(const ValueKey('phone-server-start')),
      );
      final title = tester.getRect(
        find.byKey(const ValueKey('phone-server-title')),
      );
      expect(start.top, greaterThan(title.bottom));
    });

    testWidgets('not set up: Set up is the trailing button; the rest is in '
        'the row menu', (tester) async {
      linux.installed = false;
      linux.running = false;
      await mountCard(tester, asRow: true);
      expect(detail(tester), 'OpenCode 2.0.10 · Not set up');
      await tester.tap(find.byKey(const ValueKey('phone-server-set-up')));
      await tester.pumpAndSettle();
      expect(startOpened, 1);
      await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('phone-server-remove')), findsOneWidget);
    });

    testWidgets('a tap opens the server page; Connect is a named menu act', (
      tester,
    ) async {
      var page = 0;
      var connects = 0;
      await mountCard(
        tester,
        asRow: true,
        onOpen: () => connects++,
        onOpenPage: () => page++,
      );
      await tester.tap(find.byKey(const ValueKey('phone-server-title')));
      await tester.pumpAndSettle();
      expect(page, 1);
      expect(connects, 0);
      expect(find.byKey(const ValueKey('phone-server-stop')), findsNothing);
      await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const ValueKey('phone-server-connect')));
      await tester.pumpAndSettle();
      expect(connects, 1);
    });

    testWidgets('running and connected: no button; Stop lives in the menu', (
      tester,
    ) async {
      await mountCard(tester, asRow: true, connected: true);
      expect(detail(tester), 'Connected · OpenCode 2.0.10 · Running');
      expect(find.byType(KitButton), findsNothing);
      await choose(tester, 'phone-server-stop');
      expect(linux.calls, ['stop']);
    });
  });

  group('start and stop', () {
    testWidgets('native start details never become the card explanation', (
      tester,
    ) async {
      linux.running = false;
      linux.startError = const BuiltinLinuxException(
        'The phone operation could not finish.',
        code: 'builtin_linux',
        details: 'IllegalStateException: ownershipUnknown at BuiltinLinux.x:1',
      );
      await mountCard(tester);
      await tester.tap(find.byKey(const ValueKey('phone-server-start')));
      await tester.pumpAndSettle();
      expect(
        find.text(AppLocalizationsEn().inAppServerOwnershipRecoveryBody),
        findsOneWidget,
      );
      expect(find.textContaining('IllegalStateException'), findsNothing);
      expect(find.textContaining('ownershipUnknown'), findsNothing);
      expect(status(tester), 'Stopped');
      expect(find.byKey(const ValueKey('phone-server-start')), findsNothing);
      expect(
        find.byKey(const ValueKey('phone-server-recover-ownership')),
        findsOneWidget,
      );
    });

    testWidgets('Start starts the server, shows Starting, then Running', (
      tester,
    ) async {
      linux.running = false;
      linux.startGate = Completer<void>();
      await mountCard(tester);
      expect(status(tester), 'Stopped');
      await tester.tap(find.byKey(const ValueKey('phone-server-start')));
      await tester.pump();
      await tester.pump();
      expect(status(tester), 'Starting');
      expect(linux.calls, ['start']);
      linux.startGate!.complete();
      await tester.pumpAndSettle();
      expect(status(tester), 'Running');
    });

    testWidgets('the first start asks once to keep the server running (P6.7)', (
      tester,
    ) async {
      const lifecycle = MethodChannel('oc/lifecycle');
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(
        lifecycle,
        (call) async => call.method == 'keepAliveInfo'
            ? {'manufacturer': 'Google', 'batteryOptimizationIgnored': false}
            : null,
      );
      addTearDown(() => messenger.setMockMethodCallHandler(lifecycle, null));
      linux.running = false;
      await mountCard(tester);
      await tester.tap(find.byKey(const ValueKey('phone-server-start')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('phone-consent-batteryExemption')),
        findsOneWidget,
      );
      await tester.tap(find.text('Not now'));
      await tester.pumpAndSettle();

      // A later start asks nothing.
      await choose(tester, 'phone-server-stop');
      await tester.tap(find.byKey(const ValueKey('phone-server-start')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('phone-consent-batteryExemption')),
        findsNothing,
      );
    });

    testWidgets('Stop stops the server', (tester) async {
      await mountCard(tester, connected: true);
      await choose(tester, 'phone-server-stop');
      expect(linux.calls, ['stop']);
      expect(status(tester), 'Stopped');
      expect(find.byKey(const ValueKey('phone-server-start')), findsOneWidget);
    });

    testWidgets('Show log opens the log in a terminal view', (tester) async {
      await mountCard(tester);
      await choose(tester, 'phone-server-show-log');
      // The one log view (KIT-31).
      expect(find.byType(KitLogPanel), findsOneWidget);
      expect(find.textContaining('opencode server listening'), findsOneWidget);
    });
  });

  group('menu', () {
    testWidgets('menu offers no runtime switch', (tester) async {
      await mountCard(tester);
      await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('phone-server-switch')), findsNothing);
      expect(find.text('Older OpenCode'), findsNothing);
    });

    testWidgets('Update uses OpenCode 2', (tester) async {
      await mountCard(tester);
      await choose(tester, 'phone-server-update');
      expect(engine.runs, [
        {'opencode'},
      ]);
      expect(engine.runParams.single, {
        'opencode': {'runtime': 'opencode2'},
      });
      expect(progressOpened, 1);
    });

    testWidgets('Update on OpenCode 2 keeps OpenCode 2', (tester) async {
      await mountCard(tester, profile: phone(flavor: ServerFlavor.v2));
      await choose(tester, 'phone-server-update');
      expect(engine.runParams.single, {
        'opencode': {'runtime': 'opencode2'},
      });
    });

    testWidgets('Add tools installs what the sheet chose', (tester) async {
      await mountCard(tester);
      await choose(tester, 'phone-server-add-tools');
      expect(engine.runs, [
        {'python'},
      ]);
      expect(engine.runParams.single, {
        ...SetupJobParams.adding({'python'}),
        'opencode': {'runtime': 'opencode2'},
      });
      expect(progressOpened, 1);
    });

    testWidgets('Add tools preserves the selected OpenCode 2 runtime', (
      tester,
    ) async {
      customizeAnswer = {'python', 'aiteam'};
      await mountCard(tester, profile: phone(flavor: ServerFlavor.v2));
      await choose(tester, 'phone-server-add-tools');
      expect(engine.runs.single, {'python', 'aiteam'});
      expect(engine.runParams.single, {
        ...SetupJobParams.adding({'python', 'aiteam'}),
        'opencode': {'runtime': 'opencode2'},
      });
      expect(progressOpened, 1);
    });

    testWidgets('Add tools closed without a choice installs nothing', (
      tester,
    ) async {
      customizeAnswer = null;
      await mountCard(tester);
      await choose(tester, 'phone-server-add-tools');
      expect(engine.runs, isEmpty);
      expect(progressOpened, 0);
    });

    testWidgets('Remove says how much space comes back, then uninstalls', (
      tester,
    ) async {
      var removed = 0;
      store.saved.add(phone(flavor: ServerFlavor.v2, id: 'phone2'));
      store.saved.add(work);
      connection.api = FakeServerGateway(baseUrl: BuiltinLinux.serverUrl);
      await mountCard(tester, connected: true, onRemoved: () => removed++);
      await choose(tester, 'phone-server-remove');
      // What survives is said first: the default keeps the projects.
      expect(
        find.text(
          'OpenCode and its tools are removed, freeing about 700.0 MB.',
        ),
        findsOneWidget,
      );
      // What goes and what stays, line by line, with the projects' size.
      expect(
        find.text('Conversations and settings inside OpenCode are deleted'),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('phone-server-remove-kept')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('phone-server-remove-everything')),
        findsOneWidget,
      );
      await tester.tap(
        find.byKey(const ValueKey('phone-server-remove-confirm')),
      );
      await tester.pumpAndSettle();

      expect(connection.disconnects, 1);
      expect(linux.calls, ['uninstall']);
      // Every saved "In-app Ubuntu" entry goes; other servers stay.
      expect(connection.deleted.toSet(), {'phone', 'phone2'});
      expect(store.saved.map((profile) => profile.id), ['work']);
      expect(removed, 1);
      // The card goes with the server; no toast repeats it (shared-phone-1:
      // failures are one alert, success says nothing more).
      expect(find.text('OpenCode was removed from this phone.'), findsNothing);
      await settleLiveness(tester);
    });

    testWidgets('Remove cancelled changes nothing', (tester) async {
      await mountCard(tester);
      await choose(tester, 'phone-server-remove');
      await tester.tap(find.text('Cancel'));
      await tester.pumpAndSettle();
      expect(linux.calls, isEmpty);
      expect(connection.deleted, isEmpty);
    });

    testWidgets(
      'Terminal opens a shell on this phone with the server stopped',
      (tester) async {
        linux.running = false;
        await mountCard(tester);
        expect(status(tester), 'Stopped');
        await choose(tester, 'phone-server-terminal');
        expect(find.byType(LocalTerminalView), findsOneWidget);
        expect(terminalBackend.calls, contains(startsWith('start')));
        // The server was left alone: the shell does not need it.
        expect(linux.calls, isEmpty);
      },
    );

    testWidgets(
      'Terminal stays in the menu while a server start does not answer',
      (tester) async {
        linux.running = false;
        linux.startGate = Completer<void>();
        await mountCard(tester);
        await tester.tap(find.byKey(const ValueKey('phone-server-start')));
        await tester.pump();
        await tester.pump();
        expect(status(tester), 'Starting');
        await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 400));
        // Only the way in: nothing that would change the server mid-start.
        expect(
          find.byKey(const ValueKey('phone-server-terminal')),
          findsOneWidget,
        );
        expect(find.byKey(const ValueKey('phone-server-remove')), findsNothing);
        await tester.tap(find.byKey(const ValueKey('phone-server-terminal')));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 400));
        expect(find.byType(LocalTerminalView), findsOneWidget);
        expect(terminalBackend.calls, contains(startsWith('start')));
        linux.startGate!.complete();
        await tester.pumpAndSettle();
      },
    );

    testWidgets('a host that must close first receives the action instead', (
      tester,
    ) async {
      final handed = <(PhoneServerAction, int?)>[];
      await mountCard(
        tester,
        onAction: (action, bytes) => handed.add((action, bytes)),
      );
      await choose(tester, 'phone-server-update');
      expect(handed, [(PhoneServerAction.update, 734003200)]);
      expect(engine.runs, isEmpty);
    });
  });

  group('layout', () {
    for (final locale in const [Locale('en'), Locale('ar')]) {
      testWidgets('2.5x on 320dp, 48dp targets (${locale.languageCode})', (
        tester,
      ) async {
        await mountCard(
          tester,
          onOpen: () {},
          locale: locale,
          size: const Size(320, 800),
          textScale: 2.5,
        );
        expect(tester.takeException(), isNull);
        for (final key in ['phone-server-menu']) {
          final size = tester.getSize(find.byKey(ValueKey(key)));
          expect(size.height, greaterThanOrEqualTo(48), reason: key);
          expect(size.width, greaterThanOrEqualTo(48), reason: key);
        }
        if (locale.languageCode == 'ar') {
          expect(find.text('على هذا الهاتف'), findsOneWidget);
          expect(find.text('يعمل'), findsOneWidget);
          expect(
            Directionality.of(tester.element(find.text('على هذا الهاتف'))),
            TextDirection.rtl,
          );
          // At 2.5x the status moves under the name and starts where the
          // name starts: on the right in Arabic.
          final status = tester.getRect(find.text('يعمل'));
          final title = tester.getRect(find.text('على هذا الهاتف'));
          expect(status.top, greaterThanOrEqualTo(title.bottom));
          expect((status.right - title.right).abs(), lessThan(1));
        }
        expectNoForbiddenText();
      });
    }
  });

  test('the in-app server is named This phone everywhere', () {
    expect(serverDisplayName(phone(), AppLocalizationsEn()), 'On this phone');
    expect(serverDisplayName(phone(), AppLocalizationsAr()), 'على هذا الهاتف');
    expect(serverDisplayName(work, AppLocalizationsEn()), 'Work server');
    expect(formatPhoneStorage(1181116006), '1.1 GB');
    expect(formatPhoneStorage(734003200), '700.0 MB');
  });

  test('two saved servers that read the same are told apart by port', () {
    ServerProfile at(String id, String url) =>
        ServerProfile(id: id, name: '10.0.2.2', baseUrl: url);
    final a = at('a', 'http://10.0.2.2:4180');
    final b = at('b', 'http://10.0.2.2:4181');
    final c = at('c', 'http://10.0.2.2:4181');
    final alone = at('d', 'http://10.0.2.2:4182');
    final en = AppLocalizationsEn();
    expect(serverDisplayName(a, en, among: [a]), 'Computer at 10.0.2.2');
    expect(
      serverDisplayName(a, en, among: [a, b, c]),
      'Computer at 10.0.2.2:4180',
    );
    final names = {
      for (final p in [a, b, c]) serverDisplayName(p, en, among: [a, b, c]),
    };
    expect(names.length, 3, reason: 'same address and port gets a number');
    expect(
      serverDisplayName(alone, en, among: [alone, work]),
      isNot(contains(':')),
    );
  });

  test(
    'the same host over HTTP and HTTPS is told apart by port and secure',
    () {
      final plain = ServerProfile(
        id: 'p',
        name: '10.0.2.2',
        baseUrl: 'http://10.0.2.2:4097',
      );
      final tls = ServerProfile(
        id: 't',
        name: '10.0.2.2',
        baseUrl: 'https://10.0.2.2:4443',
      );
      final en = AppLocalizationsEn();
      expect(
        serverDisplayName(plain, en, among: [plain, tls]),
        'Computer at 10.0.2.2:4097',
      );
      expect(
        serverDisplayName(tls, en, among: [plain, tls]),
        'Computer at 10.0.2.2:4443 (secure)',
      );
    },
  );

  // Open point 4 of phone setup v2: setup saves one in-app profile per
  // OpenCode generation and names both "In-app Ubuntu".
  group('OpenCode 2 on this phone', () {
    testWidgets('alone, the card is just This phone', (tester) async {
      await mountCard(tester, connected: true);
      expect(
        tester
            .widget<Text>(find.byKey(const ValueKey('phone-server-title')))
            .data,
        'On this phone',
      );
    });
  });

  group('hosts', () {
    testWidgets('the switcher shows the card, not the old in-app row', (
      tester,
    ) async {
      store.saved.addAll([phone(), work]);
      store.selected = 'phone';
      connection.api = FakeServerGateway(baseUrl: BuiltinLinux.serverUrl);
      ServerSwitcherChoice? choice;
      await tester.pumpWidget(
        app(
          Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async =>
                    choice = await showServerSwitcher(context, connection),
                child: const Text('Switch'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Switch'));
      await tester.pumpAndSettle();

      expect(
        find.byKey(const ValueKey('server-switcher-phone-phone')),
        findsOneWidget,
      );
      expect(
        find.byKey(const ValueKey('server-switcher-current')),
        findsNothing,
      );
      expect(
        find.byKey(const ValueKey('server-switcher-profile-work')),
        findsOneWidget,
      );
      expectNoForbiddenText();

      // The switcher's row has no ⋮: a tap on the server in use opens its
      // menu (vision 2026-10-20), with the check at its end.
      expect(find.byKey(const ValueKey('phone-server-menu')), findsNothing);
      expect(
        find.byKey(const ValueKey('phone-server-current')),
        findsOneWidget,
      );
      await tester.tap(find.byKey(const ValueKey('phone-server-title')));
      await tester.pumpAndSettle();
      // The sheet closes first; the shell runs the action.
      await tester.tap(find.byKey(const ValueKey('phone-server-update')));
      await tester.pumpAndSettle();
      expect(choice, isA<ServerSwitcherPhoneAction>());
      final action = choice! as ServerSwitcherPhoneAction;
      expect(action.action, PhoneServerAction.update);
      expect(action.profileID, 'phone');
      expect(engine.runs, isEmpty);
    });

    testWidgets('the switcher tells same-host servers apart', (tester) async {
      final plain = ServerProfile(
        id: 'p1',
        name: '10.0.2.2',
        baseUrl: 'http://10.0.2.2:4097',
      );
      final tls = ServerProfile(
        id: 't1',
        name: '10.0.2.2',
        baseUrl: 'https://10.0.2.2:4443',
      );
      store.saved.addAll([plain, tls]);
      store.selected = 'p1';
      connection.api = FakeServerGateway(baseUrl: plain.baseUrl);
      await tester.pumpWidget(
        app(
          Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => showServerSwitcher(context, connection),
                child: const Text('Switch'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Switch'));
      await tester.pumpAndSettle();
      expect(find.text('Computer at 10.0.2.2:4097'), findsOneWidget);
      expect(find.text('Computer at 10.0.2.2:4443 (secure)'), findsOneWidget);
    });

    testWidgets('the switcher lists This phone when another server is on', (
      tester,
    ) async {
      store.saved.addAll([work, phone()]);
      store.selected = 'work';
      connection.api = FakeServerGateway(baseUrl: work.baseUrl);
      ServerSwitcherChoice? choice;
      await tester.pumpWidget(
        app(
          Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () async =>
                    choice = await showServerSwitcher(context, connection),
                child: const Text('Switch'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Switch'));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('server-switcher-current')),
        findsOneWidget,
      );
      // The row's own tap connects (no button repeats it).
      await tester.tap(find.byKey(const ValueKey('phone-server-title')));
      await tester.pumpAndSettle();
      final open = choice! as ServerSwitcherOpenServers;
      expect(open.request?.profileID, 'phone');
      expectNoForbiddenText();
    });

    testWidgets('Servers lists the card instead of the address row', (
      tester,
    ) async {
      store.saved.addAll([phone(), work]);
      store.selected = 'phone';
      tester.view
        ..physicalSize = const Size(400, 1600)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(app(const ServersScreen()));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('phone-server-card-phone')),
        findsOneWidget,
      );
      expect(find.text('Work server'), findsOneWidget);
      // The old experimental entry is for setting up, not for a phone that
      // already has OpenCode.
      expect(
        find.byKey(const ValueKey('quick-add-builtin-card')),
        findsNothing,
      );
      // R15: This phone is a row of the one list (the row variant), not a
      // card of its own above it, and leads it while nothing is urgent.
      final list = find.byKey(const ValueKey('servers-list'));
      expect(
        find.descendant(
          of: list,
          matching: find.byKey(const ValueKey('phone-server-row')),
        ),
        findsOneWidget,
      );
      expect(find.byKey(const ValueKey('phone-server-card')), findsNothing);
      expect(
        tester.getTopLeft(find.byKey(const ValueKey('phone-server-row'))).dy,
        lessThan(tester.getTopLeft(find.text('Work server')).dy),
      );
      // The server in use leaves from its own row's menu.
      await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
      await tester.pumpAndSettle();
      expect(
        find.byKey(const ValueKey('phone-server-disconnect')),
        findsNothing,
        reason: 'not connected: nothing to leave',
      );
      expectNoForbiddenText();
    });

    // Sweep 3680: a list of one row drew a stray hairline at its foot.
    testWidgets('a list of one row has no divider', (tester) async {
      store.saved.add(phone());
      store.selected = 'phone';
      tester.view
        ..physicalSize = const Size(400, 1600)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(app(const ServersScreen()));
      await tester.pumpAndSettle();
      expect(
        find.descendant(
          of: find.byKey(const ValueKey('servers-list')),
          matching: find.byType(KitDivider),
        ),
        findsNothing,
      );
    });

    testWidgets('a working server ranks above This phone in the one list', (
      tester,
    ) async {
      store.saved.addAll([phone(), work]);
      store.selected = 'work';
      connection
        ..api = FakeServerGateway(baseUrl: work.baseUrl)
        ..busySessions = {'s1'};
      tester.view
        ..physicalSize = const Size(400, 1600)
        ..devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(app(const ServersScreen()));
      await tester.pumpAndSettle();
      final list = find.byKey(const ValueKey('servers-list'));
      final phoneRow = find.descendant(
        of: list,
        matching: find.byKey(const ValueKey('phone-server-row')),
      );
      final workRow = find.descendant(
        of: list,
        matching: find.byKey(const ValueKey('server-row-work')),
      );
      expect(phoneRow, findsOneWidget);
      expect(workRow, findsOneWidget);
      expect(
        tester.getTopLeft(workRow).dy,
        lessThan(tester.getTopLeft(phoneRow).dy),
      );
    });
  });
}

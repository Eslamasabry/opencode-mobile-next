// P0.7 (re-landed on This phone, slice-P0.7-port): removing This phone keeps
// projects by default. The question says what survives and names the space
// each choice frees; its confirm keeps the projects; "Delete everything" is
// a second question that needs the app name typed. Prompts queued for the
// phone's server are said to move to Saved prompts on both paths.
//
// Goldens of both questions: phone 412x915 and one wide window (1280x800),
// dark and light (owner decision 2026-09-27: no Arabic), real fonts, DPR 1.
// Regenerate deliberately:
//   flutter test --update-goldens test/phone_server_remove_keep_projects_test.dart
// and look at every changed image before committing it.
import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/server_probe.dart';
import 'package:opencode_mobile/builtin/builtin_linux.dart';
import 'package:opencode_mobile/builtin/builtin_server.dart';
import 'package:opencode_mobile/builtin/setup/phone_setup.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/kit/kit_buttons.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show loadCaptureFonts;
import 'revamp/screen_phone_1_fixtures.dart'
    show phoneSize, pumpPhone, unmountPhone, wideSize;
import 'revamp/shared_phone_1_fixtures.dart';
import 'support/fake_setup_engine.dart';

const _runtime = 734003200; // 700.0 MB
const _projects = 1288490188; // 1.2 GB

/// The in-app server with the removal bridge measured and recorded. Its
/// `uninstall` is the real bridge's: the preserving `remove()`.
class _RemovalLinux extends PhoneLinux {
  _RemovalLinux({this.sizes, this.failRemove = false, this.measureGate});

  final BuiltinProjectStorage? sizes;
  bool failRemove;

  /// Holds the measurement back (a phone with many projects).
  final Completer<void>? measureGate;

  @override
  Future<BuiltinProjectStorage> projectStorage() async {
    calls.add('measure');
    await measureGate?.future;
    final value = sizes;
    if (value == null) {
      throw const BuiltinLinuxException(
        'Project storage could not be measured.',
        code: 'storage_unavailable',
      );
    }
    return value;
  }

  @override
  Future<void> uninstall() => remove();

  @override
  Future<void> remove({
    bool alsoDeleteProjects = false,
    String? confirmationName,
  }) async {
    calls.add(
      alsoDeleteProjects ? 'remove all ($confirmationName)' : 'remove keep',
    );
    if (failRemove) {
      throw const BuiltinLinuxException(
        'The runtime could not be removed.',
        code: 'removal_failed',
      );
    }
    installed = false;
    running = false;
  }
}

/// A connection with prompts queued for the phone's server.
class _QueueConnection extends PhoneConnection {
  _QueueConnection(super.store);

  int queued = 0;

  @override
  int queuedPromptCountForProfile(String profileID) =>
      profileID == 'phone' ? queued : 0;
}

const _measured = BuiltinProjectStorage(
  runtimeBytes: _runtime,
  projectsBytes: _projects,
  measuredAtMilliseconds: 1,
);

void main() {
  late PhoneStore store;
  late _QueueConnection connection;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    store = PhoneStore(prefs: await SharedPreferences.getInstance());
    connection = _QueueConnection(store);
    PhoneSetup.engine = FakeSetupEngine();
    serverProbe = ({required baseUrl, username, password}) async =>
        const ServerProbeResult.success('1.18.29');
  });

  tearDown(() {
    serverProbe = probeServerConnection;
    connection.dispose();
  });

  /// Mounts This phone and opens its Remove question. Returns a flag that
  /// turns true once the card reports the server gone.
  Future<ValueNotifier<bool>> openRemove(
    WidgetTester tester,
    _RemovalLinux linux, {
    bool settle = true,
  }) async {
    final removed = ValueNotifier(false);
    addTearDown(removed.dispose);
    final profile = phoneProfile();
    store.saved.add(profile);
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
                PhoneServerCard(
                  connection: connection,
                  profile: profile,
                  linux: linux,
                  pollInterval: null,
                  onRemoved: () => removed.value = true,
                ),
              ],
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('phone-server-menu')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('phone-server-remove')));
    if (!settle) return removed;
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsOneWidget,
    );
    return removed;
  }

  Finder inSheet(String key, Finder matching) =>
      find.descendant(of: find.byKey(ValueKey(key)), matching: matching);

  testWidgets('the default keeps projects and names what each choice frees', (
    tester,
  ) async {
    final linux = _RemovalLinux(sizes: _measured);
    final removed = await openRemove(tester, linux);
    expect(linux.calls, ['measure'], reason: 'the question changes nothing');

    // What goes, with the space it frees; what stays, with its size.
    expect(find.text(l10n.removeFromPhoneKeepBody('700.0 MB')), findsOne);
    expect(find.text(l10n.removeFromPhoneKeepLost), findsOneWidget);
    expect(find.text(l10n.removeFromPhoneKeepKeptSize('1.2 GB')), findsOne);
    // The other choice names its larger figure before it is chosen.
    expect(
      inSheet(
        'phone-server-remove-everything',
        find.text(l10n.removeFromPhoneDeleteAllChoiceSize('1.9 GB')),
      ),
      findsOneWidget,
    );
    // Nothing in the default question says every project goes.
    expect(find.textContaining('every project'), findsNothing);

    final confirm = find.byKey(const ValueKey('phone-server-remove-confirm'));
    expect(
      find.descendant(
        of: confirm,
        matching: find.text('Remove OpenCode, keep my projects'),
      ),
      findsOneWidget,
    );
    await tester.tap(confirm);
    await tester.pumpAndSettle();

    expect(linux.calls, ['measure', 'remove keep']);
    expect(connection.deleted, ['phone']);
    expect(removed.value, isTrue);
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsNothing,
    );
  });

  testWidgets('Delete everything needs the exact app name typed', (
    tester,
  ) async {
    final linux = _RemovalLinux(sizes: _measured);
    final removed = await openRemove(tester, linux);
    await tester.tap(
      find.byKey(const ValueKey('phone-server-remove-everything')),
    );
    await tester.pumpAndSettle();

    const sheet = 'phone-server-delete-everything-sheet';
    expect(find.byKey(const ValueKey(sheet)), findsOneWidget);
    expect(find.text(l10n.removeFromPhoneDeleteTitle), findsOneWidget);
    expect(find.text(l10n.removeFromPhoneDeleteBody('1.9 GB')), findsOne);
    expect(find.text(l10n.removeFromPhoneDeleteLost), findsOneWidget);

    final confirm = find.byKey(
      const ValueKey('phone-server-delete-everything-confirm'),
    );
    await tester.tap(confirm, warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(linux.calls, ['measure'], reason: 'nothing typed yet');

    final field = inSheet(sheet, find.byType(TextField));
    await tester.enterText(field, 'opencode');
    await tester.pumpAndSettle();
    await tester.tap(confirm, warnIfMissed: false);
    await tester.pumpAndSettle();
    expect(linux.calls, ['measure'], reason: 'the name is case-sensitive');

    await tester.enterText(field, BuiltinLinux.deletionConfirmationName);
    await tester.pumpAndSettle();
    await tester.tap(confirm);
    await tester.pumpAndSettle();

    expect(linux.calls, ['measure', 'remove all (OpenCode)']);
    expect(connection.deleted, ['phone']);
    expect(removed.value, isTrue);
  });

  testWidgets('cancelling Delete everything removes nothing', (tester) async {
    final linux = _RemovalLinux(sizes: _measured);
    final removed = await openRemove(tester, linux);
    await tester.tap(
      find.byKey(const ValueKey('phone-server-remove-everything')),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();

    expect(linux.calls, ['measure']);
    expect(connection.deleted, isEmpty);
    expect(removed.value, isFalse);
  });

  testWidgets('an unmeasured phone leaves the figures out, never 0 B', (
    tester,
  ) async {
    final linux = _RemovalLinux();
    await openRemove(tester, linux);

    expect(find.text(l10n.removeFromPhoneKeepBodyUnmeasured), findsOneWidget);
    expect(find.text(l10n.removeFromPhoneKeepKept), findsOneWidget);
    expect(find.text(l10n.removeFromPhoneDeleteAllChoice), findsOneWidget);
    expect(find.textContaining('0 B'), findsNothing);
  });

  testWidgets('a slow measurement opens the question without figures', (
    tester,
  ) async {
    final gate = Completer<void>();
    final linux = _RemovalLinux(sizes: _measured, measureGate: gate);
    await openRemove(tester, linux, settle: false);
    await tester.pump(const Duration(seconds: 1));
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsNothing,
    );
    await tester.pump(const Duration(seconds: 3));
    await tester.pumpAndSettle();
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsOneWidget,
    );
    expect(find.text(l10n.removeFromPhoneKeepBodyUnmeasured), findsOneWidget);
    gate.complete();
  });

  testWidgets('empty projects are kept without a 0 B figure', (tester) async {
    final linux = _RemovalLinux(
      sizes: const BuiltinProjectStorage(
        runtimeBytes: _runtime,
        projectsBytes: 0,
        measuredAtMilliseconds: 1,
      ),
    );
    await openRemove(tester, linux);
    expect(find.text(l10n.removeFromPhoneKeepKept), findsOneWidget);
    expect(find.textContaining('0 B'), findsNothing);
  });

  testWidgets('queued prompts are said to be kept on both paths', (
    tester,
  ) async {
    connection.queued = 2;
    final linux = _RemovalLinux(sizes: _measured);
    await openRemove(tester, linux);
    expect(
      inSheet(
        'phone-server-remove-sheet',
        find.text(l10n.serversRemoveQueuedKept(2)),
      ),
      findsOneWidget,
    );
    await tester.tap(
      find.byKey(const ValueKey('phone-server-remove-everything')),
    );
    await tester.pumpAndSettle();
    expect(
      inSheet(
        'phone-server-delete-everything-sheet',
        find.text(l10n.serversRemoveQueuedKept(2)),
      ),
      findsOneWidget,
    );
  });

  testWidgets('a failed removal keeps the question open and the entry', (
    tester,
  ) async {
    final linux = _RemovalLinux(sizes: _measured, failRemove: true);
    final removed = await openRemove(tester, linux);
    await tester.tap(find.byKey(const ValueKey('phone-server-remove-confirm')));
    await tester.pumpAndSettle();

    expect(find.byKey(const ValueKey('kit-confirm-failed')), findsOneWidget);
    expect(
      find.byKey(const ValueKey('phone-server-remove-sheet')),
      findsOneWidget,
    );
    // No raw error text: the bridge's words are not copy.
    expect(find.textContaining('runtime could not be removed'), findsNothing);
    expect(connection.deleted, isEmpty);
    expect(removed.value, isFalse);
    // The attempt ended: the card behind no longer says it is removing.
    expect(find.text(l10n.phoneServerCardRemoving), findsNothing);

    // Try again once the phone lets it go.
    linux.failRemove = false;
    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();
    expect(linux.calls, ['measure', 'remove keep', 'remove keep']);
    expect(connection.deleted, ['phone']);
    expect(removed.value, isTrue);
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

    Future<void> shot(
      WidgetTester tester,
      String name, {
      required Size size,
      required bool light,
      bool deleteEverything = false,
    }) async {
      final boundary = GlobalKey();
      final linux = _RemovalLinux(sizes: _measured);
      debugDefaultTargetPlatformOverride = TargetPlatform.android; // ARCH-11
      try {
        await pumpPhone(
          tester,
          size: size,
          light: light,
          linux: linux,
          boundary: boundary,
          home: Scaffold(
            body: Builder(
              builder: (context) => Center(
                child: KitButton.primary(
                  label: 'open',
                  expand: false,
                  onPressed: () => confirmPhoneRuntimeRemoval(
                    context,
                    linux: linux,
                    queuedPrompts: 2,
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.tap(find.text('open'));
        await tester.pumpAndSettle();
        if (deleteEverything) {
          await tester.tap(
            find.byKey(const ValueKey('phone-server-remove-everything')),
          );
          await tester.pumpAndSettle();
        }
        expect(tester.takeException(), isNull);
        final label = [
          name,
          if (size != phoneSize) '${size.width.toInt()}x${size.height.toInt()}',
          light ? 'light' : 'dark',
        ].join('_');
        await expectLater(
          find.byKey(boundary),
          matchesGoldenFile('goldens/$label.png'),
        );
      } finally {
        debugDefaultTargetPlatformOverride = null;
        await unmountPhone(tester);
      }
    }

    for (final size in [phoneSize, wideSize]) {
      for (final light in [false, true]) {
        final where =
            '${size.width.toInt()}x${size.height.toInt()} '
            '${light ? 'light' : 'dark'}';
        testWidgets('keep my projects question ($where)', (tester) async {
          await shot(
            tester,
            'phone_server_remove_keep',
            size: size,
            light: light,
          );
        });
        testWidgets('delete everything question ($where)', (tester) async {
          await shot(
            tester,
            'phone_server_remove_delete_everything',
            size: size,
            light: light,
            deleteEverything: true,
          );
        });
      }
    }
  });
}

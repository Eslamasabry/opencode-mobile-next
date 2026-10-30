// Moving from Termux to the in-app server (slice-migration-ui): the review
// (what moves, what is only a private copy, what does not move, the space),
// the copy with its real stage, Stop, leaving the app, resume after the app
// was closed, done with its next steps, every failure code in plain words
// with the code only under Details, and the entry points: This phone's row
// for a Termux user, the one-time offer under the Termux row on Servers,
// and setup v2 when the in-app server is missing. The controller is the real
// one over fakes (test/support/termux_migration_fakes.dart).
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/termux_migration.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/phone_host.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/termux_migration_owner.dart';
import 'package:opencode_mobile/termux/bridge.dart';
import 'package:opencode_mobile/ui/screens/phone_setup/phone_setup_progress_screen.dart';
import 'package:opencode_mobile/ui/screens/termux_migration_screen.dart';
import 'package:opencode_mobile/ui/kit/kit.dart' show KitBidi;
import 'package:opencode_mobile/ui/screens/this_phone_screen.dart';
import 'package:opencode_mobile/ui/widgets/phone_server_card.dart'
    show formatPhoneStorage;
import 'package:opencode_mobile/ui/widgets/termux_migration_entry.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../tool/capture/fixtures.dart' show captureTheme;
import 'revamp/screen_phone_1_fixtures.dart';
import 'support/fake_setup_engine.dart';
import 'support/termux_channel_fixture.dart';
import 'support/termux_migration_fakes.dart';

final _l10n = lookupAppLocalizations(const Locale('en'));
const _secret = 'synthetic-test-secret';

ServerProfile _termux() => ServerProfile(
  id: migrationSource,
  name: 'This phone',
  baseUrl: TermuxBridge.managedServerUrl,
  password: _secret,
  serverVersion: '1.18.32',
);

Future<void> _frames(WidgetTester tester, {int count = 12}) async {
  for (var i = 0; i < count; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

/// Every word on screen, for the checks that no raw or secret text shows.
String _screenText(WidgetTester tester) => [
  for (final widget in tester.widgetList<RichText>(
    find.byType(RichText, skipOffstage: false),
  ))
    widget.text.toPlainText(),
  // Selectable text (technical values) draws through an editable text.
  for (final widget in tester.widgetList<EditableText>(
    find.byType(EditableText, skipOffstage: false),
  ))
    widget.controller.text,
].join('\n');

Finder _key(String key) => find.byKey(ValueKey(key));

void main() {
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  final setUps = <TermuxRuntime>[];
  var providersOpened = 0;
  var termuxOpened = 0;

  Future<MigrationFixture> open(
    WidgetTester tester, {
    void Function(MigrationFixture fixture)? arrange,
    Future<void> Function(BuildContext, TermuxRuntime)? setUpBuiltin,
    Size size = phoneSize,
    Map<String, WidgetBuilder> routes = const {},
  }) async {
    setUps.clear();
    providersOpened = 0;
    termuxOpened = 0;
    final fixture = MigrationFixture();
    arrange?.call(fixture);
    await pumpPhone(
      tester,
      size: size,
      profiles: [_termux()],
      routes: routes,
      home: TermuxMigrationScreen(
        source: _termux(),
        owner: fixture.owner,
        setUpBuiltin:
            setUpBuiltin ??
            (_, runtime) async {
              setUps.add(runtime);
              fixture.archives.installed = true;
            },
        openProviders: (_) async => providersOpened++,
        openTermux: () async => termuxOpened++,
      ),
    );
    await _frames(tester);
    return fixture;
  }

  Future<void> finish(WidgetTester tester, MigrationFixture fixture) async {
    await fixture.close();
    await unmountPhone(tester);
  }

  /// Scrolls the review to its end, where the space line is.
  Future<void> showSpace(WidgetTester tester) async {
    await tester.drag(find.byType(Scrollable).first, const Offset(0, -5000));
    await _frames(tester);
  }

  Future<void> tapKey(WidgetTester tester, String key) async {
    await tester.ensureVisible(_key(key));
    await tester.pump();
    await tester.tap(_key(key));
    await _frames(tester);
  }

  testWidgets('review: projects move and are on, private copies are off, '
      'sizes are measured, a refused item is unknown, and what stays says so', (
    tester,
  ) async {
    final fixture = await open(tester);
    expect(_key('migration-review'), findsOneWidget);
    expect(find.text(_l10n.migrationGroupMoves), findsOneWidget);
    expect(find.text(_l10n.migrationGroupExports), findsOneWidget);
    Switch toggle(String item) =>
        tester.widget<Switch>(_key('migration-switch-$item'));
    expect(toggle('projects').value, isTrue);
    for (final item in ['config', 'sessions', 'gitConfig', 'shellFiles']) {
      expect(toggle(item).value, isFalse, reason: item);
    }
    // Measured: 411.3 MB and 3412 files, sizes left to right.
    final text = _screenText(tester);
    expect(text, contains(formatPhoneStorage(431227699)));
    expect(text, contains('3412 files'));
    // The refused AI Team: why, and its size unknown, never "0 B".
    expect(toggle('aiTeam').onChanged, isNull);
    expect(
      text,
      contains(
        '${_l10n.migrationUnsupportedFiles} ${_l10n.migrationSizeUnknown}.',
      ),
    );
    expect(text, contains(_l10n.migrationUnsupportedFilesFix));
    expect(text, isNot(contains('0 B')));
    // What does not move, and Termux kept.
    await tester.scrollUntilVisible(
      _key('migration-not-moved'),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text(_l10n.migrationNotMovedSignIn), findsOneWidget);
    expect(find.text(_l10n.migrationNotMovedKeys), findsOneWidget);
    expect(find.text(_l10n.migrationTermuxKept), findsOneWidget);
    // The space it needs, and that the app stays open.
    final space = fixture.controller!.reviewSpace({
      TermuxMigrationItem.projects,
    });
    await showSpace(tester);
    expect(
      _screenText(tester),
      contains(
        _l10n.migrationReviewSpace(
          KitBidi.ltr(formatPhoneStorage(space.requiredBytes!)),
          KitBidi.ltr(formatPhoneStorage(ampleSpace)),
        ),
      ),
    );
    expect(_screenText(tester), contains(_l10n.migrationKeepOpen));
    // Nothing chosen: the start says why it is off.
    // Back to the top, clear of the pinned start.
    await tester.drag(find.byType(Scrollable).first, const Offset(0, 3000));
    await _frames(tester);
    await tester.tap(_key('migration-switch-projects'));
    await _frames(tester);
    expect(_key('migration-space'), findsNothing);
    expect(find.text(_l10n.migrationChooseOne), findsOneWidget);
    expect(fixture.transport.packCalls, 0);
    expect(fixture.journal.records, isEmpty);
    expect(_screenText(tester), isNot(contains(_secret)));
    await finish(tester, fixture);
  });

  testWidgets('copying shows the real stage per item; Stop asks, keeps the '
      'copy and offers Resume, which finishes', (tester) async {
    final fixture = await open(tester);
    fixture.transport.packGate = Completer<void>();
    await tapKey(tester, 'migration-switch-config');
    await tapKey(tester, 'migration-start');
    expect(_key('migration-progress'), findsOneWidget);
    // config comes first (the controller's order), then projects.
    expect(
      _screenText(tester),
      contains(_l10n.migrationPacking),
      reason: 'the stage Termux is in',
    );
    expect(find.text(_l10n.migrationKeepOpen), findsOneWidget);
    // Back asks too; Keep copying leaves it running.
    await tapKey(tester, 'migration-stop');
    expect(_key('migration-stop-sheet'), findsOneWidget);
    await tester.tap(find.text(_l10n.migrationKeepGoing));
    await _frames(tester);
    expect(_key('migration-progress'), findsOneWidget);
    await tapKey(tester, 'migration-stop');
    await tapKey(tester, 'migration-stop-confirm');
    expect(_key('migration-cancelled'), findsOneWidget);
    expect(find.text(_l10n.migrationCancelledBody), findsOneWidget);
    expect(fixture.transport.cancelled, [migrationJob]);
    // Resume carries on with the saved selection and finishes.
    fixture.transport.packGate = null;
    await tapKey(tester, 'migration-resume');
    expect(_key('migration-done'), findsOneWidget);
    expect(fixture.switches, 1);
    expect(fixture.archives.receipts.keys, {
      TermuxMigrationItem.config,
      TermuxMigrationItem.projects,
    });
    await finish(tester, fixture);
  });

  testWidgets('leaving the app stops the copy, says Android stopped it, and '
      'Resume picks up', (tester) async {
    final fixture = await open(tester);
    fixture.transport.copyGate = Completer<void>();
    await tapKey(tester, 'migration-start');
    expect(_screenText(tester), contains(_l10n.migrationCopying));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.hidden);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await _frames(tester);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await _frames(tester);
    expect(_key('migration-cancelled'), findsOneWidget);
    expect(find.text(_l10n.migrationStoppedLeaving), findsOneWidget);
    fixture.transport.copyGate = null;
    await tapKey(tester, 'migration-resume');
    expect(_key('migration-done'), findsOneWidget);
    // Coming back to the app re-reads the connection on a timer of its own.
    await tester.pump(const Duration(minutes: 1));
    await finish(tester, fixture);
  });

  testWidgets('after the app was closed: the unfinished copy with its items, '
      'Resume, then done with the next steps', (tester) async {
    final fixture = await open(
      tester,
      arrange: (f) => f.journal.saveUnfinished([
        TermuxMigrationItem.projects,
        TermuxMigrationItem.config,
        TermuxMigrationItem.sessions,
      ]),
    );
    expect(_key('migration-unfinished'), findsOneWidget);
    expect(_key('migration-saved-projects'), findsOneWidget);
    expect(_key('migration-saved-sessions'), findsOneWidget);
    expect(fixture.transport.inspectCalls, 0, reason: 'Resume is explicit');
    await tapKey(tester, 'migration-resume');
    expect(_key('migration-done'), findsOneWidget);
    expect(find.text(_l10n.migrationDone), findsOneWidget);
    expect(find.text(_l10n.migrationSignInAgain), findsOneWidget);
    expect(find.text(_l10n.migrationSignInAgainAny), findsOneWidget);
    expect(
      _screenText(tester),
      contains(
        _l10n.migrationProjectsWhereBody(KitBidi.ltr('termux-$migrationJob')),
      ),
    );
    expect(_key('migration-exports-where'), findsOneWidget);
    expect(find.text(_l10n.migrationRemoveTermux), findsOneWidget);
    await tapKey(tester, 'migration-sign-in');
    expect(providersOpened, 1);
    // Finished is the backend's record, not a key of the page's own.
    final completed = await fixture.controller!.completedJob(migrationSource);
    expect(completed?.jobId, migrationJob);
    final prefs = await SharedPreferences.getInstance();
    expect(
      prefs.getKeys().where((k) => k.startsWith('oc.termuxMigrationDone')),
      isEmpty,
    );
    expect(_screenText(tester), isNot(contains(_secret)));
    await finish(tester, fixture);
  });

  testWidgets('already moved: where things went, without asking Termux', (
    tester,
  ) async {
    final fixture = MigrationFixture()
      ..journal.saveUnfinished([TermuxMigrationItem.projects], done: true);
    await _pumpPlain(tester, fixture);
    expect(_key('migration-done'), findsOneWidget);
    expect(_key('migration-projects-where'), findsOneWidget);
    expect(_key('migration-exports-where'), findsNothing);
    expect(fixture.transport.inspectCalls, 0);
    await fixture.close();
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('no in-app server: set it up first (OpenCode of the Termux '
      'server), then the review', (tester) async {
    final fixture = await open(
      tester,
      arrange: (f) => f.archives.installed = false,
    );
    expect(_key('migration-needs-builtin'), findsOneWidget);
    expect(fixture.transport.inspectCalls, 0);
    expect(
      find.text(_l10n.migrationNeedsBuiltinBody(_l10n.setupRuntimeTwo)),
      findsOneWidget,
    );
    await tapKey(tester, 'migration-set-up');
    expect(setUps, [TermuxRuntime.openCode2]);
    expect(_key('migration-review'), findsOneWidget);
    await finish(tester, fixture);
  });

  testWidgets('setup v2 hand-off: the required parts with the Termux '
      'runtime, through setup progress, then a fresh check', (tester) async {
    late FakeSetupEngine engine;
    final fixture = MigrationFixture()..archives.installed = false;
    engine = await pumpPhone(
      tester,
      profiles: [_termux()],
      home: TermuxMigrationScreen(source: _termux(), owner: fixture.owner),
    );
    await _frames(tester);
    await tapKey(tester, 'migration-set-up');
    expect(engine.runs.single, {'linux', 'essentials', 'node', 'opencode'});
    expect(engine.runParams.single['opencode'], {'runtime': 'opencode2'});
    expect(find.byType(PhoneSetupProgressScreen), findsOneWidget);
    fixture.archives.installed = true;
    tester.state<NavigatorState>(find.byType(Navigator)).pop();
    await _frames(tester);
    expect(_key('migration-review'), findsOneWidget);
    await finish(tester, fixture);
  });

  testWidgets('Termux not answering: open it, then try again', (tester) async {
    final fixture = await open(
      tester,
      arrange: (f) => f.transport.inspectError = const TermuxMigrationException(
        TermuxMigrationFailure.unavailable,
      ),
    );
    expect(_key('migration-termux-unreachable'), findsOneWidget);
    await tapKey(tester, 'migration-open-termux');
    expect(termuxOpened, 1);
    fixture.transport.inspectError = null;
    await tapKey(tester, 'migration-try-again');
    expect(_key('migration-review'), findsOneWidget);
    await finish(tester, fixture);
  });

  testWidgets('space that ran out after the review: how much is needed and '
      'free, then fewer items', (tester) async {
    final fixture = await open(tester);
    fixture.freeBytes = 1024;
    await tapKey(tester, 'migration-start');
    expect(_key('migration-needs-space'), findsOneWidget);
    expect(_screenText(tester), contains(formatPhoneStorage(1024)));
    expect(fixture.transport.packCalls, 0);
    await tapKey(tester, 'migration-choose-fewer');
    expect(_key('migration-review'), findsOneWidget);
    await finish(tester, fixture);
  });

  group('discarding a saved copy', () {
    Future<void> confirmDiscard(WidgetTester tester) async {
      await tapKey(tester, 'migration-discard');
      expect(_key('migration-discard-sheet'), findsOneWidget);
      expect(find.text(_l10n.migrationDiscardTitle), findsOneWidget);
      expect(find.text(_l10n.migrationDiscardBody), findsOneWidget);
      await tapKey(tester, 'migration-discard-confirm');
    }

    testWidgets('an unfinished copy: confirm, the temporary files go, and '
        'the review starts again; cancelling changes nothing', (tester) async {
      final fixture = await open(
        tester,
        arrange: (f) =>
            f.journal.saveUnfinished([TermuxMigrationItem.projects]),
      );
      expect(_key('migration-unfinished'), findsOneWidget);
      // Keep it: the sheet's Cancel leaves everything as it was.
      await tapKey(tester, 'migration-discard');
      await tester.tap(find.text('Cancel'));
      await _frames(tester);
      expect(fixture.archives.discarded, isEmpty);
      expect(fixture.journal.records, isNotEmpty);
      await confirmDiscard(tester);
      expect(fixture.archives.discarded, [migrationJob]);
      expect(fixture.journal.records, isEmpty);
      expect(_key('migration-review'), findsOneWidget);
      expect(fixture.transport.inspectCalls, 1, reason: 'a fresh look');
      // The choice starts over: projects on, nothing else.
      expect(
        tester.widget<Switch>(_key('migration-switch-projects')).value,
        isTrue,
      );
      await finish(tester, fixture);
    });

    testWidgets('a stopped copy waits for the stop, then discards', (
      tester,
    ) async {
      final fixture = await open(tester);
      fixture.transport.packGate = Completer<void>();
      await tapKey(tester, 'migration-start');
      await tapKey(tester, 'migration-stop');
      await tapKey(tester, 'migration-stop-confirm');
      expect(_key('migration-cancelled'), findsOneWidget);
      await confirmDiscard(tester);
      expect(fixture.archives.discarded, [migrationJob]);
      expect(fixture.journal.records, isEmpty);
      expect(_key('migration-review'), findsOneWidget);
      await finish(tester, fixture);
    });

    testWidgets('a failed copy can be discarded', (tester) async {
      final fixture = await open(tester);
      fixture.transport.packError = const TermuxMigrationException(
        TermuxMigrationFailure.sourceChanged,
      );
      await tapKey(tester, 'migration-start');
      expect(_key('migration-failed'), findsOneWidget);
      await confirmDiscard(tester);
      expect(fixture.journal.records, isEmpty);
      expect(_key('migration-review'), findsOneWidget);
      await finish(tester, fixture);
    });

    testWidgets('a failed removal says so in plain words, keeps the copy and '
        'can be tried again', (tester) async {
      final fixture = await open(
        tester,
        arrange: (f) =>
            f.journal.saveUnfinished([TermuxMigrationItem.projects]),
      );
      fixture.archives.cleanupError = const FileSystemException(
        '/data/data/secret/path',
      );
      await confirmDiscard(tester);
      expect(_key('migration-discard-failed'), findsOneWidget);
      expect(find.text(_l10n.migrationDiscardFailed), findsOneWidget);
      expect(_screenText(tester), isNot(contains('/data/data')));
      expect(_key('migration-unfinished'), findsOneWidget);
      expect(fixture.journal.records, isNotEmpty);
      fixture.archives.cleanupError = null;
      await confirmDiscard(tester);
      expect(_key('migration-review'), findsOneWidget);
      await finish(tester, fixture);
    });

    testWidgets('a finished move offers no discard', (tester) async {
      final fixture = MigrationFixture()
        ..journal.saveUnfinished([TermuxMigrationItem.projects], done: true);
      await _pumpPlain(tester, fixture);
      expect(_key('migration-done'), findsOneWidget);
      expect(_key('migration-discard'), findsNothing);
      await fixture.close();
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('the backend says whether the move finished', () {
    testWidgets('a completed record shows the done page with no key of the '
        'page\'s own, and nothing is asked of Termux', (tester) async {
      final fixture = MigrationFixture()
        ..journal.saveUnfinished([TermuxMigrationItem.projects], done: true);
      await _pumpPlain(tester, fixture);
      expect(_key('migration-done'), findsOneWidget);
      expect(fixture.transport.inspectCalls, 0);
      await fixture.close();
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('a completed record that cannot be read is not "unfinished": '
        'the page says it cannot look, with Try again', (tester) async {
      final fixture = MigrationFixture();
      fixture.journal.saveUnfinished([TermuxMigrationItem.projects]);
      fixture.journal.records[migrationSource]!
        ..['done'] = true
        ..remove('destination');
      await _pumpPlain(tester, fixture);
      expect(_key('migration-unavailable'), findsOneWidget);
      expect(_key('migration-unfinished'), findsNothing);
      expect(_key('migration-resume'), findsNothing);
      await fixture.close();
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  group('free space in the review', () {
    testWidgets('needed and free are said; too little blocks the copy with '
        'the way forward, and choosing fewer clears it', (tester) async {
      final fixture = await open(
        tester,
        arrange: (f) => f.freeBytes = 200 * 1024 * 1024,
      );
      await showSpace(tester);
      final text = _screenText(tester);
      expect(text, contains(_l10n.migrationReviewSpaceShort));
      expect(text, contains(formatPhoneStorage(200 * 1024 * 1024)));
      expect(_key('migration-space'), findsOneWidget);
      await tester.tap(_key('migration-start'), warnIfMissed: false);
      await _frames(tester);
      expect(fixture.transport.packCalls, 0);
      expect(_key('migration-needs-space'), findsNothing);
      expect(_key('migration-review'), findsOneWidget);
      // Choosing fewer: with only the tiny Git settings the need fits.
      await tester.drag(find.byType(Scrollable).first, const Offset(0, 3000));
      await _frames(tester);
      await tester.tap(_key('migration-switch-projects'));
      await _frames(tester);
      await tapKey(tester, 'migration-switch-gitConfig');
      await showSpace(tester);
      expect(
        _screenText(tester),
        isNot(contains(_l10n.migrationReviewSpaceShort)),
      );
      await finish(tester, fixture);
    });

    testWidgets('ticking an item updates the need without another look', (
      tester,
    ) async {
      final fixture = await open(tester);
      final before = fixture.transport.inspectCalls;
      final projectsOnly = fixture.controller!.reviewSpace({
        TermuxMigrationItem.projects,
      });
      final both = fixture.controller!.reviewSpace({
        TermuxMigrationItem.projects,
        TermuxMigrationItem.sessions,
      });
      await tapKey(tester, 'migration-switch-sessions');
      await showSpace(tester);
      expect(
        _screenText(tester),
        contains(
          _l10n.migrationReviewSpace(
            KitBidi.ltr(formatPhoneStorage(both.requiredBytes!)),
            KitBidi.ltr(formatPhoneStorage(ampleSpace)),
          ),
        ),
      );
      expect(both.requiredBytes, greaterThan(projectsOnly.requiredBytes!));
      expect(fixture.transport.inspectCalls, before);
      await finish(tester, fixture);
    });

    testWidgets('free space that cannot be read is said, and Copy still '
        'goes to the backend, which decides', (tester) async {
      final fixture = await open(tester, arrange: (f) => f.freeBytes = null);
      await showSpace(tester);
      final text = _screenText(tester);
      expect(text, contains('Free space unknown'));
      expect(text, isNot(contains(_l10n.migrationReviewSpaceShort)));
      await tester.drag(find.byType(Scrollable).first, const Offset(0, 3000));
      await _frames(tester);
      await tapKey(tester, 'migration-start');
      expect(_key('migration-needs-space'), findsOneWidget);
      await finish(tester, fixture);
    });
  });

  testWidgets('Stop: "Stopping…" until the backend has settled, and Resume '
      'and Discard wait for it', (tester) async {
    final fixture = await open(tester);
    fixture.transport
      ..packGate = Completer<void>()
      ..holdAfterCancel = true;
    await tapKey(tester, 'migration-start');
    await tapKey(tester, 'migration-stop');
    await tapKey(tester, 'migration-stop-confirm');
    expect(_key('migration-cancelled'), findsOneWidget);
    expect(find.text(_l10n.migrationStopping), findsOneWidget);
    expect(fixture.owner.busy, isTrue);
    for (final key in ['migration-resume', 'migration-discard']) {
      await tester.tap(_key(key), warnIfMissed: false);
      await _frames(tester);
    }
    expect(_key('migration-discard-sheet'), findsNothing);
    expect(fixture.transport.packCalls, 1, reason: 'Resume did not start');
    // The stop settles: the words change and Resume works.
    fixture.transport.packGate!.complete();
    await _frames(tester);
    expect(find.text(_l10n.migrationStopping), findsNothing);
    expect(find.text(_l10n.migrationCancelled), findsOneWidget);
    expect(fixture.owner.busy, isFalse);
    fixture.transport
      ..packGate = null
      ..holdAfterCancel = false;
    await tapKey(tester, 'migration-resume');
    expect(_key('migration-done'), findsOneWidget);
    await finish(tester, fixture);
  });

  group('providers to sign in to again', () {
    testWidgets('a reopened finished move names them, offline', (tester) async {
      final fixture = MigrationFixture()
        ..journal.saveUnfinished(
          [TermuxMigrationItem.projects, TermuxMigrationItem.config],
          done: true,
          configCopied: true,
        )
        ..archives.providers = ['Anthropic', 'OpenAI'];
      await _pumpPlain(tester, fixture);
      expect(_key('migration-done'), findsOneWidget);
      expect(
        find.text(_l10n.migrationSignInAgainNamed('Anthropic and OpenAI')),
        findsOneWidget,
      );
      expect(fixture.transport.inspectCalls, 0);
      await fixture.close();
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('the copy that just finished names them too', (tester) async {
      final fixture = await open(
        tester,
        arrange: (f) => f.archives.providers = ['Anthropic'],
      );
      await tapKey(tester, 'migration-switch-config');
      await tapKey(tester, 'migration-start');
      expect(_key('migration-done'), findsOneWidget);
      // Names come from the backend: the fake reads them once the config
      // copy was received.
      expect(
        find.text(_l10n.migrationSignInAgainNamed('Anthropic')),
        findsOneWidget,
      );
      await finish(tester, fixture);
    });

    testWidgets('unknown names keep the generic words', (tester) async {
      final fixture = MigrationFixture()
        ..journal.saveUnfinished([TermuxMigrationItem.projects], done: true);
      await _pumpPlain(tester, fixture);
      expect(find.text(_l10n.migrationSignInAgainAny), findsOneWidget);
      await fixture.close();
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  testWidgets('raw text and credentials from below never reach the screen', (
    tester,
  ) async {
    final fixture = await open(tester);
    fixture.transport.packError = Exception(
      'curl: (22) Authorization: Bearer sk-ant-api03-RAWSECRET at /data/data/com.termux',
    );
    await tapKey(tester, 'migration-start');
    expect(_key('migration-failed'), findsOneWidget);
    expect(_screenText(tester), contains(_l10n.migrationStorageFailed));
    await tester.ensureVisible(_key('kit-state-details'));
    await tester.tap(_key('kit-state-details'));
    await _frames(tester);
    final text = _screenText(tester);
    for (final raw in ['RAWSECRET', 'Bearer', 'curl', 'com.termux', _secret]) {
      expect(text, isNot(contains(raw)), reason: raw);
    }
    // Try again repeats the copy from where it stopped.
    await tapKey(tester, 'migration-try-again');
    expect(_key('migration-done'), findsOneWidget);
    await finish(tester, fixture);
  });

  testWidgets('the connection fails after the files are in: try again or '
      'open the in-app server', (tester) async {
    final fixture = await open(
      tester,
      arrange: (f) => f.switchError = const TermuxMigrationException(
        TermuxMigrationFailure.profileSwitch,
      ),
    );
    await tapKey(tester, 'migration-start');
    expect(_screenText(tester), contains(_l10n.migrationConnectionFailed));
    expect(_key('migration-open-this-phone'), findsOneWidget);
    fixture.switchError = null;
    await tapKey(tester, 'migration-try-again');
    expect(_key('migration-done'), findsOneWidget);
    await finish(tester, fixture);
  });

  testWidgets('large text and right to left: the review and the copy fit', (
    tester,
  ) async {
    final fixture = MigrationFixture();
    await _pumpPlain(
      tester,
      fixture,
      size: const Size(360, 800),
      locale: const Locale('ar'),
      textScale: 2,
    );
    await _frames(tester);
    expect(_key('migration-review'), findsOneWidget);
    expect(tester.takeException(), isNull);
    fixture.transport.packGate = Completer<void>();
    await tapKey(tester, 'migration-start');
    expect(_key('migration-progress'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await fixture.close();
    await tester.pumpWidget(const SizedBox.shrink());
  });

  group('entry points', () {
    setUp(
      () => debugPlatformCapabilities = const PlatformCapabilities.android(),
    );
    tearDown(() => debugPlatformCapabilities = null);

    testWidgets('This phone for Termux leads with the move; it opens the '
        'review', (tester) async {
      final fixture = MigrationFixture();
      final previous = TermuxMigrationOwner.instance;
      TermuxMigrationOwner.instance = fixture.owner;
      addTearDown(() => TermuxMigrationOwner.instance = previous);
      final channel = TermuxChannelFixture()
        ..inventoryOutput =
            'ubuntu=installed\nversion=1.18.32\nruntime=opencode1\n'
        ..statusOutput = termuxSnapshot(phase: 'ready', version: '1.18.32');
      channel.install();
      await pumpPhone(
        tester,
        home: ThisPhoneScreen(
          kind: PhoneHostKind.termux,
          scanProcesses: () async => throw StateError('not read'),
        ),
        profiles: [_termux()],
      );
      await _frames(tester);
      expect(_key('this-phone-migrate'), findsOneWidget);
      expect(find.text(_l10n.migrationTitle), findsOneWidget);
      expect(find.text(_l10n.migrationRowBody), findsOneWidget);
      expect(fixture.transport.inspectCalls, 0, reason: 'no look by itself');
      await tapKey(tester, 'this-phone-migrate');
      expect(_key('migration-review'), findsOneWidget);
      await fixture.close();
      await unmountPhone(tester);
    });

    testWidgets('This phone says Resume for an unfinished copy and Moved '
        'after', (tester) async {
      final fixture = MigrationFixture()
        ..journal.saveUnfinished([TermuxMigrationItem.projects]);
      final previous = TermuxMigrationOwner.instance;
      TermuxMigrationOwner.instance = fixture.owner;
      addTearDown(() => TermuxMigrationOwner.instance = previous);
      TermuxChannelFixture()
        ..inventoryOutput =
            'ubuntu=installed\nversion=1.18.32\nruntime=opencode1\n'
        ..statusOutput = termuxSnapshot(phase: 'ready', version: '1.18.32')
        ..install();
      await pumpPhone(
        tester,
        home: const ThisPhoneScreen(kind: PhoneHostKind.termux),
        profiles: [_termux()],
      );
      await _frames(tester);
      expect(find.text(_l10n.migrationRowResume), findsOneWidget);
      await tapKey(tester, 'this-phone-migrate');
      await tapKey(tester, 'migration-resume');
      expect(_key('migration-done'), findsOneWidget);
      tester.state<NavigatorState>(find.byType(Navigator)).pop();
      await _frames(tester);
      expect(find.text(_l10n.migrationDoneTitle), findsOneWidget);
      expect(find.text(_l10n.migrationRowDoneBody), findsOneWidget);
      await fixture.close();
      await unmountPhone(tester);
    });

    testWidgets('the in-app host and a non-Termux server show no move', (
      tester,
    ) async {
      expect(offersTermuxMigration(_termux()), isTrue);
      expect(
        offersTermuxMigration(
          ServerProfile(id: 'w', name: 'Work', baseUrl: 'https://example.com'),
        ),
        isFalse,
      );
      expect(offersTermuxMigration(null), isFalse);
      debugPlatformCapabilities = const PlatformCapabilities.linuxDesktop();
      expect(offersTermuxMigration(_termux()), isFalse);
    });

    testWidgets('Servers: the one-time offer opens the review; closing it '
        'is remembered for that server', (tester) async {
      final fixture = MigrationFixture();
      final previous = TermuxMigrationOwner.instance;
      TermuxMigrationOwner.instance = fixture.owner;
      addTearDown(() => TermuxMigrationOwner.instance = previous);
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = _StaticStore(prefs, [_termux()]);
      Future<void> pump() => tester.pumpWidget(
        ProviderScope(
          overrides: [
            connProvider.overrideWithValue(ConnectionController(store)),
          ],
          child: MaterialApp(
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            home: Scaffold(
              body: TermuxMigrationOffer(
                profiles: store.profiles,
                store: store,
              ),
            ),
          ),
        ),
      );
      await pump();
      await _frames(tester);
      expect(_key('termux-migration-offer'), findsOneWidget);
      expect(find.text(_l10n.migrationOffer), findsOneWidget);
      expect(fixture.transport.inspectCalls, 0, reason: 'seen is not asked');
      await tapKey(tester, 'termux-migration-offer-review');
      expect(_key('migration-review'), findsOneWidget);
      tester.state<NavigatorState>(find.byType(Navigator)).pop();
      await _frames(tester);
      expect(_key('termux-migration-offer'), findsOneWidget);
      await tapKey(tester, 'termux-migration-offer-dismiss');
      expect(_key('termux-migration-offer'), findsNothing);
      expect(prefs.getBool('oc.termuxMigrationOffer.$migrationSource'), isTrue);
      await tester.pumpWidget(const SizedBox.shrink());
      await pump();
      await _frames(tester);
      expect(_key('termux-migration-offer'), findsNothing);
      // Swept with the profile, like every scoped key.
      expect(
        store.profileScopedPreferenceKeys(migrationSource),
        contains('oc.termuxMigrationOffer.$migrationSource'),
      );
      await fixture.close();
      await tester.pumpWidget(const SizedBox.shrink());
    });

    testWidgets('the offer is gone once a copy was saved', (tester) async {
      SharedPreferences.setMockInitialValues({
        'oc.termuxMigration.$migrationSource': jsonEncode({
          'job': migrationJob,
        }),
      });
      final prefs = await SharedPreferences.getInstance();
      final store = _StaticStore(prefs, [_termux()]);
      await tester.pumpWidget(
        MaterialApp(
          theme: captureTheme(),
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: Scaffold(
            body: TermuxMigrationOffer(profiles: store.profiles, store: store),
          ),
        ),
      );
      await _frames(tester);
      expect(_key('termux-migration-offer'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  });

  test(
    'the owner keeps no key of its own; the offer key is still swept',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = _StaticStore(prefs, [_termux()]);
      await prefs.setBool('oc.termuxMigrationOffer.$migrationSource', true);
      expect(store.profileScopedPreferenceKeys(migrationSource), {
        'oc.termuxMigrationOffer.$migrationSource',
      });
      final source = File(
        'lib/state/termux_migration_owner.dart',
      ).readAsStringSync();
      expect(source, isNot(contains('oc.termuxMigrationDone')));
      expect(source, isNot(contains('oc.termuxMigrationProviders')));
    },
  );
}

/// The migration page on its own, with [values] saved beforehand.
Future<void> _pumpPlain(
  WidgetTester tester,
  MigrationFixture fixture, {
  Map<String, Object> values = const {},
  Size size = phoneSize,
  Locale? locale,
  double textScale = 1,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  SharedPreferences.setMockInitialValues(values);
  final prefs = await SharedPreferences.getInstance();
  final connection = ConnectionController(_StaticStore(prefs, [_termux()]));
  addTearDown(connection.dispose);
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(connection)],
      child: MaterialApp(
        theme: captureTheme(),
        locale: locale,
        localizationsDelegates: AppLocalizations.localizationsDelegates,
        supportedLocales: AppLocalizations.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(
            textScaler: TextScaler.linear(textScale),
            disableAnimations: true,
          ),
          child: child!,
        ),
        home: TermuxMigrationScreen(source: _termux(), owner: fixture.owner),
      ),
    ),
  );
  await _frames(tester);
}

class _StaticStore extends ProfileStore {
  _StaticStore(SharedPreferences prefs, this.saved) : super(prefs: prefs);
  final List<ServerProfile> saved;

  @override
  List<ServerProfile> get profiles => List.unmodifiable(saved);

  @override
  String? get activeId => saved.isEmpty ? null : saved.first.id;
}

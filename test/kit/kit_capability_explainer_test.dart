// Behaviour tests for KitCapabilityExplainer
// (docs/ux-system/kit-api/KitCapabilityExplainer.md "Tests required").
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/ui/app_theme.dart';
import 'package:opencode_mobile/ui/kit/kit_capability_explainer.dart';
import 'package:opencode_mobile/ui/kit/kit_notice.dart';
import 'package:opencode_mobile/ui/kit/kit_row.dart';
import 'package:opencode_mobile/ui/kit/kit_state_view.dart';
import 'kit_motion_still.dart';

const _hostColumns = <String, KitHost>{
  'builtin': KitHost.thisPhone,
  'termux': KitHost.termux,
  'oc2Computer': KitHost.openCode2,
  'codex': KitHost.codex,
  'paseo': KitHost.paseo,
  'demo': KitHost.demo,
};

/// COPY-11's engine words (STANDARDS.md §8.2).
const _engineWords = [
  'convoy',
  'formula',
  'bead',
  'rig',
  'city',
  'polecat',
  'refinery',
  'sling',
  'wisp',
  'mayor',
  'gastown',
  'pty',
  'sse',
  '127.0.0.1',
];

final Map<String, dynamic> _capabilities =
    jsonDecode(File('docs/ux-system/capabilities.json').readAsStringSync())
        as Map<String, dynamic>;

List<Map<String, dynamic>> get _matrix =>
    (_capabilities['matrix'] as List).cast<Map<String, dynamic>>();

List<String> get _flowCapabilities => [
  for (final flow in (_capabilities['enableFlows'] as List))
    (flow as Map<String, dynamic>)['capability'] as String,
];

late BuildContext _context;

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Size size = const Size(412, 915),
  bool reduced = false,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      localizationsDelegates: AppLocalizations.localizationsDelegates,
      supportedLocales: AppLocalizations.supportedLocales,
      builder: (context, inner) => MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: reduced),
        child: inner!,
      ),
      home: Scaffold(
        body: Builder(
          builder: (context) {
            _context = context;
            return SingleChildScrollView(child: child);
          },
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

/// Registers a handler for [capability]'s flow that records its requests.
List<KitEnableRequest> _record(String capability) {
  final requests = <KitEnableRequest>[];
  KitCapabilities.registerFlow(
    KitCapabilities.byId(capability)!.enableFlow!,
    (context, request) async => requests.add(request),
  );
  return requests;
}

void main() {
  kitMotionStillTests(
    'KitCapabilityExplainer',
    builds: {
      'explains': () => const KitCapabilityExplainer.row(
        capability: 'flag:fileBrowsing+terminal',
        host: KitHost.codex,
        title: 'Files',
      ),
    },
    changes: {
      'host changes': KitMotionChange(
        build: () => const KitCapabilityExplainer.row(
          capability: 'flag:fileBrowsing+terminal',
          host: KitHost.codex,
          title: 'Files',
        ),
        act: (tester, stage) => stage.rebuild(
          const KitCapabilityExplainer.row(
            capability: 'flag:fileBrowsing+terminal',
            host: KitHost.paseo,
            title: 'Files',
          ),
        ),
        shows: 'Files',
      ),
    },
  );

  tearDown(KitCapabilities.debugReset);

  group('registry parity with capabilities.json', () {
    test('every enable flow has an entry with a flow id; 21, distinct', () {
      expect(KitEnableFlows.all, hasLength(21));
      expect(KitEnableFlows.all.toSet(), hasLength(21));
      expect(_flowCapabilities, hasLength(21));
      for (final capability in _flowCapabilities) {
        final entry = KitCapabilities.byId(capability);
        expect(entry, isNotNull, reason: capability);
        expect(entry!.enableFlow, isNotNull, reason: capability);
        expect(KitEnableFlows.all, contains(entry.enableFlow));
      }
      final withFlow = [
        for (final entry in KitCapabilities.all)
          if (entry.enableFlow != null) entry,
      ];
      expect(
        withFlow.map((entry) => entry.id).toSet(),
        _flowCapabilities.toSet(),
      );
      expect(
        withFlow.map((entry) => entry.enableFlow).toSet(),
        KitEnableFlows.all.toSet(),
      );
    });

    test('every matrix capability has equal supported and partly sets', () {
      expect(KitCapabilities.all, hasLength(_matrix.length));
      expect(_matrix, hasLength(34));
      for (final row in _matrix) {
        final id = row['capability'] as String;
        final hosts = row['hosts'] as Map<String, dynamic>;
        Set<KitHost> withStatus(String status) => {
          for (final MapEntry(:key, :value) in hosts.entries)
            if ((value as Map<String, dynamic>)['status'] == status)
              _hostColumns[key]!,
        };
        final entry = KitCapabilities.byId(id);
        expect(entry, isNotNull, reason: id);
        expect(entry!.supported, withStatus('supported'), reason: id);
        expect(entry.partly, withStatus('partly'), reason: id);
      }
    });
  });

  group('registration', () {
    test('canEnable follows registerFlow; unregisteredFlows shrinks', () {
      expect(KitCapabilities.canEnable('voice.model'), isFalse);
      expect(KitCapabilities.unregisteredFlows, hasLength(21));
      KitCapabilities.registerFlow(
        KitEnableFlows.voiceModelSetup,
        (context, request) async {},
      );
      expect(KitCapabilities.canEnable('voice.model'), isTrue);
      expect(
        KitCapabilities.unregisteredFlows,
        isNot(contains(KitEnableFlows.voiceModelSetup)),
      );
      expect(KitCapabilities.unregisteredFlows, hasLength(20));
      // No flow at all: never enable.
      expect(KitCapabilities.canEnable('flag:sessionDiff'), isFalse);
    });

    testWidgets('without a handler, .row and .state only explain', (
      tester,
    ) async {
      await _pump(
        tester,
        const Column(
          children: [
            KitCapabilityExplainer.row(capability: 'voice.model'),
            KitCapabilityExplainer.state(capability: 'voice.model'),
          ],
        ),
      );
      expect(find.text('Voice typing'), findsNWidgets(2));
      expect(find.text('Needs a voice model on this phone.'), findsNWidgets(2));
      expect(find.text('Download voice model'), findsNothing);
      expect(find.byType(ButtonStyleButton), findsNothing);
    });

    testWidgets('with a handler, .row offers the flow as a button', (
      tester,
    ) async {
      _record('voice.model');
      await _pump(
        tester,
        const KitCapabilityExplainer.row(capability: 'voice.model'),
      );
      expect(find.text('Download voice model'), findsOneWidget);
    });

    test('enable with no handler is a no-op returning false', () async {
      // A context is not touched when no handler is registered.
      expect(
        await KitCapabilities.enable(
          _FakeContext(),
          'voice.model',
          host: KitHost.codex,
        ),
        isFalse,
      );
    });
  });

  group('the 21 enable flows', () {
    for (final capability in [
      'server.any',
      'phone.builtin',
      'phone.termux',
      'server.oc2',
      'server.paseo',
      'claude.local',
      'model.auth',
      'team.on',
      'team.control',
      'voice.model',
      'mcp.any',
      'project.open',
      'project.git',
      'perm.notifications',
      'perm.battery',
      'perm.camera',
      'perm.mic',
      'network.tailscale',
      'quota.collector',
      'agent.a2a',
      'server.codex',
    ]) {
      testWidgets('$capability: the row runs its flow with the request', (
        tester,
      ) async {
        expect(_flowCapabilities, contains(capability));
        final requests = _record(capability);
        await _pump(
          tester,
          KitCapabilityExplainer.row(
            capability: capability,
            host: KitHost.openCode2,
            serverName: 'laptop',
            source: 'test-page',
          ),
        );
        final label = KitCapabilityExplainer.enableLabelOf(
          _context,
          capability,
        )!;
        expect(label, isNotEmpty);
        expect(
          KitCapabilityExplainer.titleOf(_context, capability),
          isNotEmpty,
        );
        expect(
          find.text(KitCapabilityExplainer.titleOf(_context, capability)),
          findsOneWidget,
        );
        await tester.tap(find.text(label));
        await tester.pumpAndSettle();
        expect(requests, [
          KitEnableRequest(
            capability: capability,
            flow: KitCapabilities.byId(capability)!.enableFlow!,
            host: KitHost.openCode2,
            serverName: 'laptop',
            source: 'test-page',
          ),
        ]);
      });
    }
  });

  group('enable', () {
    testWidgets('tapping enable calls the handler once, with the request', (
      tester,
    ) async {
      final requests = _record('team.on');
      await _pump(
        tester,
        const KitCapabilityExplainer.state(
          capability: 'team.on',
          host: KitHost.thisPhone,
          serverName: 'Pixel',
          source: 'work-home',
        ),
      );
      await tester.tap(find.text('Turn on AI Team'));
      await tester.pumpAndSettle();
      expect(requests, const [
        KitEnableRequest(
          capability: 'team.on',
          flow: KitEnableFlows.teamTurnOn,
          host: KitHost.thisPhone,
          serverName: 'Pixel',
          source: 'work-home',
        ),
      ]);
    });

    testWidgets('while the handler runs the action is working, and a second '
        'tap does not run it again', (tester) async {
      final done = Completer<void>();
      var calls = 0;
      KitCapabilities.registerFlow(KitEnableFlows.voiceModelSetup, (
        context,
        request,
      ) {
        calls++;
        return done.future;
      });
      await _pump(
        tester,
        const KitCapabilityExplainer.state(capability: 'voice.model'),
      );
      await tester.tap(find.text('Download voice model'));
      await tester.pump();
      expect(find.byKey(const ValueKey('kit-button-working')), findsOneWidget);
      await tester.tap(find.text('Download voice model'));
      await tester.pump();
      expect(calls, 1);
      done.complete();
      await tester.pumpAndSettle();
      expect(find.byKey(const ValueKey('kit-button-working')), findsNothing);
      expect(find.text('Download voice model'), findsOneWidget);
    });

    testWidgets('a throwing handler does not crash; the explainer stays', (
      tester,
    ) async {
      final reported = <FlutterErrorDetails>[];
      final previous = FlutterError.onError;
      FlutterError.onError = reported.add;
      addTearDown(() => FlutterError.onError = previous);
      KitCapabilities.registerFlow(
        KitEnableFlows.voiceModelSetup,
        (context, request) async => throw StateError('download failed'),
      );
      await _pump(
        tester,
        const KitCapabilityExplainer.row(capability: 'voice.model'),
      );
      await tester.tap(find.text('Download voice model'));
      await tester.pumpAndSettle();
      FlutterError.onError = previous;
      expect(reported, hasLength(1));
      expect(reported.single.exception, isA<StateError>());
      expect(find.text('Voice typing'), findsOneWidget);
      expect(find.text('Download voice model'), findsOneWidget);
    });
  });

  group('why wording', () {
    testWidgets('names the host and the hosts that can, without engine words', (
      tester,
    ) async {
      await _pump(tester, const SizedBox());
      final why = KitCapabilityExplainer.whyOf(
        _context,
        'flag:fileBrowsing+terminal',
        host: KitHost.codex,
      );
      expect(why, contains('Codex'));
      expect(why, contains("aren't available"));
      expect(why, contains('this phone'));
      expect(why, contains('computers with OpenCode'));
      expect(
        KitCapabilityExplainer.hostsLineOf(_context, 'voice.model'),
        'Works on \u2068this phone\u2069, \u2068computers with OpenCode 2\u2069, '
        '\u2068Codex\u2069 and \u2068Paseo\u2069',
      );
      // The server name is isolated beside its host word (COPY-30).
      final named = KitCapabilityExplainer.whyOf(
        _context,
        'flag:sessionDiff',
        host: KitHost.paseo,
        serverName: 'laptop',
      );
      expect(named, contains('\u2068laptop\u2069'));
      expect(named, contains("isn't available"));
      // A host that can: the registry's reason.
      expect(
        KitCapabilityExplainer.whyOf(
          _context,
          'voice.model',
          host: KitHost.codex,
        ),
        'Needs a voice model on this phone.',
      );
      for (final entry in KitCapabilities.all) {
        for (final host in [null, ...KitHost.values]) {
          final words = [
            KitCapabilityExplainer.titleOf(_context, entry.id),
            KitCapabilityExplainer.whyOf(_context, entry.id, host: host),
            ?KitCapabilityExplainer.enableLabelOf(_context, entry.id),
          ].join(' ').toLowerCase();
          for (final engine in _engineWords) {
            expect(
              RegExp('\\b${RegExp.escape(engine)}\\b').hasMatch(words),
              isFalse,
              reason: '${entry.id} on $host says "$engine"',
            );
          }
        }
      }
    });

    test('no kitCap/kitHost copy mentions the setup assistant (AUTO-20)', () {
      final arb =
          jsonDecode(File('lib/l10n/app_en.arb').readAsStringSync())
              as Map<String, dynamic>;
      final values = {
        for (final MapEntry(:key, :value) in arb.entries)
          if (key.startsWith('kitCap') || key.startsWith('kitHost'))
            key: value as String,
      };
      expect(values.length, greaterThan(100));
      for (final MapEntry(:key, :value) in values.entries) {
        expect(value.toLowerCase(), isNot(contains('assistant')), reason: key);
      }
    });
  });

  group('.offer', () {
    testWidgets('"Not now" calls onNotNow once; folded renders the quiet row', (
      tester,
    ) async {
      final requests = _record('team.on');
      var notNow = 0;
      await _pump(
        tester,
        KitCapabilityExplainer.offer(
          capability: 'team.on',
          onNotNow: () => notNow++,
        ),
      );
      expect(find.byType(KitNotice), findsOneWidget);
      expect(
        find.text('This server can also run an AI team. Turn it on?'),
        findsOneWidget,
      );
      expect(find.byTooltip('Not now'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('kit-notice-dismiss')));
      await tester.pumpAndSettle();
      expect(notNow, 1);
      expect(requests, isEmpty);

      await _pump(
        tester,
        KitCapabilityExplainer.offer(
          capability: 'team.on',
          folded: true,
          onNotNow: () => notNow++,
        ),
      );
      expect(find.byType(KitNotice), findsNothing);
      expect(find.byType(KitRow), findsOneWidget);
      expect(find.text('AI Team'), findsOneWidget);
      expect(find.text('Turn on AI Team'), findsOneWidget);
    });

    testWidgets('the offer runs the flow; without a handler it explains', (
      tester,
    ) async {
      await _pump(
        tester,
        KitCapabilityExplainer.offer(
          capability: 'voice.model',
          onNotNow: () {},
        ),
      );
      expect(find.byType(KitNotice), findsNothing);
      expect(find.text('Needs a voice model on this phone.'), findsOneWidget);

      final requests = _record('voice.model');
      await _pump(
        tester,
        KitCapabilityExplainer.offer(
          capability: 'voice.model',
          message: 'Talk instead of typing?',
          onNotNow: () {},
        ),
      );
      expect(find.text('Talk instead of typing?'), findsOneWidget);
      await tester.tap(find.text('Download voice model'));
      await tester.pumpAndSettle();
      expect(requests, hasLength(1));
    });

    testWidgets('reduced motion: the fold settles after one pump', (
      tester,
    ) async {
      _record('team.on');
      var folded = false;
      late StateSetter setFolded;
      await _pump(
        tester,
        StatefulBuilder(
          builder: (context, setState) {
            setFolded = setState;
            return KitCapabilityExplainer.offer(
              capability: 'team.on',
              folded: folded,
              onNotNow: () {},
            );
          },
        ),
        reduced: true,
      );
      expect(find.byType(KitNotice), findsOneWidget);
      setFolded(() => folded = true);
      await tester.pump();
      expect(find.byType(KitNotice), findsNothing);
      expect(find.byType(KitRow), findsOneWidget);
      expect(tester.hasRunningAnimations, isFalse);
    });
  });

  group('asserts', () {
    testWidgets('a prerequisite with no flow asserts (STATE-13)', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitCapabilityExplainer.state(
          capability: 'flag:sessionDiff',
          prerequisite: true,
        ),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('a prerequisite with no handler asserts (STATE-13)', (
      tester,
    ) async {
      await _pump(
        tester,
        const KitCapabilityExplainer.state(
          capability: 'project.open',
          prerequisite: true,
        ),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });

    testWidgets('a prerequisite with a handler shows its button', (
      tester,
    ) async {
      _record('project.open');
      await _pump(
        tester,
        const KitCapabilityExplainer.state(
          capability: 'project.open',
          prerequisite: true,
          cost: ['Takes seconds'],
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.text('Choose a project'), findsOneWidget);
      expect(find.byType(KitStateView), findsOneWidget);
      expect(find.textContaining('Takes seconds'), findsOneWidget);
    });

    testWidgets('an unknown capability id asserts in debug', (tester) async {
      await _pump(
        tester,
        const KitCapabilityExplainer.row(capability: 'no.such'),
      );
      expect(tester.takeException(), isA<AssertionError>());
    });
  });

  group('keyboard', () {
    testWidgets('Tab reaches the enable action and Enter runs it', (
      tester,
    ) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.linux;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final requests = _record('voice.model');
      await _pump(
        tester,
        const KitCapabilityExplainer.row(capability: 'voice.model'),
        size: const Size(1280, 800),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(requests, hasLength(1));
      debugDefaultTargetPlatformOverride = null;
    });

    testWidgets('.offer puts "Not now" after the enable action in Tab order', (
      tester,
    ) async {
      debugDefaultTargetPlatformOverride = TargetPlatform.linux;
      addTearDown(() => debugDefaultTargetPlatformOverride = null);
      final requests = _record('team.on');
      var notNow = 0;
      await _pump(
        tester,
        KitCapabilityExplainer.offer(
          capability: 'team.on',
          // Short enough for one line inside the 720 dp reading cap, so the
          // action and the close sit side by side as at any wide window.
          message: 'Turn it on.',
          onNotNow: () => notNow++,
        ),
        size: const Size(1280, 800),
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pumpAndSettle();
      expect(notNow, 1);
      expect(requests, isEmpty);
      debugDefaultTargetPlatformOverride = null;
    });
  });
}

class _FakeContext extends Fake implements BuildContext {}

// slice-queue-move: prompts queued for a server the app cannot reach move to
// a conversation on the connected server. Servers says how many wait on the
// server's row, and its menu names the move ("Move 4 waiting prompts to
// Laptop"). The sheet lists only that server's prompts (those that cannot
// move say why), offers a new or recent conversation, names the act on its
// primary, and ends with the one Undo bar. Failures keep the sheet open in
// plain words, with the technical text only under Details.
//
// Goldens: phone 412x915 and one wide window (1280x800), dark, with the
// app's real fonts at DPR 1. Regenerate deliberately:
//   flutter test --update-goldens test/revamp/queued_prompt_move_test.dart
// and look at every changed image before committing it.
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/queued_prompt_move.dart';
import 'package:opencode_mobile/ui/kit/kit_redact.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';

import '../../tool/capture/fixtures.dart';
import '../support/complete_message_history.dart';
import '../support/setup_capture_preferences.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _golden(String shot, Size size) => [
  'goldens/queued_prompt_move_$shot',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  'dark.png',
].join('_');

final _now = DateTime.now().millisecondsSinceEpoch;
const _hour = 3600000;

/// Four prompts queued for Studio Mac while it was away: two can move, one
/// may already have been sent, one carries a file only Studio Mac has. One
/// more waits for Desk, another server, and must never be listed or moved.
List<Map<String, Object?>> _entries() => [
  {
    'id': 'q1',
    'profileID': 'studio',
    'sessionID': 'ses_studio_a',
    'text':
        'Add a test for an expired coupon: the total must stay the same '
        'and the banner must say why.',
    'modelProviderID': 'anthropic',
    'modelID': 'claude-opus',
    'createdAt': _now - 3 * _hour,
  },
  {
    'id': 'q2',
    'profileID': 'studio',
    'sessionID': 'ses_studio_b',
    'text': 'Deploy with password=hunter2-staging and check the logs.',
    'createdAt': _now - 2 * _hour,
  },
  {
    'id': 'q3',
    'profileID': 'studio',
    'sessionID': 'ses_studio_b',
    'text': 'Run the checkout tests on CI too.',
    'createdAt': _now - _hour,
    'dispatchedAt': _now - _hour + 1000,
  },
  {
    'id': 'q4',
    'profileID': 'studio',
    'sessionID': 'ses_studio_a',
    'text': 'Summarise these notes.',
    'attachments': [
      {
        'mime': 'text/plain',
        'filename': 'coupon-notes.txt',
        'url': 'file:///home/dev/coupon-notes.txt',
      },
    ],
    'createdAt': _now - 30 * 60000,
  },
  {
    'id': 'd1',
    'profileID': 'desk',
    'sessionID': 'ses_desk',
    'text': 'Desk only: rotate the backup keys.',
    'createdAt': _now - _hour,
  },
];

List<ServerProfile> _profiles() => [
  ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://localhost',
    flavor: ServerFlavor.v1,
  ),
  ServerProfile(
    id: 'studio',
    name: 'Studio Mac',
    baseUrl: 'https://studio.example.net:4096',
    flavor: ServerFlavor.v1,
    serverVersion: '1.18.29',
  ),
  ServerProfile(
    id: 'desk',
    name: 'Desk',
    baseUrl: 'https://desk.example.net:4096',
    flavor: ServerFlavor.v1,
  ),
];

class _Store extends ProfileStore {
  _Store({required super.prefs, required this.activeProfile});
  final List<ServerProfile> _saved = _profiles();
  final String? activeProfile;
  @override
  List<ServerProfile> get profiles => List.unmodifiable(_saved);
  @override
  String? get activeId => activeProfile;
}

class _Api extends OpenCodeApi with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');
  @override
  Future<List<MessageWithParts>> messages(String id) async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
}

/// Connected to Laptop with two recent conversations. A new conversation
/// is made here instead of on a server; [failCreate] makes it fail the way
/// a server does, with technical text the person must not see as copy.
class _Laptop extends CaptureController {
  _Laptop(super.store, {this.failCreate = false});
  final bool failCreate;
  var created = 0;
  var flushes = 0;

  @override
  Future<Session> createSession() async {
    if (failCreate) {
      throw ApiException('HTTP 500 internal: sqlite busy at /session');
    }
    created++;
    final session = Session(id: 'ses_new_$created', title: 'New');
    sessionsById[session.id] = session;
    return session;
  }

  @override
  Future<void> flushOfflineQueue() async => flushes++;
}

void _mockPlatform(WidgetTester tester) {
  const secure = MethodChannel('plugins.it_nomads.com/flutter_secure_storage');
  const termux = MethodChannel('oc/termux');
  const tailscale = MethodChannel('oc/tailscale');
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(
    tailscale,
    (call) async => call.method == 'check' ? 'installed' : true,
  );
  messenger.setMockMethodCallHandler(
    secure,
    (call) async => call.method == 'readAll' ? <String, String>{} : null,
  );
  messenger.setMockMethodCallHandler(termux, (call) async {
    if (call.method == 'getCapabilities') return {'installed': false};
    return null;
  });
  addTearDown(() {
    messenger.setMockMethodCallHandler(secure, null);
    messenger.setMockMethodCallHandler(termux, null);
    messenger.setMockMethodCallHandler(tailscale, null);
  });
}

Future<void> _settle(WidgetTester tester, {int frames = 20}) async {
  for (var i = 0; i < frames; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

List<QueuedPrompt> _queue(_Laptop c) =>
    OfflineQueueStore(prefs: c.store.prefs).load();

QueuedPrompt _entry(_Laptop c, String id) =>
    _queue(c).singleWhere((entry) => entry.id == id);

/// Mounts Servers connected to Laptop (or to nothing) with the queue above
/// and opens Studio Mac's row menu.
Future<(_Laptop, Future<void> Function())> _openMenu(
  WidgetTester tester, {
  GlobalKey? boundary,
  Size size = _phone,
  bool connected = true,
  bool failCreate = false,
}) async {
  _mockPlatform(tester);
  debugPlatformCapabilities = const PlatformCapabilities.android();
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final prefs = await setupCapturePreferences();
  await prefs.setString('oc.offlineQueue', jsonEncode(_entries()));
  final store = _Store(
    prefs: prefs,
    activeProfile: connected ? 'laptop' : null,
  );
  final controller = _Laptop(store, failCreate: failCreate);
  if (connected) {
    controller
      ..api = _Api()
      ..status = StreamStatus.connected;
    controller.sessionsById['ses_checkout'] = Session(
      id: 'ses_checkout',
      title: 'Checkout redesign',
      time: SessionTime(created: _now - 5 * _hour, updated: _now - _hour),
    );
    controller.sessionsById['ses_docs'] = Session(
      id: 'ses_docs',
      title: 'Release notes',
      time: SessionTime(created: _now - 9 * _hour, updated: _now - 4 * _hour),
    );
  }
  await tester.pumpWidget(
    captureApp(
      home: const ServersScreen(),
      boundaryKey: boundary ?? GlobalKey(),
      controller: controller,
      store: store,
      routes: {
        '/home': (_) => const SizedBox.shrink(),
        '/guide': (_) => const SizedBox.shrink(),
      },
    ),
  );
  await _settle(tester);
  final row = find.byKey(const ValueKey('server-row-studio'));
  await tester.ensureVisible(row);
  await _settle(tester, frames: 3);
  await tester.longPress(row);
  await _settle(tester, frames: 6);
  return (
    controller,
    () async {
      KitUndo.commitPending();
      debugPlatformCapabilities = null;
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    },
  );
}

Future<(_Laptop, Future<void> Function())> _openSheet(
  WidgetTester tester, {
  GlobalKey? boundary,
  Size size = _phone,
  bool failCreate = false,
}) async {
  final (controller, done) = await _openMenu(
    tester,
    boundary: boundary,
    size: size,
    failCreate: failCreate,
  );
  try {
    await tester.tap(find.text('Move 4 waiting prompts to Laptop'));
    await _settle(tester);
    return (controller, done);
  } catch (_) {
    await done();
    rethrow;
  }
}

Future<void> _confirm(WidgetTester tester) async {
  final confirm = find.byKey(const ValueKey('queued-move-confirm'));
  await tester.ensureVisible(confirm);
  await tester.tap(confirm);
  await _settle(tester);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);
  tearDown(KitRedact.clearKnownSecrets);

  group('behaviour', () {
    testWidgets('the row says how many wait and its menu names the move', (
      tester,
    ) async {
      final (_, done) = await _openMenu(tester);
      try {
        expect(
          find.textContaining('4 prompts waiting to send', findRichText: true),
          findsOne,
        );
        expect(find.text('Move 4 waiting prompts to Laptop'), findsOne);
      } finally {
        await done();
      }
    });

    testWidgets('choose a conversation, confirm, and the picked prompts move '
        'there while the rest stay', (tester) async {
      final (c, done) = await _openSheet(tester);
      try {
        // Only Studio Mac's prompts are listed; Desk's never is.
        expect(find.text('From Studio Mac'), findsOne);
        expect(find.textContaining('Desk only'), findsNothing);
        // Secrets are masked in what the sheet shows.
        expect(find.textContaining('hunter2'), findsNothing);
        expect(
          find.textContaining(
            'May already have been sent. Check it on Studio Mac first.',
            findRichText: true,
          ),
          findsOne,
        );
        expect(
          find.textContaining(
            'Has a file only Studio Mac can open',
            findRichText: true,
          ),
          findsOne,
        );
        expect(
          find.text('Passwords and keys in 1 prompt stay hidden'),
          findsOne,
        );

        await tester.tap(find.text('Checkout redesign'));
        await _settle(tester, frames: 4);
        expect(find.text('Move 2 prompts to Laptop'), findsOne);
        await _confirm(tester);

        expect(find.byKey(const ValueKey('queued-move-sheet')), findsNothing);
        expect(find.text('2 prompts moved to Laptop'), findsOne);
        for (final id in ['q1', 'q2']) {
          expect(_entry(c, id).profileID, 'laptop');
          expect(_entry(c, id).sessionID, 'ses_checkout');
        }
        expect(_entry(c, 'q2').text, isNot(contains('hunter2')));
        expect(_entry(c, 'q1').modelID, 'claude-opus');
        expect(_entry(c, 'q3').profileID, 'studio');
        expect(_entry(c, 'q4').profileID, 'studio');
        expect(_entry(c, 'd1').profileID, 'desk');
        expect(_entry(c, 'd1').sessionID, 'ses_desk');
        expect(c.created, 0);
        // Nothing is sent until the Undo window has passed.
        expect(c.flushes, 0);
        KitUndo.commitPending();
        expect(c.flushes, 1);
      } finally {
        await done();
      }
    });

    testWidgets('a new conversation is the default; unpicking a prompt keeps '
        'it waiting', (tester) async {
      final (c, done) = await _openSheet(tester);
      try {
        await tester.tap(find.textContaining('Add a test for an expired'));
        await _settle(tester, frames: 4);
        expect(find.text('Move 1 prompt to Laptop'), findsOne);
        await _confirm(tester);
        expect(c.created, 1);
        expect(_entry(c, 'q2').profileID, 'laptop');
        expect(_entry(c, 'q2').sessionID, 'ses_new_1');
        expect(_entry(c, 'q1').profileID, 'studio');
        expect(find.text('1 prompt moved to Laptop'), findsOne);
      } finally {
        await done();
      }
    });

    testWidgets('Undo puts the prompts back exactly as they were', (
      tester,
    ) async {
      final (c, done) = await _openSheet(tester);
      final before = jsonEncode([for (final p in _queue(c)) p.toJson()]);
      try {
        await _confirm(tester);
        expect(_entry(c, 'q1').profileID, 'laptop');
        await tester.tap(find.text('Undo'));
        await _settle(tester);
        expect(jsonEncode([for (final p in _queue(c)) p.toJson()]), before);
        expect(c.flushes, 0);
      } finally {
        await done();
      }
    });

    testWidgets('Cancel moves nothing and starts no conversation', (
      tester,
    ) async {
      final (c, done) = await _openSheet(tester);
      final before = c.store.prefs.getString('oc.offlineQueue');
      try {
        await tester.tap(find.byKey(const ValueKey('queued-move-cancel')));
        await _settle(tester);
        expect(find.byKey(const ValueKey('queued-move-sheet')), findsNothing);
        expect(c.store.prefs.getString('oc.offlineQueue'), before);
        expect(c.created, 0);
      } finally {
        await done();
      }
    });

    testWidgets('a failure keeps the sheet open in plain words, with the '
        'technical text only under Details', (tester) async {
      final (c, done) = await _openSheet(tester, failCreate: true);
      final before = c.store.prefs.getString('oc.offlineQueue');
      try {
        await _confirm(tester);
        expect(find.byKey(const ValueKey('queued-move-sheet')), findsOne);
        expect(
          find.text(
            'Could not start a new conversation on Laptop, so nothing moved. '
            'Try again or choose an existing conversation.',
          ),
          findsOne,
        );
        expect(find.text('Details'), findsOne);
        expect(find.textContaining('sqlite'), findsNothing);
        expect(c.store.prefs.getString('oc.offlineQueue'), before);
      } finally {
        await done();
      }
    });

    testWidgets('with no connected server there is nowhere to move to', (
      tester,
    ) async {
      final (_, done) = await _openMenu(tester, connected: false);
      try {
        expect(
          find.textContaining('4 prompts waiting to send', findRichText: true),
          findsOne,
        );
        expect(find.textContaining('Move 4 waiting'), findsNothing);
      } finally {
        await done();
      }
    });
  });

  group('controller', () {
    Future<_Laptop> connected(WidgetTester tester) async {
      final prefs = await setupCapturePreferences();
      await prefs.setString('oc.offlineQueue', jsonEncode(_entries()));
      final c = _Laptop(_Store(prefs: prefs, activeProfile: 'laptop'))
        ..api = _Api()
        ..status = StreamStatus.connected;
      c.sessionsById['ses_checkout'] = Session(id: 'ses_checkout');
      addTearDown(c.dispose);
      return c;
    }

    testWidgets('a picked prompt no longer waiting is counted, not moved', (
      tester,
    ) async {
      final c = await connected(tester);
      final result = await c.moveQueuedPrompts(
        sourceProfileID: 'studio',
        promptIDs: {'q1', 'gone', 'q3'},
        sessionID: 'ses_checkout',
      );
      expect(result.moved, 1);
      expect(result.skipped, 2);
      expect(_entry(c, 'q1').profileID, 'laptop');
      expect(_entry(c, 'q3').profileID, 'studio');
    });

    testWidgets('Undo leaves a prompt that already started sending', (
      tester,
    ) async {
      final c = await connected(tester);
      final result = await c.moveQueuedPrompts(
        sourceProfileID: 'studio',
        promptIDs: {'q1', 'q2'},
        sessionID: 'ses_checkout',
      );
      // q1's send started on Laptop meanwhile.
      final queue = [
        for (final p in _queue(c)) p.id == 'q1' ? p.withDispatchedAt(1) : p,
      ];
      await OfflineQueueStore(prefs: c.store.prefs).save(queue);
      c.dispose();
      final reopened = _Laptop(c.store)
        ..api = _Api()
        ..status = StreamStatus.connected;
      addTearDown(reopened.dispose);
      expect(await reopened.undoQueuedPromptMove(result), 1);
      expect(_entry(reopened, 'q1').profileID, 'laptop');
      expect(_entry(reopened, 'q2').profileID, 'studio');
      expect(_entry(reopened, 'q2').sessionID, 'ses_studio_b');
    });

    testWidgets('profile isolation: no move to the source itself, to an '
        'unconnected server, or of another server\'s prompts', (tester) async {
      final c = await connected(tester);
      await expectLater(
        c.moveQueuedPrompts(
          sourceProfileID: 'laptop',
          promptIDs: {'q1'},
          sessionID: 'ses_checkout',
        ),
        throwsA(isA<QueuedPromptMoveException>()),
      );
      await expectLater(
        c.moveQueuedPrompts(
          sourceProfileID: 'studio',
          promptIDs: {'d1'},
          sessionID: 'ses_checkout',
        ),
        throwsA(
          isA<QueuedPromptMoveException>().having(
            (e) => e.problem,
            'problem',
            QueuedPromptMoveProblem.nothingToMove,
          ),
        ),
      );
      await expectLater(
        c.moveQueuedPrompts(
          sourceProfileID: 'studio',
          promptIDs: {'q1'},
          sessionID: 'not-on-laptop',
        ),
        throwsA(
          isA<QueuedPromptMoveException>().having(
            (e) => e.problem,
            'problem',
            QueuedPromptMoveProblem.conversationGone,
          ),
        ),
      );
      c.status = StreamStatus.disconnected;
      expect(c.queuedPromptMoveDestination, isNull);
      await expectLater(
        c.moveQueuedPrompts(
          sourceProfileID: 'studio',
          promptIDs: {'q1'},
          sessionID: 'ses_checkout',
        ),
        throwsA(
          isA<QueuedPromptMoveException>().having(
            (e) => e.problem,
            'problem',
            QueuedPromptMoveProblem.noDestination,
          ),
        ),
      );
      expect(_queue(c).map((p) => p.profileID).toList(), [
        'studio',
        'studio',
        'studio',
        'studio',
        'desk',
      ]);
      c.dispose();
    });

    test('a model or agent the destination lacks is not kept', () {
      final prompt = QueuedPrompt.fromJson(_entries().first)!;
      expect(
        QueuedPromptMove.keepsSelection(
          prompt,
          modelAvailable: (_) => false,
          agents: const [],
        ),
        isFalse,
      );
      expect(
        QueuedPromptMove.keepsSelection(
          prompt,
          modelAvailable: null,
          agents: const [],
        ),
        isTrue,
      );
    });
  });

  group('goldens', () {
    for (final size in [_phone, _wide]) {
      final wide = size == _wide ? ' wide' : '';
      testWidgets('server row menu$wide', (tester) async {
        final boundary = GlobalKey();
        debugDefaultTargetPlatformOverride =
            TargetPlatform.android; // ARCH-11; reset in finally
        final (_, done) = await _openMenu(
          tester,
          boundary: boundary,
          size: size,
        );
        try {
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile(_golden('row_menu', size)),
          );
        } finally {
          debugDefaultTargetPlatformOverride = null;
          await done();
        }
      });

      testWidgets('move sheet$wide', (tester) async {
        final boundary = GlobalKey();
        debugDefaultTargetPlatformOverride =
            TargetPlatform.android; // ARCH-11; reset in finally
        final (_, done) = await _openSheet(
          tester,
          boundary: boundary,
          size: size,
        );
        try {
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile(_golden('sheet', size)),
          );
        } finally {
          debugDefaultTargetPlatformOverride = null;
          await done();
        }
      });
    }
  });
}

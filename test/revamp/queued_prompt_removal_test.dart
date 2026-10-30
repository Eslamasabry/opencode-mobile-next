// slice-P7.2, queued prompts survive server removal: the Remove server sheet
// names how many prompts are still queued and says they move to Saved
// prompts; its other answer deletes them too; a queue that changed stops the
// removal in plain words. The kept prompts then show in the Saved prompts of
// the server in use.
//
// Goldens: phone 412x915 and one wide window (1280x800), dark, with the
// app's real fonts at DPR 1. Regenerate deliberately:
//   flutter test --update-goldens test/revamp/queued_prompt_removal_test.dart
// and look at every changed image before committing it.
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/models.dart';
import '../support/fake_server_gateway.dart';
import 'package:opencode_mobile/l10n/app_localizations.dart';
import 'package:opencode_mobile/platform/platform_capabilities.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/queued_prompt_removal.dart';
import 'package:opencode_mobile/ui/kit/kit_undo.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:opencode_mobile/ui/screens/servers_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../tool/capture/fixtures.dart';
import '../support/complete_message_history.dart';
import '../support/setup_capture_preferences.dart';
import '../support/stash_memory_vault.dart';
import '../support/composer_tools.dart';

const _phone = Size(412, 915);
const _wide = Size(1280, 800);

String _golden(String shot, Size size) => [
  'goldens/queued_prompt_removal_$shot',
  if (size != _phone) '${size.width.toInt()}x${size.height.toInt()}',
  'dark.png',
].join('_');

/// Queued an hour ago: the offline queue drops prompts older than
/// OfflineQueueStore.maxAge (14 days) against the real clock, so a fixed date
/// stops counting as queued once the calendar passes it. No screen shows it.
final _day = DateTime.now()
    .subtract(const Duration(hours: 1))
    .millisecondsSinceEpoch;

/// The Saved prompts goldens show the time, so they keep a fixed day: the
/// drafts store applies no age limit.
final _fixedDay = DateTime(2026, 9, 26, 18).millisecondsSinceEpoch;

/// Two prompts queued for Studio Mac while it was offline; the second one's
/// send started and was never confirmed.
String _queue({int? at}) {
  final day = at ?? _day;
  return jsonEncode([
    {
      'id': 'q1',
      'profileID': 'studio',
      'sessionID': 'ses_a',
      'text':
          'Add a test for an expired coupon: the total must stay the same '
          'and the banner must say why.',
      'attachments': [
        {
          'mime': 'text/plain',
          'filename': 'coupon-notes.txt',
          'url': 'file:///home/dev/coupon-notes.txt',
        },
      ],
      'createdAt': day,
    },
    {
      'id': 'q2',
      'profileID': 'studio',
      'sessionID': 'ses_b',
      'text': 'Run the checkout tests on CI too.',
      'createdAt': day + 60000,
      'dispatchedAt': day + 61000,
    },
  ]);
}

class _Store extends ProfileStore {
  _Store({required super.prefs, required List<ServerProfile> seeded})
    : _profiles = [...seeded];
  final List<ServerProfile> _profiles;
  @override
  List<ServerProfile> get profiles => List.unmodifiable(_profiles);
  @override
  String? get activeId => null;
}

/// Records what the sheet asked for instead of sweeping storage; the sweep
/// itself is covered by test/queued_prompt_removal_wiring_test.dart.
class _Servers extends CaptureController {
  _Servers(super.store, {this.fail});
  final QueuedPromptRemovalException? fail;
  final calls = <({String id, int? count, bool keep})>[];

  @override
  Future<DeleteProfileResult> deleteProfileAndLocalData(
    String profileId, {
    QueuedPromptRemovalPlan? queuedPrompts,
    bool keepQueuedPrompts = false,
    bool linuxGone = false,
  }) async {
    calls.add((
      id: profileId,
      count: queuedPrompts?.count,
      keep: keepQueuedPrompts,
    ));
    if (fail != null) throw fail!;
    return const DeleteProfileResult();
  }
}

List<ServerProfile> _profiles() => [
  ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'https://laptop.example.net',
    flavor: ServerFlavor.v2,
    serverVersion: '2.0.10',
  ),
  ServerProfile(
    id: 'studio',
    name: 'Studio Mac',
    baseUrl: 'https://studio.example.net:4096',
    flavor: ServerFlavor.v2,
    serverVersion: '1.18.29',
  ),
];

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

/// Mounts Servers with Studio Mac's two queued prompts, opens its Remove
/// sheet, and returns the controller and the cleanup.
Future<(_Servers, Future<void> Function())> _openRemove(
  WidgetTester tester, {
  GlobalKey? boundary,
  Size size = _phone,
  QueuedPromptRemovalException? fail,
  String? queueData,
}) async {
  _mockPlatform(tester);
  debugPlatformCapabilities = const PlatformCapabilities.android();
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final prefs = await setupCapturePreferences();
  await prefs.setString('oc.offlineQueue', queueData ?? _queue());
  final store = _Store(prefs: prefs, seeded: _profiles());
  final controller = _Servers(store, fail: fail);
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
  await tester.tap(find.text('Remove').last);
  await _settle(tester);
  return (
    controller,
    () async {
      debugPlatformCapabilities = null;
      await tester.pumpWidget(const SizedBox.shrink());
      controller.dispose();
      await tester.pump();
    },
  );
}

// The Saved prompts sheet of the server in use, after Studio Mac was
// removed with its queued prompts kept.

class _Api extends FakeServerGateway with CompleteMessageHistory {
  _Api() : super(baseUrl: 'http://localhost');
  @override
  Future<List<MessageWithParts>> messages(String id) async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissions() async => [];
  @override
  Future<List<PermissionRequest>> pendingPermissionsV2() async => [];
}

class _Laptop extends ProfileStore {
  _Laptop(SharedPreferences prefs) : super(prefs: prefs);
  final saved = ServerProfile(
    id: 'laptop',
    name: 'Laptop',
    baseUrl: 'http://localhost',
  );
  @override
  List<ServerProfile> get profiles => [saved];
}

class _Chat extends ConnectionController {
  _Chat(super.store)
    : super(
        stashAttachmentVault: StashMemoryVault(),
        draftAttachmentVault: StashMemoryVault(),
      );
  @override
  ServerProfile get profile => (store as _Laptop).saved;
}

Future<void> _frames(WidgetTester tester) async {
  for (var frame = 0; frame < 10; frame++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

Future<void> _openSaved(WidgetTester tester) async {
  await openComposerTools(tester);
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tools-prompts')));
  await tester.pumpAndSettle();
  await Scrollable.ensureVisible(
    tester.element(find.byKey(const Key('composer-tool-saved'))),
    alignment: .5,
  );
  await tester.pumpAndSettle();
  await tester.tap(find.byKey(const Key('composer-tool-saved')));
  await _frames(tester);
}

/// Returns the cleanup to await before the test ends.
Future<Future<void> Function()> _savedPrompts(
  WidgetTester tester, {
  required GlobalKey boundary,
  Size size = _phone,
}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  debugDefaultTargetPlatformOverride = TargetPlatform.android;
  final kept = [
    for (final entry in jsonDecode(_queue(at: _fixedDay)) as List)
      {...(entry as Map), 'profileID': 'studio'},
  ];
  SharedPreferences.setMockInitialValues({
    QueuedPromptRemoval.draftsKey: jsonEncode(kept),
  });
  final c = _Chat(_Laptop(await SharedPreferences.getInstance()))
    ..api = _Api()
    ..status = StreamStatus.connected;
  c.sessionsById['s'] = Session(id: 's', title: 'Checkout redesign');
  Future<void> done() async {
    KitUndo.commitPending();
    await tester.pumpWidget(const SizedBox.shrink());
    debugDefaultTargetPlatformOverride = null;
    c.dispose();
  }

  try {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(c)],
        child: RepaintBoundary(
          key: boundary,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: captureTheme(),
            localizationsDelegates: AppLocalizations.localizationsDelegates,
            supportedLocales: AppLocalizations.supportedLocales,
            builder: (context, child) => MediaQuery(
              data: MediaQuery.of(context).copyWith(disableAnimations: true),
              child: child!,
            ),
            home: const ChatScreen(sessionID: 's'),
          ),
        ),
      ),
    );
    await _frames(tester);
    await _openSaved(tester);
  } catch (_) {
    await done();
    rethrow;
  }
  return done;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(loadCaptureFonts);

  group('behaviour', () {
    testWidgets('an unreadable queue stops removal and can be retried', (
      tester,
    ) async {
      const corruptQueue = '{unreadable queue';
      final (controller, done) = await _openRemove(
        tester,
        queueData: corruptQueue,
      );
      try {
        expect(
          find.byKey(const ValueKey('confirm-remove-server-studio')),
          findsNothing,
        );
        expect(controller.calls, isEmpty);
        expect(
          controller.store.prefs.getString('oc.offlineQueue'),
          corruptQueue,
        );
        expect(find.byKey(const ValueKey('server-row-studio')), findsOne);
        expect(
          find.text(
            lookupAppLocalizations(
              const Locale('en'),
            ).serversRemoveQueuedUnreadable('Studio Mac'),
          ),
          findsOne,
        );
        expect(find.text('Details'), findsOne);
        expect(find.textContaining('StateError'), findsNothing);

        await controller.store.prefs.setString('oc.offlineQueue', _queue());
        final row = find.byKey(const ValueKey('server-row-studio'));
        await tester.ensureVisible(row);
        await tester.longPress(row);
        await _settle(tester, frames: 6);
        await tester.tap(find.text('Remove').last);
        await _settle(tester);
        await tester.tap(
          find.byKey(const ValueKey('confirm-remove-server-studio')),
        );
        await _settle(tester);
        expect(controller.calls, [(id: 'studio', count: 2, keep: true)]);
      } finally {
        await done();
      }
    });

    testWidgets('the remove sheet counts the queued prompts and keeps them '
        'by default', (tester) async {
      final (controller, done) = await _openRemove(tester);
      try {
        expect(find.text('Remove Studio Mac?'), findsOneWidget);
        expect(
          find.text('2 queued prompts move to Saved prompts'),
          findsOneWidget,
        );
        expect(find.text('1 of them may already have been sent'), findsOne);
        expect(find.text('Remove and delete 2 queued prompts'), findsOne);
        await tester.tap(
          find.byKey(const ValueKey('confirm-remove-server-studio')),
        );
        await _settle(tester);
        expect(controller.calls, [(id: 'studio', count: 2, keep: true)]);
      } finally {
        await done();
      }
    });

    testWidgets('the other answer removes the server and deletes them', (
      tester,
    ) async {
      final (controller, done) = await _openRemove(tester);
      try {
        await tester.tap(find.text('Remove and delete 2 queued prompts'));
        await _settle(tester);
        expect(controller.calls, [(id: 'studio', count: 2, keep: false)]);
      } finally {
        await done();
      }
    });

    testWidgets('Cancel removes nothing', (tester) async {
      final (controller, done) = await _openRemove(tester);
      try {
        await tester.tap(find.text('Cancel'));
        await _settle(tester);
        expect(controller.calls, isEmpty);
      } finally {
        await done();
      }
    });

    testWidgets('a queue that changed meanwhile is said in plain words', (
      tester,
    ) async {
      final (_, done) = await _openRemove(
        tester,
        fail: const QueuedPromptRemovalException(changed: true),
      );
      try {
        await tester.tap(
          find.byKey(const ValueKey('confirm-remove-server-studio')),
        );
        await _settle(tester);
        expect(
          find.textContaining(
            'The queued prompts for Studio Mac changed, so nothing was '
            'removed.',
            findRichText: true,
          ),
          findsOneWidget,
        );
        expect(find.textContaining('Exception'), findsNothing);
        expect(find.byKey(const ValueKey('server-row-studio')), findsOne);
      } finally {
        await done();
      }
    });

    testWidgets('a queue unreadable after confirmation keeps its server', (
      tester,
    ) async {
      final (controller, done) = await _openRemove(
        tester,
        fail: const QueuedPromptRemovalException(
          changed: false,
          unreadable: true,
        ),
      );
      try {
        await tester.tap(
          find.byKey(const ValueKey('confirm-remove-server-studio')),
        );
        await _settle(tester);
        expect(controller.calls, [(id: 'studio', count: 2, keep: true)]);
        expect(
          find.textContaining(
            'The queued prompts for Studio Mac cannot be read.',
            findRichText: true,
          ),
          findsOne,
        );
        expect(find.text('Details'), findsOne);
        expect(find.byKey(const ValueKey('server-row-studio')), findsOne);
        expect(controller.store.prefs.getString('oc.offlineQueue'), _queue());
      } finally {
        await done();
      }
    });

    testWidgets('kept prompts show in the Saved prompts of another server', (
      tester,
    ) async {
      final done = await _savedPrompts(tester, boundary: GlobalKey());
      try {
        expect(
          find.textContaining('Run the checkout tests on CI too.'),
          findsOneWidget,
        );
        expect(
          find.textContaining('Add a test for an expired coupon'),
          findsOneWidget,
        );
      } finally {
        await done();
      }
    });
  });

  group('goldens', () {
    for (final size in [_phone, _wide]) {
      final wide = size == _wide ? ' wide' : '';
      testWidgets('remove sheet with queued prompts$wide', (tester) async {
        final boundary = GlobalKey();
        final (_, done) = await _openRemove(
          tester,
          boundary: boundary,
          size: size,
        );
        try {
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile(_golden('remove_sheet', size)),
          );
        } finally {
          await done();
        }
      });

      testWidgets('kept prompts in Saved prompts$wide', (tester) async {
        final boundary = GlobalKey();
        final done = await _savedPrompts(
          tester,
          boundary: boundary,
          size: size,
        );
        try {
          expect(tester.takeException(), isNull);
          await expectLater(
            find.byKey(boundary),
            matchesGoldenFile(_golden('saved_prompts', size)),
          );
        } finally {
          await done();
        }
      });
    }
  });
}

import 'support/complete_message_history.dart';
import 'dart:convert';
import 'dart:async';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/opencode_api.dart';
import 'package:opencode_mobile/api/sse.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_drafts.dart';
import 'package:opencode_mobile/state/draft_attachments.dart';
import 'package:opencode_mobile/ui/screens/chat_screen.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

class _DraftStorage extends InMemorySharedPreferencesStore {
  _DraftStorage(super.data) : super.withData();
  bool refuse = false;
  Completer<void>? gate;
  bool get gated => gate != null;
  @override
  Future<bool> setValue(String type, String key, Object value) async {
    if (key == 'flutter.oc.sessionDrafts') {
      await gate?.future;
      if (refuse) return false;
    }
    return super.setValue(type, key, value);
  }

  @override
  Future<bool> remove(String key) async {
    if (key == 'flutter.oc.sessionDrafts' && refuse) return false;
    return super.remove(key);
  }
}

class _FakeApi extends OpenCodeApi with CompleteMessageHistory {
  _FakeApi() : super(baseUrl: 'http://localhost');

  @override
  Future<List<Session>> sessions() async => const [];

  @override
  Future<Map<String, String>> sessionStatuses() async => const {};

  @override
  Future<List<MessageWithParts>> messages(String id) async => [];

  @override
  Future<Session> session(String id) async => Session(id: id);
}

class _DelayedRecoveryVault extends DraftAttachmentVault {
  final gate = Completer<void>();
  @override
  Future<DraftAttachmentRecovery> restore(
    String owner,
    List<DraftAttachmentRef> refs, {
    required bool sameLocation,
  }) async {
    await gate.future;
    return super.restore(owner, refs, sameLocation: sameLocation);
  }
}

Future<ConnectionController> _controller(
  _FakeApi api, {
  StreamStatus status = StreamStatus.connected,
  bool twoProfiles = false,
  void Function(_DraftStorage)? configureStorage,
  DraftAttachmentVault? vault,
}) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {
        'id': 'profile-1',
        'name': 'Test server',
        'baseUrl': 'http://localhost',
        'username': '',
      },
      if (twoProfiles)
        {
          'id': 'profile-2',
          'name': 'Other server',
          'baseUrl': 'http://other',
          'username': '',
        },
    ]),
    'oc.activeProfile': 'profile-1',
  });
  if (configureStorage != null) {
    final disk = _DraftStorage(
      await SharedPreferencesStorePlatform.instance.getAll(),
    );
    configureStorage(disk);
    SharedPreferencesStorePlatform.instance = disk;
    SharedPreferences.resetStatic();
  }
  final prefs = await SharedPreferences.getInstance();
  final store = ProfileStore(prefs: prefs);
  await store.load();
  final controller = ConnectionController(store, draftAttachmentVault: vault)
    ..api = api
    ..status = status;
  return controller;
}

Future<void> _pumpChat(WidgetTester tester, ConnectionController conn) async {
  await tester.pumpWidget(
    ProviderScope(
      overrides: [connProvider.overrideWithValue(conn)],
      child: const MaterialApp(home: ChatScreen(sessionID: 'session-1')),
    ),
  );
  await tester.pump();
  await tester.pump();
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // See offline_queue_test.dart: unmocked flutter_secure_storage reads
    // hang inside testWidgets; answer them with null.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  test(
    'attachment-only drafts persist references and reject corrupt metadata writes',
    () async {
      final c = await _controller(_FakeApi());
      addTearDown(c.dispose);
      await c.saveSessionDraft(
        's',
        '',
        attachments: const [
          PromptAttachment(
            filename: 'server.txt',
            mime: 'text/plain',
            url: 'file:///project/server.txt',
          ),
        ],
        attachmentDirectory: '/project',
      );
      final saved = SessionDraftStore(
        prefs: c.store.prefs,
      ).load().values.single;
      expect(saved.text, isEmpty);
      expect(saved.attachments.single.filename, 'server.txt');
      expect(saved.directory, '/project');
      final recovered = await c.restoreDraftAttachments(
        's',
        profileID: 'profile-1',
        directory: '/other',
        workspace: null,
      );
      expect(recovered.unavailable, ['server.txt']);
      await c.store.prefs.setString('oc.sessionDrafts', '{broken');
      await expectLater(
        c.saveSessionDraft('s', 'replacement'),
        throwsA(isA<SessionDraftWriteException>()),
      );
      expect(c.store.prefs.getString('oc.sessionDrafts'), '{broken');
    },
  );

  test(
    'failed metadata save collects new bytes while retaining the previous draft',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'oc-draft-transaction-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final vault = DraftAttachmentVault(directory: () async => directory);
      late _DraftStorage disk;
      final c = await _controller(
        _FakeApi(),
        vault: vault,
        configureStorage: (value) => disk = value,
      );
      addTearDown(c.dispose);
      const old = PromptAttachment(
        filename: 'old.txt',
        mime: 'text/plain',
        url: 'data:text/plain;base64,b2xk',
      );
      const next = PromptAttachment(
        filename: 'next.txt',
        mime: 'text/plain',
        url: 'data:text/plain;base64,bmV4dA==',
      );
      await c.saveSessionDraft('s', '', attachments: [old]);
      disk.refuse = true;
      await expectLater(
        c.saveSessionDraft('s', 'edit', attachments: [next]),
        throwsA(isA<SessionDraftWriteException>()),
      );
      final restored = await c.restoreDraftAttachments(
        's',
        profileID: 'profile-1',
        directory: null,
        workspace: null,
      );
      expect(restored.attachments.single.url, old.url);
      expect(
        await directory
            .list(recursive: true)
            .where((entry) => entry is File)
            .length,
        1,
      );
      disk.refuse = false;
      expect(await c.clearAllSessionDrafts(), isTrue);
      expect(
        await directory
            .list(recursive: true)
            .where((entry) => entry is File)
            .isEmpty,
        isTrue,
      );
    },
  );

  test(
    'deleting a server collects its files without removing another server draft',
    () async {
      final directory = await Directory.systemTemp.createTemp(
        'oc-draft-owner-',
      );
      addTearDown(() => directory.delete(recursive: true));
      final vault = DraftAttachmentVault(directory: () async => directory);
      final c = await _controller(_FakeApi(), vault: vault, twoProfiles: true);
      addTearDown(c.dispose);
      const attachment = PromptAttachment(
        filename: 'same.txt',
        mime: 'text/plain',
        url: 'data:text/plain;base64,c2FtZQ==',
      );
      await c.saveSessionDraft(
        's',
        '',
        profileID: 'profile-1',
        attachments: [attachment],
      );
      await c.saveSessionDraft(
        's',
        '',
        profileID: 'profile-2',
        attachments: [attachment],
      );
      final deletion = await c.deleteProfileAndLocalData('profile-1');
      expect(deletion.complete, isTrue);
      final restored = await c.restoreDraftAttachments(
        's',
        profileID: 'profile-2',
        directory: null,
        workspace: null,
      );
      expect(restored.attachments.single.url, attachment.url);
      expect(
        await directory
            .list(recursive: true)
            .where((entry) => entry is File)
            .length,
        1,
      );
    },
  );

  testWidgets(
    'closing during recovery does not replace the stored draft with empty attachments',
    (tester) async {
      final vault = _DelayedRecoveryVault();
      final c = await _controller(_FakeApi(), vault: vault);
      addTearDown(c.dispose);
      await c.saveSessionDraft(
        'session-1',
        '',
        attachments: const [
          PromptAttachment(
            filename: 'saved.txt',
            mime: 'text/plain',
            url: 'https://example.com/saved.txt',
          ),
        ],
      );
      await _pumpChat(tester, c);
      await tester.pumpWidget(const SizedBox());
      vault.gate.complete();
      await tester.pump();
      await tester.pump();
      expect(
        c.savedSessionDraft('session-1')!.attachments.single.filename,
        'saved.txt',
      );
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'restored attachment removal autosaves an attachment-only draft',
    (tester) async {
      final c = await _controller(_FakeApi());
      addTearDown(c.dispose);
      await c.saveSessionDraft(
        'session-1',
        '',
        attachments: const [
          PromptAttachment(
            filename: 'server.txt',
            mime: 'text/plain',
            url: 'https://example.com/server.txt',
          ),
        ],
      );
      await _pumpChat(tester, c);
      await tester.pumpAndSettle();
      expect(find.textContaining('server.txt'), findsWidgets);
      await tester.tap(find.byTooltip('Remove attachment server.txt'));
      await tester.pump(const Duration(milliseconds: 700));
      await tester.pumpAndSettle();
      expect(c.savedSessionDraft('session-1'), isNull);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'declining partial recovery retains stored attachments until explicit acceptance',
    (tester) async {
      final c = await _controller(_FakeApi());
      addTearDown(c.dispose);
      await c.saveSessionDraft(
        'session-1',
        'Keep text',
        attachments: const [
          PromptAttachment(
            filename: 'project.txt',
            mime: 'text/plain',
            url: 'file:///other/project.txt',
          ),
        ],
        attachmentDirectory: '/other',
      );
      await _pumpChat(tester, c);
      await tester.pumpAndSettle();
      expect(find.text('Some attachments need attention'), findsOneWidget);
      await tester.tap(find.text('Keep saved draft'));
      await tester.pumpAndSettle();
      expect(
        tester
            .widget<TextField>(find.byKey(const Key('chat-composer-field')))
            .readOnly,
        isTrue,
      );
      await tester.pump(const Duration(milliseconds: 700));
      expect(c.savedSessionDraft('session-1')!.text, 'Keep text');
      expect(c.savedSessionDraft('session-1')!.attachments, hasLength(1));
      await tester.tap(find.byTooltip('Retry saving draft'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Use available attachments'));
      await tester.pumpAndSettle();
      expect(c.savedSessionDraft('session-1')!.text, 'Keep text');
      expect(c.savedSessionDraft('session-1')!.attachments, isEmpty);
      expect(tester.takeException(), isNull);
    },
  );

  test(
    'same session IDs on different servers retain independent drafts',
    () async {
      final c = await _controller(_FakeApi(), twoProfiles: true);
      addTearDown(c.dispose);
      await c.saveSessionDraft('same-session', 'first server');
      await c.store.setActiveId('profile-2');
      expect(c.sessionDraft('same-session'), isNull);
      await c.saveSessionDraft('same-session', 'second server');
      expect(c.sessionDraft('same-session'), 'second server');
      await c.store.setActiveId('profile-1');
      expect(c.sessionDraft('same-session'), 'first server');
      final reloaded = SessionDraftStore(prefs: c.store.prefs).load();
      expect(
        reloaded.values.map((d) => d.text),
        containsAll(['first server', 'second server']),
      );
      expect(reloaded, hasLength(2));
    },
  );

  test(
    'failed saves retain the last persisted draft and retry can recover',
    () async {
      late _DraftStorage disk;
      final c = await _controller(
        _FakeApi(),
        configureStorage: (value) => disk = value,
      );
      addTearDown(c.dispose);
      await c.saveSessionDraft('s', 'persisted');
      disk.refuse = true;
      await expectLater(
        c.saveSessionDraft('s', 'unsaved edit'),
        throwsA(isA<SessionDraftWriteException>()),
      );
      expect(c.sessionDraft('s'), 'persisted');
      expect(
        SessionDraftStore(prefs: c.store.prefs).load().values.single.text,
        'persisted',
      );
      disk.refuse = false;
      await c.saveSessionDraft('s', 'unsaved edit');
      expect(c.sessionDraft('s'), 'unsaved edit');
    },
  );

  test('overlapping saves and clear preserve invocation order', () async {
    late _DraftStorage disk;
    final c = await _controller(
      _FakeApi(),
      configureStorage: (value) => disk = value,
    );
    addTearDown(c.dispose);
    disk.gate = Completer<void>();
    final first = c.saveSessionDraft('s', 'older');
    await Future<void>.delayed(Duration.zero);
    final second = c.saveSessionDraft('s', 'newer');
    final clear = c.clearAllSessionDrafts();
    disk.gate!.complete();
    await Future.wait([first, second]);
    expect(await clear, isTrue);
    expect(c.sessionDraft('s'), isNull);
    expect(SessionDraftStore(prefs: c.store.prefs).load(), isEmpty);
  });

  test(
    'late saves cannot recreate data after the owning profile was removed',
    () async {
      final c = await _controller(_FakeApi());
      addTearDown(c.dispose);
      await c.saveSessionDraft('s', 'old');
      final deletion = await c.deleteProfileAndLocalData('profile-1');
      expect(deletion.complete, isTrue);
      await expectLater(
        c.saveSessionDraft('s', 'late', profileID: 'profile-1'),
        throwsA(isA<SessionDraftWriteException>()),
      );
      expect(SessionDraftStore(prefs: c.store.prefs).load(), isEmpty);
      await c.saveSessionDraft('s', '', profileID: 'profile-1');
    },
  );

  testWidgets('failed save keeps the route and text until Retry succeeds', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    late _DraftStorage disk;
    final c = await _controller(
      _FakeApi(),
      configureStorage: (value) => disk = value,
    );
    addTearDown(c.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [connProvider.overrideWithValue(c)],
        child: MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(
              context,
            ).copyWith(textScaler: TextScaler.linear(1.7)),
            child: child!,
          ),
          home: Builder(
            builder: (context) => Scaffold(
              body: TextButton(
                onPressed: () => Navigator.of(context).push(
                  MaterialPageRoute<void>(
                    builder: (_) => const ChatScreen(sessionID: 'session-1'),
                  ),
                ),
                child: const Text('Open chat'),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('Open chat'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'Keep this draft',
    );
    disk.refuse = true;
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('leave-unsaved-draft')), findsOneWidget);
    await tester.ensureVisible(find.text('Keep editing'));
    await tester.tap(find.text('Keep editing'));
    await tester.pumpAndSettle();
    tester.view.viewInsets = const FakeViewPadding(bottom: 260);
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('draft-save-error')), findsOneWidget);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('chat-composer-field')))
          .controller!
          .text,
      'Keep this draft',
    );
    expect(c.sessionDraft('session-1'), isNull);
    disk.refuse = false;
    await tester.tap(find.byTooltip('Retry saving draft'));
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('draft-save-error')), findsNothing);
    expect(c.sessionDraft('session-1'), 'Keep this draft');
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    expect(find.text('Open chat'), findsOneWidget);
  });

  test('session drafts persist and reload', () async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final store = SessionDraftStore(prefs: prefs);
    expect(
      await store.save({
        'session-1': const SessionDraft(
          sessionID: 'session-1',
          text: 'half-written prompt',
          updatedAt: 5,
        ),
      }),
      isTrue,
    );

    final reloaded = SessionDraftStore(prefs: prefs).load();
    expect(reloaded, hasLength(1));
    expect(reloaded['session-1']?.text, 'half-written prompt');
    expect(reloaded['session-1']?.updatedAt, 5);
  });

  test(
    'a full store refuses new drafts without evicting unsent work',
    () async {
      SharedPreferences.setMockInitialValues({});
      final prefs = await SharedPreferences.getInstance();
      final store = SessionDraftStore(prefs: prefs);
      final drafts = {
        for (var i = 0; i < SessionDraftStore.maxDrafts; i++)
          'session-$i': SessionDraft(
            sessionID: 'session-$i',
            text: 'draft $i',
            updatedAt: i,
          ),
      };
      expect(await store.save(drafts), isTrue);

      drafts['extra'] = const SessionDraft(
        sessionID: 'extra',
        text: 'new',
        updatedAt: 999,
      );
      expect(await store.save(drafts), isFalse);

      final reloaded = SessionDraftStore(prefs: prefs).load();
      expect(reloaded, hasLength(SessionDraftStore.maxDrafts));
      // Even the oldest drafts survive a refused save.
      for (var i = 0; i < 5; i++) {
        expect(reloaded.containsKey('session-$i'), isTrue);
      }
      expect(reloaded.containsKey('extra'), isFalse);
    },
  );

  test('saving an empty draft clears the stored entry', () async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);

    await controller.saveSessionDraft('session-1', 'keep me');
    expect(controller.sessionDraft('session-1'), 'keep me');

    await controller.saveSessionDraft('session-1', '   ');
    expect(controller.sessionDraft('session-1'), isNull);
    final prefs = await SharedPreferences.getInstance();
    expect(SessionDraftStore(prefs: prefs).load(), isEmpty);
  });

  testWidgets('composer text survives leaving and reopening a chat', (
    tester,
  ) async {
    final api = _FakeApi();
    final controller = await _controller(api);
    addTearDown(controller.dispose);
    await _pumpChat(tester, controller);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'unsent thought',
    );
    await tester.pump();

    // Navigate away: the route disposes and the draft persists.
    await tester.pumpWidget(const SizedBox());
    await tester.pump();
    expect(controller.sessionDraft('session-1'), 'unsent thought');

    await _pumpChat(tester, controller);
    expect(
      tester
          .widget<TextField>(find.byKey(const Key('chat-composer-field')))
          .controller
          ?.text,
      'unsent thought',
    );
  });

  testWidgets('sending clears the persisted draft', (tester) async {
    final api = _FakeApi();
    final controller = await _controller(
      api,
      status: StreamStatus.disconnected,
    );
    addTearDown(controller.dispose);
    await controller.saveSessionDraft('session-1', 'stale copy');
    await _pumpChat(tester, controller);

    await tester.enterText(
      find.byKey(const Key('chat-composer-field')),
      'send me',
    );
    await tester.pump();
    await tester.tap(find.byTooltip('Send'));
    await tester.pump();
    await tester.pump();

    // Disconnected send queues the prompt and the draft is gone.
    expect(controller.queuedPromptCount, 1);
    expect(controller.sessionDraft('session-1'), isNull);
  });
}

import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/widget_snapshot.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_drafts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shared_preferences_platform_interface/shared_preferences_platform_interface.dart';

/// A preferences store that refuses to write the keys it is told to refuse,
/// the way a full disk or a broken store does: `setValue`/`remove` answer
/// false and the value stays put. Keys are given unprefixed; the store sees
/// them with the plugin's `flutter.` prefix.
class _RefusingStore extends InMemorySharedPreferencesStore {
  _RefusingStore(super.data, this.refused) : super.withData();

  final Set<String> refused;

  bool _blocks(String key) =>
      refused.contains(key.replaceFirst('flutter.', ''));

  @override
  Future<bool> remove(String key) async =>
      _blocks(key) ? false : super.remove(key);

  @override
  Future<bool> setValue(String valueType, String key, Object value) async =>
      _blocks(key) ? false : super.setValue(valueType, key, value);
}

/// Everything the app persists for `doomed`, alongside a second profile whose
/// identically shaped data must survive.
Map<String, Object> _seed() => {
  'oc.profiles': jsonEncode([
    {
      'id': 'doomed',
      'name': 'Old workstation',
      'baseUrl': 'https://old.example:4096',
      'username': 'dev',
      'flavor': 'v2',
      'serverVersion': '0.14.2',
    },
    {
      'id': 'keeper',
      'name': 'Laptop',
      'baseUrl': 'https://keep.example:4096',
      'username': '',
    },
  ]),
  'oc.activeProfile': 'doomed',

  // Per-profile preferences.
  'oc.model.doomed': 'anthropic|claude',
  'oc.modelExplicit.doomed': true,
  'oc.agent.doomed': 'build',
  'oc.variant.doomed': 'high',
  'oc.sessionModels.doomed': jsonEncode({'ses_1': 'anthropic|opus|'}),
  'oc.sessionDisplayModels.doomed': '{}',
  'oc.sessionDisplayModels.doomed.agents': '{}',
  'oc.modelLibrary.doomed': jsonEncode({
    'favorites': [],
    'recent': [
      {'providerID': 'anthropic', 'modelID': 'opus'},
    ],
  }),
  'oc.modelLibrary.keeper': jsonEncode({
    'favorites': [
      {'providerID': 'openai', 'modelID': 'gpt'},
    ],
    'recent': [],
  }),
  'oc.location.doomed': jsonEncode({
    'directory': '/home/dev/code',
    'workspace': 'main',
  }),
  'oc.providerRuntimeRefresh.v1.doomed.%2Fhome%2Fdev%2Fcode%0Amain': true,
  'oc.model.keeper': 'openai|gpt',
  'oc.modelExplicit.keeper': true,
  'oc.agent.keeper': 'plan',
  'oc.variant.keeper': 'low',
  'oc.location.keeper': jsonEncode({'directory': '/srv', 'workspace': null}),
  'oc.providerRuntimeRefresh.v1.keeper.%2Fsrv%0A%3Cdefault%3E': true,

  // Shared blobs that carry profile attribution inside them.
  'oc.offlineQueue': jsonEncode([
    {
      'id': 'q1',
      'profileID': 'doomed',
      'sessionID': 'ses_a',
      'text': 'secret prompt',
      'attachments': [
        {
          'mime': 'image/png',
          'filename': 'screenshot.png',
          'url': 'data:image/png;base64,AAAA',
        },
      ],
      'createdAt': 1,
    },
    {
      'id': 'q2',
      'profileID': 'doomed',
      'sessionID': 'ses_b',
      'text': 'another',
      'createdAt': 2,
    },
    {
      'id': 'q3',
      'profileID': 'keeper',
      'sessionID': 'ses_c',
      'text': 'keep me',
      'createdAt': 3,
    },
  ]),
  'oc.sessionDrafts': jsonEncode([
    {
      'sessionID': 'ses_a',
      'profileID': 'doomed',
      'text': 'half-written prompt',
      'updatedAt': 9,
    },
    {
      'sessionID': 'ses_c',
      'profileID': 'keeper',
      'text': 'keep this draft',
      'updatedAt': 8,
    },
    // Written before drafts recorded a profile: unattributable.
    {'sessionID': 'ses_legacy', 'text': 'orphan draft', 'updatedAt': 7},
  ]),
  'oc.widgetSessions': jsonEncode({
    'connected': true,
    'profileID': 'doomed',
    'sessions': [
      {
        'id': 'ses_a',
        'title': 'Refactor billing',
        'busy': false,
        'updatedAt': 5,
      },
    ],
  }),
  // The launcher's pinned-session shortcuts and the Quick Settings tile's
  // count follow the widget snapshot's ownership rule.
  'oc.pinnedShortcuts': jsonEncode({
    'profileID': 'doomed',
    'sessions': [
      {'id': 'ses_a', 'title': 'Refactor billing'},
    ],
  }),
  'oc.attentionTile': jsonEncode({
    'pendingCount': 2,
    'profileID': 'doomed',
    'updatedAt': 5,
  }),

  // App-wide keys that a profile deletion must never touch.
  'oc.appearance': 'dark',
  'oc.themePack': 'gruvbox',
  'oc.transcript.reasoningExpanded': true,
  'oc.keepLiveInBackground': true,
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late List<String> secureDeletes;
  late List<Map<String, Object?>> shortcutPublishes;

  setUp(() {
    secureDeletes = [];
    // ProfileStore.load/remove reach flutter_secure_storage, whose unmocked
    // channel never answers. Record deletes so the Keystore password can be
    // asserted on rather than assumed.
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (call) async {
            if (call.method == 'delete') {
              secureDeletes.add(
                (call.arguments as Map)['key']?.toString() ?? '',
              );
            }
            return null;
          },
        );
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('oc/background'),
          (_) async => null,
        );
    shortcutPublishes = [];
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(const MethodChannel('oc/shortcut'), (
          call,
        ) async {
          if (call.method == 'setPinnedSessions') {
            shortcutPublishes.add(
              (call.arguments as Map).cast<String, Object?>(),
            );
          }
          return null;
        });
  });

  Future<(ConnectionController, SharedPreferences)> boot() async {
    SharedPreferences.setMockInitialValues(_seed());
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);
    await store.load();
    final controller = ConnectionController(store);
    addTearDown(controller.dispose);
    return (controller, prefs);
  }

  /// Boots the same fixture behind a store that refuses [refused].
  Future<(ConnectionController, SharedPreferences)> bootRefusing(
    Set<String> refused,
  ) async {
    SharedPreferences.setMockInitialValues(_seed());
    final seeded = await SharedPreferencesStorePlatform.instance.getAll();
    SharedPreferencesStorePlatform.instance = _RefusingStore(seeded, refused);
    SharedPreferences.resetStatic();
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);
    await store.load();
    final controller = ConnectionController(store);
    addTearDown(controller.dispose);
    return (controller, prefs);
  }

  test(
    'profileScopedPreferenceKeys finds every shape and nothing else',
    () async {
      SharedPreferences.setMockInitialValues(_seed());
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);

      expect(store.profileScopedPreferenceKeys('doomed'), {
        'oc.model.doomed',
        'oc.modelExplicit.doomed',
        'oc.agent.doomed',
        'oc.variant.doomed',
        'oc.sessionModels.doomed',
        'oc.sessionDisplayModels.doomed',
        'oc.sessionDisplayModels.doomed.agents',
        'oc.modelLibrary.doomed',
        'oc.location.doomed',
        'oc.providerRuntimeRefresh.v1.doomed.%2Fhome%2Fdev%2Fcode%0Amain',
      });
      // Never the app-wide keys, and never another profile's.
      expect(
        store.profileScopedPreferenceKeys('doomed'),
        isNot(contains('oc.model.keeper')),
      );
      expect(store.profileScopedPreferenceKeys(''), isEmpty);
    },
  );

  test('deleting a profile leaves nothing keyed to it behind', () async {
    final (controller, prefs) = await boot();

    final result = await controller.deleteProfileAndLocalData('doomed');

    // Reported truthfully: the UI copy depends on these numbers.
    expect(result.removedPreferenceKeys, hasLength(10));
    expect(result.removedQueuedPrompts, 2);
    expect(result.removedDrafts, 2); // owned + unattributable
    expect(result.clearedWidgetSnapshot, isTrue);
    expect(result.clearedPinnedShortcuts, isTrue);
    expect(result.clearedAttentionTile, isTrue);

    // Profile metadata (including the cached flavor/version) and the active
    // pointer.
    expect(controller.store.profiles.map((p) => p.id), ['keeper']);
    expect(prefs.getString('oc.profiles'), isNot(contains('doomed')));
    expect(prefs.getString('oc.profiles'), isNot(contains('0.14.2')));
    expect(prefs.getString('oc.activeProfile'), isNull);

    // Keystore password.
    expect(secureDeletes, contains('pw.doomed'));

    // Every profile-scoped preference.
    for (final key in const [
      'oc.model.doomed',
      'oc.modelExplicit.doomed',
      'oc.agent.doomed',
      'oc.variant.doomed',
      'oc.sessionModels.doomed',
      'oc.sessionDisplayModels.doomed',
      'oc.sessionDisplayModels.doomed.agents',
      'oc.modelLibrary.doomed',
      'oc.location.doomed',
      'oc.providerRuntimeRefresh.v1.doomed.%2Fhome%2Fdev%2Fcode%0Amain',
    ]) {
      expect(prefs.get(key), isNull, reason: key);
    }
    expect(controller.store.locationFor('doomed'), isNull);
    expect(controller.store.modelFor('doomed'), (null, null));
    expect(controller.store.agentFor('doomed'), isEmpty);
    expect(controller.store.variantFor('doomed'), isEmpty);
    expect(
      controller.store.providerRuntimeWasRefreshed(
        'doomed',
        directory: '/home/dev/code',
        workspace: 'main',
      ),
      isFalse,
    );

    // Queued prompts, including the embedded attachment payload.
    final queue = prefs.getString('oc.offlineQueue') ?? '';
    expect(queue, isNot(contains('doomed')));
    expect(queue, isNot(contains('secret prompt')));
    expect(queue, isNot(contains('data:image/png')));
    expect(queue, contains('keep me'));

    // Drafts.
    final drafts = prefs.getString('oc.sessionDrafts') ?? '';
    expect(drafts, isNot(contains('half-written prompt')));
    expect(drafts, isNot(contains('orphan draft')));
    expect(drafts, contains('keep this draft'));

    // Widget snapshot.
    expect(prefs.getString('oc.widgetSessions'), isNull);

    // Launcher shortcuts are withdrawn natively and forgotten; the tile's
    // cached count is gone.
    expect(prefs.getString('oc.pinnedShortcuts'), isNull);
    expect(prefs.getString('oc.attentionTile'), isNull);
    expect(shortcutPublishes, isNotEmpty);
    expect(shortcutPublishes.last['sessions'], isEmpty);

    // App-wide settings are untouched.
    expect(prefs.getString('oc.appearance'), 'dark');
    expect(prefs.getString('oc.themePack'), 'gruvbox');
    expect(prefs.getBool('oc.transcript.reasoningExpanded'), isTrue);
    expect(prefs.getBool('oc.keepLiveInBackground'), isTrue);
  });

  test(
    'deletion forgets the shared phone owner and sweeps its recovery budget',
    () async {
      final (controller, prefs) = await boot();
      await prefs.setString('oc.builtinServerOwner', 'doomed');
      await prefs.setString(
        'oc.builtinRecovery.doomed',
        '{"version":1,"attempts":3}',
      );
      await controller.deleteProfileAndLocalData('doomed');
      expect(prefs.getString('oc.builtinServerOwner'), '');
      expect(prefs.getString('oc.builtinRecovery.doomed'), isNull);
    },
  );

  test('nothing deleted comes back after a restart', () async {
    final (controller, prefs) = await boot();
    await controller.deleteProfileAndLocalData('doomed');

    // A restart rebuilds every store from the same persisted preferences.
    final store = ProfileStore(prefs: prefs);
    final profiles = await store.load();
    expect(profiles.map((p) => p.id), ['keeper']);
    expect(store.activeId, isNull);

    final queue = OfflineQueueStore(prefs: prefs).load();
    expect(queue.map((entry) => entry.id), ['q3']);
    expect(queue.every((entry) => entry.profileID == 'keeper'), isTrue);

    final drafts = SessionDraftStore(prefs: prefs).load();
    expect(drafts.values.map((draft) => draft.sessionID), ['ses_c']);

    expect(prefs.getString(WidgetSessionSnapshot.prefsKey), isNull);
    expect(
      store.profileScopedPreferenceKeys('doomed'),
      isEmpty,
      reason: 'a profile-scoped key survived the restart',
    );
  });

  test('deleting the other profile leaves the first one whole', () async {
    final (controller, prefs) = await boot();

    final result = await controller.deleteProfileAndLocalData('keeper');

    expect(result.removedQueuedPrompts, 1);
    // Only `keeper`'s own draft plus the unattributable one.
    expect(result.removedDrafts, 2);
    // The widget snapshot belongs to `doomed`, so it stays — and so do the
    // launcher shortcuts and the tile count.
    expect(result.clearedWidgetSnapshot, isFalse);
    expect(prefs.getString('oc.widgetSessions'), isNotNull);
    expect(result.clearedPinnedShortcuts, isFalse);
    expect(result.clearedAttentionTile, isFalse);
    expect(prefs.getString('oc.pinnedShortcuts'), contains('Refactor billing'));
    expect(prefs.getString('oc.attentionTile'), contains('doomed'));
    expect(shortcutPublishes, isEmpty);

    expect(controller.store.profiles.map((p) => p.id), ['doomed']);
    // `doomed` is still active and still fully configured.
    expect(prefs.getString('oc.activeProfile'), 'doomed');
    expect(controller.store.modelFor('doomed'), ('anthropic', 'claude'));
    expect(controller.store.agentFor('doomed'), 'build');
    expect(controller.store.locationFor('doomed')?.workspace, 'main');
    expect(prefs.getString('oc.offlineQueue'), contains('secret prompt'));
    expect(prefs.getString('oc.sessionDrafts'), contains('half-written'));
  });

  test('the AI Team plugin data of the deleted profile is swept', () async {
    // The same fixture with the plugin on for `doomed` (and its cache,
    // cursor and refresh stamp on disk) and a cache for `keeper`.
    final seed = _seed();
    final profiles = jsonDecode(seed['oc.profiles'] as String) as List;
    (profiles[0] as Map<String, dynamic>)['orchestration'] = {
      'provider': 'gascity',
      'url': 'http://127.0.0.1:8080',
      'city': 'bright-lights',
      'hostMode': 'computer',
      'front': false,
    };
    SharedPreferences.setMockInitialValues({
      ...seed,
      'oc.profiles': jsonEncode(profiles),
      'oc.orchestration.doomed.snapshot': jsonEncode({
        'runs': [
          {'run_id': 'oc-xru', 'status': 'running'},
        ],
      }),
      'oc.orchestration.doomed.cursor': jsonEncode({'seq': 1200}),
      'oc.orchestration.doomed.refreshedAt': '2026-09-10T10:00:00.000Z',
      'oc.orchestration.keeper.snapshot': jsonEncode({'runs': []}),
    });
    final prefs = await SharedPreferences.getInstance();
    final store = ProfileStore(prefs: prefs);
    await store.load();
    expect(store.profiles.first.orchestration?.city, 'bright-lights');
    final controller = ConnectionController(store);
    addTearDown(controller.dispose);

    final result = await controller.deleteProfileAndLocalData('doomed');

    expect(result.failures, isEmpty);
    expect(controller.store.profiles.map((p) => p.id), ['keeper']);
    // Every `oc.orchestration*.doomed` key and the plugin's Keystore entry.
    expect(
      prefs.getKeys().where(
        (key) => key.startsWith('oc.orchestration') && key.contains('doomed'),
      ),
      isEmpty,
    );
    expect(secureDeletes, contains('oc.orchestration.doomed.grant'));
    expect(secureDeletes, contains('pw.doomed'));
    // The other profile's plugin cache is untouched.
    expect(prefs.getString('oc.orchestration.keeper.snapshot'), isNotNull);
    expect(prefs.getString('oc.profiles'), isNot(contains('bright-lights')));
  });

  group('queued prompts awaiting delivery review', () {
    // Entries the flush marked as dispatched but never confirmed removed.
    // They still hold prompt text and attachment data for the server being
    // removed, so the sweep must take them; the other profile's marker must
    // survive untouched so its own review is still possible.
    Map<String, Object> seed() => {
      'oc.profiles': _seed()['oc.profiles']!,
      'oc.activeProfile': 'doomed',
      'oc.offlineQueue': jsonEncode([
        {
          'id': 'doomed-unconfirmed',
          'profileID': 'doomed',
          'sessionID': 'ses_a',
          'text': 'secret prompt',
          'attachments': [
            {
              'mime': 'image/png',
              'filename': 'screenshot.png',
              'url': 'data:image/png;base64,AAAA',
            },
          ],
          'createdAt': 1,
          'dispatchedAt': 1700000000000,
        },
        {
          'id': 'doomed-plain',
          'profileID': 'doomed',
          'sessionID': 'ses_b',
          'text': 'another',
          'createdAt': 2,
        },
        {
          'id': 'keeper-unconfirmed',
          'profileID': 'keeper',
          'sessionID': 'ses_c',
          'text': 'keep me',
          'createdAt': 3,
          'dispatchedAt': 1700000000001,
        },
      ]),
    };

    test('are swept with the profile and counted in the disclosure', () async {
      SharedPreferences.setMockInitialValues(seed());
      final prefs = await SharedPreferences.getInstance();
      final store = ProfileStore(prefs: prefs);
      await store.load();
      final controller = ConnectionController(store);
      addTearDown(controller.dispose);

      // The confirmation copy must own up to unconfirmed sends too.
      expect(controller.queuedPromptCountForProfile('doomed'), 2);

      final result = await controller.deleteProfileAndLocalData('doomed');

      expect(result.removedQueuedPrompts, 2);
      expect(result.complete, isTrue);
      final queue = prefs.getString('oc.offlineQueue') ?? '';
      expect(queue, isNot(contains('doomed')));
      expect(queue, isNot(contains('secret prompt')));
      expect(queue, isNot(contains('data:image/png')));

      final remaining = OfflineQueueStore(prefs: prefs).load().single;
      expect(remaining.id, 'keeper-unconfirmed');
      expect(remaining.dispatchedAt, 1700000000001);
      expect(controller.queuedPromptCountForProfile('keeper'), 1);
    });

    test(
      'stay on the device, marker intact, when the sweep is refused',
      () async {
        SharedPreferences.setMockInitialValues(seed());
        final seeded = await SharedPreferencesStorePlatform.instance.getAll();
        SharedPreferencesStorePlatform.instance = _RefusingStore(seeded, {
          'oc.offlineQueue',
        });
        SharedPreferences.resetStatic();
        final prefs = await SharedPreferences.getInstance();
        final store = ProfileStore(prefs: prefs);
        await store.load();
        final controller = ConnectionController(store);
        addTearDown(controller.dispose);

        final result = await controller.deleteProfileAndLocalData('doomed');

        expect(result.removedProfile, isFalse);
        expect(result.failures, ['2 queued prompts']);
        final raw = (await SharedPreferencesStorePlatform.instance
            .getAll())['flutter.oc.offlineQueue'];
        final held = [
          for (final entry in jsonDecode(raw! as String) as List)
            QueuedPrompt.fromJson(entry)!,
        ];
        expect(held.map((entry) => entry.id), [
          'doomed-unconfirmed',
          'doomed-plain',
          'keeper-unconfirmed',
        ]);
        // Neither refusal nor retry may turn an unconfirmed send back into a
        // draft that the next connection would resend.
        expect(held.first.dispatchedAt, 1700000000000);
        expect(held.last.dispatchedAt, 1700000000001);
        expect(controller.queuedPromptCountForProfile('doomed'), 2);
      },
    );
  });

  test('a widget snapshot with no owner is cleared rather than left', () async {
    SharedPreferences.setMockInitialValues({
      'oc.widgetSessions': jsonEncode({
        'connected': true,
        'sessions': [
          {'id': 'ses_a', 'title': 'Legacy', 'busy': false, 'updatedAt': 1},
        ],
      }),
    });
    final prefs = await SharedPreferences.getInstance();
    final snapshot = WidgetSessionSnapshot(
      prefs: prefs,
      refreshNative: () async {},
      isAndroid: true,
    );

    expect(
      await snapshot.clearForProfile('doomed'),
      WidgetSnapshotClear.cleared,
    );
    expect(prefs.getString(WidgetSessionSnapshot.prefsKey), isNull);
    // Idempotent: a second delete has nothing to report.
    expect(
      await snapshot.clearForProfile('doomed'),
      WidgetSnapshotClear.nothingToClear,
    );
  });

  group('a store that refuses writes', () {
    // SharedPreferences drops a value from its in-memory cache before asking
    // the store and never puts it back when the store says no, so only the
    // durable store answers "is this still on the device?" honestly.
    Future<Object?> onDisk(String key) async =>
        (await SharedPreferencesStorePlatform.instance
            .getAll())['flutter.$key'];

    test(
      'keeps the server when its queued prompts cannot be deleted',
      () async {
        final (controller, _) = await bootRefusing({'oc.offlineQueue'});

        final result = await controller.deleteProfileAndLocalData('doomed');

        // The prompts are still on the device, so the server that owns them
        // stays too — an orphaned queue behind a deleted row is exactly what
        // "remove server" promises will not happen.
        expect(result.complete, isFalse);
        expect(result.removedProfile, isFalse);
        expect(result.failures, ['2 queued prompts']);
        expect(result.removedQueuedPrompts, 0);
        expect(
          result.partialDeletionMessage,
          allOf(
            contains('The server was kept'),
            contains('2 queued prompts'),
            contains('Free up storage'),
          ),
        );

        expect(controller.store.profiles.map((p) => p.id), [
          'doomed',
          'keeper',
        ]);
        expect(await onDisk('oc.profiles'), contains('doomed'));
        expect(await onDisk('oc.activeProfile'), 'doomed');
        expect(secureDeletes, isEmpty, reason: 'the Keystore entry must stay');
        expect(await onDisk('oc.offlineQueue'), contains('secret prompt'));
      },
    );

    test('keeps the server when a draft write is refused', () async {
      final (controller, _) = await bootRefusing({'oc.sessionDrafts'});

      final result = await controller.deleteProfileAndLocalData('doomed');

      expect(result.removedProfile, isFalse);
      expect(result.failures, ['2 unsent drafts']);
      // The queue write ran first and succeeded, so it is reported as done
      // rather than folded into the failure — the retry only has to redo
      // what actually refused.
      expect(result.removedQueuedPrompts, 2);
      expect(await onDisk('oc.sessionDrafts'), contains('half-written'));
      expect(controller.store.profiles.map((p) => p.id), ['doomed', 'keeper']);
      expect(secureDeletes, isEmpty);
    });

    test('keeps the server when a scoped setting will not go', () async {
      final (controller, _) = await bootRefusing({'oc.agent.doomed'});

      final result = await controller.deleteProfileAndLocalData('doomed');

      expect(result.removedProfile, isFalse);
      expect(result.failures, ['1 saved setting']);
      // The nine keys that did go are reported; the refused key is not claimed.
      expect(result.removedPreferenceKeys, hasLength(9));
      expect(result.removedPreferenceKeys, isNot(contains('oc.agent.doomed')));
      expect(await onDisk('oc.agent.doomed'), 'build');
      expect(controller.store.profiles.map((p) => p.id), ['doomed', 'keeper']);
      expect(secureDeletes, isEmpty);
      expect(result.partialDeletionMessage, contains('1 saved setting'));
    });

    test('keeps the server when the widget snapshot will not clear', () async {
      final (controller, _) = await bootRefusing({'oc.widgetSessions'});

      final result = await controller.deleteProfileAndLocalData('doomed');

      expect(result.removedProfile, isFalse);
      expect(result.failures, ['the home-screen widget’s sessions']);
      expect(result.clearedWidgetSnapshot, isFalse);
      expect(await onDisk('oc.widgetSessions'), isNotNull);
      expect(controller.store.profiles.map((p) => p.id), ['doomed', 'keeper']);
      expect(secureDeletes, isEmpty);
    });

    test('keeps the server when the launcher shortcut record or the tile count '
        'will not clear', () async {
      final (controller, _) = await bootRefusing({
        'oc.pinnedShortcuts',
        'oc.attentionTile',
      });

      final result = await controller.deleteProfileAndLocalData('doomed');

      expect(result.removedProfile, isFalse);
      expect(result.failures, [
        'the launcher’s pinned-session shortcuts',
        'the Quick Settings tile’s count',
      ]);
      expect(result.clearedPinnedShortcuts, isFalse);
      expect(result.clearedAttentionTile, isFalse);
      expect(await onDisk('oc.pinnedShortcuts'), isNotNull);
      expect(await onDisk('oc.attentionTile'), isNotNull);
      expect(controller.store.profiles.map((p) => p.id), ['doomed', 'keeper']);
      expect(secureDeletes, isEmpty);
    });

    test('reports every refusal, not just the first', () async {
      final (controller, _) = await bootRefusing({
        'oc.offlineQueue',
        'oc.sessionDrafts',
        'oc.location.doomed',
      });

      final result = await controller.deleteProfileAndLocalData('doomed');

      expect(result.failures, [
        '2 queued prompts',
        '2 unsent drafts',
        '1 saved setting',
      ]);
      expect(result.removedProfile, isFalse);
    });

    test('a clean run still reports a complete deletion', () async {
      final (controller, _) = await bootRefusing(const {});

      final result = await controller.deleteProfileAndLocalData('doomed');

      expect(result.complete, isTrue);
      expect(result.failures, isEmpty);
      expect(result.removedProfile, isTrue);
      expect(result.partialDeletionMessage, isNull);
      expect(secureDeletes, contains('pw.doomed'));
    });
  });

  test('drafts record the profile that owns them', () async {
    final (controller, prefs) = await boot();

    await controller.saveSessionDraft('ses_new', 'typed but unsent');

    final drafts = SessionDraftStore(prefs: prefs).load();
    expect(
      drafts[SessionDraft.keyFor('doomed', 'ses_new')]?.profileID,
      'doomed',
    );

    await controller.deleteProfileAndLocalData('doomed');
    expect(
      prefs.getString('oc.sessionDrafts') ?? '',
      isNot(contains('typed but unsent')),
    );
  });
}

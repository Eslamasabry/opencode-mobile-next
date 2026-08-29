import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/background/widget_snapshot.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/offline_queue.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:opencode_mobile/state/session_drafts.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Everything the app persists for `doomed`, alongside a second profile whose
/// identically shaped data must survive.
Map<String, Object> _seed() => {
  'oc.profiles': jsonEncode([
    {
      'id': 'doomed',
      'name': 'Old workstation',
      'baseUrl': 'https://old.example:4096',
      'username': 'eslam',
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
  'oc.location.doomed': jsonEncode({
    'directory': '/home/eslam/code',
    'workspace': 'main',
  }),
  'oc.providerRuntimeRefresh.v1.doomed.%2Fhome%2Feslam%2Fcode%0Amain': true,
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

  // App-wide keys that a profile deletion must never touch.
  'oc.appearance': 'dark',
  'oc.themePack': 'gruvbox',
  'oc.transcript.reasoningExpanded': true,
  'oc.keepLiveInBackground': true,
};

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late List<String> secureDeletes;

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
        'oc.location.doomed',
        'oc.providerRuntimeRefresh.v1.doomed.%2Fhome%2Feslam%2Fcode%0Amain',
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
    expect(result.removedPreferenceKeys, hasLength(6));
    expect(result.removedQueuedPrompts, 2);
    expect(result.removedDrafts, 2); // owned + unattributable
    expect(result.clearedWidgetSnapshot, isTrue);

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
      'oc.location.doomed',
      'oc.providerRuntimeRefresh.v1.doomed.%2Fhome%2Feslam%2Fcode%0Amain',
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
        directory: '/home/eslam/code',
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

    // App-wide settings are untouched.
    expect(prefs.getString('oc.appearance'), 'dark');
    expect(prefs.getString('oc.themePack'), 'gruvbox');
    expect(prefs.getBool('oc.transcript.reasoningExpanded'), isTrue);
    expect(prefs.getBool('oc.keepLiveInBackground'), isTrue);
  });

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
    expect(drafts.keys, ['ses_c']);

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
    // The widget snapshot belongs to `doomed`, so it stays.
    expect(result.clearedWidgetSnapshot, isFalse);
    expect(prefs.getString('oc.widgetSessions'), isNotNull);

    expect(controller.store.profiles.map((p) => p.id), ['doomed']);
    // `doomed` is still active and still fully configured.
    expect(prefs.getString('oc.activeProfile'), 'doomed');
    expect(controller.store.modelFor('doomed'), ('anthropic', 'claude'));
    expect(controller.store.agentFor('doomed'), 'build');
    expect(controller.store.locationFor('doomed')?.workspace, 'main');
    expect(prefs.getString('oc.offlineQueue'), contains('secret prompt'));
    expect(prefs.getString('oc.sessionDrafts'), contains('half-written'));
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

    expect(await snapshot.clearForProfile('doomed'), isTrue);
    expect(prefs.getString(WidgetSessionSnapshot.prefsKey), isNull);
    // Idempotent: a second delete has nothing to report.
    expect(await snapshot.clearForProfile('doomed'), isFalse);
  });

  test('drafts record the profile that owns them', () async {
    final (controller, prefs) = await boot();

    await controller.saveSessionDraft('ses_new', 'typed but unsent');

    final drafts = SessionDraftStore(prefs: prefs).load();
    expect(drafts['ses_new']?.profileID, 'doomed');

    await controller.deleteProfileAndLocalData('doomed');
    expect(
      prefs.getString('oc.sessionDrafts') ?? '',
      isNot(contains('typed but unsent')),
    );
  });
}

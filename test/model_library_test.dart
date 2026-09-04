import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api/product_repository.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/model_library.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

ModelRef _model(String id, [String provider = 'local']) =>
    ModelRef(providerID: provider, modelID: id);

CatalogSnapshot _catalog() => CatalogSnapshot(
  providers: const [],
  agents: const [],
  models: [
    for (final id in ['a', 'b', 'c', 'disabled'])
      CatalogModel(
        id: id,
        providerID: 'local',
        name: 'Model $id',
        enabled: id != 'disabled',
        status: 'active',
        contextLimit: 32000,
        outputLimit: 4000,
        reasoning: false,
        attachments: false,
        tools: true,
        variants: const [],
      ),
  ],
);

Future<ProfileStore> _store([Map<String, Object> extra = const {}]) async {
  SharedPreferences.setMockInitialValues({
    'oc.profiles': jsonEncode([
      {'id': 'one', 'name': 'One', 'baseUrl': 'https://one.example'},
      {'id': 'two', 'name': 'Two', 'baseUrl': 'https://two.example'},
    ]),
    'oc.activeProfile': 'one',
    ...extra,
  });
  final store = ProfileStore(prefs: await SharedPreferences.getInstance());
  await store.load();
  return store;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          const MethodChannel('plugins.it_nomads.com/flutter_secure_storage'),
          (_) async => null,
        );
  });

  test(
    'recents normalize, deduplicate and retain the eight newest choices',
    () {
      var library = const ModelLibrary();
      for (var i = 0; i < 12; i++) {
        library = library.remember(_model('m$i'));
      }
      library = library.remember(_model('local/m7'));
      expect(library.recent.map((model) => model.modelID), [
        'm7',
        'm11',
        'm10',
        'm9',
        'm8',
        'm6',
        'm5',
        'm4',
      ]);
      expect(() => library.recent.clear(), throwsUnsupportedError);
    },
  );

  test('favorites preserve backend identity and toggle normalized refs', () {
    final library = const ModelLibrary()
        .toggleFavorite(_model('local/a'))
        .toggleFavorite(_model('a', 'other'))
        .toggleFavorite(_model('a'));
    expect(library.favorites.single.wireName, 'other/a');
    expect(library.recent, isEmpty);
  });

  test(
    'corrupt entries are ignored and oversized history is bounded on read',
    () {
      final library = ModelLibrary.fromJson({
        'favorites': [
          null,
          2,
          {'providerID': '', 'modelID': 'a'},
          _model('a').toJson(),
          _model('local/a').toJson(),
        ],
        'recent': [for (var i = 0; i < 30; i++) _model('m$i').toJson()],
      });
      expect(library.favorites.single.wireName, 'local/a');
      expect(library.recent, hasLength(ModelLibrary.recentLimit));
      expect(ModelLibrary.fromJson([]).recent, isEmpty);
    },
  );

  test(
    'persistence is isolated by profile and safely reads malformed JSON',
    () async {
      final store = await _store({'oc.modelLibrary.two': 'broken json'});
      final library = const ModelLibrary()
          .toggleFavorite(_model('a'))
          .remember(_model('b'));
      await store.setModelLibrary('one', library);
      final reopened = ProfileStore(prefs: store.prefs).modelLibraryFor('one');
      expect(reopened.favorites.single.wireName, 'local/a');
      expect(reopened.recent.single.wireName, 'local/b');
      expect(store.modelLibraryFor('two').recent, isEmpty);
      expect(
        store.profileScopedPreferenceKeys('one'),
        contains('oc.modelLibrary.one'),
      );
      await store.setModelLibrary('one', const ModelLibrary());
      expect(store.prefs.containsKey('oc.modelLibrary.one'), isFalse);
    },
  );

  test('pruning removes unavailable models from both lists', () {
    final library = const ModelLibrary()
        .toggleFavorite(_model('a'))
        .toggleFavorite(_model('b'))
        .remember(_model('a'))
        .remember(_model('b'))
        .retainWhere((model) => model.modelID == 'b');
    expect(library.favorites.single.modelID, 'b');
    expect(library.recent.single.modelID, 'b');
  });

  test('cycling skips unavailable models and wraps in both directions', () {
    final library = ModelLibrary(
      recent: [_model('a'), _model('gone'), _model('b'), _model('c')],
    );
    bool available(ModelRef model) => model.modelID != 'gone';
    expect(library.next(_model('a'), available: available)?.modelID, 'b');
    expect(
      library.next(_model('a'), reverse: true, available: available)?.modelID,
      'c',
    );
    expect(library.next(_model('c'), available: available)?.modelID, 'a');
    expect(library.next(null, available: available)?.modelID, 'a');
    expect(library.next(null, available: (_) => false), isNull);
    expect(
      ModelLibrary(
        recent: [_model('a')],
      ).next(_model('a'), available: available),
      isNull,
    );
  });

  test(
    'repeated cycles visit every recent model without changing other chats',
    () async {
      final store = await _store();
      final controller = ConnectionController(store)..catalog = _catalog();
      addTearDown(controller.dispose);
      for (final id in ['a', 'b', 'c']) {
        await controller.selectModelForSession('chat', _model(id));
      }
      await controller.selectModel(_model('a'));
      // A stable MRU ring: a, c, b. Cycling must not reorder it into a toggle.
      final results = <String?>[];
      for (var i = 0; i < 3; i++) {
        results.add((await controller.cycleModelForSession('chat'))?.modelID);
      }
      expect(results, ['b', 'a', 'c']);
      expect(controller.selectedModel?.modelID, 'a');
      expect(controller.modelForSession('other')?.modelID, 'a');
      expect(store.modelLibraryFor('one').recent.map((ref) => ref.modelID), [
        'a',
        'c',
        'b',
      ]);
      expect(store.modelLibraryFor('two').recent, isEmpty);
    },
  );

  test(
    'favoriting never selects a model and unavailable choices are rejected',
    () async {
      final controller = ConnectionController(await _store())
        ..catalog = _catalog();
      addTearDown(controller.dispose);
      await controller.selectModelForSession('chat', _model('a'));
      await controller.toggleModelFavorite(_model('b'));
      expect(controller.modelForSession('chat')?.modelID, 'a');
      await controller.selectModelForSession('chat', _model('disabled'));
      await controller.selectModelForSession('chat', _model('missing'));
      expect(controller.modelForSession('chat')?.modelID, 'a');
      expect(controller.modelLibrary.recent.single.modelID, 'a');
      expect(
        (await controller.cycleModelForSession(
          'chat',
          favoritesOnly: true,
        ))?.modelID,
        'b',
      );
      expect(controller.modelLibrary.recent.first.modelID, 'b');
    },
  );
}

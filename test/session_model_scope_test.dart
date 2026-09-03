// A model picked inside one chat must stay inside that chat.
//
// Regression for: choosing a model in a conversation changed the model shown
// and sent by every other open session, including sessions mid-turn, because
// the choice lived on the app-wide ConnectionController.selectedModel.
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';
import 'package:opencode_mobile/state/connection.dart';
import 'package:opencode_mobile/state/profiles.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<ProfileStore> _store([Map<String, Object> seed = const {}]) async {
  SharedPreferences.setMockInitialValues(seed);
  return ProfileStore(prefs: await SharedPreferences.getInstance());
}

ModelRef _ref(String provider, String id) =>
    ModelRef(providerID: provider, modelID: id);

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test(
    'per-session choice does not move the default or other sessions',
    () async {
      final controller = ConnectionController(await _store());
      addTearDown(controller.dispose);
      controller.selectedModel = _ref('opencode', 'default');
      controller.selectedVariant = 'low';
      var notified = 0;
      controller.addListener(() => notified++);

      await controller.selectModelForSession(
        'ses_a',
        _ref('anthropic', 'opus'),
      );

      expect(controller.modelForSession('ses_a')?.wireName, 'anthropic/opus');
      expect(controller.variantForSession('ses_a'), '');
      expect(controller.selectedModel?.wireName, 'opencode/default');
      expect(controller.modelForSession('ses_b')?.wireName, 'opencode/default');
      expect(controller.variantForSession('ses_b'), 'low');
      expect(notified, 1);
    },
  );

  test(
    'changing the default later does not override a session choice',
    () async {
      final controller = ConnectionController(await _store());
      addTearDown(controller.dispose);
      await controller.selectModelForSession(
        'ses_a',
        _ref('anthropic', 'opus'),
      );

      await controller.selectModel(_ref('openai', 'gpt'));

      expect(controller.selectedModel?.wireName, 'openai/gpt');
      expect(controller.modelForSession('ses_a')?.wireName, 'anthropic/opus');
      expect(controller.modelForSession('ses_new')?.wireName, 'openai/gpt');
    },
  );

  test('a variant the catalog does not offer is rejected', () async {
    final controller = ConnectionController(await _store());
    addTearDown(controller.dispose);
    controller.catalog = const CatalogSnapshot(
      providers: [],
      agents: [],
      models: [
        CatalogModel(
          id: 'opus',
          providerID: 'anthropic',
          name: 'Opus',
          enabled: true,
          status: 'active',
          contextLimit: 200000,
          outputLimit: 8192,
          reasoning: true,
          attachments: false,
          tools: true,
          variants: [CatalogVariant(id: 'high')],
        ),
      ],
    );

    await controller.selectModelForSession(
      'ses_a',
      _ref('anthropic', 'opus'),
      variant: 'nope',
    );
    expect(controller.sessionModels, isEmpty);

    await controller.selectModelForSession(
      'ses_a',
      _ref('anthropic', 'opus'),
      variant: 'high',
    );
    expect(controller.variantForSession('ses_a'), 'high');
  });

  test('store round-trips and prunes per-session choices', () async {
    final store = await _store();

    await store.setSessionModels('p1', {
      'ses_a': SessionModelChoice(model: _ref('anthropic', 'opus')),
      'ses_b': SessionModelChoice(
        model: _ref('openai', 'openai/gpt'),
        variant: 'high',
      ),
    });

    final loaded = store.sessionModelsFor('p1');
    expect(loaded.keys, ['ses_a', 'ses_b']);
    expect(loaded['ses_a']!.model.wireName, 'anthropic/opus');
    expect(loaded['ses_a']!.variant, '');
    // Composite ids are normalised on the way back in, like the default.
    expect(loaded['ses_b']!.model.wireName, 'openai/gpt');
    expect(loaded['ses_b']!.variant, 'high');
    expect(store.sessionModelsFor('p2'), isEmpty);

    // Bounded: only the most recent 200 survive.
    await store.setSessionModels('p1', {
      for (var i = 0; i < 250; i++)
        'ses_$i': SessionModelChoice(model: _ref('p', 'm$i')),
    });
    final pruned = store.sessionModelsFor('p1');
    expect(pruned, hasLength(200));
    expect(pruned.containsKey('ses_0'), isFalse);
    expect(pruned.containsKey('ses_249'), isTrue);

    await store.setSessionModels('p1', {});
    expect(store.prefs.getString('oc.sessionModels.p1'), isNull);
  });

  test('malformed stored choices are dropped, not thrown', () async {
    final store = await _store({
      'oc.sessionModels.p1': jsonEncode({
        'ok': 'a|b|',
        'no-model': 'a',
        'empty-provider': '|b',
        'wrong-type': 3,
      }),
      'oc.sessionModels.p2': 'not json',
    });
    expect(store.sessionModelsFor('p1').keys, ['ok']);
    expect(store.sessionModelsFor('p2'), isEmpty);
  });
}

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/domain/model_recommendations.dart';
import 'package:opencode_mobile/domain/models.dart';
import 'package:opencode_mobile/domain/server_gateway.dart';

CatalogModel _m(
  String id, {
  String provider = 'p',
  ModelCost? cost,
  bool enabled = true,
}) => CatalogModel(
  id: id,
  providerID: provider,
  name: id.toUpperCase(),
  enabled: enabled,
  status: 'active',
  contextLimit: 0,
  outputLimit: 0,
  reasoning: false,
  attachments: false,
  tools: true,
  variants: const [],
  cost: cost,
);

ModelCost _c(double input, double output) =>
    ModelCost(inputPerMillion: input, outputPerMillion: output);

ModelRef _r(String id, {String provider = 'p'}) =>
    ModelRef(providerID: provider, modelID: id);

List<(ModelReason, String)> _rows(List<ModelRecommendation> rows) => [
  for (final row in rows) (row.reason, row.model.id),
];

void main() {
  group('recommendModels', () {
    test('the server default, a recent choice and the cheapest, in order', () {
      final rows = recommendModels(
        [
          _m('sonnet', cost: _c(3, 15)),
          _m('opus', cost: _c(5, 25)),
          _m('mini', cost: _c(.25, 2)),
          _m('sol', cost: _c(2.5, 15)),
        ],
        current: _r('sonnet'),
        defaults: [_r('sonnet')],
        recent: [_r('sonnet'), _r('sol')],
      );
      expect(_rows(rows), [
        (ModelReason.serverDefault, 'sonnet'),
        (ModelReason.recent, 'sol'),
        (ModelReason.cheapest, 'mini'),
      ]);
    });

    test('no speed is ever claimed: there is no fastest reason', () {
      expect(
        ModelReason.values.map((reason) => reason.name),
        isNot(contains(contains('fast'))),
      );
    });

    test('a model without a price is never the cheapest', () {
      final rows = recommendModels(
        [
          _m('priced', cost: _c(1, 5)),
          _m('unknown'),
          _m('other', cost: _c(3, 15)),
        ],
        defaults: [_r('other')],
      );
      expect(_rows(rows), [
        (ModelReason.serverDefault, 'other'),
        (ModelReason.cheapest, 'priced'),
      ]);
    });

    test('equal prices name no cheapest (all free); rows say Free', () {
      final rows = recommendModels(
        [
          _m('a', cost: _c(0, 0)),
          _m('b', cost: _c(0, 0)),
          _m('c', cost: _c(0, 0)),
        ],
        current: _r('a'),
        defaults: [_r('a')],
        recent: [_r('a'), _r('b')],
      );
      expect(_rows(rows), [
        (ModelReason.serverDefault, 'a'),
        (ModelReason.recent, 'b'),
      ]);
      expect(rows.every((row) => row.free), isTrue);
    });

    test('no prices at all (a phone agent): no price row', () {
      final rows = recommendModels(
        [_m('opus'), _m('sonnet'), _m('haiku')],
        current: _r('opus'),
        defaults: [_r('sonnet')],
        recent: [_r('opus')],
      );
      expect(_rows(rows), [
        (ModelReason.inUse, 'opus'),
        (ModelReason.serverDefault, 'sonnet'),
      ]);
    });

    test('one model is one row: the default that is also the cheapest', () {
      final rows = recommendModels(
        [_m('free', cost: _c(0, 0)), _m('paid', cost: _c(3, 15))],
        defaults: [_r('free')],
      );
      expect(_rows(rows), [(ModelReason.serverDefault, 'free')]);
      expect(rows.single.alsoCheapest, isTrue);
      expect(rows.single.free, isTrue);
    });

    test('a cheapest tie prefers the model in use, then a recent one', () {
      final models = [
        _m('x', cost: _c(0, 0)),
        _m('y', cost: _c(0, 0)),
        _m('paid', cost: _c(3, 15)),
      ];
      expect(recommendModels(models, current: _r('y')).last.model.id, 'y');
      expect(
        recommendModels(
          models,
          current: _r('paid'),
          recent: [_r('y')],
        ).firstWhere((row) => row.reason == ModelReason.cheapest).model.id,
        'y',
      );
    });

    test('the model in use always shows, and the recent row gives way', () {
      final rows = recommendModels(
        [
          _m('d', cost: _c(3, 15)),
          _m('cheap', cost: _c(1, 1)),
          _m('mine', cost: _c(5, 25)),
          _m('old', cost: _c(4, 20)),
        ],
        current: _r('mine'),
        defaults: [_r('d')],
        recent: [_r('mine'), _r('old')],
      );
      expect(_rows(rows), [
        (ModelReason.inUse, 'mine'),
        (ModelReason.serverDefault, 'd'),
        (ModelReason.cheapest, 'cheap'),
      ]);
    });

    test('unavailable models and unknown refs are skipped, never padded', () {
      final rows = recommendModels(
        [_m('off', enabled: false), _m('on')],
        current: _r('gone'),
        defaults: [_r('off'), _r('nowhere')],
        recent: [_r('off')],
      );
      expect(rows, isEmpty);
    });

    test('the first default the list has wins (most authoritative first)', () {
      final rows = recommendModels(
        [_m('a', provider: 'codex'), _m('b', provider: 'codex')],
        defaults: [
          _r('x', provider: 'claude'),
          _r('b', provider: 'codex'),
        ],
      );
      expect(_rows(rows), [(ModelReason.serverDefault, 'b')]);
    });
  });
}

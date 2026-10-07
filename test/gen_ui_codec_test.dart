import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/domain/genui/gen_ui.dart';
import 'package:opencode_mobile/domain/genui/gen_ui_validation_js.dart';
import 'package:opencode_mobile/domain/server_gateway.dart'
    show ProductException;

const _scope = GenUiScope(
  profileID: 'profile',
  sourceId: 'source',
  directory: '/project',
);
Map<String, dynamic> _input({
  String title = 'Example',
  Object? ask = const {'kind': 'confirm'},
}) => {
  'v': 1,
  'id': 'example',
  'title': title,
  'body': <Object>[],
  'ask': ?ask,
};
Part _part(
  Map<String, dynamic> input, {
  String call = 'call',
  String message = 'assistant',
  String name = 'oc-ui_show',
  String status = 'completed',
  bool executed = true,
  bool synthetic = false,
}) => Part(
  type: 'tool',
  callID: call,
  messageID: message,
  toolName: name,
  synthetic: synthetic,
  toolState: ToolState(status: status, input: input, executed: executed),
);
GenUiParse? _parse(Map<String, dynamic> input) => genUiFromPart(
  _part(input),
  scope: _scope,
  sessionID: 'session',
  messageID: 'assistant',
);
GenUiCard _card([Map<String, dynamic>? input]) =>
    (_parse(input ?? _input()) as GenUiParsed).card;
MessageWithParts _assistant({
  String id = 'assistant',
  String call = 'call',
  Map<String, dynamic>? input,
}) => MessageWithParts(
  info: MessageInfo(id: id, sessionID: 'session', role: 'assistant'),
  parts: [_part(input ?? _input(), call: call, message: id)],
);
MessageWithParts _user(
  String text, {
  bool synthetic = false,
  String role = 'user',
  String session = 'session',
}) => MessageWithParts(
  info: MessageInfo(id: 'user', sessionID: session, role: role),
  parts: [Part(type: 'text', text: text, synthetic: synthetic)],
);

void main() {
  test(
    'shared corpus: Dart and standalone JS agree on acceptance and normalization',
    () async {
      final cases =
          jsonDecode(
                await File(
                  'test/fixtures/genui/validation.json',
                ).readAsString(),
              )
              as List;
      final directionalCodes = [
        0x061c,
        0x200e,
        0x200f,
        for (var code = 0x202a; code <= 0x202e; code++) code,
        for (var code = 0x2066; code <= 0x2069; code++) code,
      ];
      cases.add({
        'name': 'all-directional-controls-stripped',
        'input': _input(
          title: 'Before${String.fromCharCodes(directionalCodes)}After',
        ),
        'expectedTitle': 'BeforeAfter',
      });
      for (final code in directionalCodes) {
        cases.add({
          'name': 'directional-link-rejected-${code.toRadixString(16)}',
          'input': {
            ..._input(),
            'body': [
              {
                'type': 'link',
                'label': 'Link',
                'url': 'https://example.com/a${String.fromCharCode(code)}b',
              },
            ],
          },
          'error': 'badValue',
        });
      }
      final process = await Process.start('node', [
        '-e',
        '''
$genUiValidationJavascript
let input=''; process.stdin.setEncoding('utf8'); process.stdin.on('data', x=>input+=x);
process.stdin.on('end',()=>{const result=JSON.parse(input).map(c=>{try{return {value:normalizeGenUiCard(c.input)}}catch(e){return {error:e.message}}});process.stdout.write(JSON.stringify(result));});
''',
      ]);
      final stdoutFuture = process.stdout.transform(utf8.decoder).join();
      final stderrFuture = process.stderr.drain<void>();
      process.stdin.write(jsonEncode(cases));
      await process.stdin.close();
      final output = await stdoutFuture.timeout(
        const Duration(seconds: 15),
        onTimeout: () {
          process.kill();
          throw StateError('Validator timed out');
        },
      );
      await stderrFuture;
      expect(await process.exitCode, 0);
      final js = jsonDecode(output) as List;
      expect(js.length, cases.length);
      for (var i = 0; i < cases.length; i++) {
        final c = cases[i] as Map;
        final label = c['name'] as String;
        final parsed = _parse(Map<String, dynamic>.from(c['input'] as Map));
        if (c['error'] != null) {
          expect(parsed, isA<GenUiUnreadable>(), reason: label);
          expect(
            (parsed as GenUiUnreadable).reason.name,
            c['error'],
            reason: label,
          );
          expect(js[i]['error'], c['error'], reason: label);
        } else {
          expect(parsed, isA<GenUiParsed>(), reason: label);
          expect(js[i]['error'], isNull, reason: label);
          if (c['expectedTitle'] != null) {
            expect((parsed as GenUiParsed).card.title, c['expectedTitle']);
            expect(js[i]['value']['title'], c['expectedTitle']);
          }
          expect(
            _wire((parsed as GenUiParsed).card),
            js[i]['value'],
            reason: label,
          );
          expect(
            _card(Map<String, dynamic>.from(js[i]['value'] as Map)).revision,
            parsed.card.revision,
            reason: label,
          );
        }
      }
    },
  );

  test(
    'rejects cyclic, non-JSON, nonfinite and unpaired surrogate data safely',
    () {
      final cyclic = _input();
      cyclic['body'] = [cyclic];
      for (final input in [
        cyclic,
        {..._input(), 'title': Object()},
        {..._input(), 'title': '\ud800'},
        {
          ..._input(),
          'body': [
            {'type': 'progress', 'label': 'X', 'value': double.nan},
          ],
        },
        {
          ..._input(),
          'body': [
            {'type': 'progress', 'label': 'X', 'value': double.infinity},
          ],
        },
      ]) {
        expect(_parse(input), isA<GenUiUnreadable>());
      }
    },
  );
  test(
    'input preview, missing identity, synthetic and nonexecuted parts are not admitted',
    () {
      for (final part in [
        _part(_input(), name: 'other_oc-ui_show'),
        _part(_input(), name: 'show'),
        _part(_input(), status: 'pending'),
        _part(_input(), status: 'running'),
        _part(_input(), status: 'error'),
        _part(_input(), executed: false),
        _part(_input(), synthetic: true),
        _part(_input(), call: ''),
        _part(_input(), message: 'other'),
        Part(
          type: 'tool',
          callID: 'call',
          toolName: 'oc-ui_show',
          toolState: ToolState(
            status: 'completed',
            inputJson: jsonEncode(_input()),
          ),
        ),
      ]) {
        expect(
          genUiFromPart(
            part,
            scope: _scope,
            sessionID: 'session',
            messageID: 'assistant',
          ),
          isNull,
        );
      }
      expect(
        genUiFromPart(
          _part(_input(), name: 'mcp__oc-ui__show'),
          scope: _scope,
          sessionID: 'session',
          messageID: 'assistant',
        ),
        isA<GenUiParsed>(),
      );
    },
  );
  test(
    'revision is normalized and independent of wire key order; scope and call remain separate',
    () {
      final card = _card();
      expect(
        _card({
          'body': [],
          'title': ' Example ',
          'id': 'example',
          'v': 1,
          'ask': {'tone': 'normal', 'kind': 'confirm'},
        }).revision,
        card.revision,
      );
      expect(_card(_input(title: 'Changed')).revision, isNot(card.revision));
      final other =
          (genUiFromPart(
                    _part(_input(), call: 'other'),
                    scope: const GenUiScope(
                      profileID: 'other',
                      sourceId: 'source',
                      directory: '/project',
                    ),
                    sessionID: 'session',
                    messageID: 'assistant',
                  )
                  as GenUiParsed)
              .card;
      expect(other.revision, card.revision);
      expect(other.identity, isNot(card.identity));
    },
  );
  test(
    'state requires matching current call and revision in a complete authoritative tail',
    () {
      final card = _card();
      expect(
        genUiStateFor(card, [_assistant()], tailComplete: true),
        GenUiCardState.waiting,
      );
      expect(
        genUiStateFor(card, [_assistant()], tailComplete: false),
        GenUiCardState.unknown,
      );
      expect(
        genUiStateFor(card, [], tailComplete: true),
        GenUiCardState.unknown,
      );
      expect(
        genUiStateFor(card, [
          _assistant(input: _input(title: 'Changed')),
        ], tailComplete: true),
        GenUiCardState.unknown,
      );
      expect(
        genUiStateFor(card, [_assistant(call: 'other')], tailComplete: true),
        GenUiCardState.unknown,
      );
    },
  );
  test('only newest eligible ask wins; reports do not supersede asks', () {
    final card = _card();
    expect(
      genUiStateFor(card, [
        _assistant(),
        _assistant(id: 'second', call: 'second'),
      ], tailComplete: true),
      GenUiCardState.passedOver,
    );
    expect(
      genUiStateFor(card, [
        _assistant(),
        _assistant(id: 'report', call: 'report', input: _input(ask: null)),
      ], tailComplete: true),
      GenUiCardState.waiting,
    );
    final report = _card(_input(ask: null));
    expect(
      genUiStateFor(report, [
        _assistant(input: _input(ask: null)),
      ], tailComplete: true),
      GenUiCardState.report,
    );
  });
  test(
    'only first later authoritative user can settle; synthetic and assistant receipts do not',
    () {
      final card = _card();
      final text = genUiAnswerText(card, const GenUiConfirmAnswer(true));
      expect(
        genUiStateFor(card, [_assistant(), _user(text)], tailComplete: true),
        GenUiCardState.answered,
      );
      expect(
        genUiStateFor(card, [
          _assistant(),
          _user('Typed answer'),
          _user(text),
        ], tailComplete: true),
        GenUiCardState.passedOver,
      );
      expect(
        genUiStateFor(card, [
          _assistant(),
          _user(text, synthetic: true),
        ], tailComplete: true),
        GenUiCardState.waiting,
      );
      expect(
        genUiStateFor(card, [
          _assistant(),
          _user(text, role: 'assistant'),
        ], tailComplete: true),
        GenUiCardState.waiting,
      );
      expect(
        genUiStateFor(card, [
          _assistant(),
          _user(text, session: 'other'),
        ], tailComplete: true),
        GenUiCardState.unknown,
      );
      expect(
        genUiStateFor(card, [
          _assistant(),
          _assistant(id: 'second', call: 'second'),
          _user(text),
        ], tailComplete: true),
        GenUiCardState.passedOver,
      );
    },
  );
  test(
    'answer encoder and decoder preserve typed values and validate exact envelope',
    () {
      final card = _card();
      final text = genUiAnswerText(card, const GenUiConfirmAnswer(false));
      final answer = genUiAnswerIn(_user(text), card);
      expect(answer?.value, {'confirm': false});
      expect(answer?.summary, 'Declined');
      expect(genUiAnswerEnvelope(_user(text)), (
        cardId: 'example',
        summary: 'Declined',
      ));
      for (final invalid in [
        text.replaceFirst('"call"', '"other"'),
        text.replaceFirst('"example"', '"other"'),
        '$text\nextra',
        text.replaceFirst('"confirm":false', '"confirm":"false"'),
        text.replaceFirst('"v":1', '"v":2'),
        text.replaceFirst('"value":', '"extra":true,"value":'),
      ]) {
        expect(genUiAnswerIn(_user(invalid), card), isNull);
      }
      expect(genUiAnswerIn(_user(text, synthetic: true), card), isNull);
      expect(genUiAnswerIn(_user(text, role: 'assistant'), card), isNull);
      expect(genUiAnswerIn(_user(text, session: 'other'), card), isNull);
      expect(
        () => genUiAnswerText(card, const GenUiPhotoAnswer(1)),
        throwsA(isA<ProductException>()),
      );
    },
  );
  test(
    'choice answers require known distinct IDs, cardinality and bounded clean summaries',
    () {
      final card = _card(
        _input(
          ask: {
            'kind': 'choice',
            'options': [
              {'id': 'a', 'label': 'Alpha'},
              {'id': 'b', 'label': 'Beta'},
            ],
          },
        ),
      );
      expect(
        genUiAnswerIn(
          _user(genUiAnswerText(card, GenUiChoiceAnswer(['b']))),
          card,
        )?.value,
        {
          'choice': ['b'],
        },
      );
      for (final ids in [
        <String>[],
        ['unknown'],
        ['a', 'b'],
        ['a', 'a'],
      ]) {
        expect(
          () => genUiAnswerText(card, GenUiChoiceAnswer(ids)),
          throwsA(isA<ProductException>()),
        );
      }
    },
  );
  test(
    'form answers enforce required false, real dates, ranges, and absence rather than null',
    () {
      final card = _card(
        _input(
          ask: {
            'kind': 'form',
            'fields': [
              {
                'id': 'agree',
                'label': 'Agree',
                'type': 'toggle',
                'required': true,
              },
              {'id': 'name', 'label': 'Name', 'type': 'text', 'required': true},
              {
                'id': 'number',
                'label': 'Number',
                'type': 'number',
                'min': 0,
                'max': 5,
              },
              {'id': 'date', 'label': 'Date', 'type': 'date'},
            ],
          },
        ),
      );
      final valid = {
        'agree': false,
        'name': 'Ada',
        'number': 5,
        'date': '2024-02-29',
      };
      expect(
        genUiAnswerIn(
          _user(genUiAnswerText(card, GenUiFormAnswer(valid))),
          card,
        )?.value,
        {'form': valid},
      );
      for (final invalid in [
        <String, Object>{'name': 'Ada'},
        <String, Object>{'agree': false, 'name': ' '},
        {...valid, 'number': 6},
        {...valid, 'date': '2023-02-29'},
        {...valid, 'extra': true},
        {...valid, 'name': <String>[]},
        {...valid, 'name': '\ud800'},
      ]) {
        expect(
          () => genUiAnswerText(card, GenUiFormAnswer(invalid)),
          throwsA(isA<ProductException>()),
        );
      }
      expect(
        () => genUiAnswerText(
          card,
          GenUiFormAnswer({'agree': false, 'name': 'Ada'}),
        ),
        returnsNormally,
      );
    },
  );
  test(
    'photo receipt records submitted count without asserting retained attachments',
    () {
      final card = _card(
        _input(ask: {'kind': 'photo', 'purpose': 'Show result', 'max': 2}),
      );
      final text = genUiAnswerText(card, const GenUiPhotoAnswer(2));
      expect(genUiAnswerIn(_user(text), card)?.value, {'photo': 2});
      expect(genUiAnswerEnvelope(_user(text))?.summary, 'Submitted 2 photos');
      for (final n in [0, 3]) {
        expect(
          () => genUiAnswerText(card, GenUiPhotoAnswer(n)),
          throwsA(isA<ProductException>()),
        );
      }
    },
  );
}

Map<String, dynamic> _wire(GenUiCard c) => {
  'v': 1,
  'id': c.id,
  'title': c.title,
  'body': c.body.map(_nodeWire).toList(),
  if (c.ask != null) 'ask': _askWire(c.ask!),
};
Map<String, dynamic> _optionWire(GenUiOption o) => {
  'id': o.id,
  'label': o.label,
  if (o.detail != null) 'detail': o.detail,
};
Map<String, dynamic> _askWire(GenUiAsk ask) => switch (ask) {
  GenUiChoiceAsk(:final options, :final multi) => {
    'kind': 'choice',
    'options': options.map(_optionWire).toList(),
    'multi': multi,
  },
  GenUiFormAsk(:final fields, :final submitLabel) => {
    'kind': 'form',
    'fields': fields
        .map(
          (f) => {
            'id': f.id,
            'label': f.label,
            'type': f.type.name,
            'required': f.required,
            if (f.placeholder != null) 'placeholder': f.placeholder,
            if (f.defaultValue != null) 'default': f.defaultValue,
            if (f.type == GenUiFieldType.select)
              'options': f.options.map(_optionWire).toList(),
            if (f.min != null) 'min': f.min,
            if (f.max != null) 'max': f.max,
          },
        )
        .toList(),
    'submitLabel': ?submitLabel,
  },
  GenUiConfirmAsk(:final confirmLabel, :final cancelLabel, :final tone) => {
    'kind': 'confirm',
    'confirmLabel': ?confirmLabel,
    'cancelLabel': ?cancelLabel,
    'tone': tone.name,
  },
  GenUiPhotoAsk(:final purpose, :final max) => {
    'kind': 'photo',
    'purpose': purpose,
    'max': max,
  },
};
Map<String, dynamic> _nodeWire(GenUiNode n) => switch (n) {
  GenUiText(:final text) => {'type': 'text', 'text': text},
  GenUiKeyValue(:final rows) => {
    'type': 'keyValue',
    'rows': rows.map((r) => {'key': r.key, 'value': r.value}).toList(),
  },
  GenUiList(:final items, :final style) => {
    'type': 'list',
    'items': items.map((i) => {'text': i.text, 'done': i.done}).toList(),
    'style': style.name,
  },
  GenUiTable(:final columns, :final rows) => {
    'type': 'table',
    'columns': columns,
    'rows': rows,
  },
  GenUiChart(:final kind, :final unit, :final labels, :final series) => {
    'type': 'chart',
    'kind': kind.name,
    'unit': ?unit,
    'labels': labels,
    'series': series.map((s) => {'name': s.name, 'values': s.values}).toList(),
  },
  GenUiCode(:final language, :final text) => {
    'type': 'code',
    'language': ?language,
    'text': text,
  },
  GenUiDiffStat(:final files) => {
    'type': 'diffStat',
    'files': files
        .map((f) => {'path': f.path, 'added': f.added, 'removed': f.removed})
        .toList(),
  },
  GenUiProgress(:final label, :final value) => {
    'type': 'progress',
    'label': label,
    'value': value,
  },
  GenUiCallout(:final tone, :final text) => {
    'type': 'callout',
    'tone': tone.name,
    'text': text,
  },
  GenUiLink(:final label, :final url) => {
    'type': 'link',
    'label': label,
    'url': url,
  },
};

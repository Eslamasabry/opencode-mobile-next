import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api2/models.dart';
import 'package:opencode_mobile/api2/events.dart';

import '../tool/qa/fq3d/proof.dart';

const _wanted = Api2ModelRef(id: 'vision-mode', providerID: 'test');

Api2AssistantMessage _assistant({
  String id = 'msg_fresh',
  Api2ModelRef? model = _wanted,
  int? completed = 2,
  String? finish = 'stop',
  String? rawFinish,
  Api2StructuredError? error,
}) => Api2AssistantMessage(
  id: id,
  time: Api2MessageTime(created: 1, completed: completed),
  model: model,
  finish: finish,
  rawFinish: rawFinish,
  error: error,
  content: const [Api2TextContent(text: 'fixture answer')],
);

Api2UserMessage _user(List<Api2FileAttachment> files) => Api2UserMessage(
  id: 'msg_owned_user',
  time: Api2MessageTime(created: 1),
  text: 'Inspect the attached image.',
  files: files,
);

void main() {
  test(
    'owned execution permits omitted location but rejects foreign scope',
    () {
      Api2EventEnvelope event(String session, String? directory) =>
          Api2EventEnvelope.fromJson({
            'type': 'session.execution.succeeded',
            'data': {'sessionID': session},
            if (directory != null) 'location': {'directory': directory},
          });
      for (final directory in [null, '/owned']) {
        expect(
          ownedEventScope(
            event('ses_owned', directory),
            sessionID: 'ses_owned',
            directory: '/owned',
          ),
          isTrue,
        );
      }
      expect(
        ownedEventScope(
          event('ses_foreign', null),
          sessionID: 'ses_owned',
          directory: '/owned',
        ),
        isFalse,
      );
      expect(
        ownedEventScope(
          event('ses_owned', '/foreign'),
          sessionID: 'ses_owned',
          directory: '/owned',
        ),
        isFalse,
      );
      expect(
        ownedEventScope(event('', null), sessionID: '', directory: '/owned'),
        isFalse,
      );
    },
  );
  test('generated password requires the exact complete server line', () {
    final token = List.filled(43, 'A').join();
    expect(generatedServerPassword('server password $token'), token);
    final base64url = base64UrlEncode(
      List.generate(32, (index) => 255 - index),
    ).replaceAll('=', '');
    expect(base64url, hasLength(43));
    expect(generatedServerPassword('server password $base64url'), base64url);
    for (final line in [
      token,
      'password $token',
      'server password ${token.substring(1)}',
      'server password ${token}A',
      'server password $token=',
      'server password /${token.substring(1)}',
      'server password +${token.substring(1)}',
      'prefix server password $token',
      'server password $token suffix',
      ' server password $token',
      'server password $token\n',
      'server password $token\r',
      'server password $token\nserver listening on loopback',
    ]) {
      expect(generatedServerPassword(line), isNull);
    }
  });

  test('catalog mode identity is independent of the shared API model ID', () {
    final base = Api2ModelInfo.fromJson({
      'id': 'base',
      'modelID': 'provider-api-model',
      'providerID': 'test',
      'enabled': true,
    })!;
    final mode = Api2ModelInfo.fromJson({
      'id': 'base-fast',
      'modelID': 'provider-api-model',
      'providerID': 'test',
      'enabled': true,
    })!;
    expect(catalogRef(base).id, 'base');
    expect(catalogRef(mode).id, 'base-fast');
    expect(sameModel(catalogRef(base), catalogRef(mode)), isFalse);
    final beta = Api2ModelInfo.fromJson({
      'id': 'test/base-fast',
      'providerID': 'test',
    })!;
    expect(catalogRef(beta).id, 'base-fast');
    expect(catalogRef(beta).providerID, 'test');
  });

  test('model comparison uses values and normalizes only default variants', () {
    expect(
      sameModel(
        const Api2ModelRef(id: 'vision-mode', providerID: 'test'),
        _wanted,
      ),
      isTrue,
    );
    for (final variant in [null, '', 'default']) {
      expect(
        sameModel(
          Api2ModelRef(
            id: _wanted.id,
            providerID: _wanted.providerID,
            variant: variant,
          ),
          _wanted,
        ),
        isTrue,
      );
    }
    for (final actual in [
      null,
      const Api2ModelRef(id: 'wrong', providerID: 'test'),
      const Api2ModelRef(id: 'vision-mode', providerID: 'foreign'),
      const Api2ModelRef(
        id: 'vision-mode',
        providerID: 'test',
        variant: 'high',
      ),
    ]) {
      expect(sameModel(actual, _wanted), isFalse);
    }
    expect(
      sameModel(
        const Api2ModelRef(id: '', providerID: ''),
        const Api2ModelRef(id: '', providerID: ''),
      ),
      isFalse,
    );
    expect(
      sameModel(
        const Api2ModelRef(
          id: 'vision-mode',
          providerID: 'test',
          variant: 'high',
        ),
        const Api2ModelRef(
          id: 'vision-mode',
          providerID: 'test',
          variant: 'high',
        ),
      ),
      isTrue,
    );
  });

  test(
    'successful inference requires a fresh completed retained assistant of the exact model',
    () {
      expect(
        successfulAssistant(
          _assistant(),
          _wanted,
          previousIDs: {'msg_old'},
          executionSucceeded: true,
        ),
        isTrue,
      );
      for (final message in [
        _assistant(id: 'msg_old'),
        _assistant(id: ''),
        _assistant(completed: null),
        _assistant(model: null),
        _assistant(
          model: const Api2ModelRef(id: 'other', providerID: 'test'),
        ),
        _assistant(
          model: const Api2ModelRef(id: 'vision-mode', providerID: 'foreign'),
        ),
        _assistant(
          model: const Api2ModelRef(
            id: 'vision-mode',
            providerID: 'test',
            variant: 'high',
          ),
        ),
        _assistant(error: Api2StructuredError(type: 'provider.auth')),
      ]) {
        expect(
          successfulAssistant(
            message,
            _wanted,
            previousIDs: {'msg_old'},
            executionSucceeded: true,
          ),
          isFalse,
        );
      }
      expect(
        successfulAssistant(
          _assistant(),
          _wanted,
          previousIDs: {},
          executionSucceeded: false,
        ),
        isFalse,
      );
    },
  );

  test(
    'catalog admission or an execution-success event cannot certify an unfinished answer',
    () {
      final admitted = Api2ModelInfo.fromJson({
        'id': _wanted.id,
        'providerID': _wanted.providerID,
        'enabled': true,
      })!;
      expect(
        successfulAssistant(
          _assistant(completed: null, finish: null),
          catalogRef(admitted),
          previousIDs: {},
          executionSucceeded: true,
        ),
        isFalse,
      );
    },
  );

  test(
    'only normalized stop certifies final success and raw finish cannot override it',
    () {
      for (final finish in [
        null,
        'length',
        'tool-calls',
        'content-filter',
        'error',
        'unknown',
        'end-turn',
        'end_turn',
      ]) {
        expect(
          successfulAssistant(
            _assistant(finish: finish, rawFinish: 'stop'),
            _wanted,
            previousIDs: {},
            executionSucceeded: true,
          ),
          isFalse,
        );
      }
      expect(
        successfulAssistant(
          _assistant(rawFinish: 'end_turn'),
          _wanted,
          previousIDs: {},
          executionSucceeded: true,
        ),
        isTrue,
      );
    },
  );

  test('retained PNG proves exactly one file and exact stored bytes', () {
    final bytes = base64Decode(
      'iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAAK0lEQVR4nO3NsQkAAAzDsPz/dHpDpi4CjwalydS4d90BAAAAAAAAAADAG3A2h/wuHtDxTwAAAABJRU5ErkJggg==',
    );
    final file = Api2FileAttachment(
      data: base64Encode(bytes),
      mime: 'image/png',
    );
    expect(retainedPng(_user([file]), bytes), isTrue);
    final changed = List<int>.from(bytes);
    changed[20] ^= 1;
    expect(retainedPng(_user([file]), changed), isFalse);
    expect(retainedPng(_user([file]), bytes.sublist(1)), isFalse);
    expect(retainedPng(_user([]), bytes), isFalse);
    expect(retainedPng(_user([file, file]), bytes), isFalse);
    expect(
      retainedPng(
        _user([Api2FileAttachment(data: file.data, mime: 'image/jpeg')]),
        bytes,
      ),
      isFalse,
    );
    expect(
      retainedPng(
        _user([
          Api2FileAttachment(
            mime: 'image/png',
            sourceUri: 'data:image/png;base64,${file.data}',
          ),
        ]),
        bytes,
      ),
      isFalse,
    );
    expect(
      retainedPng(
        _user([Api2FileAttachment(data: 'not base64!', mime: 'image/png')]),
        bytes,
      ),
      isFalse,
    );
    expect(
      retainedPng(_user([Api2FileAttachment(data: '', mime: 'image/png')]), []),
      isFalse,
    );
  });

  test(
    'image answer requires exact panel colors in JSON or one whole JSON fence',
    () {
      for (final answer in [
        '{"left":"red","right":"blue"}',
        ' \n{"right":"blue","left":"red"}\n ',
        '```json\n{"left":"red","right":"blue"}\n```',
        '```JSON\r\n{"left":"red","right":"blue"}\r\n```',
        '```\n{"left":"red","right":"blue"}\n```',
      ]) {
        expect(imageAnswerMatches(answer), isTrue);
      }
      for (final answer in [
        'Image received.',
        '{"left":"blue","right":"red"}',
        '{"left":"Red","right":"blue"}',
        '{"left":"red"}',
        '{"left":"red","right":"blue","guess":true}',
        '[{"left":"red","right":"blue"}]',
        '{"left":{"color":"red"},"right":"blue"}',
        'Here is the answer: {"left":"red","right":"blue"}',
        '```json\n{"left":"red","right":"blue"}\n```\nI guessed.',
        '```json\n{"left":"red","right":"blue"}',
        '',
      ]) {
        expect(imageAnswerMatches(answer), isFalse);
      }
    },
  );

  test(
    'provider authentication failure uses only the exact structured discriminator',
    () {
      expect(
        providerAuthFailure(Api2StructuredError(type: 'provider.auth')),
        isTrue,
      );
      for (final error in [
        null,
        Api2StructuredError(
          type: 'provider.unknown',
          status: 401,
          message: 'provider.auth',
        ),
        Api2StructuredError(type: 'provider.auth-extra'),
        Api2StructuredError(type: 'Provider.Auth'),
        Api2StructuredError(status: 403, message: 'authentication failed'),
      ]) {
        expect(providerAuthFailure(error), isFalse);
      }
    },
  );
}

import 'package:flutter_test/flutter_test.dart';
import 'package:opencode_mobile/api/models.dart';
import 'package:opencode_mobile/api2/models.dart';

void main() {
  test('bareModelID strips only this provider\'s prefix', () {
    expect(bareModelID('openai', 'openai/gpt-5.6-sol'), 'gpt-5.6-sol');
    expect(bareModelID('openai', 'gpt-5.6-sol'), 'gpt-5.6-sol');
    expect(bareModelID('openai', 'anthropic/claude'), 'anthropic/claude');
    expect(bareModelID('', 'openai/gpt'), 'openai/gpt');
    expect(bareModelID('openai', 'openai/'), 'openai/');
  });

  test('ModelRef.normalized returns itself when already bare', () {
    final bare = ModelRef(providerID: 'openai', modelID: 'gpt-5.6-sol');
    expect(identical(bare.normalized, bare), isTrue);
    final composite = ModelRef(
      providerID: 'openai',
      modelID: 'openai/gpt-5.6-sol',
    );
    expect(composite.normalized.modelID, 'gpt-5.6-sol');
    expect(composite.normalized.providerID, 'openai');
  });

  test('a v2 model with a composite id and no modelID gets a bare modelID', () {
    final info = Api2ModelInfo.fromJson({
      'id': 'openai/gpt-5.6-sol',
      'providerID': 'openai',
      'name': 'GPT-5.6 Sol',
      'capabilities': {
        'tools': true,
        'input': ['text'],
        'output': ['text'],
      },
      'variants': [],
      'time': {'released': 0},
      'cost': {
        'input': 0,
        'output': 0,
        'cache': {'read': 0, 'write': 0},
      },
      'status': 'active',
      'enabled': true,
      'limit': {'context': 1, 'output': 1},
    });
    expect(info?.modelID, 'gpt-5.6-sol');
    final explicit = Api2ModelInfo.fromJson({
      'id': 'openai/gpt-5.6-sol',
      'modelID': 'gpt-5.6-sol',
      'providerID': 'openai',
      'name': 'GPT-5.6 Sol',
    });
    expect(explicit?.modelID, 'gpt-5.6-sol');
  });

  test('a v2 model ref with a composite id is sent bare', () {
    final ref = Api2ModelRef.fromJson({
      'id': 'openai/gpt-5.6-sol',
      'providerID': 'openai',
    });
    expect(ref?.id, 'gpt-5.6-sol');
    expect(ref?.toJson(), {'id': 'gpt-5.6-sol', 'providerID': 'openai'});
  });

  test('error headline drops the class name and the stack trace', () {
    const raw =
        'ProviderModelNotFoundError: Model not found: openai/x. Did you mean: x?\n'
        '    at <anonymous> (/chunk.js:1)\n    at SessionPrompt.run (/chunk.js:2)';
    expect(errorHeadline(raw), 'Model not found: openai/x. Did you mean: x?');
    expect(errorHasDetails(raw), isTrue);
    expect(errorHeadline('plain message'), 'plain message');
    expect(errorHasDetails('plain message'), isFalse);
    expect(
      MessageErrorKind.refineFromText(MessageErrorKind.unknown, raw),
      MessageErrorKind.modelNotFound,
    );
    expect(
      MessageErrorKind.refineFromText(MessageErrorKind.providerAuth, raw),
      MessageErrorKind.providerAuth,
    );
    expect(
      MessageErrorKind.fromName('ProviderModelNotFoundError'),
      MessageErrorKind.modelNotFound,
    );
  });
}

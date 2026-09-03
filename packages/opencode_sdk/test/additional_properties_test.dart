import 'package:opencode_sdk/opencode_sdk.dart';
import 'package:test/test.dart';

void expectRoundTrip<T>(
  Map<String, dynamic> fixture,
  T Function(Map<String, dynamic>) fromJson,
  Object? Function(T) toJson,
) {
  expect(toJson(fromJson(fixture)), equals(fixture));
}

void main() {
  test('AgentConfig retains typed fields and arbitrary unknown entries', () {
    final fixture = <String, dynamic>{
      'model': 'openai/gpt-5',
      'top_p': 0.8,
      'customNumber': 7,
      'customObject': {
        'nested': <Object?>[true, null, 'value'],
      },
    };
    final value = AgentConfig.fromJson(fixture);
    expect(value.model, 'openai/gpt-5');
    expect(value.topP, 0.8);
    expect(value.additionalProperties.keys, {'customNumber', 'customObject'});
    expect(value.toJson(), fixture);
  });

  test('ProviderConfig.options retains named fields and provider options', () {
    final fixture = <String, dynamic>{
      'apiKey': 'secret',
      'timeout': false,
      'region': 'eu-west-1',
      'transport': {
        'retries': 3,
        'tags': <Object?>['fast', null],
      },
    };
    final value = ProviderConfigOptions.fromJson(fixture);
    expect(value.apiKey, 'secret');
    expect(value.timeout?.toJson(), false);
    expect(value.additionalProperties['region'], 'eu-west-1');
    expect(value.toJson(), fixture);
  });

  test('nested provider model variants retain arbitrary variant options', () {
    final fixture = <String, dynamic>{
      'name': 'Example provider',
      'options': {
        'baseURL': 'https://example.test',
        'providerFlag': {'enabled': true},
      },
      'models': {
        'model-a': {
          'id': 'model-a',
          'variants': {
            'fast': {
              'disabled': false,
              'reasoningEffort': 'low',
              'limits': {'tokens': 2048},
            },
          },
        },
      },
    };
    final value = ProviderConfig.fromJson(fixture);
    final variant = value.models!['model-a']!.variants!['fast']!;
    expect(variant.disabled, isFalse);
    expect(variant.additionalProperties['reasoningEffort'], 'low');
    expect(value.options!.additionalProperties['providerFlag'], {
      'enabled': true,
    });
    expect(value.toJson(), fixture);
  });

  test('Config decodes a disabled provider chunk timeout', () {
    final fixture = <String, dynamic>{
      'provider': {
        'custom-provider': {
          'options': {'chunkTimeout': false},
        },
      },
    };
    final value = Config.fromJson(fixture);
    expect(
      value.provider!['custom-provider']!.options!.chunkTimeout?.toJson(),
      isFalse,
    );
    expect(value.toJson(), fixture);
  });

  test('Config.mode retains named and arbitrary typed agent entries', () {
    final fixture = <String, dynamic>{
      'build': {'model': 'openai/gpt-5', 'buildOption': 1},
      'review': {
        'description': 'Reviews changes',
        'mode': 'subagent',
        'reviewOption': {'strict': true},
      },
    };
    final value = ConfigMode.fromJson(fixture);
    expect(value.build?.model, 'openai/gpt-5');
    expect(value.additionalProperties['review'], isA<AgentConfig>());
    expect(
      value
          .additionalProperties['review']!
          .additionalProperties['reviewOption'],
      {'strict': true},
    );
    expect(value.toJson(), fixture);
  });

  test('Config.agent retains built-in and custom typed agent map entries', () {
    final fixture = <String, dynamic>{
      'plan': {'prompt': 'Plan carefully', 'planOption': true},
      'security-review': {
        'model': 'anthropic/claude',
        'tools': {'bash': false},
        'policy': <Object?>['dependencies', 'secrets'],
      },
    };
    final value = ConfigAgent.fromJson(fixture);
    expect(value.plan?.prompt, 'Plan carefully');
    expect(
      value.additionalProperties['security-review']?.model,
      'anthropic/claude',
    );
    expect(
      value
          .additionalProperties['security-review']
          ?.additionalProperties['policy'],
      ['dependencies', 'secrets'],
    );
    expect(value.toJson(), fixture);
  });

  test(
    'Config round trip composes agent maps and nested provider variants',
    () {
      final fixture = <String, dynamic>{
        'mode': {
          'custom-mode': {'model': 'provider/model', 'custom': 1},
        },
        'agent': {
          'custom-agent': {'description': 'Custom', 'custom': 2},
        },
        'provider': {
          'custom-provider': {
            'options': {'customOption': true},
            'models': {
              'custom-model': {
                'variants': {
                  'custom-variant': {'disabled': true, 'customVariant': 3},
                },
              },
            },
          },
        },
      };
      expectRoundTrip(fixture, Config.fromJson, (value) => value.toJson());
    },
  );

  test('PermissionConfig remains lossless for named and unknown rules', () {
    final fixture = <String, dynamic>{
      'read': 'allow',
      'custom_tool': {'*.secret': 'deny', '*': 'ask'},
    };
    expectRoundTrip(
      fixture,
      PermissionConfig.fromJson,
      (value) => value.toJson(),
    );
  });
}

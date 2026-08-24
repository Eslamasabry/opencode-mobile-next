// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'assistant_message_tokens.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AssistantMessageTokens _$AssistantMessageTokensFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('AssistantMessageTokens', json, ($checkedConvert) {
  $checkKeys(
    json,
    requiredKeys: const ['input', 'output', 'reasoning', 'cache'],
  );
  final val = AssistantMessageTokens(
    total: $checkedConvert('total', (v) => v as num?),
    input: $checkedConvert('input', (v) => v as num),
    output: $checkedConvert('output', (v) => v as num),
    reasoning: $checkedConvert('reasoning', (v) => v as num),
    cache: $checkedConvert(
      'cache',
      (v) => SessionTokensCache.fromJson(v as Map<String, dynamic>),
    ),
  );
  return val;
});

Map<String, dynamic> _$AssistantMessageTokensToJson(
  AssistantMessageTokens instance,
) => <String, dynamic>{
  'total': ?instance.total,
  'input': instance.input,
  'output': instance.output,
  'reasoning': instance.reasoning,
  'cache': instance.cache.toJson(),
};

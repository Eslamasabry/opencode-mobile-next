// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_tokens.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionTokens _$SessionTokensFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionTokens', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['input', 'output', 'reasoning', 'cache'],
      );
      final val = SessionTokens(
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

Map<String, dynamic> _$SessionTokensToJson(SessionTokens instance) =>
    <String, dynamic>{
      'input': instance.input,
      'output': instance.output,
      'reasoning': instance.reasoning,
      'cache': instance.cache.toJson(),
    };

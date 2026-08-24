// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'v2_session_prompt_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

V2SessionPromptRequest _$V2SessionPromptRequestFromJson(
  Map<String, dynamic> json,
) => $checkedCreate('V2SessionPromptRequest', json, ($checkedConvert) {
  $checkKeys(json, requiredKeys: const ['prompt']);
  final val = V2SessionPromptRequest(
    id: $checkedConvert('id', (v) => v as String?),
    prompt: $checkedConvert(
      'prompt',
      (v) => PromptInput.fromJson(v as Map<String, dynamic>),
    ),
    delivery: $checkedConvert(
      'delivery',
      (v) => $enumDecodeNullable(
        _$V2SessionPromptRequestDeliveryEnumEnumMap,
        v,
        unknownValue: V2SessionPromptRequestDeliveryEnum.unknownDefaultOpenApi,
      ),
    ),
    resume: $checkedConvert('resume', (v) => v as bool?),
  );
  return val;
});

Map<String, dynamic> _$V2SessionPromptRequestToJson(
  V2SessionPromptRequest instance,
) => <String, dynamic>{
  'id': ?instance.id,
  'prompt': instance.prompt.toJson(),
  'delivery': ?_$V2SessionPromptRequestDeliveryEnumEnumMap[instance.delivery],
  'resume': ?instance.resume,
};

const _$V2SessionPromptRequestDeliveryEnumEnumMap = {
  V2SessionPromptRequestDeliveryEnum.steer: 'steer',
  V2SessionPromptRequestDeliveryEnum.queue: 'queue',
  V2SessionPromptRequestDeliveryEnum.unknownDefaultOpenApi:
      'unknown_default_open_api',
};

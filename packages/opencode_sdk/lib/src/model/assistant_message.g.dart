// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'assistant_message.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

AssistantMessage _$AssistantMessageFromJson(Map<String, dynamic> json) =>
    $checkedCreate('AssistantMessage', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'role',
          'time',
          'parentID',
          'modelID',
          'providerID',
          'mode',
          'agent',
          'path',
          'cost',
          'tokens',
        ],
      );
      final val = AssistantMessage(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        role: $checkedConvert(
          'role',
          (v) => $enumDecode(
            _$AssistantMessageRoleEnumEnumMap,
            v,
            unknownValue: AssistantMessageRoleEnum.unknownDefaultOpenApi,
          ),
        ),
        time: $checkedConvert(
          'time',
          (v) => AssistantMessageTime.fromJson(v as Map<String, dynamic>),
        ),
        error: $checkedConvert(
          'error',
          (v) => v == null ? null : OpencodeSdkRawUnion001.fromJson(v),
        ),
        parentID: $checkedConvert('parentID', (v) => v as String),
        modelID: $checkedConvert('modelID', (v) => v as String),
        providerID: $checkedConvert('providerID', (v) => v as String),
        mode: $checkedConvert('mode', (v) => v as String),
        agent: $checkedConvert('agent', (v) => v as String),
        path: $checkedConvert(
          'path',
          (v) => AssistantMessagePath.fromJson(v as Map<String, dynamic>),
        ),
        summary: $checkedConvert('summary', (v) => v as bool?),
        cost: $checkedConvert('cost', (v) => v as num),
        tokens: $checkedConvert(
          'tokens',
          (v) => AssistantMessageTokens.fromJson(v as Map<String, dynamic>),
        ),
        structured: $checkedConvert('structured', (v) => v),
        variant: $checkedConvert('variant', (v) => v as String?),
        finish: $checkedConvert('finish', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$AssistantMessageToJson(AssistantMessage instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'role': _$AssistantMessageRoleEnumEnumMap[instance.role]!,
      'time': instance.time.toJson(),
      'error': ?instance.error?.toJson(),
      'parentID': instance.parentID,
      'modelID': instance.modelID,
      'providerID': instance.providerID,
      'mode': instance.mode,
      'agent': instance.agent,
      'path': instance.path.toJson(),
      'summary': ?instance.summary,
      'cost': instance.cost,
      'tokens': instance.tokens.toJson(),
      'structured': ?instance.structured,
      'variant': ?instance.variant,
      'finish': ?instance.finish,
    };

const _$AssistantMessageRoleEnumEnumMap = {
  AssistantMessageRoleEnum.assistant: 'assistant',
  AssistantMessageRoleEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

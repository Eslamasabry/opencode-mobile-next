// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'step_finish_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

StepFinishPart _$StepFinishPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('StepFinishPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const [
          'id',
          'sessionID',
          'messageID',
          'type',
          'reason',
          'cost',
          'tokens',
        ],
      );
      final val = StepFinishPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$StepFinishPartTypeEnumEnumMap,
            v,
            unknownValue: StepFinishPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        reason: $checkedConvert('reason', (v) => v as String),
        snapshot: $checkedConvert('snapshot', (v) => v as String?),
        cost: $checkedConvert('cost', (v) => v as num),
        tokens: $checkedConvert(
          'tokens',
          (v) => AssistantMessageTokens.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$StepFinishPartToJson(StepFinishPart instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'messageID': instance.messageID,
      'type': _$StepFinishPartTypeEnumEnumMap[instance.type]!,
      'reason': instance.reason,
      'snapshot': ?instance.snapshot,
      'cost': instance.cost,
      'tokens': instance.tokens.toJson(),
    };

const _$StepFinishPartTypeEnumEnumMap = {
  StepFinishPartTypeEnum.stepFinish: 'step-finish',
  StepFinishPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

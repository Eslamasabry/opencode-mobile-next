// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'step_start_part.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

StepStartPart _$StepStartPartFromJson(Map<String, dynamic> json) =>
    $checkedCreate('StepStartPart', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['id', 'sessionID', 'messageID', 'type'],
      );
      final val = StepStartPart(
        id: $checkedConvert('id', (v) => v as String),
        sessionID: $checkedConvert('sessionID', (v) => v as String),
        messageID: $checkedConvert('messageID', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$StepStartPartTypeEnumEnumMap,
            v,
            unknownValue: StepStartPartTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        snapshot: $checkedConvert('snapshot', (v) => v as String?),
      );
      return val;
    });

Map<String, dynamic> _$StepStartPartToJson(StepStartPart instance) =>
    <String, dynamic>{
      'id': instance.id,
      'sessionID': instance.sessionID,
      'messageID': instance.messageID,
      'type': _$StepStartPartTypeEnumEnumMap[instance.type]!,
      'snapshot': ?instance.snapshot,
    };

const _$StepStartPartTypeEnumEnumMap = {
  StepStartPartTypeEnum.stepStart: 'step-start',
  StepStartPartTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

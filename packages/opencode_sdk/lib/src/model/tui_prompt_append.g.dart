// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_prompt_append.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiPromptAppend _$TuiPromptAppendFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TuiPromptAppend', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = TuiPromptAppend(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$TuiPromptAppendTypeEnumEnumMap,
            v,
            unknownValue: TuiPromptAppendTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        durable: $checkedConvert(
          'durable',
          (v) => v == null
              ? null
              : SessionStatusSchema2Durable.fromJson(v as Map<String, dynamic>),
        ),
        location: $checkedConvert(
          'location',
          (v) => v == null
              ? null
              : LocationRef.fromJson(v as Map<String, dynamic>),
        ),
        data: $checkedConvert(
          'data',
          (v) =>
              FindText200ResponseInnerPath.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$TuiPromptAppendToJson(TuiPromptAppend instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$TuiPromptAppendTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$TuiPromptAppendTypeEnumEnumMap = {
  TuiPromptAppendTypeEnum.tuiPeriodPromptPeriodAppend: 'tui.prompt.append',
  TuiPromptAppendTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

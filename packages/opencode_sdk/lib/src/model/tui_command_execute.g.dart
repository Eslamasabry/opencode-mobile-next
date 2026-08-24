// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_command_execute.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiCommandExecute _$TuiCommandExecuteFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TuiCommandExecute', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = TuiCommandExecute(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$TuiCommandExecuteTypeEnumEnumMap,
            v,
            unknownValue: TuiCommandExecuteTypeEnum.unknownDefaultOpenApi,
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
          (v) => TuiCommandExecuteData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$TuiCommandExecuteToJson(TuiCommandExecute instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$TuiCommandExecuteTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$TuiCommandExecuteTypeEnumEnumMap = {
  TuiCommandExecuteTypeEnum.tuiPeriodCommandPeriodExecute:
      'tui.command.execute',
  TuiCommandExecuteTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

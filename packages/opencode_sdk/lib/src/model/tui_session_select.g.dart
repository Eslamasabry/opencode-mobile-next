// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_session_select.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiSessionSelect _$TuiSessionSelectFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TuiSessionSelect', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = TuiSessionSelect(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$TuiSessionSelectTypeEnumEnumMap,
            v,
            unknownValue: TuiSessionSelectTypeEnum.unknownDefaultOpenApi,
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
          (v) => TuiSelectSessionRequest.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$TuiSessionSelectToJson(TuiSessionSelect instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$TuiSessionSelectTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$TuiSessionSelectTypeEnumEnumMap = {
  TuiSessionSelectTypeEnum.tuiPeriodSessionPeriodSelect: 'tui.session.select',
  TuiSessionSelectTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

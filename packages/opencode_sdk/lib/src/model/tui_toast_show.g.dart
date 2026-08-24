// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'tui_toast_show.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

TuiToastShow _$TuiToastShowFromJson(Map<String, dynamic> json) =>
    $checkedCreate('TuiToastShow', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
      final val = TuiToastShow(
        id: $checkedConvert('id', (v) => v as String),
        metadata: $checkedConvert('metadata', (v) => v),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$TuiToastShowTypeEnumEnumMap,
            v,
            unknownValue: TuiToastShowTypeEnum.unknownDefaultOpenApi,
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
          (v) => TuiShowToastRequest.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$TuiToastShowToJson(TuiToastShow instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$TuiToastShowTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data.toJson(),
    };

const _$TuiToastShowTypeEnumEnumMap = {
  TuiToastShowTypeEnum.tuiPeriodToastPeriodShow: 'tui.toast.show',
  TuiToastShowTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

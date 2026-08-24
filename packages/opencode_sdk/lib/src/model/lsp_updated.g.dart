// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'lsp_updated.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

LspUpdated _$LspUpdatedFromJson(Map<String, dynamic> json) => $checkedCreate(
  'LspUpdated',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['id', 'type', 'data']);
    final val = LspUpdated(
      id: $checkedConvert('id', (v) => v as String),
      metadata: $checkedConvert('metadata', (v) => v),
      type: $checkedConvert(
        'type',
        (v) => $enumDecode(
          _$LspUpdatedTypeEnumEnumMap,
          v,
          unknownValue: LspUpdatedTypeEnum.unknownDefaultOpenApi,
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
        (v) =>
            v == null ? null : LocationRef.fromJson(v as Map<String, dynamic>),
      ),
      data: $checkedConvert('data', (v) => v as Object),
    );
    return val;
  },
);

Map<String, dynamic> _$LspUpdatedToJson(LspUpdated instance) =>
    <String, dynamic>{
      'id': instance.id,
      'metadata': ?instance.metadata,
      'type': _$LspUpdatedTypeEnumEnumMap[instance.type]!,
      'durable': ?instance.durable?.toJson(),
      'location': ?instance.location?.toJson(),
      'data': instance.data,
    };

const _$LspUpdatedTypeEnumEnumMap = {
  LspUpdatedTypeEnum.lspPeriodUpdated: 'lsp.updated',
  LspUpdatedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

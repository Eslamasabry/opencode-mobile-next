// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'symbol_source.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SymbolSource _$SymbolSourceFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SymbolSource', json, ($checkedConvert) {
      $checkKeys(
        json,
        requiredKeys: const ['text', 'type', 'path', 'range', 'name', 'kind'],
      );
      final val = SymbolSource(
        text: $checkedConvert(
          'text',
          (v) => FilePartSourceText.fromJson(v as Map<String, dynamic>),
        ),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$SymbolSourceTypeEnumEnumMap,
            v,
            unknownValue: SymbolSourceTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        path: $checkedConvert('path', (v) => v as String),
        range: $checkedConvert(
          'range',
          (v) => Range.fromJson(v as Map<String, dynamic>),
        ),
        name: $checkedConvert('name', (v) => v as String),
        kind: $checkedConvert('kind', (v) => (v as num).toInt()),
      );
      return val;
    });

Map<String, dynamic> _$SymbolSourceToJson(SymbolSource instance) =>
    <String, dynamic>{
      'text': instance.text.toJson(),
      'type': _$SymbolSourceTypeEnumEnumMap[instance.type]!,
      'path': instance.path,
      'range': instance.range.toJson(),
      'name': instance.name,
      'kind': instance.kind,
    };

const _$SymbolSourceTypeEnumEnumMap = {
  SymbolSourceTypeEnum.symbol: 'symbol',
  SymbolSourceTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

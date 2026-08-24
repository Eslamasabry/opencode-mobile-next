// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'symbol.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

Symbol _$SymbolFromJson(Map<String, dynamic> json) =>
    $checkedCreate('Symbol', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'kind', 'location']);
      final val = Symbol(
        name: $checkedConvert('name', (v) => v as String),
        kind: $checkedConvert('kind', (v) => (v as num).toInt()),
        location: $checkedConvert(
          'location',
          (v) => SymbolLocation.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SymbolToJson(Symbol instance) => <String, dynamic>{
  'name': instance.name,
  'kind': instance.kind,
  'location': instance.location.toJson(),
};

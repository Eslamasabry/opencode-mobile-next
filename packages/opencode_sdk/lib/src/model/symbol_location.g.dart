// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'symbol_location.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SymbolLocation _$SymbolLocationFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SymbolLocation', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['uri', 'range']);
      final val = SymbolLocation(
        uri: $checkedConvert('uri', (v) => v as String),
        range: $checkedConvert(
          'range',
          (v) => Range.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SymbolLocationToJson(SymbolLocation instance) =>
    <String, dynamic>{'uri': instance.uri, 'range': instance.range.toJson()};

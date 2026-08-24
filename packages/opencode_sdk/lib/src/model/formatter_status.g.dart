// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'formatter_status.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

FormatterStatus _$FormatterStatusFromJson(Map<String, dynamic> json) =>
    $checkedCreate('FormatterStatus', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['name', 'extensions', 'enabled']);
      final val = FormatterStatus(
        name: $checkedConvert('name', (v) => v as String),
        extensions: $checkedConvert(
          'extensions',
          (v) => (v as List<dynamic>).map((e) => e as String).toList(),
        ),
        enabled: $checkedConvert('enabled', (v) => v as bool),
      );
      return val;
    });

Map<String, dynamic> _$FormatterStatusToJson(FormatterStatus instance) =>
    <String, dynamic>{
      'name': instance.name,
      'extensions': instance.extensions,
      'enabled': instance.enabled,
    };

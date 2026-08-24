// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of67_properties.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf67Properties
_$OpencodeSdkRawUnion002AnyOf67PropertiesFromJson(Map<String, dynamic> json) =>
    $checkedCreate('OpencodeSdkRawUnion002AnyOf67Properties', json, (
      $checkedConvert,
    ) {
      $checkKeys(json, requiredKeys: const ['command']);
      final val = OpencodeSdkRawUnion002AnyOf67Properties(
        command: $checkedConvert(
          'command',
          (v) => OpencodeSdkRawUnion002AnyOf67PropertiesCommand.fromJson(
            v as Map<String, dynamic>,
          ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf67PropertiesToJson(
  OpencodeSdkRawUnion002AnyOf67Properties instance,
) => <String, dynamic>{'command': instance.command.toJson()};

// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'opencode_sdk_raw_union002_any_of45_properties.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

OpencodeSdkRawUnion002AnyOf45Properties
_$OpencodeSdkRawUnion002AnyOf45PropertiesFromJson(Map<String, dynamic> json) =>
    $checkedCreate('OpencodeSdkRawUnion002AnyOf45Properties', json, (
      $checkedConvert,
    ) {
      final val = OpencodeSdkRawUnion002AnyOf45Properties(
        sessionID: $checkedConvert('sessionID', (v) => v as String?),
        error: $checkedConvert(
          'error',
          (v) => v == null
              ? null
              : OpencodeSdkRawUnion002AnyOf45PropertiesError.fromJson(
                  v as Map<String, dynamic>,
                ),
        ),
      );
      return val;
    });

Map<String, dynamic> _$OpencodeSdkRawUnion002AnyOf45PropertiesToJson(
  OpencodeSdkRawUnion002AnyOf45Properties instance,
) => <String, dynamic>{
  'sessionID': ?instance.sessionID,
  'error': ?instance.error?.toJson(),
};

// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'event_file_edited.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

EventFileEdited _$EventFileEditedFromJson(Map<String, dynamic> json) =>
    $checkedCreate('EventFileEdited', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['id', 'type', 'properties']);
      final val = EventFileEdited(
        id: $checkedConvert('id', (v) => v as String),
        type: $checkedConvert(
          'type',
          (v) => $enumDecode(
            _$EventFileEditedTypeEnumEnumMap,
            v,
            unknownValue: EventFileEditedTypeEnum.unknownDefaultOpenApi,
          ),
        ),
        properties: $checkedConvert(
          'properties',
          (v) => FileEditedData.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$EventFileEditedToJson(EventFileEdited instance) =>
    <String, dynamic>{
      'id': instance.id,
      'type': _$EventFileEditedTypeEnumEnumMap[instance.type]!,
      'properties': instance.properties.toJson(),
    };

const _$EventFileEditedTypeEnumEnumMap = {
  EventFileEditedTypeEnum.filePeriodEdited: 'file.edited',
  EventFileEditedTypeEnum.unknownDefaultOpenApi: 'unknown_default_open_api',
};

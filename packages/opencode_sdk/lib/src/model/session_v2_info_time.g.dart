// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_v2_info_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionV2InfoTime _$SessionV2InfoTimeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionV2InfoTime', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['created', 'updated']);
      final val = SessionV2InfoTime(
        created: $checkedConvert('created', (v) => v as num),
        updated: $checkedConvert('updated', (v) => v as num),
        archived: $checkedConvert('archived', (v) => v as num?),
      );
      return val;
    });

Map<String, dynamic> _$SessionV2InfoTimeToJson(SessionV2InfoTime instance) =>
    <String, dynamic>{
      'created': instance.created,
      'updated': instance.updated,
      'archived': ?instance.archived,
    };

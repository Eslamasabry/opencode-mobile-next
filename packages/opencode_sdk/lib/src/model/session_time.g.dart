// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_time.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionTime _$SessionTimeFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionTime', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['created', 'updated']);
      final val = SessionTime(
        created: $checkedConvert('created', (v) => (v as num).toInt()),
        updated: $checkedConvert('updated', (v) => (v as num).toInt()),
        compacting: $checkedConvert('compacting', (v) => (v as num?)?.toInt()),
        archived: $checkedConvert('archived', (v) => v as num?),
      );
      return val;
    });

Map<String, dynamic> _$SessionTimeToJson(SessionTime instance) =>
    <String, dynamic>{
      'created': instance.created,
      'updated': instance.updated,
      'compacting': ?instance.compacting,
      'archived': ?instance.archived,
    };

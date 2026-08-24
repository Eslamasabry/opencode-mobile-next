// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'session_history.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SessionHistory _$SessionHistoryFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SessionHistory', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['data', 'hasMore']);
      final val = SessionHistory(
        data: $checkedConvert(
          'data',
          (v) =>
              (v as List<dynamic>).map(SessionDurableEvent.fromJson).toList(),
        ),
        hasMore: $checkedConvert('hasMore', (v) => v as bool),
      );
      return val;
    });

Map<String, dynamic> _$SessionHistoryToJson(SessionHistory instance) =>
    <String, dynamic>{
      'data': instance.data.map((e) => e.toJson()).toList(),
      'hasMore': instance.hasMore,
    };

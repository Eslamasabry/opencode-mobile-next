// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_replay_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncReplayRequest _$SyncReplayRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('SyncReplayRequest', json, ($checkedConvert) {
      $checkKeys(json, requiredKeys: const ['directory', 'events']);
      final val = SyncReplayRequest(
        directory: $checkedConvert('directory', (v) => v as String),
        events: $checkedConvert(
          'events',
          (v) => (v as List<dynamic>)
              .map(
                (e) => SyncReplayRequestEventsInner.fromJson(
                  e as Map<String, dynamic>,
                ),
              )
              .toList(),
        ),
      );
      return val;
    });

Map<String, dynamic> _$SyncReplayRequestToJson(SyncReplayRequest instance) =>
    <String, dynamic>{
      'directory': instance.directory,
      'events': instance.events.map((e) => e.toJson()).toList(),
    };

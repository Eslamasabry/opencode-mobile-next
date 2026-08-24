// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'sync_event_session_next_tool_called_sync_event_data_provider.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

SyncEventSessionNextToolCalledSyncEventDataProvider
_$SyncEventSessionNextToolCalledSyncEventDataProviderFromJson(
  Map<String, dynamic> json,
) => $checkedCreate(
  'SyncEventSessionNextToolCalledSyncEventDataProvider',
  json,
  ($checkedConvert) {
    $checkKeys(json, requiredKeys: const ['executed']);
    final val = SyncEventSessionNextToolCalledSyncEventDataProvider(
      executed: $checkedConvert('executed', (v) => v as bool),
      metadata: $checkedConvert(
        'metadata',
        (v) => (v as Map<String, dynamic>?)?.map(
          (k, e) => MapEntry(k, e as Object),
        ),
      ),
    );
    return val;
  },
);

Map<String, dynamic>
_$SyncEventSessionNextToolCalledSyncEventDataProviderToJson(
  SyncEventSessionNextToolCalledSyncEventDataProvider instance,
) => <String, dynamic>{
  'executed': instance.executed,
  'metadata': ?instance.metadata,
};

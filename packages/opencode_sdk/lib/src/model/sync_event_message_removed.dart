//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_message_removed_sync_event.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'sync_event_message_removed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SyncEventMessageRemoved {
  /// Returns a new [SyncEventMessageRemoved] instance.
  SyncEventMessageRemoved({
    required this.type,

    required this.id,

    required this.syncEvent,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SyncEventMessageRemovedTypeEnum.unknownDefaultOpenApi,
  )
  final SyncEventMessageRemovedTypeEnum type;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'syncEvent', required: true, includeIfNull: false)
  final SyncEventMessageRemovedSyncEvent syncEvent;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SyncEventMessageRemoved &&
            runtimeType == other.runtimeType &&
            equals(
              [type, id, syncEvent],
              [other.type, other.id, other.syncEvent],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([type, id, syncEvent]);

  factory SyncEventMessageRemoved.fromJson(Map<String, dynamic> json) =>
      _$SyncEventMessageRemovedFromJson(json);

  Map<String, dynamic> toJson() => _$SyncEventMessageRemovedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SyncEventMessageRemovedTypeEnum {
  @JsonValue(r'sync')
  sync_(r'sync'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SyncEventMessageRemovedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

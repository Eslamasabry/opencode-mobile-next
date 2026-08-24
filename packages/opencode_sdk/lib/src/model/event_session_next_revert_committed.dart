//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_committed_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_revert_committed.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextRevertCommitted {
  /// Returns a new [EventSessionNextRevertCommitted] instance.
  EventSessionNextRevertCommitted({
    required this.id,

    required this.type,

    required this.properties,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue:
        EventSessionNextRevertCommittedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextRevertCommittedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextRevertCommittedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextRevertCommitted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextRevertCommitted.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextRevertCommittedFromJson(json);

  Map<String, dynamic> toJson() =>
      _$EventSessionNextRevertCommittedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextRevertCommittedTypeEnum {
  @JsonValue(r'session.next.revert.committed')
  sessionPeriodNextPeriodRevertPeriodCommitted(
    r'session.next.revert.committed',
  ),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextRevertCommittedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

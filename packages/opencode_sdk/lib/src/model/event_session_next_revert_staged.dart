//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_revert_staged_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_revert_staged.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextRevertStaged {
  /// Returns a new [EventSessionNextRevertStaged] instance.
  EventSessionNextRevertStaged({
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
        EventSessionNextRevertStagedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextRevertStagedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextRevertStagedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextRevertStaged &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextRevertStaged.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextRevertStagedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextRevertStagedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextRevertStagedTypeEnum {
  @JsonValue(r'session.next.revert.staged')
  sessionPeriodNextPeriodRevertPeriodStaged(r'session.next.revert.staged'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextRevertStagedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

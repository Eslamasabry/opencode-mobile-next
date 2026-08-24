//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_context_updated_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_synthetic.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextSynthetic {
  /// Returns a new [EventSessionNextSynthetic] instance.
  EventSessionNextSynthetic({
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
    unknownEnumValue: EventSessionNextSyntheticTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextSyntheticTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextContextUpdatedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextSynthetic &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextSynthetic.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextSyntheticFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextSyntheticToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextSyntheticTypeEnum {
  @JsonValue(r'session.next.synthetic')
  sessionPeriodNextPeriodSynthetic(r'session.next.synthetic'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextSyntheticTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

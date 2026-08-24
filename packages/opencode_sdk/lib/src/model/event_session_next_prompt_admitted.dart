//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/sync_event_session_next_prompted_sync_event_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_next_prompt_admitted.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionNextPromptAdmitted {
  /// Returns a new [EventSessionNextPromptAdmitted] instance.
  EventSessionNextPromptAdmitted({
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
        EventSessionNextPromptAdmittedTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionNextPromptAdmittedTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SyncEventSessionNextPromptedSyncEventData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionNextPromptAdmitted &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionNextPromptAdmitted.fromJson(Map<String, dynamic> json) =>
      _$EventSessionNextPromptAdmittedFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionNextPromptAdmittedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionNextPromptAdmittedTypeEnum {
  @JsonValue(r'session.next.prompt.admitted')
  sessionPeriodNextPeriodPromptPeriodAdmitted(r'session.next.prompt.admitted'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionNextPromptAdmittedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

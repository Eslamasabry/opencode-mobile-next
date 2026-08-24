//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_diff_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'event_session_diff.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class EventSessionDiff {
  /// Returns a new [EventSessionDiff] instance.
  EventSessionDiff({
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
    unknownEnumValue: EventSessionDiffTypeEnum.unknownDefaultOpenApi,
  )
  final EventSessionDiffTypeEnum type;

  @JsonKey(name: r'properties', required: true, includeIfNull: false)
  final SessionDiffData properties;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is EventSessionDiff &&
            runtimeType == other.runtimeType &&
            equals(
              [id, type, properties],
              [other.id, other.type, other.properties],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([id, type, properties]);

  factory EventSessionDiff.fromJson(Map<String, dynamic> json) =>
      _$EventSessionDiffFromJson(json);

  Map<String, dynamic> toJson() => _$EventSessionDiffToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum EventSessionDiffTypeEnum {
  @JsonValue(r'session.diff')
  sessionPeriodDiff(r'session.diff'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const EventSessionDiffTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

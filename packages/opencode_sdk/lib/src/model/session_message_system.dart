//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message_agent_switched_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_system.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageSystem {
  /// Returns a new [SessionMessageSystem] instance.
  SessionMessageSystem({
    required this.id,

    this.metadata,

    required this.time,

    required this.type,

    required this.text,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionMessageAgentSwitchedTime time;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageSystemTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageSystemTypeEnum type;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageSystem &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, time, type, text],
              [other.id, other.metadata, other.time, other.type, other.text],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, time, type, text]);

  factory SessionMessageSystem.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageSystemFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageSystemToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageSystemTypeEnum {
  @JsonValue(r'system')
  system(r'system'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageSystemTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

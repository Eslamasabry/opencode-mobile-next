//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message_agent_switched_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_synthetic.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageSynthetic {
  /// Returns a new [SessionMessageSynthetic] instance.
  SessionMessageSynthetic({
    required this.id,

    this.metadata,

    required this.time,

    required this.sessionID,

    required this.text,

    required this.type,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionMessageAgentSwitchedTime time;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageSyntheticTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageSyntheticTypeEnum type;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageSynthetic &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, time, sessionID, text, type],
              [
                other.id,
                other.metadata,
                other.time,
                other.sessionID,
                other.text,
                other.type,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, time, sessionID, text, type]);

  factory SessionMessageSynthetic.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageSyntheticFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageSyntheticToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageSyntheticTypeEnum {
  @JsonValue(r'synthetic')
  synthetic(r'synthetic'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageSyntheticTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

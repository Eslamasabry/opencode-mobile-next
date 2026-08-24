//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/model_ref.dart';
import 'package:opencode_sdk/src/model/session_message_agent_switched_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_model_switched.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageModelSwitched {
  /// Returns a new [SessionMessageModelSwitched] instance.
  SessionMessageModelSwitched({
    required this.id,

    this.metadata,

    required this.time,

    required this.type,

    required this.model,
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
    unknownEnumValue: SessionMessageModelSwitchedTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageModelSwitchedTypeEnum type;

  @JsonKey(name: r'model', required: true, includeIfNull: false)
  final ModelRef model;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageModelSwitched &&
            runtimeType == other.runtimeType &&
            equals(
              [id, metadata, time, type, model],
              [other.id, other.metadata, other.time, other.type, other.model],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([id, metadata, time, type, model]);

  factory SessionMessageModelSwitched.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageModelSwitchedFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageModelSwitchedToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageModelSwitchedTypeEnum {
  @JsonValue(r'model-switched')
  modelSwitched(r'model-switched'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageModelSwitchedTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

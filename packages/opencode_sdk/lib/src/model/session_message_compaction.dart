//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/session_message_agent_switched_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'session_message_compaction.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class SessionMessageCompaction {
  /// Returns a new [SessionMessageCompaction] instance.
  SessionMessageCompaction({
    required this.type,

    required this.reason,

    required this.summary,

    required this.recent,

    required this.id,

    this.metadata,

    required this.time,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageCompactionTypeEnum.unknownDefaultOpenApi,
  )
  final SessionMessageCompactionTypeEnum type;

  @JsonKey(
    name: r'reason',
    required: true,
    includeIfNull: false,
    unknownEnumValue: SessionMessageCompactionReasonEnum.unknownDefaultOpenApi,
  )
  final SessionMessageCompactionReasonEnum reason;

  @JsonKey(name: r'summary', required: true, includeIfNull: false)
  final String summary;

  @JsonKey(name: r'recent', required: true, includeIfNull: false)
  final String recent;

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final SessionMessageAgentSwitchedTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is SessionMessageCompaction &&
            runtimeType == other.runtimeType &&
            equals(
              [type, reason, summary, recent, id, metadata, time],
              [
                other.type,
                other.reason,
                other.summary,
                other.recent,
                other.id,
                other.metadata,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, reason, summary, recent, id, metadata, time]);

  factory SessionMessageCompaction.fromJson(Map<String, dynamic> json) =>
      _$SessionMessageCompactionFromJson(json);

  Map<String, dynamic> toJson() => _$SessionMessageCompactionToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum SessionMessageCompactionTypeEnum {
  @JsonValue(r'compaction')
  compaction(r'compaction'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageCompactionTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

enum SessionMessageCompactionReasonEnum {
  @JsonValue(r'auto')
  auto(r'auto'),
  @JsonValue(r'manual')
  manual(r'manual'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const SessionMessageCompactionReasonEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

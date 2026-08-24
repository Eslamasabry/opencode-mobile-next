//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/text_part_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'reasoning_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ReasoningPart {
  /// Returns a new [ReasoningPart] instance.
  ReasoningPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.text,

    this.metadata,

    required this.time,
  });

  @JsonKey(name: r'id', required: true, includeIfNull: false)
  final String id;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ReasoningPartTypeEnum.unknownDefaultOpenApi,
  )
  final ReasoningPartTypeEnum type;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  @JsonKey(name: r'time', required: true, includeIfNull: false)
  final TextPartTime time;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ReasoningPart &&
            runtimeType == other.runtimeType &&
            equals(
              [id, sessionID, messageID, type, text, metadata, time],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.text,
                other.metadata,
                other.time,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        id,
        sessionID,
        messageID,
        type,
        text,
        metadata,
        time,
      ]);

  factory ReasoningPart.fromJson(Map<String, dynamic> json) =>
      _$ReasoningPartFromJson(json);

  Map<String, dynamic> toJson() => _$ReasoningPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ReasoningPartTypeEnum {
  @JsonValue(r'reasoning')
  reasoning(r'reasoning'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ReasoningPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

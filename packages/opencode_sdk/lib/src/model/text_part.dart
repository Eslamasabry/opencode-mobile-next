//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/text_part_time.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'text_part.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class TextPart {
  /// Returns a new [TextPart] instance.
  TextPart({
    required this.id,

    required this.sessionID,

    required this.messageID,

    required this.type,

    required this.text,

    this.synthetic,

    this.ignored,

    this.time,

    this.metadata,
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
    unknownEnumValue: TextPartTypeEnum.unknownDefaultOpenApi,
  )
  final TextPartTypeEnum type;

  @JsonKey(name: r'text', required: true, includeIfNull: false)
  final String text;

  @JsonKey(name: r'synthetic', required: false, includeIfNull: false)
  final bool? synthetic;

  @JsonKey(name: r'ignored', required: false, includeIfNull: false)
  final bool? ignored;

  @JsonKey(name: r'time', required: false, includeIfNull: false)
  final TextPartTime? time;

  @JsonKey(name: r'metadata', required: false, includeIfNull: false)
  final Object? metadata;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is TextPart &&
            runtimeType == other.runtimeType &&
            equals(
              [
                id,
                sessionID,
                messageID,
                type,
                text,
                synthetic,
                ignored,
                time,
                metadata,
              ],
              [
                other.id,
                other.sessionID,
                other.messageID,
                other.type,
                other.text,
                other.synthetic,
                other.ignored,
                other.time,
                other.metadata,
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
        synthetic,
        ignored,
        time,
        metadata,
      ]);

  factory TextPart.fromJson(Map<String, dynamic> json) =>
      _$TextPartFromJson(json);

  Map<String, dynamic> toJson() => _$TextPartToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum TextPartTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const TextPartTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

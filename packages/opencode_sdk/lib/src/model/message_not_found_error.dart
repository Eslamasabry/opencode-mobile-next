//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'message_not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MessageNotFoundError {
  /// Returns a new [MessageNotFoundError] instance.
  MessageNotFoundError({
    required this.tag,

    required this.sessionID,

    required this.messageID,

    required this.message,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MessageNotFoundErrorTagEnum.unknownDefaultOpenApi,
  )
  final MessageNotFoundErrorTagEnum tag;

  @JsonKey(name: r'sessionID', required: true, includeIfNull: false)
  final String sessionID;

  @JsonKey(name: r'messageID', required: true, includeIfNull: false)
  final String messageID;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MessageNotFoundError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, sessionID, messageID, message],
              [other.tag, other.sessionID, other.messageID, other.message],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([tag, sessionID, messageID, message]);

  factory MessageNotFoundError.fromJson(Map<String, dynamic> json) =>
      _$MessageNotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$MessageNotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MessageNotFoundErrorTagEnum {
  @JsonValue(r'MessageNotFoundError')
  messageNotFoundError(r'MessageNotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MessageNotFoundErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

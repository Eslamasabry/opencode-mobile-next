//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'message_aborted_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MessageAbortedError {
  /// Returns a new [MessageAbortedError] instance.
  MessageAbortedError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MessageAbortedErrorNameEnum.unknownDefaultOpenApi,
  )
  final MessageAbortedErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final MoveSessionErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MessageAbortedError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory MessageAbortedError.fromJson(Map<String, dynamic> json) =>
      _$MessageAbortedErrorFromJson(json);

  Map<String, dynamic> toJson() => _$MessageAbortedErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MessageAbortedErrorNameEnum {
  @JsonValue(r'MessageAbortedError')
  messageAbortedError(r'MessageAbortedError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MessageAbortedErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

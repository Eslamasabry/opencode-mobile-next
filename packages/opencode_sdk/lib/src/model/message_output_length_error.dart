//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'message_output_length_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MessageOutputLengthError {
  /// Returns a new [MessageOutputLengthError] instance.
  MessageOutputLengthError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MessageOutputLengthErrorNameEnum.unknownDefaultOpenApi,
  )
  final MessageOutputLengthErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final Object data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MessageOutputLengthError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory MessageOutputLengthError.fromJson(Map<String, dynamic> json) =>
      _$MessageOutputLengthErrorFromJson(json);

  Map<String, dynamic> toJson() => _$MessageOutputLengthErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MessageOutputLengthErrorNameEnum {
  @JsonValue(r'MessageOutputLengthError')
  messageOutputLengthError(r'MessageOutputLengthError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MessageOutputLengthErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

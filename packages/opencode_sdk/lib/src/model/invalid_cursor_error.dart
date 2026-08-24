//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'invalid_cursor_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class InvalidCursorError {
  /// Returns a new [InvalidCursorError] instance.
  InvalidCursorError({required this.tag, required this.message});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: InvalidCursorErrorTagEnum.unknownDefaultOpenApi,
  )
  final InvalidCursorErrorTagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is InvalidCursorError &&
            runtimeType == other.runtimeType &&
            equals([tag, message], [other.tag, other.message]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([tag, message]);

  factory InvalidCursorError.fromJson(Map<String, dynamic> json) =>
      _$InvalidCursorErrorFromJson(json);

  Map<String, dynamic> toJson() => _$InvalidCursorErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum InvalidCursorErrorTagEnum {
  @JsonValue(r'InvalidCursorError')
  invalidCursorError(r'InvalidCursorError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const InvalidCursorErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

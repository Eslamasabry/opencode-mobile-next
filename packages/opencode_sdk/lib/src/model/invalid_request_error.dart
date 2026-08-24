//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'invalid_request_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class InvalidRequestError {
  /// Returns a new [InvalidRequestError] instance.
  InvalidRequestError({
    required this.tag,

    required this.message,

    this.kind,

    this.field,
  });

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: InvalidRequestErrorTagEnum.unknownDefaultOpenApi,
  )
  final InvalidRequestErrorTagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(name: r'kind', required: false, includeIfNull: false)
  final String? kind;

  @JsonKey(name: r'field', required: false, includeIfNull: false)
  final String? field;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is InvalidRequestError &&
            runtimeType == other.runtimeType &&
            equals(
              [tag, message, kind, field],
              [other.tag, other.message, other.kind, other.field],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([tag, message, kind, field]);

  factory InvalidRequestError.fromJson(Map<String, dynamic> json) =>
      _$InvalidRequestErrorFromJson(json);

  Map<String, dynamic> toJson() => _$InvalidRequestErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum InvalidRequestErrorTagEnum {
  @JsonValue(r'InvalidRequestError')
  invalidRequestError(r'InvalidRequestError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const InvalidRequestErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

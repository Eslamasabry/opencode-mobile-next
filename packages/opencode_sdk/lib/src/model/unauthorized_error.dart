//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'unauthorized_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UnauthorizedError {
  /// Returns a new [UnauthorizedError] instance.
  UnauthorizedError({required this.tag, required this.message});

  @JsonKey(
    name: r'_tag',
    required: true,
    includeIfNull: false,
    unknownEnumValue: UnauthorizedErrorTagEnum.unknownDefaultOpenApi,
  )
  final UnauthorizedErrorTagEnum tag;

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UnauthorizedError &&
            runtimeType == other.runtimeType &&
            equals([tag, message], [other.tag, other.message]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([tag, message]);

  factory UnauthorizedError.fromJson(Map<String, dynamic> json) =>
      _$UnauthorizedErrorFromJson(json);

  Map<String, dynamic> toJson() => _$UnauthorizedErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum UnauthorizedErrorTagEnum {
  @JsonValue(r'UnauthorizedError')
  unauthorizedError(r'UnauthorizedError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const UnauthorizedErrorTagEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

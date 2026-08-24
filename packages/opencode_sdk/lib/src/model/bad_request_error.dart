//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/bad_request_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'bad_request_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class BadRequestError {
  /// Returns a new [BadRequestError] instance.
  BadRequestError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: BadRequestErrorNameEnum.unknownDefaultOpenApi,
  )
  final BadRequestErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final BadRequestErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is BadRequestError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory BadRequestError.fromJson(Map<String, dynamic> json) =>
      _$BadRequestErrorFromJson(json);

  Map<String, dynamic> toJson() => _$BadRequestErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum BadRequestErrorNameEnum {
  @JsonValue(r'BadRequest')
  badRequest(r'BadRequest'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const BadRequestErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/unknown_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'unknown_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class UnknownError {
  /// Returns a new [UnknownError] instance.
  UnknownError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: UnknownErrorNameEnum.unknownDefaultOpenApi,
  )
  final UnknownErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final UnknownErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is UnknownError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory UnknownError.fromJson(Map<String, dynamic> json) =>
      _$UnknownErrorFromJson(json);

  Map<String, dynamic> toJson() => _$UnknownErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum UnknownErrorNameEnum {
  @JsonValue(r'UnknownError')
  unknownError(r'UnknownError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const UnknownErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

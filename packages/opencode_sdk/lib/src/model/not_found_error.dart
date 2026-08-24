//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/not_found_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'not_found_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class NotFoundError {
  /// Returns a new [NotFoundError] instance.
  NotFoundError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: NotFoundErrorNameEnum.unknownDefaultOpenApi,
  )
  final NotFoundErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final NotFoundErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is NotFoundError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory NotFoundError.fromJson(Map<String, dynamic> json) =>
      _$NotFoundErrorFromJson(json);

  Map<String, dynamic> toJson() => _$NotFoundErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum NotFoundErrorNameEnum {
  @JsonValue(r'NotFoundError')
  notFoundError(r'NotFoundError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const NotFoundErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

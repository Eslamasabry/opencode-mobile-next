//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/context_overflow_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'context_overflow_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ContextOverflowError {
  /// Returns a new [ContextOverflowError] instance.
  ContextOverflowError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: ContextOverflowErrorNameEnum.unknownDefaultOpenApi,
  )
  final ContextOverflowErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final ContextOverflowErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ContextOverflowError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory ContextOverflowError.fromJson(Map<String, dynamic> json) =>
      _$ContextOverflowErrorFromJson(json);

  Map<String, dynamic> toJson() => _$ContextOverflowErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum ContextOverflowErrorNameEnum {
  @JsonValue(r'ContextOverflowError')
  contextOverflowError(r'ContextOverflowError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const ContextOverflowErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

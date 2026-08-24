//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/move_session_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'move_session_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class MoveSessionError {
  /// Returns a new [MoveSessionError] instance.
  MoveSessionError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: MoveSessionErrorNameEnum.unknownDefaultOpenApi,
  )
  final MoveSessionErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final MoveSessionErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is MoveSessionError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory MoveSessionError.fromJson(Map<String, dynamic> json) =>
      _$MoveSessionErrorFromJson(json);

  Map<String, dynamic> toJson() => _$MoveSessionErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum MoveSessionErrorNameEnum {
  @JsonValue(r'MoveSessionError')
  moveSessionError(r'MoveSessionError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const MoveSessionErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

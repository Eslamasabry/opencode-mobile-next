//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/vcs_apply_error_data.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_apply_error.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsApplyError {
  /// Returns a new [VcsApplyError] instance.
  VcsApplyError({required this.name, required this.data});

  @JsonKey(
    name: r'name',
    required: true,
    includeIfNull: false,
    unknownEnumValue: VcsApplyErrorNameEnum.unknownDefaultOpenApi,
  )
  final VcsApplyErrorNameEnum name;

  @JsonKey(name: r'data', required: true, includeIfNull: false)
  final VcsApplyErrorData data;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsApplyError &&
            runtimeType == other.runtimeType &&
            equals([name, data], [other.name, other.data]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, data]);

  factory VcsApplyError.fromJson(Map<String, dynamic> json) =>
      _$VcsApplyErrorFromJson(json);

  Map<String, dynamic> toJson() => _$VcsApplyErrorToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum VcsApplyErrorNameEnum {
  @JsonValue(r'VcsApplyError')
  vcsApplyError(r'VcsApplyError'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const VcsApplyErrorNameEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

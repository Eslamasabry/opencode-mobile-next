//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_apply_error_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsApplyErrorData {
  /// Returns a new [VcsApplyErrorData] instance.
  VcsApplyErrorData({required this.message, required this.reason});

  @JsonKey(name: r'message', required: true, includeIfNull: false)
  final String message;

  @JsonKey(
    name: r'reason',
    required: true,
    includeIfNull: false,
    unknownEnumValue: VcsApplyErrorDataReasonEnum.unknownDefaultOpenApi,
  )
  final VcsApplyErrorDataReasonEnum reason;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsApplyErrorData &&
            runtimeType == other.runtimeType &&
            equals([message, reason], [other.message, other.reason]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([message, reason]);

  factory VcsApplyErrorData.fromJson(Map<String, dynamic> json) =>
      _$VcsApplyErrorDataFromJson(json);

  Map<String, dynamic> toJson() => _$VcsApplyErrorDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum VcsApplyErrorDataReasonEnum {
  @JsonValue(r'non-git')
  nonGit(r'non-git'),
  @JsonValue(r'not-clean')
  notClean(r'not-clean'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const VcsApplyErrorDataReasonEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

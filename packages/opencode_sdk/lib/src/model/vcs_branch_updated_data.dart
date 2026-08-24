//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_branch_updated_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsBranchUpdatedData {
  /// Returns a new [VcsBranchUpdatedData] instance.
  VcsBranchUpdatedData({this.branch});

  @JsonKey(name: r'branch', required: false, includeIfNull: false)
  final String? branch;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsBranchUpdatedData &&
            runtimeType == other.runtimeType &&
            equals([branch], [other.branch]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([branch]);

  factory VcsBranchUpdatedData.fromJson(Map<String, dynamic> json) =>
      _$VcsBranchUpdatedDataFromJson(json);

  Map<String, dynamic> toJson() => _$VcsBranchUpdatedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

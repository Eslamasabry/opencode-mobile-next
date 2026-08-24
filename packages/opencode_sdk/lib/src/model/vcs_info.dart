//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_info.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsInfo {
  /// Returns a new [VcsInfo] instance.
  VcsInfo({this.branch, this.defaultBranch});

  @JsonKey(name: r'branch', required: false, includeIfNull: false)
  final String? branch;

  @JsonKey(name: r'default_branch', required: false, includeIfNull: false)
  final String? defaultBranch;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsInfo &&
            runtimeType == other.runtimeType &&
            equals(
              [branch, defaultBranch],
              [other.branch, other.defaultBranch],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([branch, defaultBranch]);

  factory VcsInfo.fromJson(Map<String, dynamic> json) =>
      _$VcsInfoFromJson(json);

  Map<String, dynamic> toJson() => _$VcsInfoToJson(this);

  String toString() {
    return toJson().toString();
  }
}

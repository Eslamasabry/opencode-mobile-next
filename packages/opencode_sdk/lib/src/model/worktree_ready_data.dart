//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'worktree_ready_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class WorktreeReadyData {
  /// Returns a new [WorktreeReadyData] instance.
  WorktreeReadyData({required this.name, this.branch});

  @JsonKey(name: r'name', required: true, includeIfNull: false)
  final String name;

  @JsonKey(name: r'branch', required: false, includeIfNull: false)
  final String? branch;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is WorktreeReadyData &&
            runtimeType == other.runtimeType &&
            equals([name, branch], [other.name, other.branch]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([name, branch]);

  factory WorktreeReadyData.fromJson(Map<String, dynamic> json) =>
      _$WorktreeReadyDataFromJson(json);

  Map<String, dynamic> toJson() => _$WorktreeReadyDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

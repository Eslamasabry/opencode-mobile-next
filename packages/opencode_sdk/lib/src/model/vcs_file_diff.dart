//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'vcs_file_diff.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class VcsFileDiff {
  /// Returns a new [VcsFileDiff] instance.
  VcsFileDiff({
    required this.file,

    this.patch_,

    required this.additions,

    required this.deletions,

    this.status,
  });

  @JsonKey(name: r'file', required: true, includeIfNull: false)
  final String file;

  @JsonKey(name: r'patch', required: false, includeIfNull: false)
  final String? patch_;

  @JsonKey(name: r'additions', required: true, includeIfNull: false)
  final num additions;

  @JsonKey(name: r'deletions', required: true, includeIfNull: false)
  final num deletions;

  @JsonKey(
    name: r'status',
    required: false,
    includeIfNull: false,
    unknownEnumValue: VcsFileDiffStatusEnum.unknownDefaultOpenApi,
  )
  final VcsFileDiffStatusEnum? status;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is VcsFileDiff &&
            runtimeType == other.runtimeType &&
            equals(
              [file, patch_, additions, deletions, status],
              [
                other.file,
                other.patch_,
                other.additions,
                other.deletions,
                other.status,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([file, patch_, additions, deletions, status]);

  factory VcsFileDiff.fromJson(Map<String, dynamic> json) =>
      _$VcsFileDiffFromJson(json);

  Map<String, dynamic> toJson() => _$VcsFileDiffToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum VcsFileDiffStatusEnum {
  @JsonValue(r'added')
  added(r'added'),
  @JsonValue(r'deleted')
  deleted(r'deleted'),
  @JsonValue(r'modified')
  modified(r'modified'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const VcsFileDiffStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_diff.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileDiff {
  /// Returns a new [FileDiff] instance.
  FileDiff({
    required this.path,

    required this.status,

    required this.additions,

    required this.deletions,

    required this.patch_,
  });

  @JsonKey(name: r'path', required: true, includeIfNull: false)
  final String path;

  @JsonKey(
    name: r'status',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FileDiffStatusEnum.unknownDefaultOpenApi,
  )
  final FileDiffStatusEnum status;

  // minimum: 0
  @JsonKey(name: r'additions', required: true, includeIfNull: false)
  final int additions;

  // minimum: 0
  @JsonKey(name: r'deletions', required: true, includeIfNull: false)
  final int deletions;

  @JsonKey(name: r'patch', required: true, includeIfNull: false)
  final String patch_;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileDiff &&
            runtimeType == other.runtimeType &&
            equals(
              [path, status, additions, deletions, patch_],
              [
                other.path,
                other.status,
                other.additions,
                other.deletions,
                other.patch_,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([path, status, additions, deletions, patch_]);

  factory FileDiff.fromJson(Map<String, dynamic> json) =>
      _$FileDiffFromJson(json);

  Map<String, dynamic> toJson() => _$FileDiffToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FileDiffStatusEnum {
  @JsonValue(r'added')
  added(r'added'),
  @JsonValue(r'modified')
  modified(r'modified'),
  @JsonValue(r'deleted')
  deleted(r'deleted'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileDiffStatusEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

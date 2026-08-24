//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_content_patch_hunks_inner.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_content_patch.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileContentPatch {
  /// Returns a new [FileContentPatch] instance.
  FileContentPatch({
    required this.oldFileName,

    required this.newFileName,

    this.oldHeader,

    this.newHeader,

    required this.hunks,

    this.index,
  });

  @JsonKey(name: r'oldFileName', required: true, includeIfNull: false)
  final String oldFileName;

  @JsonKey(name: r'newFileName', required: true, includeIfNull: false)
  final String newFileName;

  @JsonKey(name: r'oldHeader', required: false, includeIfNull: false)
  final String? oldHeader;

  @JsonKey(name: r'newHeader', required: false, includeIfNull: false)
  final String? newHeader;

  @JsonKey(name: r'hunks', required: true, includeIfNull: false)
  final List<FileContentPatchHunksInner> hunks;

  @JsonKey(name: r'index', required: false, includeIfNull: false)
  final String? index;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileContentPatch &&
            runtimeType == other.runtimeType &&
            equals(
              [oldFileName, newFileName, oldHeader, newHeader, hunks, index],
              [
                other.oldFileName,
                other.newFileName,
                other.oldHeader,
                other.newHeader,
                other.hunks,
                other.index,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([
        oldFileName,
        newFileName,
        oldHeader,
        newHeader,
        hunks,
        index,
      ]);

  factory FileContentPatch.fromJson(Map<String, dynamic> json) =>
      _$FileContentPatchFromJson(json);

  Map<String, dynamic> toJson() => _$FileContentPatchToJson(this);

  String toString() {
    return toJson().toString();
  }
}

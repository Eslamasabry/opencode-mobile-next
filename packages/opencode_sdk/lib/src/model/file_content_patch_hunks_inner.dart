//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_content_patch_hunks_inner.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileContentPatchHunksInner {
  /// Returns a new [FileContentPatchHunksInner] instance.
  FileContentPatchHunksInner({
    required this.oldStart,

    required this.oldLines,

    required this.newStart,

    required this.newLines,

    required this.lines,
  });

  // minimum: 0
  @JsonKey(name: r'oldStart', required: true, includeIfNull: false)
  final int oldStart;

  // minimum: 0
  @JsonKey(name: r'oldLines', required: true, includeIfNull: false)
  final int oldLines;

  // minimum: 0
  @JsonKey(name: r'newStart', required: true, includeIfNull: false)
  final int newStart;

  // minimum: 0
  @JsonKey(name: r'newLines', required: true, includeIfNull: false)
  final int newLines;

  @JsonKey(name: r'lines', required: true, includeIfNull: false)
  final List<String> lines;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileContentPatchHunksInner &&
            runtimeType == other.runtimeType &&
            equals(
              [oldStart, oldLines, newStart, newLines, lines],
              [
                other.oldStart,
                other.oldLines,
                other.newStart,
                other.newLines,
                other.lines,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([oldStart, oldLines, newStart, newLines, lines]);

  factory FileContentPatchHunksInner.fromJson(Map<String, dynamic> json) =>
      _$FileContentPatchHunksInnerFromJson(json);

  Map<String, dynamic> toJson() => _$FileContentPatchHunksInnerToJson(this);

  String toString() {
    return toJson().toString();
  }
}

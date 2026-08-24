//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_part_source_text.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FilePartSourceText {
  /// Returns a new [FilePartSourceText] instance.
  FilePartSourceText({
    required this.value,

    required this.start,

    required this.end,
  });

  @JsonKey(name: r'value', required: true, includeIfNull: false)
  final String value;

  @JsonKey(name: r'start', required: true, includeIfNull: false)
  final num start;

  @JsonKey(name: r'end', required: true, includeIfNull: false)
  final num end;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FilePartSourceText &&
            runtimeType == other.runtimeType &&
            equals([value, start, end], [other.value, other.start, other.end]);
  }

  int get hashCode =>
      runtimeType.hashCode ^ mapPropsToHashCode([value, start, end]);

  factory FilePartSourceText.fromJson(Map<String, dynamic> json) =>
      _$FilePartSourceTextFromJson(json);

  Map<String, dynamic> toJson() => _$FilePartSourceTextToJson(this);

  String toString() {
    return toJson().toString();
  }
}

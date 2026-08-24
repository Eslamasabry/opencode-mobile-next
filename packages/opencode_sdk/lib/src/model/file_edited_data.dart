//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_edited_data.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileEditedData {
  /// Returns a new [FileEditedData] instance.
  FileEditedData({required this.file});

  @JsonKey(name: r'file', required: true, includeIfNull: false)
  final String file;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileEditedData &&
            runtimeType == other.runtimeType &&
            equals([file], [other.file]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([file]);

  factory FileEditedData.fromJson(Map<String, dynamic> json) =>
      _$FileEditedDataFromJson(json);

  Map<String, dynamic> toJson() => _$FileEditedDataToJson(this);

  String toString() {
    return toJson().toString();
  }
}

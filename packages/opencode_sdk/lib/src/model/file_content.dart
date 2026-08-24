//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/file_content_patch.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'file_content.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class FileContent {
  /// Returns a new [FileContent] instance.
  FileContent({
    required this.type,

    required this.content,

    this.diff,

    this.patch_,

    this.encoding,

    this.mimeType,
  });

  @JsonKey(
    name: r'type',
    required: true,
    includeIfNull: false,
    unknownEnumValue: FileContentTypeEnum.unknownDefaultOpenApi,
  )
  final FileContentTypeEnum type;

  @JsonKey(name: r'content', required: true, includeIfNull: false)
  final String content;

  @JsonKey(name: r'diff', required: false, includeIfNull: false)
  final String? diff;

  @JsonKey(name: r'patch', required: false, includeIfNull: false)
  final FileContentPatch? patch_;

  @JsonKey(
    name: r'encoding',
    required: false,
    includeIfNull: false,
    unknownEnumValue: FileContentEncodingEnum.unknownDefaultOpenApi,
  )
  final FileContentEncodingEnum? encoding;

  @JsonKey(name: r'mimeType', required: false, includeIfNull: false)
  final String? mimeType;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is FileContent &&
            runtimeType == other.runtimeType &&
            equals(
              [type, content, diff, patch_, encoding, mimeType],
              [
                other.type,
                other.content,
                other.diff,
                other.patch_,
                other.encoding,
                other.mimeType,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([type, content, diff, patch_, encoding, mimeType]);

  factory FileContent.fromJson(Map<String, dynamic> json) =>
      _$FileContentFromJson(json);

  Map<String, dynamic> toJson() => _$FileContentToJson(this);

  String toString() {
    return toJson().toString();
  }
}

enum FileContentTypeEnum {
  @JsonValue(r'text')
  text(r'text'),
  @JsonValue(r'binary')
  binary(r'binary'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileContentTypeEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

enum FileContentEncodingEnum {
  @JsonValue(r'base64')
  base64(r'base64'),
  @JsonValue(r'unknown_default_open_api')
  unknownDefaultOpenApi(r'unknown_default_open_api');

  const FileContentEncodingEnum(this.value);

  final Object value;

  @override
  String toString() => value.toString();
}

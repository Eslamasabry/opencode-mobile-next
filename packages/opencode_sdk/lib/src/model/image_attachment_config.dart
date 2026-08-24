//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'image_attachment_config.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class ImageAttachmentConfig {
  /// Returns a new [ImageAttachmentConfig] instance.
  ImageAttachmentConfig({
    this.autoResize,

    this.maxWidth,

    this.maxHeight,

    this.maxBase64Bytes,
  });

  @JsonKey(name: r'auto_resize', required: false, includeIfNull: false)
  final bool? autoResize;

  @JsonKey(name: r'max_width', required: false, includeIfNull: false)
  final int? maxWidth;

  @JsonKey(name: r'max_height', required: false, includeIfNull: false)
  final int? maxHeight;

  @JsonKey(name: r'max_base64_bytes', required: false, includeIfNull: false)
  final int? maxBase64Bytes;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is ImageAttachmentConfig &&
            runtimeType == other.runtimeType &&
            equals(
              [autoResize, maxWidth, maxHeight, maxBase64Bytes],
              [
                other.autoResize,
                other.maxWidth,
                other.maxHeight,
                other.maxBase64Bytes,
              ],
            );
  }

  int get hashCode =>
      runtimeType.hashCode ^
      mapPropsToHashCode([autoResize, maxWidth, maxHeight, maxBase64Bytes]);

  factory ImageAttachmentConfig.fromJson(Map<String, dynamic> json) =>
      _$ImageAttachmentConfigFromJson(json);

  Map<String, dynamic> toJson() => _$ImageAttachmentConfigToJson(this);

  String toString() {
    return toJson().toString();
  }
}

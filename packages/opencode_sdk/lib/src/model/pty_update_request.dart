//
// AUTO-GENERATED FILE, DO NOT MODIFY!
//

// ignore_for_file: unused_element
import 'package:opencode_sdk/src/model/pty_update_request_size.dart';
import 'package:json_annotation/json_annotation.dart';
import 'package:equatable/src/equatable_utils.dart';

part 'pty_update_request.g.dart';

@JsonSerializable(
  checked: true,
  createToJson: true,
  disallowUnrecognizedKeys: false,
  explicitToJson: true,
)
class PtyUpdateRequest {
  /// Returns a new [PtyUpdateRequest] instance.
  PtyUpdateRequest({this.title, this.size});

  @JsonKey(name: r'title', required: false, includeIfNull: false)
  final String? title;

  @JsonKey(name: r'size', required: false, includeIfNull: false)
  final PtyUpdateRequestSize? size;

  bool operator ==(Object other) {
    return identical(this, other) ||
        other is PtyUpdateRequest &&
            runtimeType == other.runtimeType &&
            equals([title, size], [other.title, other.size]);
  }

  int get hashCode => runtimeType.hashCode ^ mapPropsToHashCode([title, size]);

  factory PtyUpdateRequest.fromJson(Map<String, dynamic> json) =>
      _$PtyUpdateRequestFromJson(json);

  Map<String, dynamic> toJson() => _$PtyUpdateRequestToJson(this);

  String toString() {
    return toJson().toString();
  }
}

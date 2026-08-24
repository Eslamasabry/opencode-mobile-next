// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'pty_update_request.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

PtyUpdateRequest _$PtyUpdateRequestFromJson(Map<String, dynamic> json) =>
    $checkedCreate('PtyUpdateRequest', json, ($checkedConvert) {
      final val = PtyUpdateRequest(
        title: $checkedConvert('title', (v) => v as String?),
        size: $checkedConvert(
          'size',
          (v) => v == null
              ? null
              : PtyUpdateRequestSize.fromJson(v as Map<String, dynamic>),
        ),
      );
      return val;
    });

Map<String, dynamic> _$PtyUpdateRequestToJson(PtyUpdateRequest instance) =>
    <String, dynamic>{
      'title': ?instance.title,
      'size': ?instance.size?.toJson(),
    };

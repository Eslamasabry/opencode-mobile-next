package io.github.eslamasabry.oc.android.migration.readers.prefs

import io.github.eslamasabry.oc.android.migration.readers.xml.RawPref

/**
 * `image_picker_android` 0.8.13+22 `ImagePickerCache` (`shared_prefs/flutter_image_picker_shared_preference.xml`).
 *
 * Audit 04+05 finding 11: when Android kills the app while the camera is open, the picker stores the result
 * path here and Dart only gets it from `retrieveLostData()`. The Flutter app's bootstrap recovery
 * (`lib/state/prompt_photos.dart`) asks for it when `oc.pendingPromptPhoto` has no completed attachment.
 * A native migration must therefore READ this file and hand the result to the pending-photo import, instead
 * of ignoring it and later deleting it.
 */
internal object ImagePickerCache {
    const val FILE_NAME: String = "flutter_image_picker_shared_preference"
    const val KEY_IMAGE_PATHS: String = "flutter_image_picker_image_path" // putStringSet
    const val KEY_ERROR_CODE: String = "flutter_image_picker_error_code"
    const val KEY_ERROR_MESSAGE: String = "flutter_image_picker_error_message"
    const val KEY_MAX_WIDTH: String = "flutter_image_picker_max_width" // putLong(doubleToRawLongBits)
    const val KEY_MAX_HEIGHT: String = "flutter_image_picker_max_height"
    const val KEY_IMAGE_QUALITY: String = "flutter_image_picker_image_quality" // putInt
    const val KEY_TYPE: String = "flutter_image_picker_type" // "image" | "video"
    const val KEY_PENDING_URI: String = "flutter_image_picker_pending_image_uri"

    /** A lost result. Paths are file paths (often under `cache/`); `toString()` hides them. */
    class LostResult(
        val type: String?,
        val paths: List<String>,
        val hasError: Boolean,
        val pendingCameraUri: String?,
        val maxWidth: Double?,
        val maxHeight: Double?,
        val imageQuality: Int?,
    ) {
        /** True when `retrieveLostData()` would return something. */
        val hasResult: Boolean get() = paths.isNotEmpty() || hasError
        override fun toString(): String =
            "LostResult(type=$type, paths=${paths.size}, hasError=$hasError, pendingCameraUri=${pendingCameraUri != null})"
    }

    /** Null when the file holds nothing the plugin would report. */
    fun read(map: Map<String, RawPref>): LostResult? {
        val paths = (map[KEY_IMAGE_PATHS] as? RawPref.StrSet)?.value?.toList().orEmpty()
        val hasError = map[KEY_ERROR_CODE] is RawPref.Str
        val pending = (map[KEY_PENDING_URI] as? RawPref.Str)?.value
        if (paths.isEmpty() && !hasError && pending == null) return null
        fun dbl(k: String) = (map[k] as? RawPref.Int64)?.value?.let { java.lang.Double.longBitsToDouble(it) }
        return LostResult(
            type = (map[KEY_TYPE] as? RawPref.Str)?.value,
            paths = paths,
            hasError = hasError,
            pendingCameraUri = pending,
            maxWidth = dbl(KEY_MAX_WIDTH),
            maxHeight = dbl(KEY_MAX_HEIGHT),
            imageQuality = (map[KEY_IMAGE_QUALITY] as? RawPref.Int32)?.value,
        )
    }
}

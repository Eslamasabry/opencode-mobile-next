package io.github.eslamasabry.oc.engine.files

import io.github.eslamasabry.oc.core.common.Outcome

/**
 * "Open with": hands an opened file's exact bytes to another app on this phone (Android: a private cache copy behind
 * the app's FileProvider, `ACTION_VIEW` with a read grant, through the system chooser). `Failure.Media(NoAppToOpen)`
 * when no app can open it. Bound only where the platform can; the viewer hides the action otherwise.
 */
public fun interface FileHandoff {
    public suspend fun openWith(data: FileData): Outcome<Unit>
}

/**
 * Save (B-MEDIA-001, files_viewer.dart `_download`): the exact original bytes to a place the person picks.
 * `Ok(true)` saved, `Ok(false)` the person cancelled the picker.
 */
public fun interface FileSaver {
    public suspend fun save(data: FileData): Outcome<Boolean>
}

/** One opened PDF. Pages render one at a time as PNG bytes, at most [width] pixels wide. */
public interface PdfDocument {
    public val pageCount: Int

    public suspend fun renderPage(index: Int, width: Int): Outcome<ByteArray>

    public suspend fun close()
}

/** PDF pages from the platform renderer (Android 10+ `PdfRenderer`). Encrypted and broken files are Media failures. */
public fun interface PdfPages {
    public suspend fun open(bytes: ByteArray): Outcome<PdfDocument>
}

/** Plan 10 §11.6 limits, the same as Flutter's `LocalPdf`. */
public object PdfLimits {
    public const val MAX_BYTES: Int = 10 * 1024 * 1024
    public const val MAX_PAGES: Int = 200
}

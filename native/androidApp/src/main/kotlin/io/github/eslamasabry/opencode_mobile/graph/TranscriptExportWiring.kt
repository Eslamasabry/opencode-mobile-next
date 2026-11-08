package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.files.TranscriptFile
import io.github.eslamasabry.oc.feature.chat.header.world.TranscriptExport

/*
 * S24-CHAT story 8 + s23-projects: the chat ⋯ menu's Export transcript saves the masked Markdown through the
 * platform's FileSaver (Android's "save as" document picker), as Flutter's `_exportTranscript` does. With no saver
 * bound the save fails plainly; desktop binds no TranscriptExport, so its menu has no Export item.
 */
@ContributesIntoSet(AppScope::class, binding = binding<TranscriptExport>())
@Inject
class AppTranscriptExport(private val savers: Set<FileSaver>) : TranscriptExport {
    override suspend fun saveMarkdown(fileName: String, markdown: String): Outcome<Boolean> {
        val saver = savers.firstOrNull() ?: return Outcome.Failed(Failure.Device())
        return saver.save(transcriptFileData(fileName, markdown))
    }
}

/** The saved file: the chat's name with only safe characters (never a path), UTF-8 Markdown. */
internal fun transcriptFileData(fileName: String, markdown: String): FileData {
    val stem = fileName.removeSuffix(".md").filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    val name = if (stem.isEmpty()) "opencode.md" else "$stem.md"
    return FileData(name, TranscriptFile.MIME, markdown.encodeToByteArray())
}

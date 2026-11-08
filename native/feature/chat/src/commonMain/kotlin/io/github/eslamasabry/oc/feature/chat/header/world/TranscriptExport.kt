package io.github.eslamasabry.oc.feature.chat.header.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome

/**
 * S24-CHAT story 8 (Flutter `_exportTranscript`): saves the conversation's Markdown (built and
 * masked by the chat, as Copy transcript is) to a file the person chooses. The file-save half is
 * the platform's (s23-projects builds the exporter); until one is bound the ⋯ menu offers no Export.
 * Ok(true) = saved, Ok(false) = the person cancelled the save dialog.
 */
public interface TranscriptExport {
    public suspend fun saveMarkdown(fileName: String, markdown: String): Outcome<Boolean>
}

@ContributesTo(AppScope::class)
public interface TranscriptExportBindings {
    @Multibinds(allowEmpty = true) public val transcriptExports: Set<TranscriptExport>
}

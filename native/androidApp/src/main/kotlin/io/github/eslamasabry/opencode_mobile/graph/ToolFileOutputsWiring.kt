package io.github.eslamasabry.opencode_mobile.graph

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import io.github.eslamasabry.oc.core.common.Failure
import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.engine.files.FileData
import io.github.eslamasabry.oc.engine.files.FileSaver
import io.github.eslamasabry.oc.engine.media.attach.DataUrl
import io.github.eslamasabry.oc.feature.chat.world.ToolFileOutputs

/* C7: a file the agent produced as inline content is saved through the platform's "save as" picker. */
@ContributesIntoSet(AppScope::class, binding = binding<ToolFileOutputs>())
@Inject
class AppToolFileOutputs(private val savers: Set<FileSaver>) : ToolFileOutputs {
    override suspend fun save(name: String, mime: String?, dataUrl: String): Outcome<Boolean> {
        val saver = savers.firstOrNull() ?: return Outcome.Failed(Failure.Device())
        val decoded = DataUrl.decode(dataUrl) ?: return Outcome.Failed(Failure.Device())
        // A name from the agent is never a path.
        val safe = name.substringAfterLast('/').substringAfterLast('\\').ifBlank { "file" }
        return saver.save(FileData(safe, mime ?: decoded.mime, decoded.bytes))
    }
}

package io.github.eslamasabry.oc.feature.chat.world

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds
import io.github.eslamasabry.oc.core.common.Outcome

/**
 * C7 (Flutter `_downloadToolOutputFile`): saves a file the agent produced, which arrived as inline `data:` content, to a
 * place the person picks. The save half is the platform's; with none bound the sheet offers Attach only.
 * Ok(true) = saved, Ok(false) = the person cancelled the picker.
 */
public interface ToolFileOutputs {
    public suspend fun save(name: String, mime: String?, dataUrl: String): Outcome<Boolean>
}

@ContributesTo(AppScope::class)
public interface ToolFileOutputsBindings {
    @Multibinds(allowEmpty = true) public val toolFileOutputs: Set<ToolFileOutputs>
}

/** A file the agent produced that has no project path or web address: its content travels inline. */
public fun isInlineFile(url: String?): Boolean = url != null && url.startsWith("data:")

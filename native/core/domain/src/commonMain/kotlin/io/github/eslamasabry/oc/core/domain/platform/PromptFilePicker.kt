package io.github.eslamasabry.oc.core.domain.platform

import io.github.eslamasabry.oc.core.common.Outcome
import io.github.eslamasabry.oc.core.model.PromptAttachment

/** User-selected, bounded prompt bytes. null means the system picker was cancelled. */
public fun interface PromptFilePicker {
    public suspend fun pickFile(current: List<PromptAttachment>): Outcome<PromptAttachment?>
}
